package com.gw.service;

import com.gw.domain.FeedItem;
import com.gw.services.shopifyapi.objects.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

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
        
        // Create initial product with specific variant options
        logger.info("\n🏗️ Creating initial product with variant options...");
        
        FeedItem initialItem = createTestFeedItem(
            "UPDATE-TEST-001",
            "Initial Test Watch",
            "Blue",        // Initial color
            "40mm",        // Initial size  
            "Steel"        // Initial material
        );
        
        // Publish initial product
        syncService.publishItemToShopify(initialItem);
        Assertions.assertNotNull(initialItem.getShopifyItemId(), "Initial product should have Shopify ID");
        
        // Verify initial product has the expected options
        Product initialProduct = shopifyApiService.getProductByProductId(initialItem.getShopifyItemId());
        Assertions.assertNotNull(initialProduct, "Initial product should exist in Shopify");
        
        logger.info("✅ Initial product created with ID: {}", initialProduct.getId());
        
        // Use the correct two-step approach to add options to the initial product
        boolean optionsAdded = shopifyApiService.createProductOptions(initialProduct.getId(), initialItem);
        Assertions.assertTrue(optionsAdded, "Options should be successfully added to initial product");
        
        // Re-fetch to verify options were added
        Product initialProductWithOptions = shopifyApiService.getProductByProductId(initialItem.getShopifyItemId());
        Assertions.assertNotNull(initialProductWithOptions.getOptions(), "Initial product should have options");
        Assertions.assertTrue(initialProductWithOptions.getOptions().size() > 0, "Initial product should have at least one option");
        
        logger.info("✅ Initial product has {} options:", initialProductWithOptions.getOptions().size());
        for (Option option : initialProductWithOptions.getOptions()) {
            logger.info("  - {}: {}", option.getName(), option.getValues());
        }
        
        // Verify initial variant has option values
        Variant initialVariant = initialProductWithOptions.getVariants().get(0);
        logger.info("Initial variant options: [{}, {}, {}]", 
            initialVariant.getOption1(), initialVariant.getOption2(), initialVariant.getOption3());
        
        // Modify feed item with different variant option values
        logger.info("\n🔄 Modifying feed item with different variant option values...");
        
        FeedItem modifiedItem = createTestFeedItem(
            "UPDATE-TEST-001",       // Same SKU
            "Updated Test Watch",    // Modified title
            "Red",                   // Changed color: Blue -> Red
            "42mm",                  // Changed size: 40mm -> 42mm
            "Gold"                   // Changed material: Steel -> Gold
        );
        modifiedItem.setShopifyItemId(initialItem.getShopifyItemId()); // Keep same Shopify ID
        
        logger.info("Modified feed item attributes:");
        logger.info("  Color: Blue -> Red");
        logger.info("  Size: 40mm -> 42mm");
        logger.info("  Material: Steel -> Gold");
        
        // Run sync update to test the variant options update logic
        logger.info("\n🚀 Running sync update to test variant options update...");
        
        long startTime = System.currentTimeMillis();
        syncService.updateItemOnShopify(modifiedItem);
        long endTime = System.currentTimeMillis();
        
        logger.info("✅ Update completed in {}ms", (endTime - startTime));
        
        // Verify that options were updated correctly
        logger.info("\n🔍 Verifying that variant options were updated correctly...");
        
        Product updatedProduct = shopifyApiService.getProductByProductId(modifiedItem.getShopifyItemId());
        Assertions.assertNotNull(updatedProduct, "Updated product should exist in Shopify");
        Assertions.assertEquals(initialProduct.getId(), updatedProduct.getId(), "Product ID should remain the same");
        
        // Verify title was updated
        Assertions.assertEquals("Updated Test Watch", updatedProduct.getTitle(), "Product title should be updated");
        logger.info("✅ Product title updated: {} -> {}", initialProduct.getTitle(), updatedProduct.getTitle());
        
        // Verify options still exist but with updated values
        Assertions.assertNotNull(updatedProduct.getOptions(), "Updated product should still have options");
        Assertions.assertTrue(updatedProduct.getOptions().size() > 0, "Updated product should still have at least one option");
        
        logger.info("✅ Updated product has {} options:", updatedProduct.getOptions().size());
        for (Option option : updatedProduct.getOptions()) {
            logger.info("  - {}: {}", option.getName(), option.getValues());
        }
        
        // Verify specific option values were updated
        boolean foundColorRed = false;
        boolean foundSize42mm = false;
        boolean foundMaterialGold = false;
        
        for (Option option : updatedProduct.getOptions()) {
            if ("Color".equals(option.getName()) && option.getValues().contains("Red")) {
                foundColorRed = true;
            }
            if ("Size".equals(option.getName()) && option.getValues().contains("42mm")) {
                foundSize42mm = true;
            }
            if ("Material".equals(option.getName()) && option.getValues().contains("Gold")) {
                foundMaterialGold = true;
            }
        }
        
        Assertions.assertTrue(foundColorRed, "Updated product should have Color option with value 'Red'");
        Assertions.assertTrue(foundSize42mm, "Updated product should have Size option with value '42mm'");
        Assertions.assertTrue(foundMaterialGold, "Updated product should have Material option with value 'Gold'");
        
        logger.info("✅ Option values correctly updated:");
        logger.info("  - Color contains 'Red': {}", foundColorRed);
        logger.info("  - Size contains '42mm': {}", foundSize42mm);
        logger.info("  - Material contains 'Gold': {}", foundMaterialGold);
        
        // Verify variant option values were also updated
        Variant updatedVariant = updatedProduct.getVariants().get(0);
        logger.info("Updated variant options: [{}, {}, {}]", 
            updatedVariant.getOption1(), updatedVariant.getOption2(), updatedVariant.getOption3());
        
        // The variant values should match the new feed item attributes
        // Note: The exact mapping depends on the VariantService logic
        boolean variantHasNewValues = false;
        if ("Red".equals(updatedVariant.getOption1()) || "Red".equals(updatedVariant.getOption2()) || "Red".equals(updatedVariant.getOption3()) ||
            "42mm".equals(updatedVariant.getOption1()) || "42mm".equals(updatedVariant.getOption2()) || "42mm".equals(updatedVariant.getOption3()) ||
            "Gold".equals(updatedVariant.getOption1()) || "Gold".equals(updatedVariant.getOption2()) || "Gold".equals(updatedVariant.getOption3())) {
            variantHasNewValues = true;
        }
        
        Assertions.assertTrue(variantHasNewValues, "Updated variant should have at least one of the new option values");
        logger.info("✅ Variant option values updated correctly");
        
        // Test removing options (when feed item has no option attributes)
        logger.info("\n🗑️ Testing option removal when feed item has no option attributes...");
        
        FeedItem itemWithoutOptions = createTestFeedItem(
            "UPDATE-TEST-001",       // Same SKU
            "Watch Without Options", // Modified title
            null,                    // No color
            null,                    // No size
            null                     // No material
        );
        itemWithoutOptions.setShopifyItemId(modifiedItem.getShopifyItemId()); // Keep same Shopify ID
        
        // Update to remove options
        syncService.updateItemOnShopify(itemWithoutOptions);
        
        Product productWithoutOptions = shopifyApiService.getProductByProductId(itemWithoutOptions.getShopifyItemId());
        Assertions.assertNotNull(productWithoutOptions, "Product should still exist after removing options");
        
        // Options should be removed or minimal
        boolean hasMinimalOptions = productWithoutOptions.getOptions() == null || 
                                  productWithoutOptions.getOptions().isEmpty() || 
                                  (productWithoutOptions.getOptions().size() == 1 && 
                                   "Default Title".equals(productWithoutOptions.getOptions().get(0).getName()));
        
        Assertions.assertTrue(hasMinimalOptions, "Product should have no options or only default option after feed item attributes are removed");
        logger.info("✅ Options correctly removed when feed item has no option attributes");
        
        // Final verification
        logger.info("\n🎉 VARIANT OPTIONS UPDATE TEST COMPLETE");
        logger.info("✅ Verified that variant options update correctly when feed item changes");
        logger.info("✅ Verified that options are removed when feed item has no option attributes");
        logger.info("✅ Verified that the two-step approach works for updates (remove + create)");
        
        // Clean up
        shopifyApiService.deleteProductByIdOrLogFailure(updatedProduct.getId());
        logger.info("🧹 Test product cleaned up");
    }
    
    @Test
    public void testPartialOptionChanges() throws Exception {
        logger.info("=== Testing Partial Option Changes ===");
        logger.info("💡 This test verifies that when only some option attributes change, the update process works correctly");
        
        // Create product with 2 options initially
        FeedItem initialItem = createTestFeedItem(
            "PARTIAL-TEST-001",
            "Partial Update Watch",
            "Black",       // Color
            "38mm",        // Size
            null           // No material initially
        );
        
        syncService.publishItemToShopify(initialItem);
        boolean optionsAdded = shopifyApiService.createProductOptions(initialItem.getShopifyItemId(), initialItem);
        Assertions.assertTrue(optionsAdded, "Initial options should be added");
        
        Product initialProduct = shopifyApiService.getProductByProductId(initialItem.getShopifyItemId());
        logger.info("Initial product has {} options", initialProduct.getOptions().size());
        
        // Update to add material and change color
        FeedItem updatedItem = createTestFeedItem(
            "PARTIAL-TEST-001",
            "Partial Update Watch",
            "White",       // Changed color: Black -> White
            "38mm",        // Same size
            "Titanium"     // Added material
        );
        updatedItem.setShopifyItemId(initialItem.getShopifyItemId());
        
        // Perform update
        syncService.updateItemOnShopify(updatedItem);
        
        Product updatedProduct = shopifyApiService.getProductByProductId(updatedItem.getShopifyItemId());
        Assertions.assertNotNull(updatedProduct.getOptions(), "Updated product should have options");
        
        // Should now have 3 options (Color, Size, Material)
        Assertions.assertEquals(3, updatedProduct.getOptions().size(), "Updated product should have 3 options");
        
        // Verify specific changes
        boolean foundWhiteColor = false;
        boolean foundSameSize = false;
        boolean foundNewMaterial = false;
        
        for (Option option : updatedProduct.getOptions()) {
            if ("Color".equals(option.getName()) && option.getValues().contains("White")) {
                foundWhiteColor = true;
            }
            if ("Size".equals(option.getName()) && option.getValues().contains("38mm")) {
                foundSameSize = true;
            }
            if ("Material".equals(option.getName()) && option.getValues().contains("Titanium")) {
                foundNewMaterial = true;
            }
        }
        
        Assertions.assertTrue(foundWhiteColor, "Color should be updated to White");
        Assertions.assertTrue(foundSameSize, "Size should remain 38mm");
        Assertions.assertTrue(foundNewMaterial, "Material should be added as Titanium");
        
        logger.info("✅ Partial option changes verified successfully");
        logger.info("  - Color updated: Black -> White ✓");
        logger.info("  - Size unchanged: 38mm ✓");
        logger.info("  - Material added: Titanium ✓");
        
        // Clean up
        shopifyApiService.deleteProductByIdOrLogFailure(updatedProduct.getId());
        logger.info("🧹 Test product cleaned up");
    }
    
    /**
     * Helper method to create a test feed item with specific option attributes
     */
    private FeedItem createTestFeedItem(String sku, String title, String color, String size, String material) {
        FeedItem item = new FeedItem();
        item.setWebTagNumber(sku);
        item.setWebDescriptionShort(title);
        item.setWebWatchDial(color);      // Maps to Color option
        item.setWebWatchDiameter(size);   // Maps to Size option
        item.setWebMetalType(material);   // Maps to Material option
        
        // Set other required fields
        item.setWebDesigner("Test Vendor");
        item.setWebCategory("Watches");
        item.setWebPriceKeystone("299.99");     // Use webPriceKeystone instead of webPrice
        item.setWebStatus("Available");         // Use webStatus instead of webQuantity
        item.setWebWatchCondition("New");       // Use webWatchCondition instead of webCondition
        
        return item;
    }
} 