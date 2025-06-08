package com.gw.diagnostics.production;

import com.gw.domain.FeedItem;
import com.gw.domain.FeedItemChange;
import com.gw.domain.FeedItemChangeSet;
import com.gw.domain.EbayMetafieldDefinition;
import com.gw.services.FeedItemService;
import com.gw.services.keystone.KeyStoneFeedService;
import com.gw.services.keystone.KeystoneShopifySyncService;
import com.gw.services.shopifyapi.ShopifyGraphQLService;
import com.gw.services.shopifyapi.objects.*;
import com.gw.services.sync.ProductUpdatePipeline;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;
import java.util.stream.Collectors;
import java.util.Comparator;
import java.util.Map;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Force Update Test for Production
 * 
 * This test provides comprehensive force update capabilities for production:
 * 1. Force update all items with fresh feed data (smallest web_tag_number first)
 * 2. Force update specific item by web_tag_number using database data
 * 3. Validate and recreate eBay metafields if not properly configured
 * 4. Detailed logging and progress tracking
 * 5. Production-safe batch processing
 * 
 * IMPORTANT: This test does NOT extend BaseGraphqlTest to avoid the destructive
 * setUp() method that would delete all products and data!
 * 
 * SAFETY FEATURES:
 * - Temporarily disables cron schedule during execution to prevent conflicts
 * - Does not clear existing data (unlike BaseGraphqlTest)
 * - Only performs actions when actually needed (smart validation)
 * - Processes items in controlled batches with detailed logging
 * 
 * Usage:
 * 
 * 1. Force update all items (refreshes feed):
 *    mvn test -Dtest=ForceUpdateTest#forceUpdateAllItems -Dspring.profiles.active=keystone-prod
 * 
 * 2. Force update specific item (uses DB data):
 *    mvn test -Dtest=ForceUpdateTest#forceUpdateSpecificItem -Dspring.profiles.active=keystone-prod -Dforce.update.web_tag_number=ABC123
 * 
 * 3. Analyze what would be force updated (dry run):
 *    mvn test -Dtest=ForceUpdateTest#analyzeForceUpdateImpact -Dspring.profiles.active=keystone-prod
 * 
 * 4. Validate and fix eBay metafields:
 *    mvn test -Dtest=ForceUpdateTest#validateAndFixEbayMetafields -Dspring.profiles.active=keystone-prod
 * 
 * 5. Retry items with STATUS_UPDATE_FAILED:
 *    mvn test -Dtest=ForceUpdateTest#retryUpdateFailedItems -Dspring.profiles.active=keystone-prod
 * 
 * 6. Fix empty product descriptions:
 *    mvn test -Dtest=ForceUpdateTest#fixEmptyDescriptions -Dspring.profiles.active=keystone-prod
 * 
 * Safety Features:
 * - Processes items in controlled batches
 * - Detailed logging of every action
 * - Validation steps before and after processing
 * - Can target specific items for surgical updates
 * - Sorts by web_tag_number for predictable processing order
 * - Validates and recreates eBay metafields when needed
 * - DOES NOT CLEAR EXISTING DATA (unlike BaseGraphqlTest)
 */
@SpringJUnitConfig
@SpringBootTest
@TestPropertySource(properties = {
    "shopify.force.update=true",
    "cron.schedule=0 0 0 31 2 ?"  // Disable cron during force updates (Feb 31st never exists)
})
public class ForceUpdateTest {
    
    private static final Logger logger = LogManager.getLogger(ForceUpdateTest.class);
    
    // Configuration
    private static final int BATCH_SIZE = 25; // Process items in smaller batches for production safety
    private static final boolean DRY_RUN = false; // Set to true for analysis only
    
    // Inject dependencies directly (no setUp method to clear data!)
    @Autowired
    protected ShopifyGraphQLService shopifyApiService;
    
    @Autowired
    protected KeystoneShopifySyncService syncService;

    @Autowired
    protected FeedItemService feedItemService;
    
    @Autowired
    protected KeyStoneFeedService keyStoneFeedService;
    
    @Autowired
    protected ProductUpdatePipeline productUpdatePipeline;
    
    /**
     * Force update all items in production
     * Refreshes the live feed and processes all items from smallest web_tag_number first
     */
    @Test
    public void forceUpdateAllItems() throws Exception {
        logger.info("=== Starting Production Force Update (All Items) ===");
        logger.info("🚀 This will force update ALL items in production");
        logger.info("📡 Will refresh live feed data");
        logger.info("🔢 Processing from smallest web_tag_number first");
        
        if (DRY_RUN) {
            logger.warn("🧪 DRY RUN MODE - No actual changes will be made");
        }
        
        // Get baseline statistics
        analyzeCurrentState();
        
        // Refresh feed from live source
        logger.info("📡 Refreshing live feed data...");
        List<FeedItem> feedItems = keyStoneFeedService.getItemsFromFeed();
        logger.info("📊 Fresh feed items loaded: " + feedItems.size());
        
        // Sort by web_tag_number (smallest first)
        List<FeedItem> sortedFeedItems = feedItems.stream()
            .sorted(Comparator.comparing(FeedItem::getWebTagNumber, 
                Comparator.nullsLast(Comparator.naturalOrder())))
            .collect(Collectors.toList());
        
        logger.info("🔢 Items sorted by web_tag_number (smallest first)");
        logger.info("📋 First 5 items: " + sortedFeedItems.stream()
            .limit(5)
            .map(FeedItem::getWebTagNumber)
            .collect(Collectors.toList()));
        
        // Force update all items in batches
        forceUpdateItemsInBatches(sortedFeedItems, "ALL-ITEMS");
        
        // Final validation
        validateForceUpdateResults();
        
        logger.info("🎉 Production force update completed successfully!");
    }
    
    /**
     * Force update a specific item by web_tag_number using database data
     * Uses ProductUpdatePipeline for more efficient single-item updates
     */
    @Test
    public void forceUpdateSpecificItem() throws Exception {
        String targetWebTagNumber = System.getProperty("force.update.web_tag_number");
        
        if (targetWebTagNumber == null || targetWebTagNumber.trim().isEmpty()) {
            throw new IllegalArgumentException(
                "❌ Missing required parameter: force.update.web_tag_number\n" +
                "Usage: mvn test -Dtest=ForceUpdateTest#forceUpdateSpecificItem " +
                "-Dspring.profiles.active=keystone-prod -Dforce.update.web_tag_number=ABC123");
        }
        
        logger.info("=== Starting Production Force Update (Specific Item) ===");
        logger.info("🎯 Target web_tag_number: " + targetWebTagNumber);
        logger.info("🗄️ Using existing database data (not refreshing feed)");
        logger.info("⚡ Using ProductUpdatePipeline for efficient single-item update");
        
        if (DRY_RUN) {
            logger.warn("🧪 DRY RUN MODE - No actual changes will be made");
            return;
        }
        
        try {
            // Find the specific item in database
            logger.info("🔍 Finding item in database...");
            FeedItem targetItem = feedItemService.findByWebTagNumber(targetWebTagNumber);
            
            if (targetItem == null) {
                throw new IllegalArgumentException("❌ Item not found in database: " + targetWebTagNumber);
            }
            
            logger.info("✅ Found item: " + targetItem.getWebTagNumber());
            logger.info("📊 Current status: " + targetItem.getStatus());
            logger.info("🛍️ Shopify ID: " + targetItem.getShopifyItemId());
            
            if (targetItem.getShopifyItemId() == null || targetItem.getShopifyItemId().trim().isEmpty()) {
                logger.warn("⚠️ Item has no Shopify ID - cannot update. You may need to publish it first.");
                throw new IllegalArgumentException("❌ Item " + targetWebTagNumber + " has no Shopify ID - cannot update");
            }
            
            // Update the specific item directly using ProductUpdatePipeline
            logger.info("🔄 Updating item on Shopify using ProductUpdatePipeline...");
            logger.info("📝 Item details:");
            logger.info("  - SKU: " + targetItem.getWebTagNumber());
            logger.info("  - Title: " + (targetItem.getWebDescriptionShort() != null ? 
                        targetItem.getWebDescriptionShort().substring(0, Math.min(50, targetItem.getWebDescriptionShort().length())) + "..." : "N/A"));
            logger.info("  - Shopify ID: " + targetItem.getShopifyItemId());
            
            syncService.updateItemOnShopify(targetItem);
            
            // Validate the specific item update
            validateSpecificItemUpdate(targetWebTagNumber);
            
            logger.info("🎉 Specific item force update completed successfully!");
            logger.info("📋 Summary:");
            logger.info("  - Item: " + targetWebTagNumber);
            logger.info("  - Method: ProductUpdatePipeline.executeUpdate");
            logger.info("  - Final status: " + targetItem.getStatus());
            
        } catch (Exception e) {
            logger.error("❌ Force update failed for item: " + targetWebTagNumber, e);
            throw e;
        }
    }
    
    /**
     * Analyze what would be force updated (read-only analysis)
     */
    @Test
    public void analyzeForceUpdateImpact() throws Exception {
        logger.info("=== Production Force Update Impact Analysis (Read-Only) ===");
        
        // Get current feed
        List<FeedItem> feedItems = keyStoneFeedService.getItemsFromFeed();
        
        // Sort by web_tag_number
        List<FeedItem> sortedFeedItems = feedItems.stream()
            .sorted(Comparator.comparing(FeedItem::getWebTagNumber, 
                Comparator.nullsLast(Comparator.naturalOrder())))
            .collect(Collectors.toList());
        
        // Analyze with force update
        FeedItemChangeSet changeSet = feedItemService.compareFeedItemWithDB(true, sortedFeedItems);
        
        int totalItems = sortedFeedItems.size();
        int changedItems = changeSet.getChangedItems() != null ? changeSet.getChangedItems().size() : 0;
        int newItems = changeSet.getNewItems() != null ? changeSet.getNewItems().size() : 0;
        int deletedItems = changeSet.getDeletedItems() != null ? changeSet.getDeletedItems().size() : 0;
        
        logger.info("📊 Force Update Impact Analysis:");
        logger.info("  - Total feed items: " + totalItems);
        logger.info("  - Items that would be updated: " + changedItems);
        logger.info("  - New items that would be created: " + newItems);
        logger.info("  - Items that would be deleted: " + deletedItems);
        logger.info("  - Update percentage: " + String.format("%.2f%%", 
                    (double) changedItems / totalItems * 100));
        
        // Show first/last items by web_tag_number
        if (!sortedFeedItems.isEmpty()) {
            logger.info("🔢 Processing order (by web_tag_number):");
            logger.info("  - First item: " + sortedFeedItems.get(0).getWebTagNumber());
            logger.info("  - Last item: " + sortedFeedItems.get(sortedFeedItems.size() - 1).getWebTagNumber());
        }
        
        // Show sample of changes
        if (changeSet.getChangedItems() != null && !changeSet.getChangedItems().isEmpty()) {
            logger.info("📋 Sample of items that would be updated:");
            
            changeSet.getChangedItems().stream()
                     .limit(10)
                     .forEach(change -> {
                         logger.info("  📝 " + change.getFromFeed().getWebTagNumber() + 
                                    " (Status: " + change.getFromDb().getStatus() + 
                                    " → " + change.getFromFeed().getStatus() + ")");
                     });
        }
        
        logger.info("✅ Analysis complete - no changes were made");
    }
    
    /**
     * Retry all items with STATUS_UPDATE_FAILED using ProductUpdatePipeline
     * Simple approach: no batching, just for loop through failed items one by one
     */
    @Test
    public void retryUpdateFailedItems() throws Exception {
        logger.info("=== Retry Items with STATUS_UPDATE_FAILED ===");
        logger.info("🔍 Finding all items with STATUS_UPDATE_FAILED in database");
        logger.info("🗄️ Using existing database data (not refreshing feed)");
        logger.info("⚡ Using ProductUpdatePipeline for individual item updates");
        logger.info("🔄 Simple for loop approach - no batching");
        
        if (DRY_RUN) {
            logger.warn("🧪 DRY RUN MODE - No actual changes will be made");
        }
        
        try {
            // Find all items with STATUS_UPDATE_FAILED
            logger.info("🔍 Searching for items with STATUS_UPDATE_FAILED...");
            List<FeedItem> updateFailedItems = feedItemService.findByStatus(FeedItem.STATUS_UPDATE_FAILED);
            
            if (updateFailedItems.isEmpty()) {
                logger.info("✅ No items found with STATUS_UPDATE_FAILED - all items are in good status!");
                logger.info("🎯 No action needed - no failed items to retry");
                return;
            }
            
            logger.info("📊 Update Failed Items Analysis:");
            logger.info("  - Items with STATUS_UPDATE_FAILED: " + updateFailedItems.size());
            
            // Filter items that have Shopify ID (can be updated)
            List<FeedItem> retryableItems = updateFailedItems.stream()
                .filter(item -> item.getShopifyItemId() != null && !item.getShopifyItemId().trim().isEmpty())
                .sorted(Comparator.comparing(FeedItem::getWebTagNumber, 
                    Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
            
            logger.info("  - Items with Shopify ID (retryable): " + retryableItems.size());
            logger.info("  - Items without Shopify ID (skipped): " + (updateFailedItems.size() - retryableItems.size()));
            
            if (retryableItems.isEmpty()) {
                logger.warn("⚠️ No retryable items found - all failed items lack Shopify ID");
                return;
            }
            
            // Show sample of items to retry
            logger.info("📋 Sample of items to retry:");
            retryableItems.stream()
                         .limit(10)
                         .forEach(item -> {
                             logger.info("  📝 " + item.getWebTagNumber() + 
                                        " (Shopify ID: " + item.getShopifyItemId() + ")");
                         });
            
            if (DRY_RUN) {
                logger.info("🧪 DRY RUN: Would retry " + retryableItems.size() + " failed items");
                return;
            }
            
            // Simple for loop to retry each failed item
            int totalProcessed = 0;
            int totalSucceeded = 0;
            int totalStillFailed = 0;
            
            logger.info("🔄 Starting simple retry process...");
            
            for (FeedItem item : retryableItems) {
                try {
                    logger.info("🔄 Retrying item " + (totalProcessed + 1) + "/" + retryableItems.size() + 
                               ": " + item.getWebTagNumber());
                    syncService.updateItemOnShopify(item);
                    totalProcessed++;
                    
                } catch (Exception e) {
                    logger.error("❌ Exception during retry for item: " + item.getWebTagNumber() + " - " + e.getMessage(), e);
                    totalProcessed++;
                    totalStillFailed++;
                }
            }
            
            logger.info("📊 Retry Results:");
            logger.info("  - Total items processed: " + totalProcessed);
            logger.info("  - Total items succeeded: " + totalSucceeded);
            logger.info("  - Total items still failed: " + totalStillFailed);
            logger.info("  - Success rate: " + String.format("%.2f%%", 
                        totalProcessed > 0 ? (double) totalSucceeded / totalProcessed * 100 : 0.0));
            
            logger.info("🎉 Retry of UPDATE_FAILED items completed!");
            
        } catch (Exception e) {
            logger.error("❌ Retry process failed", e);
            throw e;
        }
    }
    
    /**
     * Validate eBay metafield definitions and recreate them if incorrect (only when needed)
     * This ensures all eBay metafields are properly configured and pinned
     */
    @Test
    public void validateAndFixEbayMetafields() throws Exception {
        logger.info("=== eBay Metafield Validation and Fix ===");
        logger.info("🔍 This will validate eBay metafield definitions and fix any issues only when needed");
        
        if (DRY_RUN) {
            logger.warn("🧪 DRY RUN MODE - No actual changes will be made");
        }
        
        try {
            // Analyze current metafield state
            MetafieldValidationResult validationResult = analyzeEbayMetafieldState();
            
            // Check if everything is already perfect
            boolean isPerfect = validationResult.hasAllExpected && 
                               validationResult.allPinned && 
                               validationResult.structureValid && 
                               !validationResult.hasError;
            
            if (isPerfect) {
                logger.info("🎉 All eBay metafields are already perfectly configured!");
                logger.info("✅ Count: " + validationResult.currentCount + "/13");
                logger.info("✅ All pinned: " + validationResult.allPinned);
                logger.info("✅ Structure valid: " + validationResult.structureValid);
                logger.info("🎯 No action needed - metafields are in perfect state!");
                return;
            }
            
            logger.info("🔧 Issues found - determining minimal fixes needed...");
            
            // Determine if recreation is needed
            boolean needsRecreation = shouldRecreateMetafields(validationResult);
            
            if (needsRecreation) {
                logger.info("🔄 Metafields need recreation due to structural issues");
                recreateEbayMetafields();
            } else if (!validationResult.allPinned) {
                logger.info("📌 Metafields exist but some need pinning");
                fixMetafieldPinning();
            } else {
                logger.info("✅ No fixes needed");
            }
            
            // Final validation only if we made changes
            if (needsRecreation || !validationResult.allPinned) {
                validateMetafieldFixResults();
            }
            
            logger.info("🎉 eBay metafield validation and fix completed successfully!");
            
        } catch (Exception e) {
            logger.error("❌ Error during eBay metafield validation/fix: " + e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Sync database only - no Shopify operations
     * Refreshes the database with feed data without making any Shopify API calls
     * This is useful for updating database state without affecting Shopify products
     */
    @Test
    public void syncDatabaseOnly() throws Exception {
        logger.info("=== Starting Database-Only Sync ===");
        logger.info("🗄️ This will sync database with feed data only");
        logger.info("📡 Will refresh live feed data");
        logger.info("🚫 NO Shopify operations will be performed");
        logger.info("🔢 Processing from smallest web_tag_number first");
        
        if (DRY_RUN) {
            logger.warn("🧪 DRY RUN MODE - No actual changes will be made");
        }
        
        // Get baseline database statistics
        logger.info("📊 Analyzing current database state...");
        List<FeedItem> dbItemsBefore = feedItemService.findAll();
        long publishedItemsBefore = dbItemsBefore.stream().filter(item -> item.getShopifyItemId() != null).count();
        
        logger.info("📋 Database State (Before):");
        logger.info("  - Total items in database: " + dbItemsBefore.size());
        logger.info("  - Items with Shopify ID: " + publishedItemsBefore);
        
        // Status breakdown before
        Map<String, Long> statusCountsBefore = dbItemsBefore.stream()
               .collect(Collectors.groupingBy(item -> 
                   item.getStatus() != null ? item.getStatus() : "NULL", Collectors.counting()));
        statusCountsBefore.forEach((status, count) -> 
               logger.info("  - Status '" + status + "': " + count + " items"));
        
        // Refresh feed from live source
        logger.info("📡 Refreshing live feed data...");
        List<FeedItem> feedItems = keyStoneFeedService.getItemsFromFeed();
        logger.info("📊 Fresh feed items loaded: " + feedItems.size());
        
        // Sort by web_tag_number (smallest first)
        List<FeedItem> sortedFeedItems = feedItems.stream()
            .sorted(Comparator.comparing(FeedItem::getWebTagNumber, 
                Comparator.nullsLast(Comparator.naturalOrder())))
            .collect(Collectors.toList());
        
        logger.info("🔢 Items sorted by web_tag_number (smallest first)");
        logger.info("📋 First 5 items: " + sortedFeedItems.stream()
            .limit(5)
            .map(FeedItem::getWebTagNumber)
            .collect(Collectors.toList()));
        
        // Analyze changes (database comparison only)
        logger.info("🔍 Analyzing changes between feed and database...");
        FeedItemChangeSet changeSet = feedItemService.compareFeedItemWithDB(false, sortedFeedItems);
        
        int newItems = changeSet.getNewItems() != null ? changeSet.getNewItems().size() : 0;
        int changedItems = changeSet.getChangedItems() != null ? changeSet.getChangedItems().size() : 0;
        int deletedItems = changeSet.getDeletedItems() != null ? changeSet.getDeletedItems().size() : 0;
        
        logger.info("📊 Change Analysis:");
        logger.info("  - New items (not in DB): " + newItems);
        logger.info("  - Changed items (different data): " + changedItems);
        logger.info("  - Deleted items (in DB but not in feed): " + deletedItems);
        logger.info("  - Total feed items: " + sortedFeedItems.size());
        
        if (newItems == 0 && changedItems == 0 && deletedItems == 0) {
            logger.info("✅ Database is already synchronized with feed - no changes needed!");
            return;
        }
        
        // Perform database-only sync operations
        if (!DRY_RUN) {
            syncDatabaseWithFeedData(changeSet);
        } else {
            logger.info("🧪 DRY RUN: Would sync database with " + (newItems + changedItems + deletedItems) + " total changes");
        }
        
        // Analyze final database state
        logger.info("📊 Analyzing final database state...");
        List<FeedItem> dbItemsAfter = feedItemService.findAll();
        long publishedItemsAfter = dbItemsAfter.stream().filter(item -> item.getShopifyItemId() != null).count();
        
        logger.info("📋 Database State (After):");
        logger.info("  - Total items in database: " + dbItemsAfter.size());
        logger.info("  - Items with Shopify ID: " + publishedItemsAfter);
        logger.info("  - Net change in total items: " + (dbItemsAfter.size() - dbItemsBefore.size()));
        logger.info("  - Net change in published items: " + (publishedItemsAfter - publishedItemsBefore));
        
        // Status breakdown after
        Map<String, Long> statusCountsAfter = dbItemsAfter.stream()
               .collect(Collectors.groupingBy(item -> 
                   item.getStatus() != null ? item.getStatus() : "NULL", Collectors.counting()));
        statusCountsAfter.forEach((status, count) -> 
               logger.info("  - Status '" + status + "': " + count + " items"));
        
        logger.info("🎉 Database-only sync completed successfully!");
        logger.info("ℹ️ NOTE: No Shopify products were modified - database only sync");
    }
    
    /**
     * Perform database synchronization with feed data without any Shopify operations
     */
    private void syncDatabaseWithFeedData(FeedItemChangeSet changeSet) throws Exception {
        logger.info("🔄 Performing database-only synchronization...");
        
        int totalProcessed = 0;
        int totalErrors = 0;
        
        // Handle new items (add to database only)
        if (changeSet.getNewItems() != null && !changeSet.getNewItems().isEmpty()) {
            logger.info("➕ Adding " + changeSet.getNewItems().size() + " new items to database...");
            for (FeedItem newItem : changeSet.getNewItems()) {
                try {
                    // Set appropriate status for new items (not published to Shopify)
                    newItem.setStatus(FeedItem.STATUS_NEW_WAITING_PUBLISH);
                    newItem.setShopifyItemId(null); // Ensure no Shopify ID
                    feedItemService.saveAutonomous(newItem);
                    totalProcessed++;
                    logger.debug("✅ Added new item to database: " + newItem.getWebTagNumber());
                } catch (Exception e) {
                    logger.error("❌ Failed to add item to database: " + newItem.getWebTagNumber() + " - " + e.getMessage());
                    totalErrors++;
                }
            }
            logger.info("✅ Completed adding new items: " + (changeSet.getNewItems().size() - totalErrors) + " successful, " + totalErrors + " errors");
        }
        
        // Handle changed items (update database only)
        if (changeSet.getChangedItems() != null && !changeSet.getChangedItems().isEmpty()) {
            logger.info("📝 Updating " + changeSet.getChangedItems().size() + " changed items in database...");
            int changedErrors = 0;
            for (FeedItemChange change : changeSet.getChangedItems()) {
                try {
                    FeedItem itemFromDb = change.getFromDb();
                    FeedItem itemFromFeed = change.getFromFeed();
                    
                    // Preserve Shopify-related fields from database
                    String originalShopifyId = itemFromDb.getShopifyItemId();
                    String originalStatus = itemFromDb.getStatus();
                    java.util.Date originalPublishedDate = itemFromDb.getPublishedDate();
                    
                    // Copy feed data to database item
                    itemFromDb.copyFrom(itemFromFeed);
                    
                    // Restore Shopify-related fields (don't overwrite from feed)
                    itemFromDb.setShopifyItemId(originalShopifyId);
                    // Keep existing status for published items
                    itemFromDb.setStatus(originalStatus);
                    itemFromDb.setPublishedDate(originalPublishedDate);

                    if (originalShopifyId == null || originalShopifyId.trim().isEmpty()) {
                        // Update status for unpublished items
                        itemFromDb.setStatus(originalStatus);
                        itemFromDb.setPublishedDate(originalPublishedDate);
                        logger.error("🔄 Item was determined changed but has no Shopify ID: " + itemFromDb.getWebTagNumber() + " is new and waiting to be published");
                    }
                    
                    feedItemService.updateAutonomous(itemFromDb);
                    totalProcessed++;
                    logger.debug("✅ Updated item in database: " + itemFromDb.getWebTagNumber());
                } catch (Exception e) {
                    logger.error("❌ Failed to update item in database: " + change.getFromDb().getWebTagNumber() + " - " + e.getMessage());
                    changedErrors++;
                    totalErrors++;
                }
            }
            logger.info("✅ Completed updating changed items: " + (changeSet.getChangedItems().size() - changedErrors) + " successful, " + changedErrors + " errors");
        }
        
        // Handle deleted items (remove from database only)
        if (changeSet.getDeletedItems() != null && !changeSet.getDeletedItems().isEmpty()) {
            logger.info("🗑️ Removing " + changeSet.getDeletedItems().size() + " deleted items from database...");
            logger.warn("⚠️ CAUTION: Items removed from database but Shopify products will remain");
            int deletedErrors = 0;
            for (FeedItem deletedItem : changeSet.getDeletedItems()) {
                try {
                    if (deletedItem.getShopifyItemId() != null && !deletedItem.getShopifyItemId().trim().isEmpty()) {
                        logger.warn("⚠️ Removing database item that has Shopify ID: " + deletedItem.getWebTagNumber() + 
                                   " (Shopify ID: " + deletedItem.getShopifyItemId() + ") - Shopify product will remain orphaned");
                    }
                    feedItemService.deleteAutonomous(deletedItem);
                    totalProcessed++;
                    logger.debug("✅ Removed item from database: " + deletedItem.getWebTagNumber());
                } catch (Exception e) {
                    logger.error("❌ Failed to remove item from database: " + deletedItem.getWebTagNumber() + " - " + e.getMessage());
                    deletedErrors++;
                    totalErrors++;
                }
            }
            logger.info("✅ Completed removing deleted items: " + (changeSet.getDeletedItems().size() - deletedErrors) + " successful, " + deletedErrors + " errors");
        }
        
        logger.info("📊 Database-only sync results:");
        logger.info("  - Total operations: " + totalProcessed);
        logger.info("  - Total errors: " + totalErrors);
        logger.info("  - Success rate: " + String.format("%.2f%%", 
                    totalProcessed > 0 ? (double) (totalProcessed - totalErrors) / totalProcessed * 100 : 100.0));
        
        if (totalErrors > 0) {
            logger.warn("⚠️ " + totalErrors + " errors occurred during database sync - check logs for details");
        }
    }
    
    /**
     * Fix empty product descriptions by updating from feed items
     * Gets all products from Shopify, checks if they have descriptions, and force updates empty ones
     */
    @Test
    public void fixEmptyDescriptions() throws Exception {
        logger.info("=== Fix Empty Product Descriptions ===");
        logger.info("🔍 This will identify products with empty descriptions and fix them");
        logger.info("📝 Will force update descriptions from corresponding FeedItems");
        logger.info("🗄️ Using existing database data (not refreshing feed)");
        
        if (DRY_RUN) {
            logger.warn("🧪 DRY RUN MODE - No actual changes will be made");
        }
        
        try {
            // Get all products from Shopify
            logger.info("🛍️ Getting all products from Shopify...");
            List<Product> shopifyProducts = shopifyApiService.getAllProducts();
            logger.info("📊 Found " + shopifyProducts.size() + " products in Shopify");
            
            // Get all feed items from database for comparison
            logger.info("🗄️ Getting all feed items from database...");
            List<FeedItem> dbItems = feedItemService.findAll();
            logger.info("📊 Found " + dbItems.size() + " items in database");
            
            // Validate descriptions and find products needing fixes
            DescriptionValidationResult validationResult = validateProductDescriptions(shopifyProducts, dbItems);
            
            logger.info("📊 Description Validation Results:");
            logger.info("  - Total products checked: " + validationResult.productsChecked);
            logger.info("  - Products with descriptions: " + validationResult.productsWithDescriptions);
            logger.info("  - Products with empty descriptions: " + validationResult.emptyDescriptions.size());
            logger.info("  - Products without matching feed items: " + validationResult.noMatchingFeedItem);
            logger.info("  - Products ready for description fix: " + validationResult.productsReadyForFix.size());
            
            if (validationResult.emptyDescriptions.isEmpty()) {
                logger.info("🎉 All products already have descriptions - no fixes needed!");
                return;
            }
            
            // Show sample of products with empty descriptions
            if (!validationResult.emptyDescriptions.isEmpty()) {
                logger.info("📋 Sample products with empty descriptions:");
                validationResult.emptyDescriptions.stream()
                    .limit(10)
                    .forEach(sku -> logger.info("  - SKU: " + sku));
                
                if (validationResult.emptyDescriptions.size() > 10) {
                    logger.info("  ... and " + (validationResult.emptyDescriptions.size() - 10) + " more");
                }
            }
            
            if (validationResult.productsReadyForFix.isEmpty()) {
                logger.warn("⚠️ No products can be fixed - all empty descriptions lack matching feed items");
                return;
            }
            
            if (DRY_RUN) {
                logger.info("🧪 DRY RUN: Would fix descriptions for " + validationResult.productsReadyForFix.size() + " products");
                return;
            }
            
            // Fix empty descriptions in batches
            fixEmptyDescriptionsInBatches(validationResult.productsReadyForFix);
            
            // Final validation
            validateDescriptionFixResults();
            
            logger.info("🎉 Description fix completed successfully!");
            
        } catch (Exception e) {
            logger.error("❌ Error during description fix: " + e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Helper method to validate product descriptions and identify products needing fixes
     * 
     * @param products List of Shopify products to validate
     * @param feedItems List of feed items with description information
     * @return DescriptionValidationResult with statistics and products ready for fixing
     */
    private DescriptionValidationResult validateProductDescriptions(List<Product> products, List<FeedItem> feedItems) {
        logger.info("📝 Validating product descriptions...");
        logger.info("💡 Business rules: Products should have descriptions from feed items");
        
        int productsChecked = 0;
        int productsWithDescriptions = 0;
        int noMatchingFeedItem = 0;
        List<String> emptyDescriptions = new ArrayList<>();
        List<FeedItem> productsReadyForFix = new ArrayList<>();
        boolean loggedDescriptionSample = false;
        
        for (Product product : products) {
            // Get the SKU to match with feed items
            String productSku = null;
            if (product.getVariants() != null && !product.getVariants().isEmpty()) {
                productSku = product.getVariants().get(0).getSku();
            }
            
            if (productSku != null) {
                productsChecked++;
                
                // Find matching feed item by SKU
                FeedItem matchingFeedItem = null;
                for (FeedItem feedItem : feedItems) {
                    if (feedItem.getWebTagNumber() != null && feedItem.getWebTagNumber().equals(productSku)) {
                        matchingFeedItem = feedItem;
                        break;
                    }
                }
                
                if (matchingFeedItem == null) {
                    noMatchingFeedItem++;
                    logger.debug("⚠️ No matching feed item found for SKU: " + productSku);
                    continue;
                }
                
                // Check if product description is empty or null
                String productDescription = product.getBodyHtml();
                boolean hasDescription = productDescription != null && !productDescription.trim().isEmpty();
                
                // Log sample for debugging
                if (!loggedDescriptionSample) {
                    logger.info("📊 Sample Description Validation (SKU: {}):", productSku);
                    logger.info("  Shopify description: '{}'", 
                        productDescription != null ? productDescription.substring(0, Math.min(100, productDescription.length())) + "..." : "NULL");
                    logger.info("  Feed item description: '{}'", 
                        matchingFeedItem.getWebDescriptionShort() != null ? 
                        matchingFeedItem.getWebDescriptionShort().substring(0, Math.min(100, matchingFeedItem.getWebDescriptionShort().length())) + "..." : "NULL");
                    logger.info("  Has description: {}", hasDescription);
                    loggedDescriptionSample = true;
                }
                
                if (hasDescription) {
                    productsWithDescriptions++;
                } else {
                    emptyDescriptions.add(productSku);
                    
                    // Check if feed item has description data to fix with
                    String feedDescription = matchingFeedItem.getWebDescriptionShort();
                    if (feedDescription != null && !feedDescription.trim().isEmpty()) {
                        productsReadyForFix.add(matchingFeedItem);
                        logger.debug("✅ Product ready for description fix: " + productSku);
                    } else {
                        logger.debug("⚠️ Product has empty description but feed item also lacks description: " + productSku);
                    }
                }
            }
        }
        
        return new DescriptionValidationResult(productsChecked, productsWithDescriptions, 
            noMatchingFeedItem, emptyDescriptions, productsReadyForFix);
    }
    
    /**
     * Fix empty descriptions by updating products in batches
     */
    private void fixEmptyDescriptionsInBatches(List<FeedItem> feedItemsToFix) throws Exception {
        logger.info("🔧 Fixing empty descriptions in batches of " + BATCH_SIZE + "...");
        
        // Sort by web_tag_number for predictable processing order
        List<FeedItem> sortedItems = feedItemsToFix.stream()
            .sorted(Comparator.comparing(FeedItem::getWebTagNumber, 
                Comparator.nullsLast(Comparator.naturalOrder())))
            .collect(Collectors.toList());
        
        int totalBatches = (int) Math.ceil((double) sortedItems.size() / BATCH_SIZE);
        int totalProcessed = 0;
        int totalFixed = 0;
        int totalErrors = 0;
        
        for (int batchIndex = 0; batchIndex < totalBatches; batchIndex++) {
            int startIndex = batchIndex * BATCH_SIZE;
            int endIndex = Math.min(startIndex + BATCH_SIZE, sortedItems.size());
            
            List<FeedItem> batch = sortedItems.subList(startIndex, endIndex);
            
            logger.info("📦 Processing description fix batch " + (batchIndex + 1) + "/" + totalBatches + 
                       " (items " + startIndex + "-" + (endIndex - 1) + ")");
            
            // Log the web_tag_numbers in this batch
            String batchWebTags = batch.stream()
                .map(FeedItem::getWebTagNumber)
                .collect(Collectors.joining(", "));
            logger.info("🔢 Batch web_tag_numbers: " + batchWebTags);
            
            // Process this batch
            for (FeedItem feedItem : batch) {
                try {
                    logger.info("🔧 Fixing description for SKU: " + feedItem.getWebTagNumber());
                    
                    // Use the sync service to update the item (which will update description)
                    syncService.updateItemOnShopify(feedItem);
                    
                    totalProcessed++;
                    totalFixed++;
                    
                    logger.debug("✅ Fixed description for: " + feedItem.getWebTagNumber());
                    
                } catch (Exception e) {
                    totalProcessed++;
                    totalErrors++;
                    logger.error("❌ Failed to fix description for SKU: " + feedItem.getWebTagNumber() + " - " + e.getMessage());
                }
            }
            
            logger.info("✅ Batch " + (batchIndex + 1) + " completed: " + batch.size() + " items processed");
            
            // Small delay between batches to be gentle on APIs
            if (batchIndex < totalBatches - 1) {
                Thread.sleep(2000); // 2 second delay between batches
            }
        }
        
        logger.info("📊 Description Fix Results:");
        logger.info("  - Total items processed: " + totalProcessed);
        logger.info("  - Total descriptions fixed: " + totalFixed);
        logger.info("  - Total errors: " + totalErrors);
        logger.info("  - Success rate: " + String.format("%.2f%%", 
                    totalProcessed > 0 ? (double) (totalProcessed - totalErrors) / totalProcessed * 100 : 100.0));
        
        if (totalErrors > 0) {
            logger.warn("⚠️ " + totalErrors + " errors occurred during description fixes - check logs for details");
        }
    }
    
    /**
     * Validate that description fixes were successful
     */
    private void validateDescriptionFixResults() throws Exception {
        logger.info("🔍 Validating description fix results...");
        
        try {
            // Get updated products from Shopify
            List<Product> updatedProducts = shopifyApiService.getAllProducts();
            List<FeedItem> dbItems = feedItemService.findAll();
            
            // Re-validate descriptions
            DescriptionValidationResult result = validateProductDescriptions(updatedProducts, dbItems);
            
            logger.info("📊 Post-Fix Validation Results:");
            logger.info("  - Total products checked: " + result.productsChecked);
            logger.info("  - Products with descriptions: " + result.productsWithDescriptions);
            logger.info("  - Products still missing descriptions: " + result.emptyDescriptions.size());
            logger.info("  - Description completion rate: " + String.format("%.2f%%", 
                        result.productsChecked > 0 ? (double) result.productsWithDescriptions / result.productsChecked * 100 : 100.0));
            
            // Show any remaining products with empty descriptions
            if (!result.emptyDescriptions.isEmpty()) {
                logger.warn("⚠️ Products still missing descriptions:");
                result.emptyDescriptions.stream()
                    .limit(10)
                    .forEach(sku -> logger.warn("  - SKU: " + sku));
                
                if (result.emptyDescriptions.size() > 10) {
                    logger.warn("  ... and " + (result.emptyDescriptions.size() - 10) + " more");
                }
            }
            
            // Assertions for test validation
            if (!DRY_RUN) {
                // Allow some tolerance - not all products may have description data in feed
                double descriptionRate = result.productsChecked > 0 ? 
                    (double) result.productsWithDescriptions / result.productsChecked * 100 : 100.0;
                
                assertTrue(descriptionRate >= 50.0, 
                          "Description completion rate too low: " + String.format("%.2f%%", descriptionRate) + 
                          " (expected at least 50%)");
            }
            
            logger.info("✅ Description fix validation complete");
            
        } catch (Exception e) {
            logger.error("❌ Error validating description fix results: " + e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Result class for description validation
     */
    private static class DescriptionValidationResult {
        final int productsChecked;
        final int productsWithDescriptions;
        final int noMatchingFeedItem;
        final List<String> emptyDescriptions;
        final List<FeedItem> productsReadyForFix;
        
        DescriptionValidationResult(int productsChecked, int productsWithDescriptions, 
                                  int noMatchingFeedItem, List<String> emptyDescriptions, 
                                  List<FeedItem> productsReadyForFix) {
            this.productsChecked = productsChecked;
            this.productsWithDescriptions = productsWithDescriptions;
            this.noMatchingFeedItem = noMatchingFeedItem;
            this.emptyDescriptions = emptyDescriptions;
            this.productsReadyForFix = productsReadyForFix;
        }
    }
    
    /**
     * Analyze current state before force update
     */
    private void analyzeCurrentState() throws Exception {
        logger.info("📊 Analyzing current state...");
        
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
               .collect(Collectors.groupingBy(item -> 
                   item.getStatus() != null ? item.getStatus() : "NULL", Collectors.counting()))
               .forEach((status, count) -> 
                   logger.info("  - Status '" + status + "': " + count + " items"));
        
        // Show web_tag_number range
        List<String> webTagNumbers = dbItems.stream()
            .map(FeedItem::getWebTagNumber)
            .filter(tag -> tag != null && !tag.trim().isEmpty())
            .sorted()
            .collect(Collectors.toList());
        
        if (!webTagNumbers.isEmpty()) {
            logger.info("🔢 Web Tag Number Range:");
            logger.info("  - Smallest: " + webTagNumbers.get(0));
            logger.info("  - Largest: " + webTagNumbers.get(webTagNumbers.size() - 1));
        }
    }
    
    /**
     * Process feed items in controlled batches with force update (only update what needs updating)
     */
    private void forceUpdateItemsInBatches(List<FeedItem> sortedFeedItems, String operation) throws Exception {
        logger.info("🔄 Analyzing items for force update in batches of " + BATCH_SIZE + " (" + operation + ")...");
        
        // First, check which items actually need updating
        logger.info("🔍 Pre-checking which items need force updates...");
        FeedItemChangeSet changeSet = feedItemService.compareFeedItemWithDB(true, sortedFeedItems);
        
        int totalChangedItems = changeSet.getChangedItems() != null ? changeSet.getChangedItems().size() : 0;
        int totalNewItems = changeSet.getNewItems() != null ? changeSet.getNewItems().size() : 0;
        int totalItemsNeedingUpdate = totalChangedItems + totalNewItems;
        
        if (totalItemsNeedingUpdate == 0) {
            logger.info("✅ All items are already up to date - no force updates needed!");
            logger.info("📊 Total items checked: " + sortedFeedItems.size());
            logger.info("🎯 No action needed - everything is current!");
            return;
        }
        
        logger.info("📊 Force Update Analysis:");
        logger.info("  - Total items: " + sortedFeedItems.size());
        logger.info("  - Items needing updates: " + totalItemsNeedingUpdate);
        logger.info("  - Changed items: " + totalChangedItems);
        logger.info("  - New items: " + totalNewItems);
        logger.info("  - Update percentage: " + String.format("%.2f%%", 
                    (double) totalItemsNeedingUpdate / sortedFeedItems.size() * 100));
        
        // Only process items that actually need updates
        List<FeedItem> itemsToUpdate = new ArrayList<>();
        if (changeSet.getChangedItems() != null) {
            changeSet.getChangedItems().forEach(change -> itemsToUpdate.add(change.getFromFeed()));
        }
        if (changeSet.getNewItems() != null) {
            itemsToUpdate.addAll(changeSet.getNewItems());
        }
        
        // Sort the items to update by web_tag_number (maintain order)
        itemsToUpdate.sort(Comparator.comparing(FeedItem::getWebTagNumber, 
            Comparator.nullsLast(Comparator.naturalOrder())));
        
        logger.info("🔧 Processing " + itemsToUpdate.size() + " items that actually need updates...");
        
        int totalBatches = (int) Math.ceil((double) itemsToUpdate.size() / BATCH_SIZE);
        int totalProcessed = 0;
        int totalUpdated = 0;
        int totalErrors = 0;
        
        for (int batchIndex = 0; batchIndex < totalBatches; batchIndex++) {
            int startIndex = batchIndex * BATCH_SIZE;
            int endIndex = Math.min(startIndex + BATCH_SIZE, itemsToUpdate.size());
            
            List<FeedItem> batch = itemsToUpdate.subList(startIndex, endIndex);
            
            logger.info("📦 Processing batch " + (batchIndex + 1) + "/" + totalBatches + 
                       " (items " + startIndex + "-" + (endIndex - 1) + " of " + itemsToUpdate.size() + " that need updates)");
            
            // Log the web_tag_numbers in this batch
            String batchWebTags = batch.stream()
                .map(FeedItem::getWebTagNumber)
                .collect(Collectors.joining(", "));
            logger.info("🔢 Batch web_tag_numbers: " + batchWebTags);
            
            // Process this batch with force update
            BatchResult result = processForceUpdateBatch(batch, batchIndex + 1);
            
            totalProcessed += result.processed;
            totalUpdated += result.updated;
            totalErrors += result.errors;
            
            logger.info("✅ Batch completed: " + result.processed + " processed, " + 
                       result.updated + " updated, " + result.errors + " errors");
            
            // Small delay between batches to be gentle on APIs
            if (batchIndex < totalBatches - 1) {
                Thread.sleep(2000); // 2 second delay between batches
            }
        }
        
        logger.info("📊 Overall Results (" + operation + "):");
        logger.info("  - Total items that needed updates: " + itemsToUpdate.size());
        logger.info("  - Total items processed: " + totalProcessed);
        logger.info("  - Total items updated: " + totalUpdated);
        logger.info("  - Total errors: " + totalErrors);
        logger.info("  - Items skipped (already current): " + (sortedFeedItems.size() - itemsToUpdate.size()));
        logger.info("  - Success rate: " + String.format("%.2f%%", 
                    (double) (totalProcessed - totalErrors) / totalProcessed * 100));
    }
    
    /**
     * Process a single batch with force update
     */
    private BatchResult processForceUpdateBatch(List<FeedItem> batch, int batchNumber) throws Exception {
        BatchResult result = new BatchResult();
        
        try {
            logger.info("🔧 Batch " + batchNumber + ": Applying force update...");
            
            if (!DRY_RUN) {
                // Force update by calling sync with force flag enabled
                // The @TestPropertySource annotation ensures shopify.force.update=true
                syncService.doSyncForFeedItems(batch);
                logger.info("✅ Force update applied for batch " + batchNumber);
                result.updated = batch.size();
            } else {
                logger.info("🧪 DRY RUN: Would force update " + batch.size() + " items");
                result.updated = 0;
            }
            
            result.processed = batch.size();
            
        } catch (Exception e) {
            logger.error("❌ Error processing force update batch " + batchNumber + ": " + e.getMessage(), e);
            result.processed = batch.size();
            result.errors = batch.size();
            e.getMessage();
        }
        
        return result;
    }
    
    /**
     * Validate force update results
     */
    private void validateForceUpdateResults() throws Exception {
        logger.info("🔍 Validating force update results...");
        
        // Check database status distribution
        List<FeedItem> dbItems = feedItemService.findAll();
        long publishedItems = dbItems.stream().filter(item -> item.getShopifyItemId() != null).count();
        long failedItems = dbItems.stream()
                                 .filter(item -> item.getStatus() != null && 
                                               (item.getStatus().contains("FAILED") || 
                                                item.getStatus().contains("ERROR")))
                                 .count();
        
        logger.info("📊 Validation Results:");
        logger.info("  - Total items in database: " + dbItems.size());
        logger.info("  - Items with Shopify ID: " + publishedItems);
        logger.info("  - Items with failed/error status: " + failedItems);
        
        // Status breakdown
        dbItems.stream()
               .collect(Collectors.groupingBy(item -> 
                   item.getStatus() != null ? item.getStatus() : "NULL", Collectors.counting()))
               .forEach((status, count) -> 
                   logger.info("  - Status '" + status + "': " + count + " items"));
        
        // Final assertions for test success
        if (!DRY_RUN) {
            assertTrue(failedItems < 50, 
                      "Too many failed items (" + failedItems + ") - force update may have issues");
        }
        
        logger.info("✅ Validation complete");
    }
    
    /**
     * Validate specific item update
     */
    private void validateSpecificItemUpdate(String webTagNumber) throws Exception {
        logger.info("🔍 Validating specific item update...");
        
        // Re-fetch the item from database
        FeedItem updatedItem = feedItemService.findByWebTagNumber(webTagNumber);
        
        if (updatedItem == null) {
            throw new AssertionError("❌ Item not found after update: " + webTagNumber);
        }
        
        logger.info("📊 Updated Item Validation:");
        logger.info("  - Web Tag Number: " + updatedItem.getWebTagNumber());
        logger.info("  - Current Status: " + updatedItem.getStatus());
        logger.info("  - Shopify ID: " + updatedItem.getShopifyItemId());
        logger.info("  - Last Updated: " + updatedItem.getLastUpdatedDate());
        
        // Check if item has Shopify ID (should be published)
        boolean hasShopifyId = updatedItem.getShopifyItemId() != null && 
                              !updatedItem.getShopifyItemId().trim().isEmpty();
        
        if (hasShopifyId) {
            logger.info("✅ Item successfully published to Shopify");
        } else {
            logger.warn("⚠️ Item does not have Shopify ID - may not be published");
        }
        
        // Check status
        String status = updatedItem.getStatus();
        boolean hasErrorStatus = status != null && 
                               (status.contains("FAILED") || status.contains("ERROR"));
        
        if (hasErrorStatus) {
            logger.warn("⚠️ Item has error status: " + status);
        } else {
            logger.info("✅ Item status looks good: " + status);
        }
        
        logger.info("✅ Specific item validation complete");
    }
    
    /**
     * Analyze current eBay metafield state
     */
    private MetafieldValidationResult analyzeEbayMetafieldState() throws Exception {
        logger.info("🔍 Analyzing current eBay metafield state...");
        
        MetafieldValidationResult result = new MetafieldValidationResult();
        
        try {
            // Get all eBay metafield definitions
            List<Map<String, String>> ebayMetafields = shopifyApiService.getMetafieldDefinitions("ebay");
            
            result.currentCount = ebayMetafields.size();
            result.expectedCount = EbayMetafieldDefinition.getCount(); // Use enum count
            
            logger.info("📊 Current eBay Metafield State:");
            logger.info("  - Found metafields: " + result.currentCount);
            logger.info("  - Expected metafields: " + result.expectedCount);
            
            // Check if all expected metafields exist
            result.hasAllExpected = (result.currentCount == result.expectedCount);
            
            // Check pinning status
            long pinnedCount = ebayMetafields.stream()
                .filter(metafield -> {
                    String pinnedPosition = metafield.get("pinnedPosition");
                    return pinnedPosition != null && !pinnedPosition.trim().isEmpty() && !pinnedPosition.equals("null");
                })
                .count();
            
            result.pinnedCount = (int) pinnedCount;
            result.allPinned = (pinnedCount == result.currentCount && result.currentCount > 0);
            
            logger.info("  - Pinned metafields: " + result.pinnedCount + "/" + result.currentCount);
            logger.info("  - All pinned: " + result.allPinned);
            
            // Log details of current metafields
            if (!ebayMetafields.isEmpty()) {
                logger.info("📋 Current eBay Metafields:");
                ebayMetafields.forEach(metafield -> {
                    String key = metafield.get("key");
                    String name = metafield.get("name");
                    String pinnedPos = metafield.get("pinnedPosition");
                    String pinStatus = (pinnedPos != null && !pinnedPos.equals("null")) ? 
                                      "📌 Position " + pinnedPos : "❌ Not pinned";
                    logger.info("  - " + key + " (" + name + ") - " + pinStatus);
                });
            }
            
            // Validate metafield structure and content
            result.structureValid = validateMetafieldStructure(ebayMetafields);
            
        } catch (Exception e) {
            logger.error("❌ Error analyzing metafield state: " + e.getMessage());
            result.hasError = true;
            e.getMessage();
        }
        
        return result;
    }
    
    /**
     * Validate the structure and content of metafield definitions
     * Enhanced to validate both keys AND types against the EbayMetafieldDefinition enum
     */
    private boolean validateMetafieldStructure(List<Map<String, String>> metafields) {
        int expectedCount = EbayMetafieldDefinition.getCount();
        if (metafields.size() != expectedCount) {
            logger.warn("⚠️ Expected " + expectedCount + " eBay metafields, found " + metafields.size());
            return false;
        }
        
        // Get expected keys from enum (already sorted)
        List<String> expectedKeys = EbayMetafieldDefinition.getAllKeys();
        
        // Get current keys from existing metafields (sorted)
        List<String> currentKeys = metafields.stream()
            .map(m -> m.get("key"))
            .sorted()
            .collect(Collectors.toList());
        
        boolean keysValid = currentKeys.equals(expectedKeys);
        
        if (!keysValid) {
            logger.warn("⚠️ Metafield key validation failed");
            logger.warn("  Expected keys: " + expectedKeys);
            logger.warn("  Current keys: " + currentKeys);
            return false;
        }
        
        // NEW: Validate types for each metafield
        boolean typesValid = true;
        for (Map<String, String> metafield : metafields) {
            String key = metafield.get("key");
            String currentType = extractTypeFromMetafieldMap(metafield);
            
            // Find the expected type from enum
            EbayMetafieldDefinition definition = EbayMetafieldDefinition.findByKey(key);
            if (definition != null) {
                String expectedType = definition.getType();
                if (!expectedType.equals(currentType)) {
                    logger.warn("⚠️ Type mismatch for metafield '" + key + "': expected '" + expectedType + "', found '" + currentType + "'");
                    typesValid = false;
                }
            }
        }
        
        if (!typesValid) {
            logger.warn("⚠️ Metafield type validation failed - some types don't match enum definitions");
            return false;
        }
        
        logger.info("✅ Metafield structure validation passed - all " + expectedCount + " expected keys and types found");
        return true;
    }
    
    /**
     * Extract type from metafield map (handles both GraphQL response formats)
     */
    private String extractTypeFromMetafieldMap(Map<String, String> metafield) {
        String type = metafield.get("type");
        if (type != null) {
            // Handle nested type object from GraphQL (type.name)
            if (type.startsWith("{") && type.contains("name")) {
                // This might be a JSON string representation of the type object
                try {
                    // Simple extraction for type.name pattern
                    if (type.contains("single_line_text_field")) {
                        return "single_line_text_field";
                    } else if (type.contains("number_decimal")) {
                        return "number_decimal";
                    }
                    // Note: multi_line_text_field removed since all eBay metafields now use single_line_text_field
                } catch (Exception e) {
                    logger.debug("Could not parse type object: " + type);
                }
            }
            return type;
        }
        return "unknown";
    }
    
    /**
     * Determine if metafields need to be recreated
     */
    private boolean shouldRecreateMetafields(MetafieldValidationResult result) {
        if (result.hasError) {
            logger.info("🔄 Recreation needed: Error occurred during validation");
            return true;
        }
        
        if (!result.hasAllExpected) {
            logger.info("🔄 Recreation needed: Missing metafields (" + 
                       result.currentCount + "/" + result.expectedCount + ")");
            return true;
        }
        
        if (!result.structureValid) {
            logger.info("🔄 Recreation needed: Invalid metafield structure");
            return true;
        }
        
        logger.info("✅ Metafields exist and structure is valid");
        return false;
    }
    
    /**
     * Recreate all eBay metafields from scratch (only if needed)
     */
    private void recreateEbayMetafields() throws Exception {
        logger.info("🔄 Checking if eBay metafields need recreation...");
        
        if (DRY_RUN) {
            logger.info("🧪 DRY RUN: Would recreate eBay metafields");
            return;
        }
        
        try {
            // First check if we actually need to recreate
            List<Map<String, String>> existingMetafields = shopifyApiService.getMetafieldDefinitions("ebay");
            
            // Check if structure is already correct
            if (existingMetafields.size() == 13 && validateMetafieldStructure(existingMetafields)) {
                logger.info("✅ eBay metafields already have correct structure - skipping recreation");
                return;
            }
            
            logger.info("🔄 eBay metafields need recreation (count: " + existingMetafields.size() + "/13, structure valid: " + validateMetafieldStructure(existingMetafields) + ")");
            
            // Remove existing eBay metafield definitions
            if (!existingMetafields.isEmpty()) {
                logger.info("🗑️ Removing " + existingMetafields.size() + " existing eBay metafield definitions...");
                for (Map<String, String> metafield : existingMetafields) {
                    String id = metafield.get("id");
                    String key = metafield.get("key");
                    
                    if (id != null) {
                        shopifyApiService.deleteMetafieldDefinition(id);
                        logger.info("🗑️ Deleted metafield definition: ebay." + key + " (ID: " + id + ")");
                        
                        // Small delay to avoid overwhelming the API
                        Thread.sleep(200);
                    }
                }
                logger.info("🗑️ Removed " + existingMetafields.size() + " existing eBay metafield definitions");
            } else {
                logger.info("ℹ️ No existing eBay metafield definitions to remove");
            }
            
            // Create fresh eBay metafield definitions
            logger.info("🏗️ Creating fresh eBay metafield definitions...");
            shopifyApiService.createEbayMetafieldDefinitions();
            logger.info("✅ Successfully recreated eBay metafield definitions");
            
        } catch (Exception e) {
            logger.error("❌ Error recreating eBay metafields: " + e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Fix metafield pinning without recreating (only pin what needs pinning)
     */
    private void fixMetafieldPinning() throws Exception {
        logger.info("📌 Checking which eBay metafields need pinning...");
        
        if (DRY_RUN) {
            logger.info("🧪 DRY RUN: Would fix metafield pinning");
            return;
        }
        
        try {
            // Get current metafields
            List<Map<String, String>> metafields = shopifyApiService.getMetafieldDefinitions("ebay");
            
            // First check if all are already pinned
            long alreadyPinnedCount = metafields.stream()
                .filter(metafield -> {
                    String pinnedPosition = metafield.get("pinnedPosition");
                    return pinnedPosition != null && !pinnedPosition.trim().isEmpty() && !pinnedPosition.equals("null");
                })
                .count();
            
            if (alreadyPinnedCount == metafields.size() && metafields.size() > 0) {
                logger.info("✅ All " + metafields.size() + " eBay metafields are already pinned - no action needed");
                return;
            }
            
            logger.info("📌 Found " + alreadyPinnedCount + "/" + metafields.size() + " metafields already pinned - fixing the rest...");
            
            // Pin each metafield that isn't already pinned
            int pinnedCount = 0;
            for (int i = 0; i < metafields.size(); i++) {
                Map<String, String> metafield = metafields.get(i);
                String id = metafield.get("id");
                String key = metafield.get("key");
                String currentPinnedPos = metafield.get("pinnedPosition");
                
                boolean needsPinning = (currentPinnedPos == null || 
                                       currentPinnedPos.trim().isEmpty() || 
                                       currentPinnedPos.equals("null"));
                
                if (needsPinning && id != null) {
                    int position = i + 1; // 1-based position
                    shopifyApiService.pinMetafieldDefinition(id);
                    logger.info("📌 Pinned metafield: ebay." + key + " at position " + position);
                    pinnedCount++;
                    
                    // Small delay between pinning operations
                    Thread.sleep(200);
                } else if (!needsPinning) {
                    logger.info("✅ Metafield already pinned: ebay." + key + " (position: " + currentPinnedPos + ")");
                }
            }
            
            if (pinnedCount > 0) {
                logger.info("📌 Fixed pinning for " + pinnedCount + " metafields");
            } else {
                logger.info("✅ No metafields needed pinning");
            }
            
        } catch (Exception e) {
            logger.error("❌ Error fixing metafield pinning: " + e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Validate that metafield fix was successful
     */
    private void validateMetafieldFixResults() throws Exception {
        logger.info("🔍 Validating metafield fix results...");
        
        try {
            // Re-analyze metafield state
            MetafieldValidationResult result = analyzeEbayMetafieldState();
            
            logger.info("📊 Validation Results:");
            logger.info("  - Total metafields: " + result.currentCount + "/" + result.expectedCount);
            logger.info("  - Pinned metafields: " + result.pinnedCount + "/" + result.currentCount);
            logger.info("  - Structure valid: " + result.structureValid);
            logger.info("  - All requirements met: " + 
                       (result.hasAllExpected && result.allPinned && result.structureValid));
            
            // Assertions for test validation
            if (!DRY_RUN) {
                assertTrue(result.hasAllExpected, 
                          "Not all expected eBay metafields were created");
                assertTrue(result.structureValid, 
                          "eBay metafield structure is not valid");
                assertTrue(result.allPinned, 
                          "Not all eBay metafields are pinned");
            }
            
            logger.info("✅ Metafield validation successful");
            
        } catch (Exception e) {
            logger.error("❌ Error validating metafield fix results: " + e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Result holder for metafield validation
     */
    private static class MetafieldValidationResult {
        int currentCount = 0;
        int expectedCount = EbayMetafieldDefinition.getCount(); // Use enum count
        int pinnedCount = 0;
        boolean hasAllExpected = false;
        boolean allPinned = false;
        boolean structureValid = false;
        boolean hasError = false;
    }
    
    /**
     * Result holder for batch processing
     */
    private static class BatchResult {
        int processed = 0;
        int updated = 0;
        int errors = 0;
    }
} 