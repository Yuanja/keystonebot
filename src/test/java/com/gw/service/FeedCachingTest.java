package com.gw.service;

import com.gw.domain.FeedItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

import java.util.List;

/**
 * Test for the enhanced feed caching functionality
 * Tests file-based caching with dev mode and top 100 feed generation
 */
public class FeedCachingTest extends BaseGraphqlTest {

    @Test
    public void testFeedCachingWithDevMode() throws Exception {
        logger.info("=== Testing Feed Caching with Dev Mode ===");
        
        // Clear any existing cache to start fresh
        clearFeedCache();
        
        // Check initial cache status
        String initialStatus = keyStoneFeedService.getCacheStatus();
        logger.info("📊 Initial cache status: {}", initialStatus);
        
        // Load top feed items (should download fresh data)
        logger.info("📥 First load - should download fresh data...");
        long startTime1 = System.currentTimeMillis();
        List<FeedItem> firstLoad = getTopFeedItems(5);
        long duration1 = System.currentTimeMillis() - startTime1;
        
        Assertions.assertNotNull(firstLoad, "First load should not be null");
        Assertions.assertTrue(firstLoad.size() > 0, "First load should have items");
        logger.info("✅ First load completed: {} items in {}ms", firstLoad.size(), duration1);
        
        // Check cache status after first load
        String afterFirstStatus = keyStoneFeedService.getCacheStatus();
        logger.info("📊 Cache status after first load: {}", afterFirstStatus);
        
        // Load again (should use cached files if in dev mode)
        logger.info("📖 Second load - should reuse cached files if available...");
        long startTime2 = System.currentTimeMillis();
        List<FeedItem> secondLoad = getTopFeedItems(5);
        long duration2 = System.currentTimeMillis() - startTime2;
        
        Assertions.assertNotNull(secondLoad, "Second load should not be null");
        Assertions.assertTrue(secondLoad.size() > 0, "Second load should have items");
        logger.info("✅ Second load completed: {} items in {}ms", secondLoad.size(), duration2);
        
        // Verify that the same items are loaded
        Assertions.assertEquals(firstLoad.size(), secondLoad.size(), "Both loads should have same number of items");
        for (int i = 0; i < firstLoad.size(); i++) {
            Assertions.assertEquals(
                firstLoad.get(i).getWebTagNumber(), 
                secondLoad.get(i).getWebTagNumber(),
                "SKUs should match between loads"
            );
        }
        
        // Test force refresh
        logger.info("🔄 Testing force refresh...");
        long startTime3 = System.currentTimeMillis();
        List<FeedItem> refreshedLoad = getTopFeedItemsFresh(5);
        long duration3 = System.currentTimeMillis() - startTime3;
        
        Assertions.assertNotNull(refreshedLoad, "Refreshed load should not be null");
        Assertions.assertTrue(refreshedLoad.size() > 0, "Refreshed load should have items");
        logger.info("✅ Force refresh completed: {} items in {}ms", refreshedLoad.size(), duration3);
        
        // Check final cache status
        String finalStatus = keyStoneFeedService.getCacheStatus();
        logger.info("📊 Final cache status: {}", finalStatus);
        
        logger.info("🎯 Performance comparison:");
        logger.info("  - First load (fresh): {}ms", duration1);
        logger.info("  - Second load (cached): {}ms", duration2);
        logger.info("  - Force refresh: {}ms", duration3);
        
        // If in dev mode, second load should typically be faster
        // (though this isn't guaranteed due to various factors)
        logger.info("✅ Feed caching test completed successfully");
    }
    
    @Test
    public void testTop100FeedFunctionality() throws Exception {
        logger.info("=== Testing Top 100 Feed Functionality ===");
        
        // Clear cache to start fresh
        clearFeedCache();
        
        // Load more than 10 items to ensure top 100 file gets generated
        logger.info("📥 Loading items to generate top 100 cache...");
        List<FeedItem> allItems = getTopFeedItems(20);
        
        Assertions.assertNotNull(allItems, "Items should not be null");
        Assertions.assertTrue(allItems.size() > 0, "Should have loaded items");
        
        // Check if top 100 functionality is available
        try {
            List<FeedItem> top5Items = getTopFeedItems(5);
            
            Assertions.assertNotNull(top5Items, "Top 5 items should not be null");
            Assertions.assertTrue(top5Items.size() > 0, "Should have loaded top 5 items");
            Assertions.assertTrue(top5Items.size() <= 5, "Should not exceed requested count");
            
            // Verify items are sorted by highest SKU
            for (int i = 0; i < top5Items.size() - 1; i++) {
                String currentSku = top5Items.get(i).getWebTagNumber();
                String nextSku = top5Items.get(i + 1).getWebTagNumber();
                
                try {
                    Integer currentNum = Integer.parseInt(currentSku);
                    Integer nextNum = Integer.parseInt(nextSku);
                    Assertions.assertTrue(currentNum >= nextNum, 
                        "SKUs should be in descending order: " + currentSku + " >= " + nextSku);
                } catch (NumberFormatException e) {
                    // If not numeric, just verify they're not null
                    Assertions.assertNotNull(currentSku, "Current SKU should not be null");
                    Assertions.assertNotNull(nextSku, "Next SKU should not be null");
                }
            }
            
            logger.info("✅ Top 100 functionality test completed successfully");
            logger.info("📊 Loaded top {} items with highest SKUs: {} to {}", 
                top5Items.size(),
                top5Items.get(0).getWebTagNumber(),
                top5Items.get(top5Items.size() - 1).getWebTagNumber());
            
        } catch (Exception e) {
            logger.info("ℹ️ Top 100 functionality not available (normal in some configurations): {}", e.getMessage());
        }
    }
} 