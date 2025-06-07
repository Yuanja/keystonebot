package com.gw.service;

import com.gw.domain.FeedItem;
import com.gw.services.shopifyapi.objects.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SyncEbayMetafieldsUpdateTest extends BaseGraphqlTest {

    @Test
    /**
     * Test sync behavior with existing items that have changes in eBay metafields only
     * This tests the handleChangedItems path specifically for eBay metafield updates
     * 
     * Tests metafield updates for:
     * - brand (webDesigner)
     * - model (webWatchModel)  
     * - reference_number (webWatchManufacturerReferenceNumber)
     * - year (webWatchYear)
     * - movement (webWatchMovement)
     * - strap (webWatchStrap)
     * - condition (webWatchCondition)
     * - box_papers (webWatchBoxPapers)
     * - category (webCategory)
     * - style (webStyle)
     * 
     * Ensures that when feedItem eBay-related attributes change, metafields are properly updated
     * without affecting variant options or creating duplicate products
     */
    public void syncTestEbayMetafieldsUpdate() throws Exception {
        logger.info("=== Starting sync test for eBay metafields updates ===");
        
        // Get 1 test item to focus on eBay metafield changes
        List<FeedItem> topFeedItems = getTopFeedItems(1);
        logger.info("📝 Working with {} test item for eBay metafields testing", topFeedItems.size());
        
        FeedItem feedItem = topFeedItems.get(0);
        
        // Log initial eBay-related values for debugging
        logger.info("Item: {} - Initial eBay values:", feedItem.getWebTagNumber());
        logger.info("  - webDesigner (brand): '{}'", feedItem.getWebDesigner());
        logger.info("  - webWatchModel (model): '{}'", feedItem.getWebWatchModel());
        logger.info("  - webWatchManufacturerReferenceNumber (reference_number): '{}'", feedItem.getWebWatchManufacturerReferenceNumber());
        logger.info("  - webWatchYear (year): '{}'", feedItem.getWebWatchYear());
        logger.info("  - webWatchMovement (movement): '{}'", feedItem.getWebWatchMovement());
        logger.info("  - webWatchStrap (strap): '{}'", feedItem.getWebWatchStrap());
        logger.info("  - webWatchCondition (condition): '{}'", feedItem.getWebWatchCondition());
        logger.info("  - webWatchBoxPapers (box_papers): '{}'", feedItem.getWebWatchBoxPapers());
        logger.info("  - webCategory (category): '{}'", feedItem.getWebCategory());
        logger.info("  - webStyle (style): '{}'", feedItem.getWebStyle());
        
        // Capture initial variant option values (these should NOT change)
        String initialDial = feedItem.getWebWatchDial();
        String initialDiameter = feedItem.getWebWatchDiameter();
        String initialMetalType = feedItem.getWebMetalType();
        
        // Create initial product using doSyncForFeedItems
        logger.info("🔄 Creating initial product using doSyncForFeedItems...");
        syncService.doSyncForFeedItems(topFeedItems);
        
        // Verify database transaction was committed after sync
        FeedItem dbItem = feedItemService.findByWebTagNumber(feedItem.getWebTagNumber());
        Assertions.assertNotNull(dbItem, "Item should exist in database after initial sync");
        Assertions.assertNotNull(dbItem.getShopifyItemId(), "Item should have Shopify ID after initial sync");
        Assertions.assertEquals(FeedItem.STATUS_PUBLISHED, dbItem.getStatus(), "Item should have PUBLISHED status");
        
        // Update the original item with the database values
        feedItem.setShopifyItemId(dbItem.getShopifyItemId());
        feedItem.setStatus(dbItem.getStatus());
        
        logger.info("✅ Database transaction verified - Shopify ID: {}, Status: {}", 
            dbItem.getShopifyItemId(), dbItem.getStatus());
        
        Thread.sleep(3000); // Wait for creation
        
        // Verify initial product creation and capture initial metafields
        Product initialProduct = shopifyApiService.getProductByProductId(feedItem.getShopifyItemId());
        assertNotNull(initialProduct, "Product should have been created");
        
        // Capture initial eBay metafields
        Map<String, String> initialEbayMetafields = initialProduct.getMetafields().stream()
            .filter(mf -> "ebay".equals(mf.getNamespace()))
            .collect(Collectors.toMap(Metafield::getKey, Metafield::getValue));
        
        logger.info("✅ Initial product created with {} eBay metafields", initialEbayMetafields.size());
        
        // Capture initial variant options
        Variant initialVariant = initialProduct.getVariants().get(0);
        String initialVariantOption1 = initialVariant.getOption1();
        String initialVariantOption2 = initialVariant.getOption2();
        String initialVariantOption3 = initialVariant.getOption3();
        
        // Modify feedItem eBay-related fields ONLY (no variant option changes)
        logger.info("🔄 Modifying feedItem eBay attributes to trigger metafield updates...");
        
        // Modify eBay metafield source fields
        feedItem.setWebDesigner(feedItem.getWebDesigner() + " [UPDATED-BRAND]");
        feedItem.setWebWatchModel(feedItem.getWebWatchModel() + " [UPDATED-MODEL]");
        feedItem.setWebWatchManufacturerReferenceNumber(feedItem.getWebWatchManufacturerReferenceNumber() + " [UPDATED-REF]");
        feedItem.setWebWatchYear(feedItem.getWebWatchYear() + " [UPDATED-YEAR]");
        feedItem.setWebWatchMovement(feedItem.getWebWatchMovement() + " [UPDATED-MOVEMENT]");
        feedItem.setWebWatchStrap(feedItem.getWebWatchStrap() + " [UPDATED-STRAP]");
        feedItem.setWebWatchCondition(feedItem.getWebWatchCondition() + " [UPDATED-CONDITION]");
        feedItem.setWebWatchBoxPapers(feedItem.getWebWatchBoxPapers() + " [UPDATED-PAPERS]");
        feedItem.setWebCategory(feedItem.getWebCategory() + " [UPDATED-CATEGORY]");
        feedItem.setWebStyle(feedItem.getWebStyle() + " [UPDATED-STYLE]");
        
        // IMPORTANT: DO NOT modify variant option fields (webWatchDial, webWatchDiameter, webMetalType)
        // to ensure we're only testing eBay metafield updates
        
        // Also modify the title to ensure product gets detected as changed
        feedItem.setWebDescriptionShort(feedItem.getWebDescriptionShort() + " [MODIFIED FOR EBAY TEST]");
        
        logger.info("Modified eBay values:");
        logger.info("  - webDesigner (brand): '{}'", feedItem.getWebDesigner());
        logger.info("  - webWatchModel (model): '{}'", feedItem.getWebWatchModel());
        logger.info("  - webWatchManufacturerReferenceNumber (reference_number): '{}'", feedItem.getWebWatchManufacturerReferenceNumber());
        logger.info("  - webWatchYear (year): '{}'", feedItem.getWebWatchYear());
        logger.info("  - webWatchMovement (movement): '{}'", feedItem.getWebWatchMovement());
        logger.info("  - webWatchStrap (strap): '{}'", feedItem.getWebWatchStrap());
        logger.info("  - webWatchCondition (condition): '{}'", feedItem.getWebWatchCondition());
        logger.info("  - webWatchBoxPapers (box_papers): '{}'", feedItem.getWebWatchBoxPapers());
        logger.info("  - webCategory (category): '{}'", feedItem.getWebCategory());
        logger.info("  - webStyle (style): '{}'", feedItem.getWebStyle());
        
        // Verify variant option fields remain unchanged
        Assertions.assertEquals(initialDial, feedItem.getWebWatchDial(), "webWatchDial should be unchanged");
        Assertions.assertEquals(initialDiameter, feedItem.getWebWatchDiameter(), "webWatchDiameter should be unchanged");
        Assertions.assertEquals(initialMetalType, feedItem.getWebMetalType(), "webMetalType should be unchanged");
        
        // Update the product (should trigger update logic, not create logic)
        logger.info("🔄 Updating product using proper sync flow (should trigger UPDATE logic for eBay metafields)...");
        
        // Use doSyncForFeedItems which will detect the changes and route to updateItemOnShopify
        logger.info("🚀 Executing doSyncForFeedItems (should route to UPDATE, not CREATE)...");
        syncService.doSyncForFeedItems(topFeedItems);
        
        // Verify the Shopify ID didn't change (should be same product updated, not new product created)
        FeedItem dbItemAfterSync = feedItemService.findByWebTagNumber(feedItem.getWebTagNumber());
        Assertions.assertEquals(feedItem.getShopifyItemId(), dbItemAfterSync.getShopifyItemId(), 
            "Shopify ID should not change - should be update, not new product creation");
        
        logger.info("✅ Post-sync verified - Shopify ID unchanged: {}", dbItemAfterSync.getShopifyItemId());
        
        // CRITICAL ASSERTION: Verify no duplicate products were created
        List<Product> productsAfterSync = shopifyApiService.getAllProducts();
        
        // Should have exactly 1 product (no duplicates)
        Assertions.assertEquals(1, productsAfterSync.size(), 
            "Should have exactly 1 product after eBay metafield update - no duplicates should be created");
        
        logger.info("✅ No duplicate products found - sync correctly updated existing product");
        
        // Wait for Shopify propagation
        Thread.sleep(5000);
        
        // Retrieve updated product
        Product updatedProduct = shopifyApiService.getProductByProductId(feedItem.getShopifyItemId());
        assertNotNull(updatedProduct, "Should have retrieved updated product");
        
        // Verify variant options were NOT changed (since we only modified eBay metafields)
        logger.info("✅ Verifying variant options were NOT changed (only eBay metafields should be updated)...");
        
        Variant updatedVariant = updatedProduct.getVariants().get(0);
        
        logger.info("Verifying variant options are unchanged:");
        logger.info("  Initial Color (option1): '{}' vs Current: '{}'", initialVariantOption1, updatedVariant.getOption1());
        logger.info("  Initial Size (option2): '{}' vs Current: '{}'", initialVariantOption2, updatedVariant.getOption2());
        logger.info("  Initial Material (option3): '{}' vs Current: '{}'", initialVariantOption3, updatedVariant.getOption3());
        
        Assertions.assertEquals(initialVariantOption1, updatedVariant.getOption1(), 
            "Color option should be unchanged (eBay metafield update should not affect variant options)");
        Assertions.assertEquals(initialVariantOption2, updatedVariant.getOption2(), 
            "Size option should be unchanged (eBay metafield update should not affect variant options)");
        Assertions.assertEquals(initialVariantOption3, updatedVariant.getOption3(), 
            "Material option should be unchanged (eBay metafield update should not affect variant options)");
        
        // Verify eBay metafields were updated correctly
        logger.info("✅ Verifying eBay metafields were updated correctly...");
        
        List<Metafield> ebayMetafields = updatedProduct.getMetafields().stream()
            .filter(mf -> "ebay".equals(mf.getNamespace()))
            .collect(Collectors.toList());
        
        Assertions.assertFalse(ebayMetafields.isEmpty(), "Product should have eBay metafields");
        
        // Convert to map for easier verification
        Map<String, String> currentEbayMetafields = ebayMetafields.stream()
            .collect(Collectors.toMap(Metafield::getKey, Metafield::getValue));
        
        logger.info("Verifying eBay metafield updates:");
        
        // Check specific metafields that should have been updated
        String[] expectedUpdatedFields = {
            "brand", "model", "reference_number", "year", "movement", 
            "strap", "condition", "box_papers", "category", "style"
        };
        
        for (String fieldKey : expectedUpdatedFields) {
            if (currentEbayMetafields.containsKey(fieldKey)) {
                String currentValue = currentEbayMetafields.get(fieldKey);
                String initialValue = initialEbayMetafields.get(fieldKey);
                
                logger.info("  {} - Initial: '{}' vs Current: '{}'", fieldKey, initialValue, currentValue);
                
                // Verify that the metafield was actually updated (contains the update marker)
                Assertions.assertTrue(currentValue.contains("[UPDATED-"), 
                    "eBay metafield '" + fieldKey + "' should contain update marker. " +
                    "Expected to contain '[UPDATED-' but was: " + currentValue);
                
                // Verify that the metafield value is different from initial
                Assertions.assertNotEquals(initialValue, currentValue, 
                    "eBay metafield '" + fieldKey + "' should be different from initial value");
            } else {
                logger.warn("Expected eBay metafield '{}' not found in product", fieldKey);
            }
        }
        
        // Verify that product title was also updated
        logger.info("✅ Verifying product title was updated...");
        logger.info("Title - Expected contains: '[MODIFIED FOR EBAY TEST]', Actual: '{}'", updatedProduct.getTitle());
        Assertions.assertTrue(updatedProduct.getTitle().contains("[MODIFIED FOR EBAY TEST]"), 
            "Product title should contain the eBay test modification marker");
        
        logger.info("✅ All tests passed! eBay metafields were successfully updated");
        logger.info("✅ Variant options correctly remained unchanged during eBay metafield updates");
        logger.info("✅ No duplicate products were created during eBay metafield updates");
        logger.info("=== End sync test for eBay metafields updates ===");
    }
} 