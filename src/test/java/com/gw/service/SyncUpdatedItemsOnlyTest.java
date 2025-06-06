package com.gw.service;

import com.gw.domain.FeedItem;
import com.gw.services.shopifyapi.objects.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SyncUpdatedItemsOnlyTest extends BaseGraphqlTest {

    /**
     * Helper method to validate inventory levels match web_status and are never > 1
     * 
     * @param products List of Shopify products to validate
     * @param feedItems List of feed items with web_status information
     * @return InventoryValidationResult with statistics and violations
     */
    private InventoryValidationResult validateInventoryLevels(List<Product> products, List<FeedItem> feedItems) {
        logger.info("📦 Validating inventory levels match web_status and are never > 1...");
        logger.info("💡 Business rules: SOLD items → inventory = 0, all others → inventory = 1, never > 1");
        
        int productsChecked = 0;
        int soldItemsWithZeroInventory = 0;
        int availableItemsWithOneInventory = 0;
        int violationsFound = 0;
        List<String> inventoryViolations = new ArrayList<>();
        boolean loggedInventorySample = false;
        
        for (Product product : products) {
            // Get the corresponding feed item to check web_status
            FeedItem matchingFeedItem = null;
            String productSku = null;
            
            if (product.getVariants() != null && !product.getVariants().isEmpty()) {
                productSku = product.getVariants().get(0).getSku();
                
                // Find matching feed item by SKU
                for (FeedItem feedItem : feedItems) {
                    if (feedItem.getWebTagNumber().equals(productSku)) {
                        matchingFeedItem = feedItem;
                        break;
                    }
                }
            }
            
            if (matchingFeedItem != null && product.getVariants() != null && !product.getVariants().isEmpty()) {
                productsChecked++;
                
                Variant variant = product.getVariants().get(0);
                String webStatus = matchingFeedItem.getWebStatus();
                
                // Calculate total inventory across all locations
                int totalInventory = 0;
                if (variant.getInventoryLevels() != null && variant.getInventoryLevels().get() != null) {
                    for (InventoryLevel level : variant.getInventoryLevels().get()) {
                        try {
                            totalInventory += Integer.parseInt(level.getAvailable());
                        } catch (NumberFormatException e) {
                            logger.warn("Invalid inventory quantity for SKU {}: {}", productSku, level.getAvailable());
                        }
                    }
                }
                
                // Log sample for debugging
                if (!loggedInventorySample) {
                    logger.info("📊 Sample Inventory Validation (SKU: {}):", productSku);
                    logger.info("  Feed item web_status: '{}'", webStatus);
                    logger.info("  Total inventory: {}", totalInventory);
                    if (variant.getInventoryLevels() != null && variant.getInventoryLevels().get() != null) {
                        logger.info("  Inventory breakdown across {} locations:", variant.getInventoryLevels().get().size());
                        for (int i = 0; i < variant.getInventoryLevels().get().size(); i++) {
                            InventoryLevel level = variant.getInventoryLevels().get().get(i);
                            logger.info("    Location[{}]: {} units", i, level.getAvailable());
                        }
                    }
                    loggedInventorySample = true;
                }
                
                // Rule 1: Inventory should NEVER be > 1
                if (totalInventory > 1) {
                    violationsFound++;
                    String violation = String.format("SKU %s has inventory %d > 1 (web_status: %s)", 
                        productSku, totalInventory, webStatus);
                    inventoryViolations.add(violation);
                    logger.error("❌ INVENTORY VIOLATION: {}", violation);
                }
                
                // Rule 2: SOLD items should have inventory = 0
                if ("SOLD".equalsIgnoreCase(webStatus)) {
                    if (totalInventory == 0) {
                        soldItemsWithZeroInventory++;
                    } else {
                        violationsFound++;
                        String violation = String.format("SKU %s is SOLD but has inventory %d (should be 0)", 
                            productSku, totalInventory);
                        inventoryViolations.add(violation);
                        logger.error("❌ INVENTORY VIOLATION: {}", violation);
                    }
                }
                
                // Rule 3: Non-SOLD items should have inventory = 1
                else {
                    if (totalInventory == 1) {
                        availableItemsWithOneInventory++;
                    } else {
                        violationsFound++;
                        String violation = String.format("SKU %s is %s but has inventory %d (should be 1)", 
                            productSku, webStatus, totalInventory);
                        inventoryViolations.add(violation);
                        logger.error("❌ INVENTORY VIOLATION: {}", violation);
                    }
                }
            }
        }
        
        return new InventoryValidationResult(productsChecked, soldItemsWithZeroInventory, 
            availableItemsWithOneInventory, violationsFound, inventoryViolations);
    }
    
    /**
     * Result class for inventory validation
     */
    private static class InventoryValidationResult {
        final int productsChecked;
        final int soldItemsWithZeroInventory;
        final int availableItemsWithOneInventory;
        final int violationsFound;
        final List<String> inventoryViolations;
        
        InventoryValidationResult(int productsChecked, int soldItemsWithZeroInventory, 
                                int availableItemsWithOneInventory, int violationsFound, 
                                List<String> inventoryViolations) {
            this.productsChecked = productsChecked;
            this.soldItemsWithZeroInventory = soldItemsWithZeroInventory;
            this.availableItemsWithOneInventory = availableItemsWithOneInventory;
            this.violationsFound = violationsFound;
            this.inventoryViolations = inventoryViolations;
        }
    }

    @Test
    /**
     * Test sync behavior with existing items that have changes in both variant options and metafields
     * This tests the handleChangedItems path of the sync logic
     * 
     * Tests both:
     * 1. Variant option updates (Color, Size, Material) using remove-and-recreate approach
     * 2. Metafield updates (brand, model, etc.)
     * 
     * Ensures that when feedItem attributes change, both variant options and metafields are properly updated
     */
    public void syncTestUpdatedItemsOnly() throws Exception {
        logger.info("=== Starting sync test for updated items with variant options and metafields ===");
        
        // Get test items and create initial products
        List<FeedItem> topFeedItems = getTopFeedItems(2);
        logger.info("📝 Working with {} test items", topFeedItems.size());
        
        // Log initial values for debugging
        for (int i = 0; i < topFeedItems.size(); i++) {
            FeedItem item = topFeedItems.get(i);
            logger.info("Item {}: {} - Initial values:", (i + 1), item.getWebTagNumber());
            logger.info("  - webWatchDial (Color): '{}'", item.getWebWatchDial());
            logger.info("  - webWatchDiameter (Size): '{}'", item.getWebWatchDiameter());
            logger.info("  - webMetalType (Material): '{}'", item.getWebMetalType());
            logger.info("  - webWatchModel (Metafield): '{}'", item.getWebWatchModel());
        }
        
        // Create initial products using doSyncForFeedItems (same method used for updates)
        logger.info("🔄 Creating initial products using doSyncForFeedItems...");
        syncService.doSyncForFeedItems(topFeedItems);
        
        // CRITICAL: Verify database transactions were committed after sync
        logger.info("🔍 Verifying database transactions were committed after initial sync...");
        for (FeedItem item : topFeedItems) {
            // Re-fetch the item from database to confirm it was persisted
            FeedItem dbItem = feedItemService.findByWebTagNumber(item.getWebTagNumber());
            Assertions.assertNotNull(dbItem, 
                "❌ TRANSACTION ISSUE: Item should exist in database after initial sync for SKU: " + item.getWebTagNumber());
            
            Assertions.assertNotNull(dbItem.getShopifyItemId(), 
                "❌ TRANSACTION ISSUE: Item should have Shopify ID in database after initial sync for SKU: " + item.getWebTagNumber() + 
                " - Found status: " + dbItem.getStatus());
            
            Assertions.assertEquals(FeedItem.STATUS_PUBLISHED, dbItem.getStatus(), 
                "❌ TRANSACTION ISSUE: Item should have PUBLISHED status in database for SKU: " + item.getWebTagNumber() + 
                " - Found status: " + dbItem.getStatus());
            
            // Update the original item with the database values for later comparisons
            item.setShopifyItemId(dbItem.getShopifyItemId());
            item.setStatus(dbItem.getStatus());
            
            logger.info("✅ Database transaction verified for SKU: {} - Shopify ID: {}, Status: {}", 
                item.getWebTagNumber(), dbItem.getShopifyItemId(), dbItem.getStatus());
        }
        Thread.sleep(3000); // Wait for creation
        
        // Verify initial creation and get initial states
        List<Product> initialProducts = new ArrayList<>();
        for (FeedItem item : topFeedItems) {
            Product product = shopifyApiService.getProductByProductId(item.getShopifyItemId());
            assertNotNull(product, "Product should have been created for item " + item.getWebTagNumber());
            
            // Assert exactly 1 variant per product after initial creation
            Assertions.assertNotNull(product.getVariants(), "Initial product should have variants");
            Assertions.assertEquals(1, product.getVariants().size(), "Initial product should have exactly 1 variant: " + item.getWebTagNumber());
            
            initialProducts.add(product);
            logger.info("✅ Initial product created: {} with ID {}", item.getWebTagNumber(), product.getId());
        }
        
        // Modify feedItem fields to trigger both variant option and metafield changes
        logger.info("🔄 Modifying feedItem attributes to trigger updates...");
        List<FeedItem> modifiedItems = new ArrayList<>();
        for (int i = 0; i < topFeedItems.size(); i++) {
            FeedItem modifiedItem = topFeedItems.get(i);
            
            // Modify variant option fields (triggers remove-and-recreate options logic)
            modifiedItem.setWebWatchDial(modifiedItem.getWebWatchDial() + " [UPDATED-DIAL-" + (i + 1) + "]");
            modifiedItem.setWebWatchDiameter(modifiedItem.getWebWatchDiameter() + " [UPDATED-SIZE-" + (i + 1) + "]");
            modifiedItem.setWebMetalType(modifiedItem.getWebMetalType() + " [UPDATED-MATERIAL-" + (i + 1) + "]");
            
            // Modify metafield-only fields
            modifiedItem.setWebWatchModel(modifiedItem.getWebWatchModel() + " [UPDATED-MODEL-" + (i + 1) + "]");
            
            // Also modify the title to trigger product update
            modifiedItem.setWebDescriptionShort(modifiedItem.getWebDescriptionShort() + " [MODIFIED FOR TEST]");
            
            modifiedItems.add(modifiedItem);
            
            logger.info("Modified item {}: {} - New values:", (i + 1), modifiedItem.getWebTagNumber());
            logger.info("  - webWatchDial (Color): '{}'", modifiedItem.getWebWatchDial());
            logger.info("  - webWatchDiameter (Size): '{}'", modifiedItem.getWebWatchDiameter());
            logger.info("  - webMetalType (Material): '{}'", modifiedItem.getWebMetalType());
            logger.info("  - webWatchModel (Metafield): '{}'", modifiedItem.getWebWatchModel());
            logger.info("  - webDescriptionShort: '{}'", modifiedItem.getWebDescriptionShort());
        }
        
        // Update the products (should trigger update logic, not create logic)
        logger.info("🔄 Updating products using proper sync flow (should trigger UPDATE logic)...");
        
        // CRITICAL: Verify items still exist in database before sync operation
        logger.info("🔍 Pre-sync verification: Checking items still exist in database...");
        for (FeedItem modifiedItem : modifiedItems) {
            FeedItem dbItemBeforeSync = feedItemService.findByWebTagNumber(modifiedItem.getWebTagNumber());
            Assertions.assertNotNull(dbItemBeforeSync, 
                "❌ PRE-SYNC ISSUE: Item should still exist in database before sync for SKU: " + modifiedItem.getWebTagNumber());
            
            Assertions.assertNotNull(dbItemBeforeSync.getShopifyItemId(), 
                "❌ PRE-SYNC ISSUE: Item should still have Shopify ID before sync for SKU: " + modifiedItem.getWebTagNumber());
            
            logger.info("✅ Pre-sync verified SKU: {} exists in DB with Shopify ID: {}", 
                modifiedItem.getWebTagNumber(), dbItemBeforeSync.getShopifyItemId());
        }
        
        // Use doSyncForFeedItems which will detect the changes and route to updateItemOnShopify
        // This is the correct way to test the update flow
        logger.info("🚀 Executing doSyncForFeedItems (should route to UPDATE, not CREATE)...");
        syncService.doSyncForFeedItems(modifiedItems);
        
        // CRITICAL: Verify items still exist in database after sync operation
        logger.info("🔍 Post-sync verification: Checking items still exist in database with same Shopify IDs...");
        for (FeedItem modifiedItem : modifiedItems) {
            FeedItem dbItemAfterSync = feedItemService.findByWebTagNumber(modifiedItem.getWebTagNumber());
            Assertions.assertNotNull(dbItemAfterSync, 
                "❌ POST-SYNC ISSUE: Item should still exist in database after sync for SKU: " + modifiedItem.getWebTagNumber());
            
            Assertions.assertNotNull(dbItemAfterSync.getShopifyItemId(), 
                "❌ POST-SYNC ISSUE: Item should still have Shopify ID after sync for SKU: " + modifiedItem.getWebTagNumber());
            
            // Verify the Shopify ID didn't change (should be same product updated, not new product created)
            String originalShopifyId = modifiedItem.getShopifyItemId();
            Assertions.assertEquals(originalShopifyId, dbItemAfterSync.getShopifyItemId(), 
                "❌ DUPLICATE CREATION: Shopify ID changed during sync, indicating new product was created instead of updating existing one. " +
                "SKU: " + modifiedItem.getWebTagNumber() + 
                ", Original ID: " + originalShopifyId + 
                ", New ID: " + dbItemAfterSync.getShopifyItemId());
            
            logger.info("✅ Post-sync verified SKU: {} - Shopify ID unchanged: {}", 
                modifiedItem.getWebTagNumber(), dbItemAfterSync.getShopifyItemId());
        }
        
        // CRITICAL ASSERTION: Verify no duplicate products were created
        logger.info("🔍 Verifying no duplicate products were created during update...");
        List<Product> productsAfterSync = shopifyApiService.getAllProducts();
        
        // Should have exactly the same number of products as we started with (no duplicates)
        Assertions.assertEquals(topFeedItems.size(), productsAfterSync.size(), 
            "Should have same number of products after update - no duplicates should be created. " +
            "Expected: " + topFeedItems.size() + ", Actual: " + productsAfterSync.size());
        
        // Additional validation: Check for duplicate SKUs in Shopify
        Map<String, List<Product>> productsBySku = productsAfterSync.stream()
            .filter(p -> p.getVariants() != null && !p.getVariants().isEmpty())
            .collect(Collectors.groupingBy(p -> p.getVariants().get(0).getSku()));
        
        for (Map.Entry<String, List<Product>> entry : productsBySku.entrySet()) {
            String sku = entry.getKey();
            List<Product> productsWithSku = entry.getValue();
            if (productsWithSku.size() > 1) {
                logger.error("❌ DUPLICATE SKU DETECTED: {} has {} products", sku, productsWithSku.size());
                for (Product dupProduct : productsWithSku) {
                    logger.error("  - Product ID: {}, Title: {}", dupProduct.getId(), dupProduct.getTitle());
                }
            }
            Assertions.assertEquals(1, productsWithSku.size(), 
                "SKU " + sku + " should have exactly 1 product, but found " + productsWithSku.size() + " duplicates");
        }
        
        logger.info("✅ No duplicate products found - sync correctly updated existing products");
        
        // Wait for Shopify propagation (increased wait time for option updates)
        Thread.sleep(8000);  // Increased from 3000ms
        
        // Retrieve updated products individually for better consistency
        List<Product> shopifyProducts = new ArrayList<>();
        for (FeedItem item : topFeedItems) {
            Product product = shopifyApiService.getProductByProductId(item.getShopifyItemId());
            if (product != null) {
                shopifyProducts.add(product);
                logger.info("Retrieved updated product {} individually", product.getId());
            }
        }
        
        Assertions.assertEquals(topFeedItems.size(), shopifyProducts.size(), 
            "Should have retrieved all updated products");
        
        // Verify variant options were updated correctly using remove-and-recreate approach
        logger.info("✅ Verifying variant options were updated using remove-and-recreate approach...");
        for (int i = 0; i < shopifyProducts.size(); i++) {
            Product product = shopifyProducts.get(i);
            FeedItem modifiedItem = modifiedItems.get(i);
            
            Assertions.assertNotNull(product.getVariants(), "Product should have variants");
            Assertions.assertFalse(product.getVariants().isEmpty(), "Product should have at least one variant");
            
            // Assert exactly 1 variant per product after update
            Assertions.assertEquals(1, product.getVariants().size(), "Updated product should have exactly 1 variant: " + modifiedItem.getWebTagNumber());
            
            Variant variant = product.getVariants().get(0);
            
            // Assert variant has inventory levels
            Assertions.assertNotNull(variant.getInventoryLevels(), "Updated product variant should have inventory levels: " + modifiedItem.getWebTagNumber());
            Assertions.assertNotNull(variant.getInventoryLevels().get(), "Updated product variant should have inventory level list: " + modifiedItem.getWebTagNumber());
            Assertions.assertFalse(variant.getInventoryLevels().get().isEmpty(), "Updated product variant should have at least one inventory level: " + modifiedItem.getWebTagNumber());
            
            logger.info("Product {}: Checking variant options", (i + 1));
            logger.info("  Expected Color (webWatchDial): '{}'", modifiedItem.getWebWatchDial());
            logger.info("  Actual Color (option1): '{}'", variant.getOption1());
            logger.info("  Expected Size (webWatchDiameter): '{}'", modifiedItem.getWebWatchDiameter());
            logger.info("  Actual Size (option2): '{}'", variant.getOption2());
            logger.info("  Expected Material (webMetalType): '{}'", modifiedItem.getWebMetalType());
            logger.info("  Actual Material (option3): '{}'", variant.getOption3());
            
            // Assert variant options were updated (remove-and-recreate approach)
            Assertions.assertEquals(modifiedItem.getWebWatchDial(), variant.getOption1(), 
                "Color option should be updated to match webWatchDial");
            Assertions.assertEquals(modifiedItem.getWebWatchDiameter(), variant.getOption2(), 
                "Size option should be updated to match webWatchDiameter");
            Assertions.assertEquals(modifiedItem.getWebMetalType(), variant.getOption3(), 
                "Material option should be updated to match webMetalType");
        }
        
        // Verify metafields were updated correctly
        logger.info("✅ Verifying metafields were updated correctly...");
        for (int i = 0; i < shopifyProducts.size(); i++) {
            Product product = shopifyProducts.get(i);
            FeedItem modifiedItem = modifiedItems.get(i);
            
            // Assert exactly 1 variant per product during metafield verification
            Assertions.assertNotNull(product.getVariants(), "Product should have variants during metafield verification");
            Assertions.assertEquals(1, product.getVariants().size(), "Product should still have exactly 1 variant during metafield verification: " + modifiedItem.getWebTagNumber());
            
            Assertions.assertNotNull(product.getMetafields(), "Product should have metafields");
            
            List<Metafield> ebayMetafields = product.getMetafields().stream()
                .filter(mf -> "ebay".equals(mf.getNamespace()))
                .collect(Collectors.toList());
            
            Assertions.assertFalse(ebayMetafields.isEmpty(), "Product should have eBay metafields");
            
            // Check that model metafield was updated
            boolean modelMetafieldFound = false;
            for (Metafield metafield : ebayMetafields) {
                if ("model".equals(metafield.getKey())) {
                    logger.info("Product {}: Model metafield - Expected: '{}', Actual: '{}'", 
                        (i + 1), modifiedItem.getWebWatchModel(), metafield.getValue());
                    Assertions.assertEquals(modifiedItem.getWebWatchModel(), metafield.getValue(), 
                        "Model metafield should be updated");
                    modelMetafieldFound = true;
                    break;
                }
            }
            Assertions.assertTrue(modelMetafieldFound, "Model metafield should exist and be updated");
        }
        
        // Verify that product titles were also updated
        logger.info("✅ Verifying product titles were updated...");
        for (int i = 0; i < shopifyProducts.size(); i++) {
            Product product = shopifyProducts.get(i);
            logger.info("Product {}: Title - Expected contains: '[MODIFIED FOR TEST]', Actual: '{}'", 
                (i + 1), product.getTitle());
            Assertions.assertTrue(product.getTitle().contains("[MODIFIED FOR TEST]"), 
                "Product title should contain the modification marker");
        }
        
        // =================== ASSERT: Inventory Levels Match Web Status and Are Never > 1 ===================
        InventoryValidationResult inventoryResult = validateInventoryLevels(shopifyProducts, modifiedItems);
        
        // Summary logging
        logger.info("📈 Inventory Validation Results:");
        logger.info("  Products checked: {}/{}", inventoryResult.productsChecked, modifiedItems.size());
        logger.info("  SOLD items with correct inventory (0): {}", inventoryResult.soldItemsWithZeroInventory);
        logger.info("  Available items with correct inventory (1): {}", inventoryResult.availableItemsWithOneInventory);
        logger.info("  Total violations found: {}", inventoryResult.violationsFound);
        
        if (!inventoryResult.inventoryViolations.isEmpty()) {
            logger.error("❌ Inventory violations details:");
            for (String violation : inventoryResult.inventoryViolations) {
                logger.error("  - {}", violation);
            }
        }
        
        // CRITICAL ASSERTIONS
        Assertions.assertEquals(0, inventoryResult.violationsFound, 
            "Inventory violations found! " + inventoryResult.violationsFound + " products have incorrect inventory levels. " +
            "Violations: " + String.join("; ", inventoryResult.inventoryViolations) + ". " +
            "All products must follow the rules: SOLD items = 0 inventory, others = 1 inventory, never > 1.");
        
        logger.info("✅ PASS: All inventory levels match web_status correctly after update");
        
        // Verify reasonable distribution
        Assertions.assertTrue(inventoryResult.productsChecked > 0, 
            "Should have checked at least one product's inventory. Found: " + inventoryResult.productsChecked);
        
        logger.info("✅ PASS: Inventory validation completed successfully for {} updated products", inventoryResult.productsChecked);
        logger.info("🎉 INVENTORY LEVEL VERIFICATION COMPLETE");
        logger.info("💡 All updated products follow the correct inventory rules:");
        logger.info("   1. ✅ Inventory is NEVER > 1");
        logger.info("   2. ✅ SOLD items have inventory = 0");
        logger.info("   3. ✅ Available items have inventory = 1");
        logger.info("   4. ✅ Inventory levels maintained correctly during updates");

        logger.info("✅ All tests passed! Both variant options and metafields were successfully updated");
        logger.info("✅ Remove-and-recreate approach for variant options is working correctly");
        logger.info("✅ Inventory validation passed - all levels match web_status and are ≤ 1");
        logger.info("=== End sync test for updated items ===");
    }
}
