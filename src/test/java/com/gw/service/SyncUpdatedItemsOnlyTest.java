package com.gw.service;

import com.gw.domain.FeedItem;
import com.gw.services.shopifyapi.objects.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SyncUpdatedItemsOnlyTest extends BaseGraphqlTest {

    @Test
    /**
     * Test sync behavior with existing items that have changes
     * This tests the handleChangedItems path of the sync logic
     */
    public void syncTestUpdatedItemsOnly() throws Exception {
        logger.info("=== Starting Updated Items Only Sync Test ===");
        
        // Setup: First publish some items to have something to update
        List<FeedItem> feedItems = keyStoneFeedService.loadFromXmlFile("src/test/resources/testShopifyUpdate/trimmedFeed20.xml");
        List<FeedItem> testItems = feedItems.stream().limit(3).collect(Collectors.toList());
        
        logger.info("Step 1: Publishing initial items...");
        for (FeedItem item : testItems) {
            syncService.publishItemToShopify(item);
            assertNotNull(item.getShopifyItemId(), "Item should have Shopify ID after publishing");
            logger.info("Published: " + item.getWebTagNumber() + " (ID: " + item.getShopifyItemId() + ")");
        }
        
        // Verify initial state
        List<Product> initialProducts = shopifyApiService.getAllProducts();
        Assertions.assertEquals(testItems.size(), initialProducts.size(), "Should have initial products");
        
        logger.info("Step 2: Modifying items to simulate changes...");
        // Simulate changes by modifying the feed items
        List<FeedItem> modifiedItems = new ArrayList<>();
        for (FeedItem item : testItems) {
            FeedItem modifiedItem = new FeedItem();
            modifiedItem.copyFrom(item); // Copy all fields
            
            // Simulate changes that would trigger an update
            modifiedItem.setWebDescriptionShort(item.getWebDescriptionShort() + " [MODIFIED FOR TEST]");
            modifiedItem.setShopifyItemId(item.getShopifyItemId()); // Keep the same Shopify ID
            
            modifiedItems.add(modifiedItem);
            logger.info("Modified: " + modifiedItem.getWebTagNumber() + " - " + modifiedItem.getWebDescriptionShort());
        }
        
        logger.info("Step 3: Running sync with modified items...");
        long startTime = System.currentTimeMillis();
        syncService.doSyncForFeedItems(modifiedItems);
        long endTime = System.currentTimeMillis();
        
        logger.info("✅ Updated items sync completed in " + (endTime - startTime) + "ms");
        
        // Verify updates were applied
        List<Product> updatedProducts = shopifyApiService.getAllProducts();
        Assertions.assertEquals(testItems.size(), updatedProducts.size(), "Should still have same number of products");
        
        for (FeedItem modifiedItem : modifiedItems) {
            Optional<Product> foundProduct = updatedProducts.stream()
                .filter(p -> p.getId().equals(modifiedItem.getShopifyItemId()))
                .findFirst();
            
            Assertions.assertTrue(foundProduct.isPresent(), "Updated product should exist for SKU: " + modifiedItem.getWebTagNumber());
            
            Product product = foundProduct.get();
            Assertions.assertTrue(product.getTitle().contains("[MODIFIED FOR TEST]"), "Product title should contain modification marker");
            Assertions.assertEquals("ACTIVE", product.getStatus(), "Product should maintain ACTIVE status");
            
            logger.info("✅ Verified update: " + modifiedItem.getWebTagNumber() + 
                       " - Title: " + product.getTitle());
        }
        
        logger.info("=== Updated Items Only Sync Test Complete ===");
    }
} 