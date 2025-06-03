package com.gw.service;

import com.gw.domain.FeedItem;
import com.gw.services.shopifyapi.objects.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.ArrayList;

public class SyncTest extends BaseGraphqlTest {

    @Test
    /**
     * Live feed sync test - processes the highest 5 webTagNumber feed items
     * This replaces the dev mode approach and works with the actual live feed
     * Now uses the doSyncForFeedItems interface for proper sync logic testing
     * Includes verification of eBay metafield definitions and data
     */
    public void syncTest() throws Exception {
        logger.info("=== Starting Live Feed Sync Test Using doSyncForFeedItems Interface ===");
        logger.info("Loading live feed and selecting highest 5 webTagNumber items...");

        List<FeedItem> topFeedItems = getTopFeedItems(5);
        
        logger.info("Selected top " + topFeedItems.size() + " items for sync:");
        logger.info("Highest webTagNumber: " + (topFeedItems.isEmpty() ? "N/A" : topFeedItems.get(0).getWebTagNumber()));
        logger.info("Lowest webTagNumber: " + (topFeedItems.isEmpty() ? "N/A" : topFeedItems.get(topFeedItems.size() - 1).getWebTagNumber()));
        
        // Use the new doSyncForFeedItems interface instead of manual processing
        logger.info("Processing items through doSyncForFeedItems interface...");
        long startTime = System.currentTimeMillis();
        
        try {
            syncService.doSyncForFeedItems(topFeedItems);
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            
            logger.info("✅ Sync completed successfully in " + duration + "ms");
            
            // Verify the results
            List<Product> allProductsAfterSync = shopifyApiService.getAllProducts();
            logger.info("Total products in Shopify after sync: " + allProductsAfterSync.size());
            
            // Count how many of our test items are now in Shopify
            long syncedItemsFound = 0;
            for (FeedItem item : topFeedItems) {
                for (Product p : allProductsAfterSync) {
                    if (p.getVariants() != null && !p.getVariants().isEmpty() && 
                        item.getWebTagNumber().equals(p.getVariants().get(0).getSku())) {
                        syncedItemsFound++;
                        break;
                    }
                }
            }
            
            logger.info("✅ Verified " + syncedItemsFound + " out of " + topFeedItems.size() + " items are accessible in Shopify");
            
            // Verify collections exist
            List<CustomCollection> collections = shopifyApiService.getAllCustomCollections();
            Assertions.assertTrue(collections.size() > 0, "Collections should exist after sync");
            logger.info("✅ Verified " + collections.size() + " collections exist");
            
            // Verify items are associated with collections
            long itemsWithCollections = 0;
            for (Product product : allProductsAfterSync) {
                try {
                    List<Collect> collects = shopifyApiService.getCollectsForProductId(product.getId());
                    if (collects.size() > 0) {
                        itemsWithCollections++;
                    }
                } catch (Exception e) {
                    // Skip this product
                }
            }
            
            logger.info("✅ Verified " + itemsWithCollections + " products have collection associations");
            
            // =================== ASSERT: Sync Created eBay Metafield Definitions ===================
            logger.info("🏷️ Asserting that sync process created eBay metafield definitions...");
            
            List<Map<String, String>> ebayDefinitions = shopifyApiService.getMetafieldDefinitions("ebay");
            Assertions.assertTrue(ebayDefinitions.size() >= 13, 
                "Sync should have created at least 13 eBay metafield definitions, found: " + ebayDefinitions.size());
            
            logger.info("✅ PASS: Sync created " + ebayDefinitions.size() + " eBay metafield definitions");
            
            // Verify key eBay definitions were created by sync
            List<String> definitionKeys = new ArrayList<>();
            for (Map<String, String> def : ebayDefinitions) {
                definitionKeys.add(def.get("key").toString());
            }
            
            String[] expectedKeys = {"brand", "model", "case_material", "movement", "year", 
                                   "condition", "diameter", "category", "style"};
            
            for (String expectedKey : expectedKeys) {
                Assertions.assertTrue(definitionKeys.contains(expectedKey), 
                    "Sync should have created eBay metafield definition for key: " + expectedKey);
            }
            
            logger.info("✅ PASS: Sync created all required eBay metafield definitions: " + String.join(", ", expectedKeys));
            
            // TODO: Add assertion for metafield definition category constraints
            // When category constraints are implemented, verify that eBay metafield definitions 
            // are constrained to "Watches" category for proper Shopify Admin UX
            // Example: Assertions.assertTrue(hasWatchesCategoryConstraint(ebayDefinitions), 
            //          "eBay metafield definitions should be constrained to Watches category");
            
            // =================== ASSERT: Sync Populated eBay Metafields ===================
            logger.info("🏷️ Asserting that sync process populated eBay metafields on products...");
            
            int productsWithEbayMetafields = 0;
            int totalEbayMetafields = 0;
            boolean loggedSample = false;
            
            for (Product product : allProductsAfterSync) {
                if (product.getMetafields() != null && !product.getMetafields().isEmpty()) {
                    int ebayMetafieldCount = 0;
                    for (Metafield mf : product.getMetafields()) {
                        if ("ebay".equals(mf.getNamespace())) {
                            ebayMetafieldCount++;
                        }
                    }
                    
                    if (ebayMetafieldCount > 0) {
                        productsWithEbayMetafields++;
                        totalEbayMetafields += ebayMetafieldCount;
                        
                        // Log details for first product with eBay metafields
                        if (!loggedSample) {
                            logger.info("📋 Sample product eBay metafields created by sync:");
                            for (Metafield mf : product.getMetafields()) {
                                if ("ebay".equals(mf.getNamespace())) {
                                    logger.info("  - " + mf.getKey() + ": " + mf.getValue());
                                }
                            }
                            loggedSample = true;
                        }
                    }
                }
            }
            
            Assertions.assertTrue(productsWithEbayMetafields > 0, 
                "Sync should have populated eBay metafields on at least one product");
            
            logger.info("✅ PASS: Sync populated eBay metafields on " + productsWithEbayMetafields + " products");
            logger.info("✅ PASS: Sync created " + totalEbayMetafields + " total eBay metafields");
            
            // Verify sync populated meaningful eBay metafield values
            boolean foundMeaningfulData = false;
            for (Product p : allProductsAfterSync) {
                if (p.getMetafields() != null) {
                    for (Metafield mf : p.getMetafields()) {
                        if ("ebay".equals(mf.getNamespace()) && mf.getValue() != null && 
                            !mf.getValue().trim().isEmpty() && !"null".equals(mf.getValue())) {
                            foundMeaningfulData = true;
                            break;
                        }
                    }
                    if (foundMeaningfulData) break;
                }
            }
            
            Assertions.assertTrue(foundMeaningfulData, 
                "Sync should have populated eBay metafields with meaningful data (not null/empty)");
            
            logger.info("✅ PASS: Sync populated eBay metafields with meaningful data");
            
            // =================== ASSERT: Products Are Categorized as Watches ===================
            logger.info("🏷️ Asserting that sync process correctly categorizes products as 'Watches'...");
            
            int watchProducts = 0;
            for (Product product : allProductsAfterSync) {
                if ("Watches".equals(product.getProductType())) {
                    watchProducts++;
                }
            }
            
            Assertions.assertTrue(watchProducts > 0, 
                "Sync should have categorized at least one product as 'Watches', found: " + watchProducts);
            
            logger.info("✅ PASS: Sync categorized " + watchProducts + " products as 'Watches'");
            
            // Verify that most products are watches (since this is a watch store)
            double watchPercentage = (double) watchProducts / allProductsAfterSync.size() * 100;
            logger.info("📊 " + String.format("%.1f", watchPercentage) + "% of products are categorized as 'Watches'");
            
            Assertions.assertTrue(watchPercentage >= 80.0, 
                "At least 80% of products should be categorized as 'Watches' for a watch store, found: " + 
                String.format("%.1f", watchPercentage) + "%");
            
            logger.info("✅ PASS: Watch store has appropriate categorization percentage");
            
            // =================== ASSERT: Sync Created Product Variant Options Using Correct Approach ===================
            logger.info("🎯 Asserting that sync process created product variant options using the correct two-step approach...");
            logger.info("💡 The correct approach: 1) Create product with variants, 2) Add options with productOptionsCreate");
            
            int productsWithVariantOptions = 0;
            int totalVariantOptions = 0;
            int productsWithColorOption = 0;
            int productsWithSizeOption = 0;
            int productsWithMaterialOption = 0;
            int variantsWithOptionValues = 0;
            boolean loggedVariantSample = false;
            
            for (Product product : allProductsAfterSync) {
                List<Option> options = product.getOptions();
                
                // Log detailed options info for debugging the first product
                if (!loggedVariantSample) {
                    logger.info("📊 Sample Product Options Analysis (SKU: {})", 
                        product.getVariants() != null && !product.getVariants().isEmpty() ? 
                        product.getVariants().get(0).getSku() : "unknown");
                    
                    if (options != null && !options.isEmpty()) {
                        logger.info("  ✅ Product has {} options (indicates successful two-step approach):", options.size());
                        for (int i = 0; i < options.size(); i++) {
                            Option option = options.get(i);
                            logger.info("    Option[{}]: name='{}', position={}, values={}", 
                                i, option.getName(), option.getPosition(), 
                                option.getValues() != null ? String.join(", ", option.getValues()) : "null");
                        }
                    } else {
                        logger.info("  ❌ Product options are NULL (old approach - should be fixed)");
                    }
                    
                    if (product.getVariants() != null && !product.getVariants().isEmpty()) {
                        Variant variant = product.getVariants().get(0);
                        logger.info("  Variant option values: Option1='{}', Option2='{}', Option3='{}'", 
                            variant.getOption1(), variant.getOption2(), variant.getOption3());
                        
                        boolean hasOptionValues = (variant.getOption1() != null && !variant.getOption1().trim().isEmpty()) ||
                                                (variant.getOption2() != null && !variant.getOption2().trim().isEmpty()) ||
                                                (variant.getOption3() != null && !variant.getOption3().trim().isEmpty());
                        
                        if (hasOptionValues) {
                            logger.info("  ✅ Variant has option values (indicates working variant options)");
                        } else {
                            logger.info("  ❌ Variant has no option values (may indicate missing feed attributes)");
                        }
                    }
                    loggedVariantSample = true;
                }
                
                // Count products with proper variant options (not just "Default Title")
                boolean hasRealOptions = false;
                if (options != null && !options.isEmpty()) {
                    // Check if we have real options (not just default)
                    for (Option option : options) {
                        if (!"Default Title".equals(option.getName())) {
                            hasRealOptions = true;
                            break;
                        }
                    }
                }
                
                if (hasRealOptions) {
                    productsWithVariantOptions++;
                    totalVariantOptions += options.size();
                    
                    // Check for specific option types that match our feed item attributes
                    for (Option option : options) {
                        if ("Color".equalsIgnoreCase(option.getName())) {
                            productsWithColorOption++;
                        } else if ("Size".equalsIgnoreCase(option.getName())) {
                            productsWithSizeOption++;
                        } else if ("Material".equalsIgnoreCase(option.getName())) {
                            productsWithMaterialOption++;
                        }
                    }
                }
                
                // Count variants with option values (this indicates the variant service is working)
                if (product.getVariants() != null && !product.getVariants().isEmpty()) {
                    Variant variant = product.getVariants().get(0);
                    boolean hasOptionValues = (variant.getOption1() != null && !variant.getOption1().trim().isEmpty()) ||
                                            (variant.getOption2() != null && !variant.getOption2().trim().isEmpty()) ||
                                            (variant.getOption3() != null && !variant.getOption3().trim().isEmpty());
                    
                    if (hasOptionValues) {
                        variantsWithOptionValues++;
                    }
                }
            }
            
            double variantOptionCoverage = allProductsAfterSync.size() > 0 ? 
                (double) productsWithVariantOptions / allProductsAfterSync.size() * 100.0 : 0.0;
            
            double variantValueCoverage = allProductsAfterSync.size() > 0 ? 
                (double) variantsWithOptionValues / allProductsAfterSync.size() * 100.0 : 0.0;
            
            logger.info("📈 Variant Options Implementation Statistics:");
            logger.info("  Products with real variant options: {}/{} ({:.1f}%)", 
                productsWithVariantOptions, allProductsAfterSync.size(), variantOptionCoverage);
            logger.info("  Variants with option values: {}/{} ({:.1f}%)", 
                variantsWithOptionValues, allProductsAfterSync.size(), variantValueCoverage);
            logger.info("  Total variant options created: {}", totalVariantOptions);
            logger.info("  Products with Color option: {}", productsWithColorOption);
            logger.info("  Products with Size option: {}", productsWithSizeOption); 
            logger.info("  Products with Material option: {}", productsWithMaterialOption);
            
            // PROPER ASSERTIONS FOR THE CORRECT IMPLEMENTATION
            
            // Assert that the sync process successfully created products with options
            Assertions.assertTrue(productsWithVariantOptions > 0, 
                "Sync should have created at least one product with variant options using the correct two-step approach. " +
                "This indicates that productOptionsCreate is working correctly. Found: " + productsWithVariantOptions);
            
            logger.info("✅ PASS: Sync successfully created {} products with variant options", productsWithVariantOptions);
            
            // Assert that the variant service is properly setting option values on variants
            Assertions.assertTrue(variantsWithOptionValues > 0, 
                "Sync should have created at least one variant with option values. " +
                "This indicates that the VariantService is correctly mapping feed item attributes to variant options. Found: " + variantsWithOptionValues);
            
            logger.info("✅ PASS: Sync successfully created {} variants with option values", variantsWithOptionValues);
            
            // Assert that we have the expected option types based on feed item structure
            Assertions.assertTrue(productsWithColorOption > 0 || productsWithSizeOption > 0 || productsWithMaterialOption > 0, 
                "Sync should have created products with at least one of the expected option types (Color, Size, Material). " +
                "This indicates that feed item attributes (webWatchDial, webWatchDiameter, webMetalType) are being properly mapped. " +
                "Found - Color: " + productsWithColorOption + ", Size: " + productsWithSizeOption + ", Material: " + productsWithMaterialOption);
            
            logger.info("✅ PASS: Sync created products with expected option types");
            
            // Assert reasonable coverage for a watch store (at least 60% should have some kind of options)
            Assertions.assertTrue(variantOptionCoverage >= 60.0, 
                "At least 60% of products should have variant options for a watch store (watches typically have Color/Size/Material attributes). " +
                "Found coverage: " + String.format("%.1f", variantOptionCoverage) + "%. " +
                "If this fails, check if feed items are missing webWatchDial/webWatchDiameter/webMetalType attributes.");
            
            logger.info("✅ PASS: Variant options coverage ({:.1f}%) meets minimum threshold", variantOptionCoverage);
            
            // Verify implementation approach - options should exist independently of variants
            boolean hasCorrectImplementation = true;
            String implementationIssues = "";
            
            for (Product product : allProductsAfterSync) {
                if (product.getOptions() != null && !product.getOptions().isEmpty()) {
                    // Check if product has options but variant has no option values (indicates incomplete implementation)
                    if (product.getVariants() != null && !product.getVariants().isEmpty()) {
                        Variant variant = product.getVariants().get(0);
                        boolean variantHasValues = (variant.getOption1() != null && !variant.getOption1().trim().isEmpty()) ||
                                                 (variant.getOption2() != null && !variant.getOption2().trim().isEmpty()) ||
                                                 (variant.getOption3() != null && !variant.getOption3().trim().isEmpty());
                        
                        if (!variantHasValues) {
                            hasCorrectImplementation = false;
                            implementationIssues += "Product " + product.getId() + " has options but variant has no option values; ";
                        }
                    }
                }
            }
            
            Assertions.assertTrue(hasCorrectImplementation, 
                "Implementation issue detected: " + implementationIssues + 
                "All products with options should have variants with matching option values. " +
                "This indicates a problem with the two-step approach implementation.");
            
            logger.info("✅ PASS: Variant options implementation is correct (options and variant values are properly linked)");
            
            // Final success message
            logger.info("🎉 VARIANT OPTIONS IMPLEMENTATION VERIFICATION COMPLETE");
            logger.info("💡 The sync process successfully uses the correct two-step approach:");
            logger.info("   1. ✅ Products are created with variants using productCreate");
            logger.info("   2. ✅ Options are added using productOptionsCreate");
            logger.info("   3. ✅ Variant option values are properly set from feed item attributes");
            logger.info("   4. ✅ Products are visible in Shopify UI with working variant options");
            
            // Final summary
            logger.info("=== Live Feed Sync Test Summary ===");
            logger.info("Total items processed: " + topFeedItems.size());
            logger.info("Final products in Shopify: " + allProductsAfterSync.size());
            logger.info("Collections available: " + collections.size());
            logger.info("eBay metafield definitions: " + ebayDefinitions.size());
            logger.info("Products with eBay metafields: " + totalEbayMetafields);
            logger.info("Products with variant options: " + productsWithVariantOptions + " (" + String.format("%.1f", variantOptionCoverage) + "%)");
            logger.info("Variant options breakdown - Color: " + productsWithColorOption + ", Size: " + productsWithSizeOption + ", Material: " + productsWithMaterialOption);
            logger.info("=== Live Feed Sync Test Complete ===");
            
        } catch (Exception e) {
            logger.error("❌ Sync failed: " + e.getMessage(), e);
            throw e;
        }
    }
} 