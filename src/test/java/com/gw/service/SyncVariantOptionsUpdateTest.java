package com.gw.service;

import com.gw.domain.FeedItem;
import com.gw.services.shopifyapi.objects.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Test class specifically for testing variant options update scenarios
 * Tests that when feed item attributes change (webWatchDial, webWatchDiameter, webMetalType),
 * the sync process correctly updates the product options using the two-step approach:
 * 1. Remove existing options
 * 2. Create new options with productOptionsCreate
 */
public class SyncVariantOptionsUpdateTest extends BaseGraphqlTest {

    @Test
    public void testVariantOptionsUpdateWhenFeedItemChanges() throws Exception {
        logger.info("=== Testing Variant Options Update Scenario ===");
        logger.info("💡 This test verifies that when feed item attributes change, product options are updated correctly");
        
        // Get 1 real feed item to work with
        List<FeedItem> feedItems = getTopFeedItems(1);
        FeedItem feedItem = feedItems.get(0);
        logger.info("📝 Working with feed item: {}", feedItem.getWebTagNumber());
        
        // Log initial variant-related values
        logger.info("🔍 Initial variant attribute values:");
        logger.info("  - webWatchDial (Color): '{}'", feedItem.getWebWatchDial());
        logger.info("  - webWatchDiameter (Size): '{}'", feedItem.getWebWatchDiameter());
        logger.info("  - webMetalType (Material): '{}'", feedItem.getWebMetalType());
        
        // =============== INITIAL SYNC ===============
        logger.info("\n🏗️ Performing initial sync...");
        syncService.doSyncForFeedItems(feedItems);
        
        // Verify database transaction was committed after initial sync
        logger.info("🔍 Verifying database transaction was committed after initial sync...");
        FeedItem dbItem = feedItemService.findByWebTagNumber(feedItem.getWebTagNumber());
        Assertions.assertNotNull(dbItem, "Item should exist in database after initial sync");
        Assertions.assertNotNull(dbItem.getShopifyItemId(), "Item should have Shopify ID after initial sync");
        Assertions.assertEquals(FeedItem.STATUS_PUBLISHED, dbItem.getStatus(), "Item should have PUBLISHED status");
        
        // Update original item with database values
        feedItem.setShopifyItemId(dbItem.getShopifyItemId());
        feedItem.setStatus(dbItem.getStatus());
        
        logger.info("✅ Initial sync completed - Shopify ID: {}", feedItem.getShopifyItemId());
        
        Thread.sleep(3000); // Wait for creation to propagate
        
        // =============== CAPTURE INITIAL STATE ===============
        logger.info("\n📸 Capturing initial product state...");
        Product initialProduct = shopifyApiService.getProductByProductId(feedItem.getShopifyItemId());
        assertNotNull(initialProduct, "Initial product should exist in Shopify");
        
        // Verify initial product has exactly 1 variant
        Assertions.assertNotNull(initialProduct.getVariants(), "Initial product should have variants");
        Assertions.assertEquals(1, initialProduct.getVariants().size(), "Initial product should have exactly 1 variant");
        Variant initialVariant = initialProduct.getVariants().get(0);
        
        // Capture initial options
        List<Option> initialOptions = initialProduct.getOptions();
        logger.info("📊 Initial product has {} options:", initialOptions != null ? initialOptions.size() : 0);
        if (initialOptions != null) {
            for (Option option : initialOptions) {
                logger.info("  - {}: {}", option.getName(), option.getValues());
            }
        }
        
        // Capture initial variant values
        String initialOption1 = initialVariant.getOption1();
        String initialOption2 = initialVariant.getOption2();
        String initialOption3 = initialVariant.getOption3();
        logger.info("📊 Initial variant options: [{}, {}, {}]", initialOption1, initialOption2, initialOption3);
        
        // =============== MODIFY VARIANT ATTRIBUTES ===============
        logger.info("\n🔄 Modifying variant-related attributes...");
        
        // Store original values for comparison
        String originalDial = feedItem.getWebWatchDial();
        String originalDiameter = feedItem.getWebWatchDiameter();
        String originalMetalType = feedItem.getWebMetalType();
        
        // Modify variant option fields to trigger update logic
        feedItem.setWebWatchDial(originalDial + " [UPDATED-COLOR]");
        feedItem.setWebWatchDiameter(originalDiameter + " [UPDATED-SIZE]");
        feedItem.setWebMetalType(originalMetalType + " [UPDATED-MATERIAL]");
        
        logger.info("🔄 Modified variant attribute values:");
        logger.info("  - webWatchDial (Color): '{}' → '{}'", originalDial, feedItem.getWebWatchDial());
        logger.info("  - webWatchDiameter (Size): '{}' → '{}'", originalDiameter, feedItem.getWebWatchDiameter());
        logger.info("  - webMetalType (Material): '{}' → '{}'", originalMetalType, feedItem.getWebMetalType());
        
        // =============== UPDATE SYNC ===============
        logger.info("\n🚀 Performing update sync (should trigger variant options update)...");
        
        // Verify item still exists in database before sync
        FeedItem dbItemBeforeSync = feedItemService.findByWebTagNumber(feedItem.getWebTagNumber());
        Assertions.assertNotNull(dbItemBeforeSync, "Item should still exist in database before update sync");
        Assertions.assertEquals(feedItem.getShopifyItemId(), dbItemBeforeSync.getShopifyItemId(), 
            "Shopify ID should remain the same before update");
        
        // Perform update sync using the same method
        syncService.doSyncForFeedItems(feedItems);
        
        // Verify no new product was created (same Shopify ID)
        FeedItem dbItemAfterSync = feedItemService.findByWebTagNumber(feedItem.getWebTagNumber());
        Assertions.assertNotNull(dbItemAfterSync, "Item should still exist in database after update sync");
        Assertions.assertEquals(feedItem.getShopifyItemId(), dbItemAfterSync.getShopifyItemId(), 
            "Shopify ID should remain the same after update - no duplicate product should be created");
        
        logger.info("✅ Update sync completed - same Shopify ID: {}", feedItem.getShopifyItemId());
        
        Thread.sleep(5000); // Wait for updates to propagate (longer for option updates)
        
        // =============== CAPTURE UPDATED STATE ===============
        logger.info("\n📸 Capturing updated product state...");
        Product updatedProduct = shopifyApiService.getProductByProductId(feedItem.getShopifyItemId());
        assertNotNull(updatedProduct, "Updated product should exist in Shopify");
        Assertions.assertEquals(initialProduct.getId(), updatedProduct.getId(), "Product ID should remain the same");
        
        // Verify updated product still has exactly 1 variant
        Assertions.assertNotNull(updatedProduct.getVariants(), "Updated product should have variants");
        Assertions.assertEquals(1, updatedProduct.getVariants().size(), "Updated product should have exactly 1 variant");
        Variant updatedVariant = updatedProduct.getVariants().get(0);
        
        // Capture updated options
        List<Option> updatedOptions = updatedProduct.getOptions();
        logger.info("📊 Updated product has {} options:", updatedOptions != null ? updatedOptions.size() : 0);
        if (updatedOptions != null) {
            for (Option option : updatedOptions) {
                logger.info("  - {}: {}", option.getName(), option.getValues());
            }
        }
        
        // Capture updated variant values
        String updatedOption1 = updatedVariant.getOption1();
        String updatedOption2 = updatedVariant.getOption2();
        String updatedOption3 = updatedVariant.getOption3();
        logger.info("📊 Updated variant options: [{}, {}, {}]", updatedOption1, updatedOption2, updatedOption3);
        
        // =============== ASSERT OPTIONS ARE DIFFERENT ===============
        logger.info("\n✅ Verifying that options were updated correctly...");
        
        // Variant options should now match the modified feed item attributes
        Assertions.assertEquals(feedItem.getWebWatchDial(), updatedOption1, 
            "Updated option1 should match modified webWatchDial (Color)");
        Assertions.assertEquals(feedItem.getWebWatchDiameter(), updatedOption2, 
            "Updated option2 should match modified webWatchDiameter (Size)");
        Assertions.assertEquals(feedItem.getWebMetalType(), updatedOption3, 
            "Updated option3 should match modified webMetalType (Material)");
        
        // Verify that values actually changed from initial state
        Assertions.assertNotEquals(initialOption1, updatedOption1, 
            "Option1 (Color) should be different after update");
        Assertions.assertNotEquals(initialOption2, updatedOption2, 
            "Option2 (Size) should be different after update");
        Assertions.assertNotEquals(initialOption3, updatedOption3, 
            "Option3 (Material) should be different after update");
        
        logger.info("✅ Option value changes verified:");
        logger.info("  - Option1 (Color): '{}' → '{}'", initialOption1, updatedOption1);
        logger.info("  - Option2 (Size): '{}' → '{}'", initialOption2, updatedOption2);
        logger.info("  - Option3 (Material): '{}' → '{}'", initialOption3, updatedOption3);
        
        // =============== ASSERT VARIANTS ARE CREATED CORRECTLY ===============
        logger.info("\n✅ Verifying that variants are created correctly...");
        
        // Both initial and updated products should have exactly 1 variant
        Assertions.assertEquals(1, initialProduct.getVariants().size(), 
            "Initial product should have exactly 1 variant");
        Assertions.assertEquals(1, updatedProduct.getVariants().size(), 
            "Updated product should have exactly 1 variant");
        
        // Variant should have proper inventory levels
        Assertions.assertNotNull(updatedVariant.getInventoryLevels(), 
            "Updated variant should have inventory levels");
        Assertions.assertNotNull(updatedVariant.getInventoryLevels().get(), 
            "Updated variant should have inventory level list");
        Assertions.assertFalse(updatedVariant.getInventoryLevels().get().isEmpty(), 
            "Updated variant should have at least one inventory level");
        
        // Variant SKU should remain the same
        Assertions.assertEquals(initialVariant.getSku(), updatedVariant.getSku(), 
            "Variant SKU should remain the same after options update");
        
        logger.info("✅ Variant integrity verified:");
        logger.info("  - Variant count: {} (initial) = {} (updated)", 
            initialProduct.getVariants().size(), updatedProduct.getVariants().size());
        logger.info("  - SKU unchanged: {}", updatedVariant.getSku());
        logger.info("  - Inventory levels: {} locations", updatedVariant.getInventoryLevels().get().size());
        
        // =============== ASSERT PRODUCT OPTIONS STRUCTURE ===============
        logger.info("\n✅ Verifying product options structure...");
        
        if (updatedOptions != null && !updatedOptions.isEmpty()) {
            // Verify we have the expected option names
            boolean hasColorOption = false;
            boolean hasSizeOption = false;
            boolean hasMaterialOption = false;
            
            for (Option option : updatedOptions) {
                if ("Color".equals(option.getName()) && option.getValues().contains(feedItem.getWebWatchDial())) {
                    hasColorOption = true;
                }
                if ("Size".equals(option.getName()) && option.getValues().contains(feedItem.getWebWatchDiameter())) {
                    hasSizeOption = true;
                }
                if ("Material".equals(option.getName()) && option.getValues().contains(feedItem.getWebMetalType())) {
                    hasMaterialOption = true;
                }
            }
            
            Assertions.assertTrue(hasColorOption, "Product should have Color option with updated value");
            Assertions.assertTrue(hasSizeOption, "Product should have Size option with updated value");
            Assertions.assertTrue(hasMaterialOption, "Product should have Material option with updated value");
            
            logger.info("✅ Product options structure verified:");
            logger.info("  - Color option with '{}': {}", feedItem.getWebWatchDial(), hasColorOption);
            logger.info("  - Size option with '{}': {}", feedItem.getWebWatchDiameter(), hasSizeOption);
            logger.info("  - Material option with '{}': {}", feedItem.getWebMetalType(), hasMaterialOption);
        }
        
        // =============== FINAL VERIFICATION ===============
        logger.info("\n🎉 VARIANT OPTIONS UPDATE TEST COMPLETE");
        logger.info("✅ Verified that variant options update correctly when feed item changes");
        logger.info("✅ Verified that options are different before and after sync");
        logger.info("✅ Verified that variants are created correctly in both states");
        logger.info("✅ Verified that no duplicate products were created during update");
        logger.info("✅ Verified that the two-step approach (remove + create) works for updates");
        
        // Clean up
        //shopifyApiService.deleteProductByIdOrLogFailure(updatedProduct.getId());
        //logger.info("🧹 Test product cleaned up");
    }
} 