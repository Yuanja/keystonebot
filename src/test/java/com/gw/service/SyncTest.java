package com.gw.service;

import com.gw.domain.FeedItem;
import com.gw.services.shopifyapi.objects.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

public class SyncTest extends BaseGraphqlTest {

    @Test
    /**
     * Live feed sync test - processes the highest 50 webTagNumber feed items
     * This replaces the dev mode approach and works with the actual live feed
     * Now uses the doSyncForFeedItems interface for proper sync logic testing
     */
    public void syncTest() throws Exception {
        logger.info("=== Starting Live Feed Sync Test Using doSyncForFeedItems Interface ===");
        logger.info("Loading live feed and selecting highest 50 webTagNumber items...");

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
            long syncedItemsFound = topFeedItems.stream()
                .filter(item -> allProductsAfterSync.stream()
                    .anyMatch(p -> p.getVariants() != null && !p.getVariants().isEmpty() && 
                              item.getWebTagNumber().equals(p.getVariants().get(0).getSku())))
                .count();
            
            logger.info("✅ Verified " + syncedItemsFound + " out of " + topFeedItems.size() + " items are accessible in Shopify");
            
            // Verify collections exist
            List<CustomCollection> collections = shopifyApiService.getAllCustomCollections();
            Assertions.assertTrue(collections.size() > 0, "Collections should exist after sync");
            logger.info("✅ Verified " + collections.size() + " collections exist");
            
            // Verify items are associated with collections
            long itemsWithCollections = allProductsAfterSync.stream()
                .filter(product -> {
                    try {
                        List<Collect> collects = shopifyApiService.getCollectsForProductId(product.getId());
                        return collects.size() > 0;
                    } catch (Exception e) {
                        return false;
                    }
                })
                .count();
            
            logger.info("✅ Verified " + itemsWithCollections + " products have collection associations");
            
        } catch (Exception e) {
            logger.error("❌ Sync failed: " + e.getMessage(), e);
            throw e;
        }
        
        // Final summary
        logger.info("=== Live Feed Sync Test Summary ===");
        logger.info("Total items processed: " + topFeedItems.size());
        logger.info("Final products in Shopify: " + shopifyApiService.getAllProducts().size());
        logger.info("Collections available: " + shopifyApiService.getAllCustomCollections().size());
        logger.info("=== Live Feed Sync Test Complete ===");
    }
} 