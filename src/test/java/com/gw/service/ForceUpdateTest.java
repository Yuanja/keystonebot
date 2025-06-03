package com.gw.service;

import com.gw.domain.FeedItem;
import com.gw.domain.FeedItemChangeSet;
import com.gw.services.FeedItemService;
import com.gw.services.keystone.KeyStoneFeedService;
import com.gw.services.keystone.KeystoneShopifySyncService;
import com.gw.services.shopifyapi.ShopifyGraphQLService;
import com.gw.services.shopifyapi.objects.*;
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
import java.util.Collections;
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
    "shopify.force.update=true"
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
        
        // Step 1: Get baseline statistics
        analyzeCurrentState();
        
        // Step 2: Refresh feed from live source
        logger.info("📡 Step 2: Refreshing live feed data...");
        List<FeedItem> feedItems = keyStoneFeedService.getItemsFromFeed();
        logger.info("📊 Fresh feed items loaded: " + feedItems.size());
        
        // Step 3: Sort by web_tag_number (smallest first)
        List<FeedItem> sortedFeedItems = feedItems.stream()
            .sorted(Comparator.comparing(FeedItem::getWebTagNumber, 
                Comparator.nullsLast(Comparator.naturalOrder())))
            .collect(Collectors.toList());
        
        logger.info("🔢 Items sorted by web_tag_number (smallest first)");
        logger.info("📋 First 5 items: " + sortedFeedItems.stream()
            .limit(5)
            .map(FeedItem::getWebTagNumber)
            .collect(Collectors.toList()));
        
        // Step 4: Force update all items in batches
        forceUpdateItemsInBatches(sortedFeedItems, "ALL-ITEMS");
        
        // Step 5: Final validation
        validateForceUpdateResults();
        
        logger.info("🎉 Production force update completed successfully!");
    }
    
    /**
     * Force update a specific item by web_tag_number
     * Uses existing database data (does not refresh feed)
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
        
        if (DRY_RUN) {
            logger.warn("🧪 DRY RUN MODE - No actual changes will be made");
        }
        
        // Step 1: Find the specific item in database
        logger.info("🔍 Step 1: Finding item in database...");
        FeedItem targetItem = feedItemService.findByWebTagNumber(targetWebTagNumber);
        
        if (targetItem == null) {
            throw new IllegalArgumentException("❌ Item not found in database: " + targetWebTagNumber);
        }
        
        logger.info("✅ Found item: " + targetItem.getWebTagNumber());
        logger.info("📊 Current status: " + targetItem.getStatus());
        logger.info("🛍️ Shopify ID: " + targetItem.getShopifyItemId());
        
        // Step 2: Force update this single item
        List<FeedItem> singleItemList = Collections.singletonList(targetItem);
        forceUpdateItemsInBatches(singleItemList, "SPECIFIC-ITEM: " + targetWebTagNumber);
        
        // Step 3: Validate the specific item update
        validateSpecificItemUpdate(targetWebTagNumber);
        
        logger.info("🎉 Specific item force update completed successfully!");
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
     * Validate eBay metafield definitions and recreate them if incorrect
     * This ensures all eBay metafields are properly configured and pinned
     */
    @Test
    public void validateAndFixEbayMetafields() throws Exception {
        logger.info("=== eBay Metafield Validation and Fix ===");
        logger.info("🔍 This will validate eBay metafield definitions and fix any issues");
        
        if (DRY_RUN) {
            logger.warn("🧪 DRY RUN MODE - No actual changes will be made");
        }
        
        try {
            // Step 1: Analyze current metafield state
            MetafieldValidationResult validationResult = analyzeEbayMetafieldState();
            
            // Step 2: Determine if recreation is needed
            boolean needsRecreation = shouldRecreateMetafields(validationResult);
            
            if (!needsRecreation && validationResult.allPinned) {
                logger.info("✅ All eBay metafields are correctly configured and pinned");
                logger.info("🎉 No action needed - metafields are in perfect state!");
                return;
            }
            
            // Step 3: Recreate metafields if needed
            if (needsRecreation) {
                recreateEbayMetafields();
            } else if (!validationResult.allPinned) {
                // Only fix pinning if metafields exist but aren't pinned
                fixMetafieldPinning();
            }
            
            // Step 4: Final validation
            validateMetafieldFixResults();
            
            logger.info("🎉 eBay metafield validation and fix completed successfully!");
            
        } catch (Exception e) {
            logger.error("❌ Error during eBay metafield validation/fix: " + e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Analyze current state before force update
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
     * Process feed items in controlled batches with force update
     */
    private void forceUpdateItemsInBatches(List<FeedItem> sortedFeedItems, String operation) throws Exception {
        logger.info("🔄 Step 3: Force updating items in batches of " + BATCH_SIZE + " (" + operation + ")...");
        
        int totalBatches = (int) Math.ceil((double) sortedFeedItems.size() / BATCH_SIZE);
        int totalProcessed = 0;
        int totalUpdated = 0;
        int totalErrors = 0;
        
        for (int batchIndex = 0; batchIndex < totalBatches; batchIndex++) {
            int startIndex = batchIndex * BATCH_SIZE;
            int endIndex = Math.min(startIndex + BATCH_SIZE, sortedFeedItems.size());
            
            List<FeedItem> batch = sortedFeedItems.subList(startIndex, endIndex);
            
            logger.info("📦 Processing batch " + (batchIndex + 1) + "/" + totalBatches + 
                       " (items " + startIndex + "-" + (endIndex - 1) + ")");
            
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
        logger.info("  - Total items processed: " + totalProcessed);
        logger.info("  - Total items updated: " + totalUpdated);
        logger.info("  - Total errors: " + totalErrors);
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
            result.error = e.getMessage();
        }
        
        return result;
    }
    
    /**
     * Validate force update results
     */
    private void validateForceUpdateResults() throws Exception {
        logger.info("🔍 Step 4: Validating force update results...");
        
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
        logger.info("🔍 Step 3: Validating specific item update...");
        
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
        logger.info("🔍 Step 1: Analyzing current eBay metafield state...");
        
        MetafieldValidationResult result = new MetafieldValidationResult();
        
        try {
            // Get all eBay metafield definitions
            List<Map<String, String>> ebayMetafields = shopifyApiService.getMetafieldDefinitions("ebay");
            
            result.currentCount = ebayMetafields.size();
            result.expectedCount = 13; // Expected number of eBay metafields
            
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
            result.errorMessage = e.getMessage();
        }
        
        return result;
    }
    
    /**
     * Validate the structure and content of metafield definitions
     */
    private boolean validateMetafieldStructure(List<Map<String, String>> metafields) {
        if (metafields.size() != 13) {
            logger.warn("⚠️ Expected 13 eBay metafields, found " + metafields.size());
            return false;
        }
        
        // Expected eBay metafield keys
        String[] expectedKeys = {
            "year", "strap", "box_papers", "reference_number", "condition",
            "diameter", "model", "style", "category", "brand", "movement",
            "case_material", "dial"
        };
        
        List<String> currentKeys = metafields.stream()
            .map(m -> m.get("key"))
            .sorted()
            .collect(Collectors.toList());
        
        List<String> expectedKeysList = new ArrayList<>(List.of(expectedKeys));
        Collections.sort(expectedKeysList);
        
        boolean structureValid = currentKeys.equals(expectedKeysList);
        
        if (!structureValid) {
            logger.warn("⚠️ Metafield structure validation failed");
            logger.warn("  Expected keys: " + expectedKeysList);
            logger.warn("  Current keys: " + currentKeys);
        } else {
            logger.info("✅ Metafield structure validation passed - all 13 expected keys found");
        }
        
        return structureValid;
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
     * Recreate all eBay metafields from scratch
     */
    private void recreateEbayMetafields() throws Exception {
        logger.info("🔄 Step 2: Recreating eBay metafields...");
        
        if (DRY_RUN) {
            logger.info("🧪 DRY RUN: Would recreate eBay metafields");
            return;
        }
        
        try {
            // Step 1: Remove existing eBay metafield definitions
            logger.info("🗑️ Removing existing eBay metafield definitions...");
            List<Map<String, String>> existingMetafields = shopifyApiService.getMetafieldDefinitions("ebay");
            
            if (!existingMetafields.isEmpty()) {
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
            
            // Step 2: Create fresh eBay metafield definitions
            logger.info("🏗️ Creating fresh eBay metafield definitions...");
            shopifyApiService.createEbayMetafieldDefinitions();
            logger.info("✅ Successfully recreated eBay metafield definitions");
            
        } catch (Exception e) {
            logger.error("❌ Error recreating eBay metafields: " + e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Fix metafield pinning without recreating
     */
    private void fixMetafieldPinning() throws Exception {
        logger.info("📌 Step 2: Fixing eBay metafield pinning...");
        
        if (DRY_RUN) {
            logger.info("🧪 DRY RUN: Would fix metafield pinning");
            return;
        }
        
        try {
            // Get current metafields
            List<Map<String, String>> metafields = shopifyApiService.getMetafieldDefinitions("ebay");
            
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
                }
            }
            
            logger.info("📌 Fixed pinning for " + pinnedCount + " metafields");
            
        } catch (Exception e) {
            logger.error("❌ Error fixing metafield pinning: " + e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Validate that metafield fix was successful
     */
    private void validateMetafieldFixResults() throws Exception {
        logger.info("🔍 Step 3: Validating metafield fix results...");
        
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
        int expectedCount = 13;
        int pinnedCount = 0;
        boolean hasAllExpected = false;
        boolean allPinned = false;
        boolean structureValid = false;
        boolean hasError = false;
        String errorMessage = null;
    }
    
    /**
     * Result holder for batch processing
     */
    private static class BatchResult {
        int processed = 0;
        int updated = 0;
        int errors = 0;
        String error = null;
    }
} 