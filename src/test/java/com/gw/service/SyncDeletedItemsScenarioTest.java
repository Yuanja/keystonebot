package com.gw.service;

import com.gw.domain.FeedItem;
import com.gw.services.shopifyapi.objects.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SyncDeletedItemsScenarioTest extends BaseGraphqlTest {

    @Test
    /**
     * Test sync behavior when items are removed from feed (should be deleted)
     * This tests the handleDeletedItems path of the sync logic
     * Uses live feed top 5 items - publishes them first, then removes 2 and verifies deletion
     */
    public void syncTestDeletedItemsScenario() throws Exception {
        logger.info("=== Starting Deleted Items Scenario Sync Test ===");
        
        // Get top 5 items from live feed using the same pattern as other tests
        List<FeedItem> topFeedItems = getTopFeedItems(5);
        Assertions.assertTrue(topFeedItems.size() >= 5, "Should have at least 5 items from live feed");
        
        List<FeedItem> initialItems = topFeedItems.stream().limit(5).collect(Collectors.toList());
        
        logger.info("Publishing initial 5 items from live feed...");
        logger.info("Items to be published:");
        for (FeedItem item : initialItems) {
            logger.info("- " + item.getWebTagNumber() + " - " + item.getWebDescriptionShort());
        }
        
        // Publish all 5 items initially
        for (FeedItem item : initialItems) {
            syncService.publishItemToShopify(item);
            assertNotNull(item.getShopifyItemId(), "Item should have Shopify ID after publishing");
            logger.info("Published: " + item.getWebTagNumber() + " (ID: " + item.getShopifyItemId() + ")");
        }
        
        // Verify initial state in Shopify
        List<Product> initialProducts = shopifyApiService.getAllProducts();
        Assertions.assertEquals(5, initialProducts.size(), "Should have 5 initial products in Shopify");
        
        // Verify initial state in database
        List<FeedItem> dbItemsInitial = feedItemService.findAll();
        Assertions.assertEquals(5, dbItemsInitial.size(), "Should have 5 initial items in database");
        
        logger.info("✅ Initial state verified: 5 items in both Shopify and database");
        
        logger.info("Creating reduced feed (simulating 2 items removed from feed)...");
        // Create a reduced feed that only contains the first 3 items
        // This simulates the scenario where 2 items have been removed from the feed
        List<FeedItem> reducedFeed = initialItems.stream().limit(3).collect(Collectors.toList());
        
        // Items that should remain
        logger.info("Items remaining in reduced feed:");
        for (FeedItem item : reducedFeed) {
            logger.info("- " + item.getWebTagNumber() + " (should remain)");
        }
        
        // Items that should be deleted (last 2 items)
        List<FeedItem> itemsToBeDeleted = initialItems.stream().skip(3).collect(Collectors.toList());
        logger.info("Items that should be deleted (removed from feed):");
        for (FeedItem item : itemsToBeDeleted) {
            logger.info("- " + item.getWebTagNumber() + " (should be deleted)");
        }
        
        logger.info("Running sync with reduced feed (3 items instead of 5)...");
        long startTime = System.currentTimeMillis();
        syncService.doSyncForFeedItems(reducedFeed);
        long endTime = System.currentTimeMillis();
        
        logger.info("✅ Deleted items sync completed in " + (endTime - startTime) + "ms");
        
        logger.info("Verifying deletions occurred...");
        
        // Verify Shopify state
        List<Product> finalProducts = shopifyApiService.getAllProducts();
        logger.info("Final product count in Shopify: " + finalProducts.size() + " (expected: 3)");
        
        // Verify database state
        List<FeedItem> dbItemsFinal = feedItemService.findAll();
        logger.info("Final item count in database: " + dbItemsFinal.size() + " (expected: 3)");
        
        // Assert that we have the correct number of items (3 remaining)
        Assertions.assertEquals(3, finalProducts.size(), "Should have 3 products remaining in Shopify after deletion");
        Assertions.assertEquals(3, dbItemsFinal.size(), "Should have 3 items remaining in database after deletion");
        
        // Verify that the remaining 3 items still exist in Shopify
        for (FeedItem remainingItem : reducedFeed) {
            Optional<Product> foundProduct = finalProducts.stream()
                .filter(p -> p.getVariants() != null && !p.getVariants().isEmpty())
                .filter(p -> remainingItem.getWebTagNumber().equals(p.getVariants().get(0).getSku()))
                .findFirst();
            
            Assertions.assertTrue(foundProduct.isPresent(), "Remaining item should still exist in Shopify: " + remainingItem.getWebTagNumber());
            
            logger.info("✅ Verified remaining item in Shopify: " + remainingItem.getWebTagNumber());
        }
        
        // Verify that the remaining 3 items still exist in database
        for (FeedItem remainingItem : reducedFeed) {
            Optional<FeedItem> foundDbItem = dbItemsFinal.stream()
                .filter(item -> remainingItem.getWebTagNumber().equals(item.getWebTagNumber()))
                .findFirst();
            
            Assertions.assertTrue(foundDbItem.isPresent(), "Remaining item should still exist in database: " + remainingItem.getWebTagNumber());
            
            logger.info("✅ Verified remaining item in database: " + remainingItem.getWebTagNumber());
        }
        
        // Assert that the 2 deleted items are no longer in Shopify
        for (FeedItem deletedItem : itemsToBeDeleted) {
            Optional<Product> foundProduct = finalProducts.stream()
                .filter(p -> p.getVariants() != null && !p.getVariants().isEmpty())
                .filter(p -> deletedItem.getWebTagNumber().equals(p.getVariants().get(0).getSku()))
                .findFirst();
            
            Assertions.assertFalse(foundProduct.isPresent(), "Deleted item should NOT exist in Shopify: " + deletedItem.getWebTagNumber());
            
            logger.info("✅ Confirmed deletion from Shopify: " + deletedItem.getWebTagNumber());
        }
        
        // Assert that the 2 deleted items are no longer in database
        for (FeedItem deletedItem : itemsToBeDeleted) {
            Optional<FeedItem> foundDbItem = dbItemsFinal.stream()
                .filter(item -> deletedItem.getWebTagNumber().equals(item.getWebTagNumber()))
                .findFirst();
            
            Assertions.assertFalse(foundDbItem.isPresent(), "Deleted item should NOT exist in database: " + deletedItem.getWebTagNumber());
            
            logger.info("✅ Confirmed deletion from database: " + deletedItem.getWebTagNumber());
        }
        
        logger.info("=== Deleted Items Scenario Sync Test Complete ===");
        logger.info("✅ Successfully verified deletion of 2 items from both Shopify and database");
        logger.info("✅ Successfully verified retention of 3 items in both Shopify and database");
    }
}