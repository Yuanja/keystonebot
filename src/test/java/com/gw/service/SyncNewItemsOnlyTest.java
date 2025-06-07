package com.gw.service;

import com.gw.domain.FeedItem;
import com.gw.services.shopifyapi.objects.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class SyncNewItemsOnlyTest extends BaseGraphqlTest {

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
     * Test sync behavior with completely new items that don't exist in DB or Shopify
     * This tests the handleNewItems path of the sync logic
     * Uses live feed top 10 items - syncs first 5, then syncs all 10 (preserving first 5, adding second 5)
     */
    public void syncTestNewItemsOnly() throws Exception {
        logger.info("=== Starting New Items Only Sync Test ===");
        
        // Load top 10 items from live feed using private method
        List<FeedItem> topFeedItems = getTopFeedItems(10);
        Assertions.assertTrue(topFeedItems.size() >= 10, "Should have at least 10 items from live feed");
        
        // Split into first 5 and second 5 for analysis
        List<FeedItem> firstBatch = topFeedItems.stream().limit(5).collect(Collectors.toList());
        List<FeedItem> secondBatch = topFeedItems.stream().skip(5).limit(5).collect(Collectors.toList());
        
        logger.info("Testing with " + topFeedItems.size() + " items from live feed (top by webTagNumber):");
        logger.info("First batch (5 items):");
        for (FeedItem item : firstBatch) {
            logger.info("- SKU: " + item.getWebTagNumber() + " - " + item.getWebDescriptionShort());
        }
        logger.info("Second batch (5 items):");
        for (FeedItem item : secondBatch) {
            logger.info("- SKU: " + item.getWebTagNumber() + " - " + item.getWebDescriptionShort());
        }
        
        // PHASE 1: Sync first 5 items (should treat all as new)
        logger.info("=== PHASE 1: Syncing first batch (5 items) ===");
        long startTime1 = System.currentTimeMillis();
        syncService.doSyncForFeedItems(firstBatch);
        long endTime1 = System.currentTimeMillis();
        
        logger.info("✅ First batch sync completed in " + (endTime1 - startTime1) + "ms");
        
        // Verify first batch was published
        List<Product> productsAfterFirstBatch = shopifyApiService.getAllProducts();
        Assertions.assertEquals(firstBatch.size(), productsAfterFirstBatch.size(), "Should have created products for first 5 items");
        
        // Store details of first batch products for later comparison
        Map<String, String> firstBatchProductDetails = new HashMap<>();
        Map<String, Integer> firstBatchImageCounts = new HashMap<>();
        for (FeedItem originalItem : firstBatch) {
            Optional<Product> foundProduct = productsAfterFirstBatch.stream()
                    .filter(p -> p.getVariants() != null && !p.getVariants().isEmpty())
                .filter(p -> originalItem.getWebTagNumber().equals(p.getVariants().get(0).getSku()))
                    .findFirst();
                
            Assertions.assertTrue(foundProduct.isPresent(), "Product should exist for SKU: " + originalItem.getWebTagNumber());
            
            Product product = foundProduct.get();
            Assertions.assertEquals("ACTIVE", product.getStatus(), "Product should have ACTIVE status");
            
            // Assert exactly 1 variant per product
            Assertions.assertNotNull(product.getVariants(), "Product should have variants");
            Assertions.assertEquals(1, product.getVariants().size(), "Product should have exactly 1 variant: " + originalItem.getWebTagNumber());
            
            // Store product details for comparison after second batch
            firstBatchProductDetails.put(originalItem.getWebTagNumber(), 
                product.getId() + "|" + product.getTitle() + "|" + product.getUpdatedAt());
            
            // CRITICAL: Store image count to verify no duplication during second sync
            int imageCount = product.getImages() != null ? product.getImages().size() : 0;
            firstBatchImageCounts.put(originalItem.getWebTagNumber(), imageCount);
            
            // Verify collection associations
            List<Collect> collects = shopifyApiService.getCollectsForProductId(product.getId());
            Assertions.assertTrue(collects.size() > 0, "Product should be associated with collections");
            
            logger.info("✅ Verified first batch item: " + originalItem.getWebTagNumber() + 
                       " (Shopify ID: " + product.getId() + ", Collections: " + collects.size() + 
                       ", Images: " + imageCount + ")");
        }
        
        logger.info("First batch products stored for change detection");
        
        // PHASE 2: Sync ALL 10 items (should keep first 5 unchanged and add second 5)
        logger.info("=== PHASE 2: Syncing all 10 items (preserving first 5, adding second 5) ===");
        long startTime2 = System.currentTimeMillis();
        syncService.doSyncForFeedItems(topFeedItems); // Pass ALL 10 items
        long endTime2 = System.currentTimeMillis();
        
        logger.info("✅ All items sync completed in " + (endTime2 - startTime2) + "ms");
        
        // VERIFICATION PHASE: Check total count and that first 5 weren't changed
        logger.info("=== VERIFICATION PHASE ===");
        List<Product> finalProducts = shopifyApiService.getAllProducts();
        
        // Verify total count is 10
        Assertions.assertEquals(10, finalProducts.size(), "Should have total of 10 products after both batches");
        logger.info("✅ Verified total product count: " + finalProducts.size());
        
        // Verify all second batch items were added
        for (FeedItem secondBatchItem : secondBatch) {
            Optional<Product> foundProduct = finalProducts.stream()
                .filter(p -> p.getVariants() != null && !p.getVariants().isEmpty())
                .filter(p -> secondBatchItem.getWebTagNumber().equals(p.getVariants().get(0).getSku()))
                .findFirst();
            
            Assertions.assertTrue(foundProduct.isPresent(), "Second batch product should exist for SKU: " + secondBatchItem.getWebTagNumber());
            
            Product product = foundProduct.get();
            Assertions.assertEquals("ACTIVE", product.getStatus(), "Second batch product should have ACTIVE status");
            
            // Assert exactly 1 variant per product
            Assertions.assertNotNull(product.getVariants(), "Second batch product should have variants");
            Assertions.assertEquals(1, product.getVariants().size(), "Second batch product should have exactly 1 variant: " + secondBatchItem.getWebTagNumber());
            
            // Verify collection associations
            List<Collect> collects = shopifyApiService.getCollectsForProductId(product.getId());
            Assertions.assertTrue(collects.size() > 0, "Second batch product should be associated with collections");
            
            logger.info("✅ Verified second batch item: " + secondBatchItem.getWebTagNumber() + 
                       " (Shopify ID: " + product.getId() + ", Collections: " + collects.size() + ")");
        }
        
        // CRITICAL VERIFICATION: Ensure first 5 items weren't changed during second sync
        logger.info("=== CRITICAL VERIFICATION: First batch unchanged ===");
        for (FeedItem firstBatchItem : firstBatch) {
            Optional<Product> currentProduct = finalProducts.stream()
                .filter(p -> p.getVariants() != null && !p.getVariants().isEmpty())
                .filter(p -> firstBatchItem.getWebTagNumber().equals(p.getVariants().get(0).getSku()))
                .findFirst();
            
            Assertions.assertTrue(currentProduct.isPresent(), "First batch product should still exist: " + firstBatchItem.getWebTagNumber());
            
            Product product = currentProduct.get();
            String storedDetails = firstBatchProductDetails.get(firstBatchItem.getWebTagNumber());
            String[] storedDetailsParts = storedDetails.split("\\|");
            String storedId = storedDetailsParts[0];
            String storedTitle = storedDetailsParts[1];
            String storedUpdatedAt = storedDetailsParts[2];
            
            // Verify the product ID hasn't changed (most critical)
            Assertions.assertEquals(storedId, product.getId(), "First batch product ID should be unchanged: " + firstBatchItem.getWebTagNumber());
            
            // Verify the title hasn't changed
            Assertions.assertEquals(storedTitle, product.getTitle(), "First batch product title should be unchanged: " + firstBatchItem.getWebTagNumber());
            
            // Verify the updatedAt timestamp hasn't changed (indicates no modification)
            Assertions.assertEquals(storedUpdatedAt, product.getUpdatedAt(), "First batch product updatedAt should be unchanged (no modification): " + firstBatchItem.getWebTagNumber());
            
            // Assert exactly 1 variant per product (should remain unchanged)
            Assertions.assertNotNull(product.getVariants(), "First batch product should still have variants");
            Assertions.assertEquals(1, product.getVariants().size(), "First batch product should still have exactly 1 variant: " + firstBatchItem.getWebTagNumber());
            
            // CRITICAL: Verify image count hasn't doubled (no duplicate images)
            int currentImageCount = product.getImages() != null ? product.getImages().size() : 0;
            int originalImageCount = firstBatchImageCounts.get(firstBatchItem.getWebTagNumber());
            Assertions.assertEquals(originalImageCount, currentImageCount, 
                "First batch product image count should be unchanged (no duplicate images): " + firstBatchItem.getWebTagNumber() + 
                " - Original: " + originalImageCount + ", Current: " + currentImageCount);
            
            logger.info("✅ Verified first batch item UNCHANGED: " + firstBatchItem.getWebTagNumber() + 
                       " (ID: " + product.getId() + ", UpdatedAt: " + product.getUpdatedAt() + 
                       ", Images: " + currentImageCount + "/" + originalImageCount + ")");
        }
        
        // =================== ASSERT: Inventory Levels Match Web Status and Are Never > 1 ===================
        InventoryValidationResult inventoryResult = validateInventoryLevels(finalProducts, topFeedItems);
        
        // Summary logging
        logger.info("📈 Inventory Validation Results:");
        logger.info("  Products checked: {}/{}", inventoryResult.productsChecked, topFeedItems.size());
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
        
        logger.info("✅ PASS: All inventory levels match web_status correctly for new items");
        
        // Verify reasonable distribution
        Assertions.assertTrue(inventoryResult.productsChecked > 0, 
            "Should have checked at least one product's inventory. Found: " + inventoryResult.productsChecked);
        
        logger.info("✅ PASS: Inventory validation completed successfully for {} new products", inventoryResult.productsChecked);
        logger.info("🎉 INVENTORY LEVEL VERIFICATION COMPLETE");
        logger.info("💡 All new products follow the correct inventory rules:");
        logger.info("   1. ✅ Inventory is NEVER > 1");
        logger.info("   2. ✅ SOLD items have inventory = 0");
        logger.info("   3. ✅ Available items have inventory = 1");
        logger.info("   4. ✅ Inventory levels are set correctly during initial creation");

        // Final summary
        logger.info("=== New Items Only Sync Test Summary ===");
        logger.info("✅ Used live feed top " + topFeedItems.size() + " items (highest webTagNumber)");
        logger.info("✅ First batch (5 items) sync duration: " + (endTime1 - startTime1) + "ms");
        logger.info("✅ Second batch (all 10 items) sync duration: " + (endTime2 - startTime2) + "ms");
        logger.info("✅ Total products created: " + finalProducts.size());
        logger.info("✅ First batch products verified UNCHANGED during second sync");
        logger.info("✅ Second batch products verified ADDED successfully");
        logger.info("✅ All products have ACTIVE status and collection associations");
        logger.info("✅ Inventory validation passed - all levels match web_status and are ≤ 1");
        logger.info("=== New Items Only Sync Test Complete ===");
    }
} 