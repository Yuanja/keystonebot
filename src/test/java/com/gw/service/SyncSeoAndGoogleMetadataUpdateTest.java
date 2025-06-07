package com.gw.service;

import com.gw.domain.FeedItem;
import com.gw.services.shopifyapi.objects.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Test class specifically for testing SEO metadata and Google metadata update scenarios
 * Tests that when feed item attributes change (webDesigner, webWatchModel, webMetalType, webStyle, webWatchCondition),
 * the sync process correctly updates:
 * 1. SEO metadata (metafieldsGlobalTitleTag, metafieldsGlobalDescriptionTag)
 * 2. Google metadata (Google Shopping metafields like gender, condition, adwords_grouping, etc.)
 */
public class SyncSeoAndGoogleMetadataUpdateTest extends BaseGraphqlTest {

    @Test
    /**
     * Test that SEO and Google metadata are created initially and updated correctly when feed item changes
     * This tests a critical bug where ProductUpdatePipeline.java doesn't update SEO and Google metadata
     * 
     * Test flow:
     * 1. Sync initial feed item (should create SEO and Google metadata)
     * 2. Modify feed item fields that drive SEO and Google metadata changes
     * 3. Sync updated feed item (should update SEO and Google metadata)
     * 4. Assert that both initial creation and updates work correctly
     */
    public void testSeoAndGoogleMetadataUpdates() throws Exception {
        logger.info("=== Starting SEO and Google Metadata Update Test ===");
        logger.info("💡 This test verifies SEO metadata and Google metafields are created and updated correctly");
        
        // Get 1 test item for focused testing
        List<FeedItem> topFeedItems = getTopFeedItems(1);
        FeedItem testItem = topFeedItems.get(0);
        logger.info("📝 Working with test item: {}", testItem.getWebTagNumber());
        
        // Log initial values for metadata-driving fields
        logger.info("=== Initial Metadata-Driving Field Values ===");
        logger.info("  - webDesigner (SEO Title + Google adwords_grouping): '{}'", testItem.getWebDesigner());
        logger.info("  - webWatchModel (SEO Title + Google adwords_labels): '{}'", testItem.getWebWatchModel());
        logger.info("  - webWatchManufacturerReferenceNumber (SEO Title): '{}'", testItem.getWebWatchManufacturerReferenceNumber());
        logger.info("  - webMetalType (SEO Title + Description): '{}'", testItem.getWebMetalType());
        logger.info("  - webStyle (SEO Description + Google gender): '{}'", testItem.getWebStyle());
        logger.info("  - webWatchCondition (SEO Description + Google condition): '{}'", testItem.getWebWatchCondition());
        logger.info("  - webWatchDial (SEO Description): '{}'", testItem.getWebWatchDial());
        logger.info("  - webWatchDiameter (SEO Description): '{}'", testItem.getWebWatchDiameter());
        logger.info("  - webWatchMovement (SEO Description): '{}'", testItem.getWebWatchMovement());
        logger.info("  - webWatchYear (SEO Description): '{}'", testItem.getWebWatchYear());
        
        // =================== STEP 1: INITIAL SYNC ===================
        logger.info("🔄 Step 1: Creating initial product with SEO and Google metadata...");
        syncService.doSyncForFeedItems(topFeedItems);
        
        // Verify database transactions were committed
        logger.info("🔍 Verifying database transaction was committed after initial sync...");
        FeedItem dbItem = feedItemService.findByWebTagNumber(testItem.getWebTagNumber());
        Assertions.assertNotNull(dbItem, "Item should exist in database after initial sync");
        Assertions.assertNotNull(dbItem.getShopifyItemId(), "Item should have Shopify ID after initial sync");
        Assertions.assertEquals(FeedItem.STATUS_PUBLISHED, dbItem.getStatus(), "Item should have PUBLISHED status");
        
        // Update test item with database values
        testItem.setShopifyItemId(dbItem.getShopifyItemId());
        testItem.setStatus(dbItem.getStatus());
        
        Thread.sleep(3000); // Wait for creation
        
        // Retrieve initial product and verify metadata
        Product initialProduct = shopifyApiService.getProductByProductId(testItem.getShopifyItemId());
        assertNotNull(initialProduct, "Initial product should have been created");
        
        logger.info("✅ Initial product created with ID: {}", initialProduct.getId());
        
        // =================== ASSERT: INITIAL SEO METADATA ===================
        logger.info("📋 Step 1a: Verifying initial SEO metadata creation...");
        
        String initialSeoTitle = initialProduct.getMetafieldsGlobalTitleTag();
        String initialSeoDescription = initialProduct.getMetafieldsGlobalDescriptionTag();
        
        assertNotNull(initialSeoTitle, "Initial product should have SEO title (metafieldsGlobalTitleTag)");
        assertNotNull(initialSeoDescription, "Initial product should have SEO description (metafieldsGlobalDescriptionTag)");
        
        logger.info("✅ Initial SEO Title: '{}'", initialSeoTitle);
        logger.info("✅ Initial SEO Description: '{}'", initialSeoDescription);
        
        // Verify SEO title contains expected fields
        if (testItem.getWebDesigner() != null) {
            Assertions.assertTrue(initialSeoTitle.contains(testItem.getWebDesigner()), 
                "Initial SEO title should contain webDesigner: " + testItem.getWebDesigner());
        }
        if (testItem.getWebWatchModel() != null) {
            Assertions.assertTrue(initialSeoTitle.contains(testItem.getWebWatchModel()), 
                "Initial SEO title should contain webWatchModel: " + testItem.getWebWatchModel());
        }
        if (testItem.getWebMetalType() != null) {
            Assertions.assertTrue(initialSeoTitle.contains(testItem.getWebMetalType()), 
                "Initial SEO title should contain webMetalType: " + testItem.getWebMetalType());
        }
        
        // Verify SEO description contains expected fields
        if (testItem.getWebWatchCondition() != null) {
            Assertions.assertTrue(initialSeoDescription.contains(testItem.getWebWatchCondition()), 
                "Initial SEO description should contain webWatchCondition: " + testItem.getWebWatchCondition());
        }
        if (testItem.getWebStyle() != null) {
            Assertions.assertTrue(initialSeoDescription.contains(testItem.getWebStyle()), 
                "Initial SEO description should contain webStyle: " + testItem.getWebStyle());
        }
        
        // =================== ASSERT: INITIAL GOOGLE METADATA ===================
        logger.info("📋 Step 1b: Verifying initial Google metadata creation...");
        
        List<Metafield> initialGoogleMetafields = initialProduct.getMetafields().stream()
            .filter(mf -> "google".equals(mf.getNamespace()))
            .collect(Collectors.toList());
        
        Assertions.assertFalse(initialGoogleMetafields.isEmpty(), "Initial product should have Google metafields");
        logger.info("✅ Found {} initial Google metafields", initialGoogleMetafields.size());
        
        // Log all initial Google metafields
        for (Metafield metafield : initialGoogleMetafields) {
            logger.info("  - Google {}: '{}'", metafield.getKey(), metafield.getValue());
        }
        
        // Verify specific Google metafields exist
        boolean hasGenderMetafield = initialGoogleMetafields.stream()
            .anyMatch(mf -> "gender".equals(mf.getKey()));
        boolean hasConditionMetafield = initialGoogleMetafields.stream()
            .anyMatch(mf -> "condition".equals(mf.getKey()));
        boolean hasAdwordsGroupingMetafield = initialGoogleMetafields.stream()
            .anyMatch(mf -> "adwords_grouping".equals(mf.getKey()));
        
        Assertions.assertTrue(hasGenderMetafield, "Initial product should have Google gender metafield");
        Assertions.assertTrue(hasConditionMetafield, "Initial product should have Google condition metafield");
        if (testItem.getWebDesigner() != null) {
            Assertions.assertTrue(hasAdwordsGroupingMetafield, "Initial product should have Google adwords_grouping metafield when webDesigner exists");
        }
        
        // Store initial values for comparison
        String initialGenderValue = getMetafieldValue(initialGoogleMetafields, "gender");
        String initialConditionValue = getMetafieldValue(initialGoogleMetafields, "condition");
        String initialAdwordsGroupingValue = getMetafieldValue(initialGoogleMetafields, "adwords_grouping");
        
        logger.info("✅ Initial Google gender: '{}'", initialGenderValue);
        logger.info("✅ Initial Google condition: '{}'", initialConditionValue);
        logger.info("✅ Initial Google adwords_grouping: '{}'", initialAdwordsGroupingValue);
        
        // =================== STEP 2: MODIFY METADATA-DRIVING FIELDS ===================
        logger.info("🔄 Step 2: Modifying metadata-driving fields...");
        
        // Modify fields that drive SEO metadata changes
        String originalDesigner = testItem.getWebDesigner();
        String originalModel = testItem.getWebWatchModel();
        String originalMaterialType = testItem.getWebMetalType();
        String originalStyle = testItem.getWebStyle();
        String originalCondition = testItem.getWebWatchCondition();
        String originalDial = testItem.getWebWatchDial();
        
        testItem.setWebDesigner(originalDesigner + " [UPDATED-DESIGNER]");
        testItem.setWebWatchModel(originalModel + " [UPDATED-MODEL]");
        testItem.setWebMetalType(originalMaterialType + " [UPDATED-MATERIAL]");
        testItem.setWebStyle("Men's"); // Change gender: Unisex -> Men's to trigger Google gender metafield change
        testItem.setWebWatchCondition("New"); // Change to trigger Google condition metafield change
        testItem.setWebWatchDial(originalDial + " [UPDATED-DIAL]");
        
        logger.info("=== Modified Metadata-Driving Field Values ===");
        logger.info("  - webDesigner: '{}' → '{}'", originalDesigner, testItem.getWebDesigner());
        logger.info("  - webWatchModel: '{}' → '{}'", originalModel, testItem.getWebWatchModel());
        logger.info("  - webMetalType: '{}' → '{}'", originalMaterialType, testItem.getWebMetalType());
        logger.info("  - webStyle: '{}' → '{}'", originalStyle, testItem.getWebStyle());
        logger.info("  - webWatchCondition: '{}' → '{}'", originalCondition, testItem.getWebWatchCondition());
        logger.info("  - webWatchDial: '{}' → '{}'", originalDial, testItem.getWebWatchDial());
        
        // =================== STEP 3: UPDATE SYNC ===================
        logger.info("🔄 Step 3: Updating product with modified metadata fields...");
        
        // Verify item still exists in database before sync
        FeedItem dbItemBeforeSync = feedItemService.findByWebTagNumber(testItem.getWebTagNumber());
        Assertions.assertNotNull(dbItemBeforeSync, "Item should still exist in database before update sync");
        Assertions.assertNotNull(dbItemBeforeSync.getShopifyItemId(), "Item should still have Shopify ID before update sync");
        
        // Execute update sync
        List<FeedItem> modifiedItems = List.of(testItem);
        syncService.doSyncForFeedItems(modifiedItems);
        
        // Verify item still exists in database after sync and Shopify ID didn't change
        FeedItem dbItemAfterSync = feedItemService.findByWebTagNumber(testItem.getWebTagNumber());
        Assertions.assertNotNull(dbItemAfterSync, "Item should still exist in database after update sync");
        Assertions.assertEquals(testItem.getShopifyItemId(), dbItemAfterSync.getShopifyItemId(), 
            "Shopify ID should not change during update - no duplicate products should be created");
        
        Thread.sleep(5000); // Wait for update propagation
        
        // =================== STEP 4: VERIFY UPDATED METADATA ===================
        logger.info("🔄 Step 4: Verifying SEO and Google metadata were updated...");
        
        Product updatedProduct = shopifyApiService.getProductByProductId(testItem.getShopifyItemId());
        assertNotNull(updatedProduct, "Updated product should still exist");
        
        // =================== ASSERT: UPDATED SEO METADATA ===================
        logger.info("📋 Step 4a: Verifying updated SEO metadata...");
        
        String updatedSeoTitle = updatedProduct.getMetafieldsGlobalTitleTag();
        String updatedSeoDescription = updatedProduct.getMetafieldsGlobalDescriptionTag();
        
        assertNotNull(updatedSeoTitle, "Updated product should have SEO title");
        assertNotNull(updatedSeoDescription, "Updated product should have SEO description");
        
        logger.info("📊 SEO Title Comparison:");
        logger.info("  Initial:  '{}'", initialSeoTitle);
        logger.info("  Updated:  '{}'", updatedSeoTitle);
        
        logger.info("📊 SEO Description Comparison:");
        logger.info("  Initial:  '{}'", initialSeoDescription);
        logger.info("  Updated:  '{}'", updatedSeoDescription);
        
        // CRITICAL: Assert SEO metadata changed
        Assertions.assertNotEquals(initialSeoTitle, updatedSeoTitle, 
            "❌ BUG DETECTED: SEO title should have changed after field updates. This indicates ProductUpdatePipeline.java is NOT updating SEO metadata!");
        
        Assertions.assertNotEquals(initialSeoDescription, updatedSeoDescription, 
            "❌ BUG DETECTED: SEO description should have changed after field updates. This indicates ProductUpdatePipeline.java is NOT updating SEO metadata!");
        
        // Verify updated SEO title contains new values
        Assertions.assertTrue(updatedSeoTitle.contains("[UPDATED-DESIGNER]"), 
            "Updated SEO title should contain the modified webDesigner");
        Assertions.assertTrue(updatedSeoTitle.contains("[UPDATED-MODEL]"), 
            "Updated SEO title should contain the modified webWatchModel");
        Assertions.assertTrue(updatedSeoTitle.contains("[UPDATED-MATERIAL]"), 
            "Updated SEO title should contain the modified webMetalType");
        
        // Verify updated SEO description contains new values
        Assertions.assertTrue(updatedSeoDescription.contains("Men's"), 
            "Updated SEO description should contain the modified webStyle");
        Assertions.assertTrue(updatedSeoDescription.contains("New"), 
            "Updated SEO description should contain the modified webWatchCondition");
        Assertions.assertTrue(updatedSeoDescription.contains("[UPDATED-DIAL]"), 
            "Updated SEO description should contain the modified webWatchDial");
        
        // =================== ASSERT: UPDATED GOOGLE METADATA ===================
        logger.info("📋 Step 4b: Verifying updated Google metadata...");
        
        List<Metafield> updatedGoogleMetafields = updatedProduct.getMetafields().stream()
            .filter(mf -> "google".equals(mf.getNamespace()))
            .collect(Collectors.toList());
        
        Assertions.assertFalse(updatedGoogleMetafields.isEmpty(), "Updated product should have Google metafields");
        logger.info("✅ Found {} updated Google metafields", updatedGoogleMetafields.size());
        
        String updatedGenderValue = getMetafieldValue(updatedGoogleMetafields, "gender");
        String updatedConditionValue = getMetafieldValue(updatedGoogleMetafields, "condition");
        String updatedAdwordsGroupingValue = getMetafieldValue(updatedGoogleMetafields, "adwords_grouping");
        
        logger.info("📊 Google Metadata Comparison:");
        logger.info("  Gender -     Initial: '{}', Updated: '{}'", initialGenderValue, updatedGenderValue);
        logger.info("  Condition -  Initial: '{}', Updated: '{}'", initialConditionValue, updatedConditionValue);
        logger.info("  Adwords -    Initial: '{}', Updated: '{}'", initialAdwordsGroupingValue, updatedAdwordsGroupingValue);
        
        // CRITICAL: Assert Google metadata changed
        Assertions.assertNotEquals(initialGenderValue, updatedGenderValue, 
            "❌ BUG DETECTED: Google gender metafield should have changed from '" + initialGenderValue + 
            "' to 'Male' after webStyle change to 'Men\\'s'. This indicates ProductUpdatePipeline.java is NOT updating Google metafields!");
        
        Assertions.assertNotEquals(initialConditionValue, updatedConditionValue, 
            "❌ BUG DETECTED: Google condition metafield should have changed from '" + initialConditionValue + 
            "' to 'new' after webWatchCondition change. This indicates ProductUpdatePipeline.java is NOT updating Google metafields!");
        
        Assertions.assertNotEquals(initialAdwordsGroupingValue, updatedAdwordsGroupingValue, 
            "❌ BUG DETECTED: Google adwords_grouping metafield should have changed after webDesigner change. This indicates ProductUpdatePipeline.java is NOT updating Google metafields!");
        
        // Verify specific expected values
        Assertions.assertEquals("Male", updatedGenderValue, 
            "Updated Google gender should be 'Male' after webStyle change to 'Men\\'s'");
        Assertions.assertEquals("New", updatedConditionValue, 
            "Updated Google condition should be 'New' after webWatchCondition change to 'New'");
        Assertions.assertTrue(updatedAdwordsGroupingValue.contains("[UPDATED-DESIGNER]"), 
            "Updated Google adwords_grouping should contain the modified webDesigner");
        
        logger.info("✅ PASS: SEO metadata was updated correctly");
        logger.info("✅ PASS: Google metafields were updated correctly");
        logger.info("🎉 All SEO and Google metadata update tests passed!");
        logger.info("=== End SEO and Google Metadata Update Test ===");
    }
    
    /**
     * Helper method to get metafield value by key
     */
    private String getMetafieldValue(List<Metafield> metafields, String key) {
        return metafields.stream()
            .filter(mf -> key.equals(mf.getKey()))
            .map(Metafield::getValue)
            .findFirst()
            .orElse(null);
    }
} 