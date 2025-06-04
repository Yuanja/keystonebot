package com.gw.service;

import com.gw.domain.FeedItem;
import com.gw.services.shopifyapi.objects.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SyncUpdatedItemsOnlyTest extends BaseGraphqlTest {

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
        
        // Step 1: Get test items and create initial products
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
        
        // Step 2: Create initial products
        logger.info("🔄 Creating initial products...");
        for (FeedItem item : topFeedItems) {
            syncService.publishItemToShopify(item);
        }
        Thread.sleep(3000); // Wait for creation
        
        // Step 3: Verify initial creation and get initial states
        List<Product> initialProducts = new ArrayList<>();
        for (FeedItem item : topFeedItems) {
            Product product = shopifyApiService.getProductByProductId(item.getShopifyItemId());
            assertNotNull(product, "Product should have been created for item " + item.getWebTagNumber());
            initialProducts.add(product);
            logger.info("✅ Initial product created: {} with ID {}", item.getWebTagNumber(), product.getId());
        }
        
        // Step 4: Modify feedItem fields to trigger both variant option and metafield changes
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
        
        // Step 5: Update the products (should trigger update logic, not create logic)
        logger.info("🔄 Updating products using proper sync flow (should trigger UPDATE logic)...");
        
        // Use doSyncForFeedItems which will detect the changes and route to updateItemOnShopify
        // This is the correct way to test the update flow
        syncService.doSyncForFeedItems(modifiedItems);
        
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
        
        // Step 6: Verify variant options were updated correctly using remove-and-recreate approach
        logger.info("✅ Verifying variant options were updated using remove-and-recreate approach...");
        for (int i = 0; i < shopifyProducts.size(); i++) {
            Product product = shopifyProducts.get(i);
            FeedItem modifiedItem = modifiedItems.get(i);
            
            Assertions.assertNotNull(product.getVariants(), "Product should have variants");
            Assertions.assertFalse(product.getVariants().isEmpty(), "Product should have at least one variant");
            
            Variant variant = product.getVariants().get(0);
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
        
        // Step 7: Verify metafields were updated correctly
        logger.info("✅ Verifying metafields were updated correctly...");
        for (int i = 0; i < shopifyProducts.size(); i++) {
            Product product = shopifyProducts.get(i);
            FeedItem modifiedItem = modifiedItems.get(i);
            
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
        
        // Step 8: Verify that product titles were also updated
        logger.info("✅ Verifying product titles were updated...");
        for (int i = 0; i < shopifyProducts.size(); i++) {
            Product product = shopifyProducts.get(i);
            logger.info("Product {}: Title - Expected contains: '[MODIFIED FOR TEST]', Actual: '{}'", 
                (i + 1), product.getTitle());
            Assertions.assertTrue(product.getTitle().contains("[MODIFIED FOR TEST]"), 
                "Product title should contain the modification marker");
        }
        
        logger.info("✅ All tests passed! Both variant options and metafields were successfully updated");
        logger.info("✅ Remove-and-recreate approach for variant options is working correctly");
        logger.info("=== End sync test for updated items ===");
    }
} 