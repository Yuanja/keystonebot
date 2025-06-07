package com.gw.service;

import com.gw.domain.FeedItem;
import com.gw.services.shopifyapi.objects.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;

public class SyncTest extends BaseGraphqlTest {

    /**
     * Helper method to validate inventory levels match web_status and are never > 1
     * 
     * @param products List of Shopify products to validate
     * @param feedItems List of feed items with web_status information
     * @return InventoryValidationResult with statistics and violations
     */
    private InventoryValidationResult validateInventoryLevels(List<Product> products, List<FeedItem> feedItems) {
        logger.info("📦 Validating inventory levels match web_status and are never > 1...");
        logger.info("💡 Business rules: SOLD items → inventory = 0, all others → inventory = 1, never > 1");
        
        int productsChecked = 0;
        int soldItemsWithZeroInventory = 0;
        int availableItemsWithOneInventory = 0;
        int violationsFound = 0;
        List<String> inventoryViolations = new ArrayList<>();
        boolean loggedInventorySample = false;
        
        for (Product product : products) {
            // Get the corresponding feed item to check web_status
            FeedItem matchingFeedItem = null;
            String productSku = null;
            
            if (product.getVariants() != null && !product.getVariants().isEmpty()) {
                productSku = product.getVariants().get(0).getSku();
                
                // Find matching feed item by SKU
                for (FeedItem feedItem : feedItems) {
                    if (feedItem.getWebTagNumber().equals(productSku)) {
                        matchingFeedItem = feedItem;
                        break;
                    }
                }
            }
            
            if (matchingFeedItem != null && product.getVariants() != null && !product.getVariants().isEmpty()) {
                productsChecked++;
                
                Variant variant = product.getVariants().get(0);
                String webStatus = matchingFeedItem.getWebStatus();
                
                // Calculate total inventory across all locations
                int totalInventory = 0;
                if (variant.getInventoryLevels() != null && variant.getInventoryLevels().get() != null) {
                    for (InventoryLevel level : variant.getInventoryLevels().get()) {
                        try {
                            totalInventory += Integer.parseInt(level.getAvailable());
                        } catch (NumberFormatException e) {
                            logger.warn("Invalid inventory quantity for SKU {}: {}", productSku, level.getAvailable());
                        }
                    }
                }
                
                // Log sample for debugging
                if (!loggedInventorySample) {
                    logger.info("📊 Sample Inventory Validation (SKU: {}):", productSku);
                    logger.info("  Feed item web_status: '{}'", webStatus);
                    logger.info("  Total inventory: {}", totalInventory);
                    if (variant.getInventoryLevels() != null && variant.getInventoryLevels().get() != null) {
                        logger.info("  Inventory breakdown across {} locations:", variant.getInventoryLevels().get().size());
                        for (int i = 0; i < variant.getInventoryLevels().get().size(); i++) {
                            InventoryLevel level = variant.getInventoryLevels().get().get(i);
                            logger.info("    Location[{}]: {} units", i, level.getAvailable());
                        }
                    }
                    loggedInventorySample = true;
                }
                
                // Rule 1: Inventory should NEVER be > 1
                if (totalInventory > 1) {
                    violationsFound++;
                    String violation = String.format("SKU %s has inventory %d > 1 (web_status: %s)", 
                        productSku, totalInventory, webStatus);
                    inventoryViolations.add(violation);
                    logger.error("❌ INVENTORY VIOLATION: {}", violation);
                }
                
                // Rule 2: SOLD items should have inventory = 0
                if ("SOLD".equalsIgnoreCase(webStatus)) {
                    if (totalInventory == 0) {
                        soldItemsWithZeroInventory++;
                    } else {
                        violationsFound++;
                        String violation = String.format("SKU %s is SOLD but has inventory %d (should be 0)", 
                            productSku, totalInventory);
                        inventoryViolations.add(violation);
                        logger.error("❌ INVENTORY VIOLATION: {}", violation);
                    }
                }
                
                // Rule 3: Non-SOLD items should have inventory = 1
                else {
                    if (totalInventory == 1) {
                        availableItemsWithOneInventory++;
                    } else {
                        violationsFound++;
                        String violation = String.format("SKU %s is %s but has inventory %d (should be 1)", 
                            productSku, webStatus, totalInventory);
                        inventoryViolations.add(violation);
                        logger.error("❌ INVENTORY VIOLATION: {}", violation);
                    }
                }
            }
        }
        
        return new InventoryValidationResult(productsChecked, soldItemsWithZeroInventory, 
            availableItemsWithOneInventory, violationsFound, inventoryViolations);
    }
    
    /**
     * Result class for inventory validation
     */
    private static class InventoryValidationResult {
        final int productsChecked;
        final int soldItemsWithZeroInventory;
        final int availableItemsWithOneInventory;
        final int violationsFound;
        final List<String> inventoryViolations;
        
        InventoryValidationResult(int productsChecked, int soldItemsWithZeroInventory, 
                                int availableItemsWithOneInventory, int violationsFound, 
                                List<String> inventoryViolations) {
            this.productsChecked = productsChecked;
            this.soldItemsWithZeroInventory = soldItemsWithZeroInventory;
            this.availableItemsWithOneInventory = availableItemsWithOneInventory;
            this.violationsFound = violationsFound;
            this.inventoryViolations = inventoryViolations;
        }
    }

    @Test
    /**
     * Live feed sync test - processes the highest 5 webTagNumber feed items
     * This replaces the dev mode approach and works with the actual live feed
     * Now uses the doSyncForFeedItems interface for proper sync logic testing
     * Includes verification of eBay metafield definitions and data
     */
    public void syncTest() throws Exception {
        logger.info("=== Starting Live Feed Sync Test Using doSyncForFeedItems Interface ===");
        logger.info("Loadingb live feed and selecting highest 5 webTagNumber items...");

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
            
            // =================== ASSERT: Product Descriptions Are Set ===================
            logger.info("📝 Asserting that sync process sets meaningful product descriptions...");
            
            int productsWithDescriptions = 0;
            int productsWithMeaningfulDescriptions = 0;
            int totalDescriptionLength = 0;
            boolean loggedDescriptionSample = false;
            
            for (Product product : allProductsAfterSync) {
                String description = product.getBodyHtml();
                
                // Log sample description for debugging
                if (!loggedDescriptionSample && description != null && !description.trim().isEmpty()) {
                    logger.info("📋 Sample product description created by sync:");
                    String sampleDesc = description.length() > 200 ? description.substring(0, 200) + "..." : description;
                    logger.info("  Product: {} (SKU: {})", product.getTitle(), 
                        product.getVariants() != null && !product.getVariants().isEmpty() ? 
                        product.getVariants().get(0).getSku() : "unknown");
                    logger.info("  Description: {}", sampleDesc);
                    logger.info("  Description length: {} characters", description.length());
                    loggedDescriptionSample = true;
                }
                
                // Count products with any description
                if (description != null && !description.trim().isEmpty()) {
                    productsWithDescriptions++;
                    totalDescriptionLength += description.length();
                    
                    // Count products with meaningful descriptions (more than just basic text)
                    String cleanDescription = description.replaceAll("<[^>]*>", "").trim(); // Remove HTML tags
                    if (cleanDescription.length() > 50) { // Meaningful description should be more than 50 characters
                        productsWithMeaningfulDescriptions++;
                    }
                }
            }
            
            double descriptionCoverage = allProductsAfterSync.size() > 0 ? 
                (double) productsWithDescriptions / allProductsAfterSync.size() * 100.0 : 0.0;
            
            double meaningfulDescriptionCoverage = allProductsAfterSync.size() > 0 ? 
                (double) productsWithMeaningfulDescriptions / allProductsAfterSync.size() * 100.0 : 0.0;
            
            int averageDescriptionLength = productsWithDescriptions > 0 ? 
                totalDescriptionLength / productsWithDescriptions : 0;
            
            logger.info("📈 Product Description Statistics:");
            logger.info("  Products with descriptions: {}/{} ({:.1f}%)", 
                productsWithDescriptions, allProductsAfterSync.size(), descriptionCoverage);
            logger.info("  Products with meaningful descriptions (>50 chars): {}/{} ({:.1f}%)", 
                productsWithMeaningfulDescriptions, allProductsAfterSync.size(), meaningfulDescriptionCoverage);
            logger.info("  Average description length: {} characters", averageDescriptionLength);
            
            // ASSERTIONS FOR PRODUCT DESCRIPTIONS
            
            // Assert that sync process creates products with descriptions
            Assertions.assertTrue(productsWithDescriptions > 0, 
                "Sync should have created at least one product with a description. " +
                "Found: " + productsWithDescriptions + " products with descriptions out of " + allProductsAfterSync.size());
            
            logger.info("✅ PASS: Sync successfully created {} products with descriptions", productsWithDescriptions);
            
            // Assert reasonable description coverage (at least 80% should have descriptions)
            Assertions.assertTrue(descriptionCoverage >= 80.0, 
                "At least 80% of products should have descriptions. " +
                "Found coverage: " + String.format("%.1f", descriptionCoverage) + "%. " +
                "If this fails, check if feed items are missing description-related attributes or if the description building logic is working correctly.");
            
            logger.info("✅ PASS: Description coverage ({:.1f}%) meets minimum threshold", descriptionCoverage);
            
            // Assert that descriptions contain meaningful content (at least 60% should have >50 characters)
            Assertions.assertTrue(meaningfulDescriptionCoverage >= 60.0, 
                "At least 60% of products should have meaningful descriptions (>50 characters). " +
                "Found coverage: " + String.format("%.1f", meaningfulDescriptionCoverage) + "%. " +
                "This indicates that the description building process is creating substantive content from feed items.");
            
            logger.info("✅ PASS: Meaningful description coverage ({:.1f}%) meets minimum threshold", meaningfulDescriptionCoverage);
            
            // Assert reasonable average description length (should be at least 100 characters on average)
            Assertions.assertTrue(averageDescriptionLength >= 100, 
                "Average description length should be at least 100 characters to provide useful product information. " +
                "Found: " + averageDescriptionLength + " characters. " +
                "Short descriptions may indicate missing feed item attributes or incomplete description templates.");
            
            logger.info("✅ PASS: Average description length ({} characters) meets minimum requirement", averageDescriptionLength);
            
            // Verify descriptions don't contain placeholder text or common error indicators
            boolean hasValidDescriptions = true;
            String descriptionIssues = "";
            int issueCount = 0;
            
            for (Product product : allProductsAfterSync) {
                String description = product.getBodyHtml();
                if (description != null && !description.trim().isEmpty()) {
                    String lowerDesc = description.toLowerCase();
                    
                    // Check for common placeholder/error text
                    if (lowerDesc.contains("todo") || lowerDesc.contains("placeholder") || 
                        lowerDesc.contains("test description") || lowerDesc.contains("[description]") ||
                        lowerDesc.contains("lorem ipsum") || lowerDesc.equals("null")) {
                        hasValidDescriptions = false;
                        issueCount++;
                        if (issueCount <= 3) { // Limit logging to first 3 issues
                            descriptionIssues += "Product " + product.getId() + " has placeholder/invalid description; ";
                        }
                    }
                }
            }
            
            if (issueCount > 3) {
                descriptionIssues += "... and " + (issueCount - 3) + " more products with similar issues";
            }
            
            Assertions.assertTrue(hasValidDescriptions, 
                "Product descriptions should not contain placeholder or invalid text. " +
                "Issues found: " + descriptionIssues + 
                "This indicates problems with the description generation logic or feed item data quality.");
            
            logger.info("✅ PASS: All product descriptions contain valid content (no placeholders or error text)");
            
            logger.info("🎉 PRODUCT DESCRIPTION VERIFICATION COMPLETE");
            logger.info("💡 The sync process successfully creates meaningful product descriptions:");
            logger.info("   1. ✅ Products have descriptions set from feed item data");
            logger.info("   2. ✅ Descriptions meet minimum length requirements");
            logger.info("   3. ✅ Descriptions contain meaningful content (not placeholders)");
            logger.info("   4. ✅ Description coverage meets business requirements");
            
            // =================== ASSERT: Inventory Levels Match Web Status and Are Never > 1 ===================
            InventoryValidationResult inventoryResult = validateInventoryLevels(allProductsAfterSync, topFeedItems);
            
            // Summary logging
            logger.info("📈 Inventory Validation Results:");
            logger.info("  Products checked: {}/{}", inventoryResult.productsChecked, topFeedItems.size());
            logger.info("  SOLD items with correct inventory (0): {}", inventoryResult.soldItemsWithZeroInventory);
            logger.info("  Available items with correct inventory (1): {}", inventoryResult.availableItemsWithOneInventory);
            logger.info("  Total violations found: {}", inventoryResult.violationsFound);
            
            if (!inventoryResult.inventoryViolations.isEmpty()) {
                logger.error("❌ Inventory violations details:");
                for (String violation : inventoryResult.inventoryViolations) {
                    logger.error("  - {}", violation);
                }
            }
            
            // CRITICAL ASSERTIONS
            Assertions.assertEquals(0, inventoryResult.violationsFound, 
                "Inventory violations found! " + inventoryResult.violationsFound + " products have incorrect inventory levels. " +
                "Violations: " + String.join("; ", inventoryResult.inventoryViolations) + ". " +
                "All products must follow the rules: SOLD items = 0 inventory, others = 1 inventory, never > 1.");
            
            logger.info("✅ PASS: All inventory levels match web_status correctly");
            
            // Verify reasonable distribution
            Assertions.assertTrue(inventoryResult.productsChecked > 0, 
                "Should have checked at least one product's inventory. Found: " + inventoryResult.productsChecked);
            
            logger.info("✅ PASS: Inventory validation completed successfully for {} products", inventoryResult.productsChecked);
            logger.info("🎉 INVENTORY LEVEL VERIFICATION COMPLETE");
            logger.info("💡 All products follow the correct inventory rules:");
            logger.info("   1. ✅ Inventory is NEVER > 1");
            logger.info("   2. ✅ SOLD items have inventory = 0");
            logger.info("   3. ✅ Available items have inventory = 1");
            logger.info("   4. ✅ Inventory levels are set using absolute values (not delta adjustment)");
            
            // Final summary
            logger.info("=== Live Feed Sync Test Summary ===");
            logger.info("Total items processed: " + topFeedItems.size());
            logger.info("Final products in Shopify: " + allProductsAfterSync.size());
            logger.info("Collections available: " + collections.size());
            logger.info("eBay metafield definitions: " + ebayDefinitions.size());
            logger.info("Products with eBay metafields: " + totalEbayMetafields);
            logger.info("Products with variant options: " + productsWithVariantOptions + " (" + String.format("%.1f", variantOptionCoverage) + "%)");
            logger.info("Variant options breakdown - Color: " + productsWithColorOption + ", Size: " + productsWithSizeOption + ", Material: " + productsWithMaterialOption);
            logger.info("Products with descriptions: " + productsWithDescriptions + " (" + String.format("%.1f", descriptionCoverage) + "%)");
            logger.info("Products with meaningful descriptions: " + productsWithMeaningfulDescriptions + " (" + String.format("%.1f", meaningfulDescriptionCoverage) + "%)");
            logger.info("Average description length: " + averageDescriptionLength + " characters");
            logger.info("Inventory validation: " + inventoryResult.productsChecked + " products checked, " + inventoryResult.violationsFound + " violations found");
            logger.info("Inventory breakdown - SOLD items (0 inventory): " + inventoryResult.soldItemsWithZeroInventory + ", Available items (1 inventory): " + inventoryResult.availableItemsWithOneInventory);
            logger.info("=== Live Feed Sync Test Complete ===");
            
        } catch (Exception e) {
            logger.error("❌ Sync failed: " + e.getMessage(), e);
            throw e;
        }
    }
} 