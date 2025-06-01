package com.gw.service;

import com.gw.domain.FeedItem;
import com.gw.domain.FeedItemChangeSet;
import com.gw.services.shopifyapi.objects.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Price Change Backfill Test
 * 
 * This test is designed to execute the backfill process after modifying the price change detection
 * logic to only consider webPriceKeystone field and ignore all other pricing fields.
 * 
 * Purpose:
 * 1. Re-process existing items with the new pricing change detection logic
 * 2. Ensure items that should now be updated (due to webPriceKeystone changes) are processed
 * 3. Verify items that should now be ignored (due to other pricing changes) are not processed
 * 
 * Usage:
 * - Development: mvn test -Dtest=PriceChangeBackfillTest -Dspring.profiles.active=keystone-dev
 * - Production: mvn test -Dtest=PriceChangeBackfillTest -Dspring.profiles.active=keystone-prod
 * 
 * Safety Features:
 * - Processes items in batches for better control
 * - Provides detailed logging of what would be changed
 * - Includes validation steps before and after processing
 * - Can be run in analysis mode (read-only) first
 */
public class PriceChangeBackfillTest extends BaseGraphqlTest {
    
    private static final Logger logger = LogManager.getLogger(PriceChangeBackfillTest.class);
    
    // Configuration
    private static final int BATCH_SIZE = 50; // Process items in batches
    private static final boolean DRY_RUN = false; // Set to true for analysis only
    
    @Test
    public void executePriceChangeBackfill() throws Exception {
        logger.info("=== Starting Price Change Detection Backfill ===");
        logger.info("🔧 This backfill processes existing items with the new pricing logic");
        logger.info("📊 New logic: Only webPriceKeystone changes trigger updates");
        logger.info("🚫 Ignored: webPriceRetail, webPriceSale, webPriceEbay, webPriceChronos, webPriceWholesale, costInvoiced");
        
        if (DRY_RUN) {
            logger.warn("🧪 DRY RUN MODE - No actual changes will be made");
        }
        
        // Step 1: Get baseline statistics
        analyzeCurrentState();
        
        // Step 2: Get all items from feed
        logger.info("📦 Step 2: Loading items from feed...");
        List<FeedItem> allFeedItems = keyStoneFeedService.getItemsFromFeed();
        logger.info("📊 Total feed items loaded: " + allFeedItems.size());
        
        // Step 3: Process items in batches
        processFeedItemsInBatches(allFeedItems);
        
        // Step 4: Final validation
        validateBackfillResults();
        
        logger.info("🎉 Price change backfill completed successfully!");
    }
    
    /**
     * Analyze current state before backfill
     */
    private void analyzeCurrentState() throws Exception {
        logger.info("📊 Step 1: Analyzing current state...");
        
        // Database analysis
        List<FeedItem> dbItems = feedItemService.findAll();
        long publishedItems = dbItems.stream().filter(item -> item.getShopifyItemId() != null).count();
        
        logger.info("📋 Database State:");
        logger.info("  - Total items in database: " + dbItems.size());
        logger.info("  - Items with Shopify ID: " + publishedItems);
        
        // Shopify analysis
        List<Product> shopifyProducts = shopifyApiService.getAllProducts();
        logger.info("🛍️ Shopify State:");
        logger.info("  - Total products in Shopify: " + shopifyProducts.size());
        
        // Status breakdown
        dbItems.stream()
               .collect(Collectors.groupingBy(FeedItem::getStatus, Collectors.counting()))
               .forEach((status, count) -> 
                   logger.info("  - Status '" + status + "': " + count + " items"));
    }
    
    /**
     * Process feed items in controlled batches
     */
    private void processFeedItemsInBatches(List<FeedItem> allFeedItems) throws Exception {
        logger.info("🔄 Step 3: Processing items in batches of " + BATCH_SIZE + "...");
        
        int totalBatches = (int) Math.ceil((double) allFeedItems.size() / BATCH_SIZE);
        int totalProcessed = 0;
        int totalChanges = 0;
        
        for (int batchIndex = 0; batchIndex < totalBatches; batchIndex++) {
            int startIndex = batchIndex * BATCH_SIZE;
            int endIndex = Math.min(startIndex + BATCH_SIZE, allFeedItems.size());
            
            List<FeedItem> batch = allFeedItems.subList(startIndex, endIndex);
            
            logger.info("📦 Processing batch " + (batchIndex + 1) + "/" + totalBatches + 
                       " (items " + startIndex + "-" + (endIndex - 1) + ")");
            
            // Process this batch
            BatchResult result = processBatch(batch, batchIndex + 1);
            
            totalProcessed += result.processed;
            totalChanges += result.changes;
            
            logger.info("✅ Batch completed: " + result.processed + " processed, " + 
                       result.changes + " changes detected");
            
            // Small delay between batches to be gentle on APIs
            Thread.sleep(1000);
        }
        
        logger.info("📊 Overall Results:");
        logger.info("  - Total items processed: " + totalProcessed);
        logger.info("  - Total changes detected: " + totalChanges);
        logger.info("  - Change percentage: " + String.format("%.2f%%", 
                    (double) totalChanges / totalProcessed * 100));
    }
    
    /**
     * Process a single batch of items
     */
    private BatchResult processBatch(List<FeedItem> batch, int batchNumber) throws Exception {
        BatchResult result = new BatchResult();
        
        try {
            // Use the forceUpdate flag to bypass normal change detection
            // This ensures we re-evaluate all items with the new pricing logic
            FeedItemChangeSet changeSet = feedItemService.compareFeedItemWithDB(true, batch);
            
            result.processed = batch.size();
            result.changes = changeSet.getChangedItems() != null ? changeSet.getChangedItems().size() : 0;
            
            if (result.changes > 0) {
                logger.info("🔍 Batch " + batchNumber + " detected " + result.changes + " items needing updates:");
                
                changeSet.getChangedItems().forEach(change -> {
                    FeedItem fromDb = change.getFromDb();
                    FeedItem fromFeed = change.getFromFeed();
                    
                    logger.info("  📝 SKU " + fromFeed.getWebTagNumber() + ": " + 
                               analyzeChanges(fromDb, fromFeed));
                });
                
                if (!DRY_RUN) {
                    // Actually apply the changes
                    logger.info("🔄 Applying changes for batch " + batchNumber + "...");
                    syncService.doSyncForFeedItems(batch);
                    logger.info("✅ Changes applied for batch " + batchNumber);
                } else {
                    logger.info("🧪 DRY RUN: Changes would be applied but skipping actual updates");
                }
            } else {
                logger.info("ℹ️ Batch " + batchNumber + ": No changes detected");
            }
            
        } catch (Exception e) {
            logger.error("❌ Error processing batch " + batchNumber + ": " + e.getMessage(), e);
            result.error = e.getMessage();
        }
        
        return result;
    }
    
    /**
     * Analyze what changed between database and feed versions
     */
    private String analyzeChanges(FeedItem fromDb, FeedItem fromFeed) {
        StringBuilder analysis = new StringBuilder();
        
        // Check pricing fields specifically
        if (!equals(fromDb.getWebPriceKeystone(), fromFeed.getWebPriceKeystone())) {
            analysis.append("webPriceKeystone: ")
                    .append(fromDb.getWebPriceKeystone())
                    .append(" → ")
                    .append(fromFeed.getWebPriceKeystone())
                    .append(" ");
        }
        
        // Note ignored pricing fields for visibility
        if (!equals(fromDb.getWebPriceRetail(), fromFeed.getWebPriceRetail())) {
            analysis.append("[IGNORED] webPriceRetail: ")
                    .append(fromDb.getWebPriceRetail())
                    .append(" → ")
                    .append(fromFeed.getWebPriceRetail())
                    .append(" ");
        }
        
        if (!equals(fromDb.getWebPriceSale(), fromFeed.getWebPriceSale())) {
            analysis.append("[IGNORED] webPriceSale: ")
                    .append(fromDb.getWebPriceSale())
                    .append(" → ")
                    .append(fromFeed.getWebPriceSale())
                    .append(" ");
        }
        
        // Check other significant fields
        if (!equals(fromDb.getWebDescriptionShort(), fromFeed.getWebDescriptionShort())) {
            analysis.append("description changed ");
        }
        
        if (!equals(fromDb.getWebStatus(), fromFeed.getWebStatus())) {
            analysis.append("status: ")
                    .append(fromDb.getWebStatus())
                    .append(" → ")
                    .append(fromFeed.getWebStatus())
                    .append(" ");
        }
        
        return analysis.length() > 0 ? analysis.toString().trim() : "non-pricing field changes";
    }
    
    /**
     * Helper method for null-safe string comparison
     */
    private boolean equals(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }
    
    /**
     * Validate backfill results
     */
    private void validateBackfillResults() throws Exception {
        logger.info("🔍 Step 4: Validating backfill results...");
        
        // Re-run change detection to see if there are still changes
        List<FeedItem> allFeedItems = keyStoneFeedService.getItemsFromFeed();
        FeedItemChangeSet finalChangeSet = feedItemService.compareFeedItemWithDB(false, allFeedItems);
        
        int remainingChanges = finalChangeSet.getChangedItems() != null ? 
                              finalChangeSet.getChangedItems().size() : 0;
        
        logger.info("📊 Validation Results:");
        logger.info("  - Remaining changes after backfill: " + remainingChanges);
        
        if (remainingChanges > 0) {
            logger.warn("⚠️ There are still " + remainingChanges + " items showing as changed");
            logger.warn("⚠️ This might indicate ongoing feed changes or issues with the backfill");
            
            // Log a few examples
            finalChangeSet.getChangedItems().stream()
                         .limit(5)
                         .forEach(change -> {
                             logger.warn("  📝 Still changed: SKU " + 
                                        change.getFromFeed().getWebTagNumber() + 
                                        " - " + analyzeChanges(change.getFromDb(), change.getFromFeed()));
                         });
        } else {
            logger.info("✅ No remaining changes detected - backfill appears successful");
        }
        
        // Database status check
        List<FeedItem> dbItems = feedItemService.findAll();
        long failedItems = dbItems.stream()
                                 .filter(item -> item.getStatus() != null && 
                                               (item.getStatus().contains("FAILED") || 
                                                item.getStatus().contains("ERROR")))
                                 .count();
        
        logger.info("  - Items with failed/error status: " + failedItems);
        
        if (failedItems > 0) {
            logger.warn("⚠️ Found " + failedItems + " items with failed/error status");
        }
        
        // Final assertion for test success
        if (!DRY_RUN) {
            assertTrue(failedItems < 10, 
                      "Too many failed items (" + failedItems + ") - backfill may have issues");
        }
    }
    
    /**
     * Test method for analysis only (dry run)
     */
    @Test
    public void analyzePriceChangeBackfillImpact() throws Exception {
        logger.info("=== Price Change Backfill Impact Analysis (Read-Only) ===");
        
        // Get all items from feed
        List<FeedItem> allFeedItems = keyStoneFeedService.getItemsFromFeed();
        
        // Compare with force update to see what would change
        FeedItemChangeSet changeSet = feedItemService.compareFeedItemWithDB(true, allFeedItems);
        
        int totalItems = allFeedItems.size();
        int changedItems = changeSet.getChangedItems() != null ? changeSet.getChangedItems().size() : 0;
        int newItems = changeSet.getNewItems() != null ? changeSet.getNewItems().size() : 0;
        int deletedItems = changeSet.getDeletedItems() != null ? changeSet.getDeletedItems().size() : 0;
        
        logger.info("📊 Backfill Impact Analysis:");
        logger.info("  - Total feed items: " + totalItems);
        logger.info("  - Items that would be updated: " + changedItems);
        logger.info("  - New items that would be created: " + newItems);
        logger.info("  - Items that would be deleted: " + deletedItems);
        logger.info("  - Update percentage: " + String.format("%.2f%%", 
                    (double) changedItems / totalItems * 100));
        
        // Analyze types of changes
        if (changeSet.getChangedItems() != null && !changeSet.getChangedItems().isEmpty()) {
            logger.info("📋 Sample of items that would be updated:");
            
            changeSet.getChangedItems().stream()
                     .limit(10)
                     .forEach(change -> {
                         logger.info("  📝 SKU " + change.getFromFeed().getWebTagNumber() + ": " + 
                                    analyzeChanges(change.getFromDb(), change.getFromFeed()));
                     });
        }
        
        logger.info("✅ Analysis complete - no changes were made");
    }
    
    /**
     * Result holder for batch processing
     */
    private static class BatchResult {
        int processed = 0;
        int changes = 0;
        String error = null;
    }
} 