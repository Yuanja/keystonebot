package com.gw.service;

import com.gw.domain.FeedItem;
import com.gw.services.shopifyapi.objects.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SyncUpdatedCollectionsTest extends BaseGraphqlTest {

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
     * Test sync behavior with collection changes when brand (webDesigner) is updated
     * This tests the collection assignment logic in the update pipeline
     * 
     * Tests:
     * 1. Initial products are assigned to correct brand collections
     * 2. When webDesigner (brand) changes, products move to new brand collections
     * 3. Products are removed from old collections and added to new ones
     * 4. Inventory levels remain correct during collection updates
     * 
     * Ensures that collection assignments are properly updated when feed item brand changes
     */
    public void syncTestUpdatedCollections() throws Exception {
        logger.info("=== Starting sync test for collection updates ===");
        
        // Get test items and create initial products
        List<FeedItem> topFeedItems = getTopFeedItems(2);
        logger.info("📝 Working with {} test items", topFeedItems.size());
        
        // Log initial values for debugging
        for (int i = 0; i < topFeedItems.size(); i++) {
            FeedItem item = topFeedItems.get(i);
            logger.info("Item {}: {} - Initial values:", (i + 1), item.getWebTagNumber());
            logger.info("  - webDesigner (Brand): '{}'", item.getWebDesigner());
            logger.info("  - webStyle (Gender): '{}'", item.getWebStyle());
            logger.info("  - webDescriptionShort (Title): '{}'", item.getWebDescriptionShort());
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
        
        // Verify initial creation and get initial states with collection information
        List<Product> initialProducts = new ArrayList<>();
        for (FeedItem item : topFeedItems) {
            Product product = shopifyApiService.getProductByProductId(item.getShopifyItemId());
            assertNotNull(product, "Product should have been created for item " + item.getWebTagNumber());
            
            // Assert exactly 1 variant per product after initial creation
            Assertions.assertNotNull(product.getVariants(), "Initial product should have variants");
            Assertions.assertEquals(1, product.getVariants().size(), "Initial product should have exactly 1 variant: " + item.getWebTagNumber());
            
            initialProducts.add(product);
            logger.info("✅ Initial product created: {} with ID {}", item.getWebTagNumber(), product.getId());
            
            // Log initial collection assignments
            List<Collect> initialCollects = shopifyApiService.getCollectsForProductId(product.getId());
            logger.info("📋 Initial collections for SKU {} (Brand: {}):", item.getWebTagNumber(), item.getWebDesigner());
            List<CustomCollection> allCollections = shopifyApiService.getAllCustomCollections();
            for (Collect collect : initialCollects) {
                // Find the collection title by ID
                String collectionTitle = "Unknown";
                for (CustomCollection customCollection : allCollections) {
                    if (customCollection.getId().equals(collect.getCollectionId())) {
                        collectionTitle = customCollection.getTitle();
                        break;
                    }
                }
                logger.info("  - {} (ID: {})", collectionTitle, collect.getCollectionId());
            }
        }
        
        // Modify feedItem brands to trigger collection changes
        logger.info("🔄 Modifying feedItem brands to trigger collection updates...");
        List<FeedItem> modifiedItems = new ArrayList<>();
        for (int i = 0; i < topFeedItems.size(); i++) {
            FeedItem modifiedItem = topFeedItems.get(i);
            String originalBrand = modifiedItem.getWebDesigner();
            
            // Change brands to trigger collection moves
            String newBrand;
            if ("Audemars Piguet".equals(originalBrand)) {
                newBrand = "Rolex";
            } else if ("Patek Philippe".equals(originalBrand)) {
                newBrand = "Omega";
            } else if ("Rolex".equals(originalBrand)) {
                newBrand = "Patek Philippe";
            } else {
                newBrand = "Rolex"; // Default fallback
            }
            
            modifiedItem.setWebDesigner(newBrand);
            
            // Also modify the title to reflect the brand change
            String originalTitle = modifiedItem.getWebDescriptionShort();
            String newTitle = originalTitle.replace(originalBrand, newBrand) + " [BRAND-CHANGED-TEST]";
            modifiedItem.setWebDescriptionShort(newTitle);
            
            modifiedItems.add(modifiedItem);
            
            logger.info("Modified item {}: {} - Brand change:", (i + 1), modifiedItem.getWebTagNumber());
            logger.info("  - Original Brand: '{}'", originalBrand);
            logger.info("  - New Brand: '{}'", newBrand);
            logger.info("  - New Title: '{}'", newTitle);
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
        
        // Wait for Shopify propagation (increased wait time for collection updates)
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
        
        // Verify product titles were updated to reflect brand changes
        logger.info("✅ Verifying product titles were updated to reflect brand changes...");
        for (int i = 0; i < shopifyProducts.size(); i++) {
            Product product = shopifyProducts.get(i);
            FeedItem modifiedItem = modifiedItems.get(i);
            
            logger.info("Product {}: Title - Expected contains: '[BRAND-CHANGED-TEST]', Actual: '{}'", 
                (i + 1), product.getTitle());
            Assertions.assertTrue(product.getTitle().contains("[BRAND-CHANGED-TEST]"), 
                "Product title should contain the brand change marker");
            
            // Verify the new brand name is in the title
            Assertions.assertTrue(product.getTitle().contains(modifiedItem.getWebDesigner()), 
                "Product title should contain the new brand name: " + modifiedItem.getWebDesigner());
        }
        
        // =================== MAIN TEST: Verify Collection Changes ===================
        logger.info("🏷️ MAIN TEST: Verifying collection assignments have changed correctly...");
        
        for (int i = 0; i < shopifyProducts.size(); i++) {
            Product product = shopifyProducts.get(i);
            FeedItem originalItem = topFeedItems.get(i);
            FeedItem modifiedItem = modifiedItems.get(i);
            
            String originalBrand = null;
            // Reverse-engineer the original brand from the modified item
            if ("Rolex".equals(modifiedItem.getWebDesigner())) {
                originalBrand = "Audemars Piguet";
            } else if ("Omega".equals(modifiedItem.getWebDesigner())) {
                originalBrand = "Patek Philippe";
            } else if ("Patek Philippe".equals(modifiedItem.getWebDesigner())) {
                originalBrand = "Rolex";
            }
            
            String newBrand = modifiedItem.getWebDesigner();
            
            logger.info("🔍 Checking collection changes for SKU: {} (Product ID: {})", 
                originalItem.getWebTagNumber(), product.getId());
            logger.info("  Original Brand: '{}' → New Brand: '{}'", originalBrand, newBrand);
            
            // Get current collections for the product
            List<Collect> currentCollects = shopifyApiService.getCollectsForProductId(product.getId());
            logger.info("  Current collections after update ({} total):", currentCollects.size());
            
            // Get all collections for lookup
            List<CustomCollection> allCollections = shopifyApiService.getAllCustomCollections();
            Map<String, String> collectionIdToTitle = new HashMap<>();
            for (CustomCollection customCollection : allCollections) {
                collectionIdToTitle.put(customCollection.getId(), customCollection.getTitle());
            }
            
            // Log current collections
            for (Collect collect : currentCollects) {
                String collectionTitle = collectionIdToTitle.getOrDefault(collect.getCollectionId(), "Unknown");
                logger.info("    - '{}' (ID: {})", collectionTitle, collect.getCollectionId());
            }
            
            // Assert that the product is NO LONGER in the original brand collection
            boolean foundOriginalBrandCollection = false;
            for (Collect collect : currentCollects) {
                String collectionTitle = collectionIdToTitle.get(collect.getCollectionId());
                if (originalBrand != null && originalBrand.equals(collectionTitle)) {
                    foundOriginalBrandCollection = true;
                    break;
                }
            }
            if (originalBrand != null) {
                Assertions.assertFalse(foundOriginalBrandCollection, 
                    "Product " + originalItem.getWebTagNumber() + " should NOT be in original brand collection '" + 
                    originalBrand + "' after brand change to '" + newBrand + "'");
                logger.info("  ✅ Confirmed: Product is NO LONGER in '{}' collection", originalBrand);
            }
            
            // Assert that the product IS NOW in the new brand collection
            boolean foundNewBrandCollection = false;
            for (Collect collect : currentCollects) {
                String collectionTitle = collectionIdToTitle.get(collect.getCollectionId());
                if (newBrand.equals(collectionTitle)) {
                    foundNewBrandCollection = true;
                    break;
                }
            }
            Assertions.assertTrue(foundNewBrandCollection, 
                "Product " + originalItem.getWebTagNumber() + " should be in new brand collection '" + 
                newBrand + "' after brand change from '" + originalBrand + "'");
            logger.info("  ✅ Confirmed: Product is NOW in '{}' collection", newBrand);
            
            // Verify the product still has other expected collections (Men's, Women's, etc.)
            boolean foundGenderCollection = false;
            for (Collect collect : currentCollects) {
                String collectionTitle = collectionIdToTitle.get(collect.getCollectionId());
                if ("Men's".equals(collectionTitle) || "Women's".equals(collectionTitle)) {
                    foundGenderCollection = true;
                    break;
                }
            }
            Assertions.assertTrue(foundGenderCollection, 
                "Product " + originalItem.getWebTagNumber() + " should still be in a gender-based collection (Men's or Women's)");
            logger.info("  ✅ Confirmed: Product is still in appropriate gender collection");
            
            // Assert exactly 1 variant per product during collection verification
            Assertions.assertNotNull(product.getVariants(), "Product should have variants during collection verification");
            Assertions.assertEquals(1, product.getVariants().size(), "Product should still have exactly 1 variant during collection verification: " + modifiedItem.getWebTagNumber());
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
        
        logger.info("✅ PASS: All inventory levels match web_status correctly after collection update");
        
        // Verify reasonable distribution
        Assertions.assertTrue(inventoryResult.productsChecked > 0, 
            "Should have checked at least one product's inventory. Found: " + inventoryResult.productsChecked);
        
        logger.info("✅ PASS: Inventory validation completed successfully for {} updated products", inventoryResult.productsChecked);
        logger.info("🎉 COLLECTION UPDATE VERIFICATION COMPLETE");
        logger.info("💡 All updated products correctly moved to new brand collections:");
        logger.info("   1. ✅ Products removed from original brand collections");
        logger.info("   2. ✅ Products added to new brand collections");
        logger.info("   3. ✅ Products retained appropriate gender collections");
        logger.info("   4. ✅ Inventory levels maintained correctly during collection updates");

        logger.info("✅ All tests passed! Collection assignments were successfully updated");
        logger.info("✅ Collection update pipeline is working correctly");
        logger.info("✅ Inventory validation passed - all levels match web_status and are ≤ 1");
        logger.info("=== End sync test for collection updates ===");
    }
} 