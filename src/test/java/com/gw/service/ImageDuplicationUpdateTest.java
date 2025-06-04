package com.gw.service;

import com.gw.domain.FeedItem;
import com.gw.services.shopifyapi.objects.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class ImageDuplicationUpdateTest extends BaseGraphqlTest {

    @Test
    /**
     * Test that images are properly replaced during updates without duplication
     * This test creates a product, then simulates an update to verify image count stays the same
     */
    public void testImageReplacementDuringUpdate() throws Exception {
        logger.info("=== Starting Image Duplication Update Test ===");
        
        // Load a single item from live feed
        List<FeedItem> feedItems = getTopFeedItems(1);
        Assertions.assertFalse(feedItems.isEmpty(), "Should have at least 1 item from live feed");
        
        FeedItem testItem = feedItems.get(0);
        logger.info("Testing with SKU: {} - {}", testItem.getWebTagNumber(), testItem.getWebDescriptionShort());
        
        // PHASE 1: Create initial product
        logger.info("=== PHASE 1: Creating initial product ===");
        syncService.doSyncForFeedItems(feedItems);
        
        // Verify product was created
        List<Product> products = shopifyApiService.getAllProducts();
        Assertions.assertEquals(1, products.size(), "Should have created exactly 1 product");
        
        Product createdProduct = products.get(0);
        int initialImageCount = createdProduct.getImages() != null ? createdProduct.getImages().size() : 0;
        
        logger.info("✅ Product created - ID: {}, Initial image count: {}", 
            createdProduct.getId(), initialImageCount);
        
        // Store initial state
        String initialUpdatedAt = createdProduct.getUpdatedAt();
        
        // PHASE 2: Simulate an update (sync the same item again)
        logger.info("=== PHASE 2: Simulating update (syncing same item again) ===");
        
        // Wait a moment to ensure timestamp differences can be detected
        Thread.sleep(100);
        
        // Sync the same item again - this should trigger an update path
        syncService.doSyncForFeedItems(feedItems);
        
        // VERIFICATION: Check that images weren't duplicated
        logger.info("=== VERIFICATION: Checking image count ===");
        products = shopifyApiService.getAllProducts();
        Assertions.assertEquals(1, products.size(), "Should still have exactly 1 product");
        
        Product updatedProduct = products.get(0);
        int finalImageCount = updatedProduct.getImages() != null ? updatedProduct.getImages().size() : 0;
        
        logger.info("Product after update - ID: {}, Final image count: {}", 
            updatedProduct.getId(), finalImageCount);
        
        // CRITICAL ASSERTION: Image count should not have doubled
        Assertions.assertEquals(initialImageCount, finalImageCount, 
            "Image count should remain the same during update - no duplicates! " +
            "Initial: " + initialImageCount + ", Final: " + finalImageCount);
        
        // Verify product was actually processed (timestamp might change or stay same if no actual updates)
        logger.info("Initial UpdatedAt: {}, Final UpdatedAt: {}", initialUpdatedAt, updatedProduct.getUpdatedAt());
        
        logger.info("=== Image Duplication Update Test Summary ===");
        logger.info("✅ Product ID: {}", updatedProduct.getId());
        logger.info("✅ Initial image count: {}", initialImageCount);
        logger.info("✅ Final image count: {}", finalImageCount);
        logger.info("✅ No image duplication detected");
        logger.info("=== Image Duplication Update Test Complete ===");
    }
} 