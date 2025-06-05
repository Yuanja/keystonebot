package com.gw.service;

import com.gw.domain.FeedItem;
import com.gw.services.shopifyapi.objects.Product;
import com.gw.services.shopifyapi.objects.Variant;
import com.gw.services.shopifyapi.objects.InventoryLevel;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Test to reproduce the critical SOLD inventory inflation bug
 * 
 * Bug scenario:
 * 1. Create product with SOLD feedItem (inventory = 0)
 * 2. Publish the product successfully (inventory = 0)
 * 3. Update the same SOLD feedItem 
 * 4. BUG: Inventory inflates to 1 (should remain 0)
 * 
 * Run with: mvn test -Dtest=SoldInventoryInflationTest#testSoldInventoryInflationBug
 */
public class SoldInventoryInflationTest extends BaseGraphqlTest {
    
    private static final Logger logger = LoggerFactory.getLogger(SoldInventoryInflationTest.class);
    
    @Test
    public void testSoldInventoryInflationBug() throws Exception {
        logger.info("=== Starting SOLD Inventory Inflation Bug Test ===");
        logger.info("🐛 Testing: SOLD item (inventory=0) + Update = Should remain 0, but inflates to 1");
        
        // Get a test item and mark it as SOLD
        List<FeedItem> feedItems = getTopFeedItems(1);
        FeedItem testItem = feedItems.get(0);
        
        // CRITICAL: Mark the item as SOLD
        testItem.setWebStatus("SOLD");
        
        logger.info("📝 Test item: {} - {}", testItem.getWebTagNumber(), testItem.getWebDescriptionShort());
        logger.info("📊 Feed item status: '{}' (should create 0 inventory)", testItem.getWebStatus());
        logger.info("📊 Feed item option values:");
        logger.info("  - webWatchDial (Color): '{}'", testItem.getWebWatchDial());
        logger.info("  - webWatchDiameter (Size): '{}'", testItem.getWebWatchDiameter());  
        logger.info("  - webMetalType (Material): '{}'", testItem.getWebMetalType());
        
        // STEP 1: Create and publish SOLD product
        logger.info("🏗️ STEP 1: Creating and publishing SOLD product...");
        syncService.publishItemToShopify(testItem);
        
        // Wait for processing
        Thread.sleep(3000);
        
        // Verify initial state
        Product initialProduct = shopifyApiService.getProductByProductId(testItem.getShopifyItemId());
        int initialInventoryCount = calculateTotalInventory(initialProduct);
        
        logger.info("✅ SOLD product created:");
        logger.info("  - Product ID: {}", testItem.getShopifyItemId());
        logger.info("  - Status: SOLD");
        logger.info("  - Initial inventory count: {} (should be 0)", initialInventoryCount);
        
        if (initialInventoryCount != 0) {
            logger.error("❌ SETUP ISSUE: SOLD product should have 0 inventory, but has {}", initialInventoryCount);
        }
        
        // STEP 2: Update the same SOLD item (this is where the bug happens)
        logger.info("🔄 STEP 2: Updating the SOLD product (should remain at 0 inventory)...");
        
        // Simulate a small change to trigger update (but keep status SOLD)
        testItem.setWebDescriptionShort(testItem.getWebDescriptionShort() + " [UPDATED]");
        testItem.setWebWatchDial(testItem.getWebWatchDial() + " [UPDATED]");
        testItem.setWebWatchDiameter(testItem.getWebWatchDiameter() + " [UPDATED]"); 
        testItem.setWebMetalType(testItem.getWebMetalType() + " [UPDATED]");
        
        // CRITICAL: Status remains SOLD
        testItem.setWebStatus("SOLD");
        
        logger.info("📊 Updated feed item (still SOLD):");
        logger.info("  - webWatchDial (Color): '{}' [UPDATED]", testItem.getWebWatchDial());
        logger.info("  - webWatchDiameter (Size): '{}' [UPDATED]", testItem.getWebWatchDiameter());
        logger.info("  - webMetalType (Material): '{}' [UPDATED]", testItem.getWebMetalType());
        logger.info("  - webStatus: '{}' (STILL SOLD - should keep inventory = 0)", testItem.getWebStatus());
        
        // THIS IS WHERE THE BUG OCCURS: Update the SOLD item
        logger.info("🔄 Calling updateItemOnShopify for SOLD item (this should trigger the bug)...");
        syncService.updateItemOnShopify(testItem);
        
        // Wait for update processing
        Thread.sleep(5000);
        
        // STEP 3: Verify the bug - inventory should remain 0 but likely inflates to 1
        logger.info("🔍 STEP 3: Checking for inventory inflation bug...");
        
        Product updatedProduct = shopifyApiService.getProductByProductId(testItem.getShopifyItemId());
        int finalInventoryCount = calculateTotalInventory(updatedProduct);
        
        logger.info("📊 After update results:");
        logger.info("  - Variant count: {} (should be 1)", updatedProduct.getVariants().size());
        logger.info("  - Option count: {} (should be 3)", updatedProduct.getOptions() != null ? updatedProduct.getOptions().size() : 0);
        logger.info("  - Initial inventory: {} (was 0)", initialInventoryCount);
        logger.info("  - Final inventory: {} (should still be 0)", finalInventoryCount);
        logger.info("  - Inventory change: {}", finalInventoryCount - initialInventoryCount);
        
        // Log detailed inventory information
        if (updatedProduct.getVariants() != null && !updatedProduct.getVariants().isEmpty()) {
            Variant variant = updatedProduct.getVariants().get(0);
            logger.info("📋 Detailed variant inventory:");
            logger.info("  Variant: ID={}, SKU={}, Option1='{}', Option2='{}', Option3='{}'", 
                variant.getId(), variant.getSku(), variant.getOption1(), variant.getOption2(), variant.getOption3());
            
            if (variant.getInventoryLevels() != null && variant.getInventoryLevels().get() != null) {
                for (InventoryLevel level : variant.getInventoryLevels().get()) {
                    logger.info("    Location {}: Available = {}", level.getLocationId(), level.getAvailable());
                }
            }
        }
        
        // STEP 4: Check for the bug
        logger.info("🚨 STEP 4: Analyzing inventory inflation...");
        
        if (finalInventoryCount > initialInventoryCount) {
            logger.error("❌ BUG REPRODUCED: SOLD inventory inflated from {} to {}", 
                initialInventoryCount, finalInventoryCount);
            logger.error("❌ SOLD items should ALWAYS have 0 inventory, regardless of updates");
            logger.error("❌ This inflation bug causes incorrect inventory reporting");
            
            // Fail the test to highlight the issue
            throw new AssertionError(String.format(
                "SOLD INVENTORY INFLATION BUG: Inventory increased from %d to %d. " +
                "SOLD items must always have 0 inventory, but update process inflated it.", 
                initialInventoryCount, finalInventoryCount));
        } else if (finalInventoryCount == 0) {
            logger.info("✅ No inventory inflation detected - SOLD item correctly maintained 0 inventory");
            logger.info("✅ Update process worked correctly for SOLD items");
        } else {
            logger.warn("⚠️ Unexpected inventory state: {} (should be 0 for SOLD items)", finalInventoryCount);
        }
        
        logger.info("=== SOLD Inventory Inflation Bug Test Complete ===");
    }
    
    /**
     * Calculate total inventory across all variants and locations
     */
    private int calculateTotalInventory(Product product) {
        int total = 0;
        
        if (product.getVariants() != null) {
            for (Variant variant : product.getVariants()) {
                if (variant.getInventoryLevels() != null && variant.getInventoryLevels().get() != null) {
                    for (InventoryLevel level : variant.getInventoryLevels().get()) {
                        try {
                            total += Integer.parseInt(level.getAvailable());
                        } catch (NumberFormatException e) {
                            logger.warn("Invalid inventory quantity: {}", level.getAvailable());
                        }
                    }
                }
            }
        }
        
        return total;
    }
} 