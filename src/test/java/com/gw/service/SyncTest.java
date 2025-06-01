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
            Assertions.assertTrue(ebayDefinitions.size() >= 20, 
                "Sync should have created at least 20 eBay metafield definitions, found: " + ebayDefinitions.size());
            
            logger.info("✅ PASS: Sync created " + ebayDefinitions.size() + " eBay metafield definitions");
            
            // Verify key eBay definitions were created by sync
            List<String> definitionKeys = new ArrayList<>();
            for (Map<String, String> def : ebayDefinitions) {
                definitionKeys.add(def.get("key"));
            }
            
            String[] expectedKeys = {"brand", "model", "case_material", "movement", "year", 
                                   "condition", "diameter", "price_ebay", "category", "style"};
            
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
            
            // Final summary
            logger.info("=== Live Feed Sync Test Summary ===");
            logger.info("Total items processed: " + topFeedItems.size());
            logger.info("Final products in Shopify: " + allProductsAfterSync.size());
            logger.info("Collections available: " + collections.size());
            logger.info("eBay metafield definitions: " + ebayDefinitions.size());
            logger.info("Products with eBay metafields: " + totalEbayMetafields);
            logger.info("=== Live Feed Sync Test Complete ===");
            
        } catch (Exception e) {
            logger.error("❌ Sync failed: " + e.getMessage(), e);
            throw e;
        }
    }
} 