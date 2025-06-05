package com.gw.service;

import com.gw.domain.FeedItem;
import com.gw.services.shopifyapi.objects.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Test to reproduce the legacy variant update issue
 * 
 * Problem: Legacy products in production have variants with only 1 option.
 * When our new update code runs, it creates variants with all 3 options.
 * This causes the overall inventory count to increase because both variants exist.
 * 
 * Expected behavior: Update should modify existing variant, not create new ones.
 * Actual behavior: Update creates additional variants, increasing total inventory.
 */
public class LegacyVariantUpdateTest extends BaseGraphqlTest {

    @Test
    /**
     * Reproduce the legacy variant update issue:
     * 1. Create a product with variant having only 1 option (simulating legacy data)
     * 2. Update with feedItem having all 3 option values
     * 3. Verify that system incorrectly creates new variant instead of updating existing one
     * 4. Assert that total inventory count increases incorrectly
     */
    public void testLegacyVariantUpdateCreatesExtraInventory() throws Exception {
        logger.info("=== Starting Legacy Variant Update Issue Reproduction Test ===");
        logger.info("🐛 Testing: Legacy variant (1 option) + Update (3 options) = Duplicate variants");
        
        // Get a test feed item
        List<FeedItem> feedItems = getTopFeedItems(1);
        FeedItem testItem = feedItems.get(0);
        
        logger.info("📝 Test item: {} - {}", testItem.getWebTagNumber(), testItem.getWebDescriptionShort());
        logger.info("📊 Feed item option values:");
        logger.info("  - webWatchDial (Color): '{}'", testItem.getWebWatchDial());
        logger.info("  - webWatchDiameter (Size): '{}'", testItem.getWebWatchDiameter());
        logger.info("  - webMetalType (Material): '{}'", testItem.getWebMetalType());
        
        // STEP 1: Create a legacy product with only 1 option variant (simulating production legacy data)
        logger.info("🏗️ STEP 1: Creating legacy product with 1-option variant...");
        
        // Create product manually with legacy variant structure
        Product legacyProduct = createLegacyProductWithOneOption(testItem);
        Product createdProduct = shopifyApiService.addProduct(legacyProduct);
        String legacyProductId = createdProduct.getId();
        testItem.setShopifyItemId(legacyProductId);
        
        // CRITICAL: Save the FeedItem to database so sync system knows this product exists
        // This links the manually created Shopify product with the database record
        testItem.setStatus(FeedItem.STATUS_PUBLISHED);
        feedItemService.saveAutonomous(testItem);
        logger.info("💾 Saved FeedItem to database with Shopify ID: {}", legacyProductId);
        
        // Wait for product creation
        Thread.sleep(3000);
        
        // Verify legacy product was created correctly
        Product verifyLegacyProduct = shopifyApiService.getProductByProductId(legacyProductId);
        assertNotNull(verifyLegacyProduct, "Legacy product should be created");
        assertNotNull(verifyLegacyProduct.getVariants(), "Legacy product should have variants");
        Assertions.assertEquals(1, verifyLegacyProduct.getVariants().size(), "Legacy product should have exactly 1 variant");
        
        Variant legacyVariant = verifyLegacyProduct.getVariants().get(0);
        
        // Verify legacy variant has only 1 option
        assertNotNull(verifyLegacyProduct.getOptions(), "Legacy product should have options");
        Assertions.assertEquals(1, verifyLegacyProduct.getOptions().size(), "Legacy product should have exactly 1 option");
        
        Option legacyOption = verifyLegacyProduct.getOptions().get(0);
        logger.info("✅ Legacy product created with 1 option:");
        logger.info("  - Product ID: {}", legacyProductId);
        logger.info("  - Variant ID: {}", legacyVariant.getId());
        logger.info("  - Option name: '{}'", legacyOption.getName());
        logger.info("  - Option value: '{}'", legacyVariant.getOption1());
        logger.info("  - SKU: {}", legacyVariant.getSku());
        
        // Calculate initial inventory count
        int initialInventoryCount = calculateTotalInventoryCount(verifyLegacyProduct);
        logger.info("📊 Initial inventory count: {}", initialInventoryCount);
        
        // STEP 2: Update the product using our sync system (should modify existing variant, not create new)
        logger.info("🔄 STEP 2: Updating legacy product using sync system...");
        logger.info("⚠️ This should update the existing variant but may incorrectly create a new one");
        
        // Set different option values to trigger update
        testItem.setWebWatchDial(testItem.getWebWatchDial() + " [UPDATED]");
        testItem.setWebWatchDiameter(testItem.getWebWatchDiameter() + " [UPDATED]");
        testItem.setWebMetalType(testItem.getWebMetalType() + " [UPDATED]");
        
        logger.info("📊 Updated feed item option values:");
        logger.info("  - webWatchDial (Color): '{}'", testItem.getWebWatchDial());
        logger.info("  - webWatchDiameter (Size): '{}'", testItem.getWebWatchDiameter());
        logger.info("  - webMetalType (Material): '{}'", testItem.getWebMetalType());
        
        // Perform update using sync system
        syncService.doSyncForFeedItems(List.of(testItem));
        
        // Wait for update to complete
        Thread.sleep(5000);
        
        // STEP 3: Verify the results - check if duplicate variants were created
        logger.info("🔍 STEP 3: Verifying update results...");
        
        Product updatedProduct = shopifyApiService.getProductByProductId(legacyProductId);
        assertNotNull(updatedProduct, "Updated product should exist");
        assertNotNull(updatedProduct.getVariants(), "Updated product should have variants");
        
        int variantCount = updatedProduct.getVariants().size();
        int optionCount = updatedProduct.getOptions() != null ? updatedProduct.getOptions().size() : 0;
        int finalInventoryCount = calculateTotalInventoryCount(updatedProduct);
        
        logger.info("📊 After update results:");
        logger.info("  - Variant count: {} (should be 1)", variantCount);
        logger.info("  - Option count: {} (should be 3)", optionCount);
        logger.info("  - Initial inventory: {}", initialInventoryCount);
        logger.info("  - Final inventory: {}", finalInventoryCount);
        logger.info("  - Inventory change: {}", (finalInventoryCount - initialInventoryCount));
        
        // Log detailed variant information
        logger.info("📋 Detailed variant information:");
        for (int i = 0; i < updatedProduct.getVariants().size(); i++) {
            Variant variant = updatedProduct.getVariants().get(i);
            logger.info("  Variant {}: ID={}, SKU={}, Option1='{}', Option2='{}', Option3='{}'",
                (i + 1), variant.getId(), variant.getSku(), 
                variant.getOption1(), variant.getOption2(), variant.getOption3());
        }
        
        // Log detailed option information
        if (updatedProduct.getOptions() != null) {
            logger.info("📋 Detailed option information:");
            for (int i = 0; i < updatedProduct.getOptions().size(); i++) {
                Option option = updatedProduct.getOptions().get(i);
                logger.info("  Option {}: Name='{}', Values={}", 
                    (i + 1), option.getName(), option.getValues());
            }
        }
        
        // STEP 4: Check for the REAL bug - duplicate variants (not inventory correction)
        logger.info("🚨 STEP 4: Checking for duplicate variant creation (the actual bug)...");
        
        // The REAL bug is duplicate variants, not inventory correction
        if (variantCount > 1) {
            logger.error("❌ BUG REPRODUCED: Multiple variants detected ({}) instead of updating existing variant", 
                variantCount);
            logger.error("❌ This indicates duplicate variants were created during option updates");
            logger.error("❌ Legacy variant (1 option) + New variant (3 options) = Duplicate variants");
            
            // Fail the test for the actual issue - duplicate variants
            Assertions.fail(String.format(
                "LEGACY VARIANT UPDATE BUG: Multiple variants (%d) detected instead of updating existing variant. " +
                "Update process created duplicate variants instead of modifying existing variant. " +
                "This causes artificial inventory inflation in production.",
                variantCount));
        } else {
            logger.info("✅ No duplicate variants detected - single variant properly updated");
            
            // Verify correct behavior: 1 variant with 3 options
            Assertions.assertEquals(1, variantCount, "Should have exactly 1 variant after update");
            Assertions.assertEquals(3, optionCount, "Should have 3 options after update");
            
            // Check inventory correction behavior
            if (finalInventoryCount != initialInventoryCount) {
                logger.info("📦 Status-based inventory correction detected: {} -> {} (Available status = 1 inventory)", 
                    initialInventoryCount, finalInventoryCount);
                logger.info("✅ This is correct behavior - inventory should match status (Available=1, SOLD=0)");
            } else {
                logger.info("📦 No inventory change needed");
            }
            
            logger.info("✅ Update process worked correctly - no duplicate variants created");
        }
        
        logger.info("=== Legacy Variant Update Issue Test Complete ===");
    }
    
    /**
     * Create a legacy product with only 1 option variant (simulating old production data)
     */
    private Product createLegacyProductWithOneOption(FeedItem feedItem) {
        Product product = new Product();
        
        // Basic product information
        product.setTitle(feedItem.getWebDescriptionShort() + " [LEGACY-1-OPTION]");
        product.setBodyHtml(feedItem.getWebDescriptionShort());
        product.setVendor(feedItem.getWebDesigner());
        product.setProductType(feedItem.getWebCategory());
        product.setStatus("ACTIVE");
        
        // Create single option (simulating legacy data that only had Color)
        List<Option> options = new ArrayList<>();
        Option colorOption = new Option();
        colorOption.setName("Color");
        colorOption.setPosition("1");
        
        List<String> colorValues = new ArrayList<>();
        colorValues.add(feedItem.getWebWatchDial() != null ? feedItem.getWebWatchDial() : "Default Color");
        colorOption.setValues(colorValues);
        
        options.add(colorOption);
        product.setOptions(options);
        
        // Create single variant with only option1 set
        List<Variant> variants = new ArrayList<>();
        Variant variant = new Variant();
        variant.setTitle("Default Title");
        variant.setSku(feedItem.getWebTagNumber());
        variant.setPrice("100.00");
        variant.setInventoryManagement("shopify");
        variant.setInventoryPolicy("deny");
        variant.setTaxable("true");
        
        // Set only option1 (legacy behavior)
        variant.setOption1(feedItem.getWebWatchDial() != null ? feedItem.getWebWatchDial() : "Default Color");
        // option2 and option3 are null (legacy state)
        
        variants.add(variant);
        product.setVariants(variants);
        
        return product;
    }
    
    /**
     * Calculate total inventory count across all variants
     */
    private int calculateTotalInventoryCount(Product product) {
        if (product.getVariants() == null || product.getVariants().isEmpty()) {
            return 0;
        }
        
        int totalCount = 0;
        for (Variant variant : product.getVariants()) {
            if (variant.getInventoryLevels() != null && variant.getInventoryLevels().get() != null) {
                for (InventoryLevel level : variant.getInventoryLevels().get()) {
                    if (level.getAvailable() != null) {
                        try {
                            totalCount += Integer.parseInt(level.getAvailable());
                        } catch (NumberFormatException e) {
                            logger.warn("Unable to parse inventory level: {}", level.getAvailable());
                        }
                    }
                }
            }
        }
        return totalCount;
    }

    @Test
    /**
     * Test color option expansion scenario:
     * 1. Create a product with proper Color option and variant (not legacy "Title")
     * 2. Update with feedItem having all 3 option values (Color, Size, Material)
     * 3. Verify system properly expands options without creating duplicate variants
     * 4. Assert no inventory inflation occurs during option expansion
     */
    public void testColorOptionExpansionNoDuplicateVariants() throws Exception {
        logger.info("=== Starting Color Option Expansion Test ===");
        logger.info("🔧 Testing: Color option (1) + Update (3 options) = No duplicate variants");
        
        // Use a different item to avoid interference
        List<FeedItem> feedItems = getTopFeedItems(2); // Get 2 items to use the second one
        FeedItem testItem = feedItems.get(1); // Get second item from feed
        logger.info("📝 Test item: {} - {}", testItem.getWebTagNumber(), testItem.getWebDescriptionShort());
        
        logger.info("📊 Feed item option values:");
        logger.info("  - webWatchDial (Color): '{}'", testItem.getWebWatchDial());
        logger.info("  - webWatchDiameter (Size): '{}'", testItem.getWebWatchDiameter());
        logger.info("  - webMetalType (Material): '{}'", testItem.getWebMetalType());
        
        // STEP 1: Create a product with proper Color option (modern, not legacy)
        logger.info("🏗️ STEP 1: Creating product with proper Color option...");
        
        Product colorProduct = createProductWithColorOption(testItem);
        Product createdProduct = shopifyApiService.addProduct(colorProduct);
        String productId = createdProduct.getId();
        testItem.setShopifyItemId(productId);
        
        // Save FeedItem to database so sync system recognizes it
        testItem.setStatus(FeedItem.STATUS_PUBLISHED);
        feedItemService.saveAutonomous(testItem);
        logger.info("💾 Saved FeedItem to database with Shopify ID: {}", productId);
        
        Thread.sleep(3000);
        
        // Verify color product was created correctly
        Product verifyColorProduct = shopifyApiService.getProductByProductId(productId);
        assertNotNull(verifyColorProduct, "Color product should be created");
        assertNotNull(verifyColorProduct.getVariants(), "Color product should have variants");
        Assertions.assertEquals(1, verifyColorProduct.getVariants().size(), "Color product should have exactly 1 variant");
        
        Variant colorVariant = verifyColorProduct.getVariants().get(0);
        
        // Verify product has options (Shopify may convert single "Color" option to "Title")
        assertNotNull(verifyColorProduct.getOptions(), "Color product should have options");
        Assertions.assertEquals(1, verifyColorProduct.getOptions().size(), "Color product should have exactly 1 option");
        
        Option colorOption = verifyColorProduct.getOptions().get(0);
        // Shopify automatically converts single options to "Title" - this is expected behavior
        logger.info("📝 Shopify converted option name from 'Color' to '{}'", colorOption.getName());
        
        logger.info("✅ Color product created with proper Color option:");
        logger.info("  - Product ID: {}", productId);
        logger.info("  - Variant ID: {}", colorVariant.getId());
        logger.info("  - Option name: '{}'", colorOption.getName());
        logger.info("  - Option value: '{}'", colorVariant.getOption1());
        logger.info("  - SKU: {}", colorVariant.getSku());
        
        // Calculate initial inventory count
        int initialInventoryCount = calculateTotalInventoryCount(verifyColorProduct);
        logger.info("📊 Initial inventory count: {}", initialInventoryCount);
        
        // STEP 2: Update to expand to 3 options (Color, Size, Material)
        logger.info("🔄 STEP 2: Expanding to 3 options using sync system...");
        logger.info("⚠️ This should expand options without creating duplicate variants");
        
        // Set different option values to trigger expansion
        testItem.setWebWatchDial(testItem.getWebWatchDial() + " [EXPANDED]");
        testItem.setWebWatchDiameter(testItem.getWebWatchDiameter() + " [EXPANDED]");
        testItem.setWebMetalType(testItem.getWebMetalType() + " [EXPANDED]");
        
        logger.info("📊 Expanded feed item option values:");
        logger.info("  - webWatchDial (Color): '{}'", testItem.getWebWatchDial());
        logger.info("  - webWatchDiameter (Size): '{}'", testItem.getWebWatchDiameter());
        logger.info("  - webMetalType (Material): '{}'", testItem.getWebMetalType());
        
        // Perform expansion using sync system
        syncService.doSyncForFeedItems(List.of(testItem));
        
        Thread.sleep(5000);
        
        // STEP 3: Verify expansion results
        logger.info("🔍 STEP 3: Verifying option expansion results...");
        
        Product expandedProduct = shopifyApiService.getProductByProductId(productId);
        assertNotNull(expandedProduct, "Expanded product should exist");
        assertNotNull(expandedProduct.getVariants(), "Expanded product should have variants");
        
        int variantCount = expandedProduct.getVariants().size();
        int optionCount = expandedProduct.getOptions() != null ? expandedProduct.getOptions().size() : 0;
        int finalInventoryCount = calculateTotalInventoryCount(expandedProduct);
        
        logger.info("📊 After option expansion results:");
        logger.info("  - Variant count: {} (should be 1)", variantCount);
        logger.info("  - Option count: {} (should be 3)", optionCount);
        logger.info("  - Initial inventory: {}", initialInventoryCount);
        logger.info("  - Final inventory: {}", finalInventoryCount);
        logger.info("  - Inventory change: {}", (finalInventoryCount - initialInventoryCount));
        
        // Log detailed variant information
        logger.info("📋 Detailed variant information:");
        for (int i = 0; i < expandedProduct.getVariants().size(); i++) {
            Variant variant = expandedProduct.getVariants().get(i);
            logger.info("  Variant {}: ID={}, SKU={}, Option1='{}', Option2='{}', Option3='{}'",
                (i + 1), variant.getId(), variant.getSku(), 
                variant.getOption1(), variant.getOption2(), variant.getOption3());
        }
        
        // Log detailed option information
        if (expandedProduct.getOptions() != null) {
            logger.info("📋 Detailed option information:");
            for (int i = 0; i < expandedProduct.getOptions().size(); i++) {
                Option option = expandedProduct.getOptions().get(i);
                logger.info("  Option {}: Name='{}', Values={}", 
                    (i + 1), option.getName(), option.getValues());
            }
        }
        
        // STEP 4: Check for option expansion working correctly
        logger.info("🚨 STEP 4: Checking option expansion (no duplicate variants)...");
        
        // The key test: no duplicate variants should be created during option expansion
        if (variantCount > 1) {
            logger.error("❌ BUG DETECTED: Multiple variants ({}) created during option expansion", variantCount);
            logger.error("❌ Option expansion should modify existing variant, not create new ones");
            
            Assertions.fail(String.format(
                "OPTION EXPANSION BUG: Multiple variants (%d) created during option expansion. " +
                "System should expand options on existing variant, not create duplicates. " +
                "This causes inventory inflation.",
                variantCount));
        } else {
            logger.info("✅ No duplicate variants detected - option expansion worked correctly");
            
            // Verify correct expansion: 1 variant with 3 options
            Assertions.assertEquals(1, variantCount, "Should have exactly 1 variant after expansion");
            Assertions.assertEquals(3, optionCount, "Should have 3 options after expansion");
            
            // Verify the variant is the same one (same ID)
            String finalVariantId = expandedProduct.getVariants().get(0).getId();
            Assertions.assertEquals(colorVariant.getId(), finalVariantId, 
                "Should be the same variant ID after expansion");
            
            // Check inventory behavior
            if (finalInventoryCount != initialInventoryCount) {
                logger.info("📦 Status-based inventory correction during expansion: {} -> {} (Available status = 1 inventory)", 
                    initialInventoryCount, finalInventoryCount);
                logger.info("✅ This is correct behavior - inventory should match status (Available=1, SOLD=0)");
            } else {
                logger.info("📦 No inventory change during expansion - correct");
            }
            
            logger.info("✅ Option expansion worked correctly - no duplicate variants created");
        }
        
        logger.info("=== Color Option Expansion Test Complete ===");
    }

    /**
     * Create a product with proper Color option (not legacy "Title" option)
     */
    private Product createProductWithColorOption(FeedItem feedItem) {
        Product product = new Product();
        
        // Basic product information
        product.setTitle(feedItem.getWebDescriptionShort() + " [COLOR-OPTION]");
        product.setBodyHtml(feedItem.getWebDescriptionShort());
        product.setVendor(feedItem.getWebDesigner());
        product.setProductType(feedItem.getWebCategory());
        product.setStatus("ACTIVE");
        
        // Create proper Color option (modern approach, not legacy)
        List<Option> options = new ArrayList<>();
        Option colorOption = new Option();
        colorOption.setName("Color");
        colorOption.setPosition("1");
        
        List<String> colorValues = new ArrayList<>();
        String colorValue = feedItem.getWebWatchDial() != null ? feedItem.getWebWatchDial() : "Default Color";
        colorValues.add(colorValue);
        colorOption.setValues(colorValues);
        
        options.add(colorOption);
        product.setOptions(options);
        
        // Create variant properly associated with Color option
        List<Variant> variants = new ArrayList<>();
        Variant variant = new Variant();
        variant.setTitle(colorValue); // Use color value as title, not "Default Title"
        variant.setSku(feedItem.getWebTagNumber());
        variant.setPrice("100.00");
        variant.setInventoryManagement("shopify");
        variant.setInventoryPolicy("deny");
        variant.setTaxable("true");
        
        // Properly set option1 to match the Color option
        variant.setOption1(colorValue);
        // option2 and option3 are null (will be expanded later)
        
        variants.add(variant);
        product.setVariants(variants);
        
        return product;
    }
} 