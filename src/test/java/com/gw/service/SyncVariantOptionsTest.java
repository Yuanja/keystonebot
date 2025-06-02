package com.gw.service;

import com.gw.domain.FeedItem;
import com.gw.services.shopifyapi.objects.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Dedicated test for variant options functionality in sync operations
 * Tests that products created through sync have the correct variant options:
 * - Color: webWatchDial 
 * - Size: webWatchDiameter
 * - Material: webMetalType
 */
public class SyncVariantOptionsTest extends BaseGraphqlTest {

    @Test
    /**
     * Test that sync creates products with proper variant options from feed item attributes
     */
    public void testSyncCreatesVariantOptions() throws Exception {
        logger.info("=== Testing Sync Variant Options Creation ===");
        
        // Load a small number of items for focused testing
        List<FeedItem> topFeedItems = getTopFeedItems(3);
        Assertions.assertTrue(topFeedItems.size() >= 3, "Should have at least 3 items from live feed");
        
        logger.info("Testing variant options with 3 items:");
        for (int i = 0; i < topFeedItems.size(); i++) {
            FeedItem item = topFeedItems.get(i);
            logger.info("Item {}: SKU={}, Dial={}, Diameter={}, Metal={}",
                i + 1, item.getWebTagNumber(), item.getWebWatchDial(), 
                item.getWebWatchDiameter(), item.getWebMetalType());
        }
        
        // Sync the items
        logger.info("Syncing items...");
        long startTime = System.currentTimeMillis();
        syncService.doSyncForFeedItems(topFeedItems);
        long endTime = System.currentTimeMillis();
        
        logger.info("✅ Sync completed in {}ms", (endTime - startTime));
        
        // Verify products were created
        List<Product> allProducts = shopifyApiService.getAllProducts();
        Assertions.assertEquals(topFeedItems.size(), allProducts.size(), 
            "Should have created products for all synced items");
        
        logger.info("✅ Verified {} products created", allProducts.size());
        
        // Test variant options on each product
        int productsWithVariantOptions = 0;
        int productsWithColorOption = 0;
        int productsWithSizeOption = 0;
        int productsWithMaterialOption = 0;
        
        for (FeedItem feedItem : topFeedItems) {
            // Find the corresponding product
            Product product = null;
            for (Product p : allProducts) {
                if (p.getVariants() != null && !p.getVariants().isEmpty() && 
                    feedItem.getWebTagNumber().equals(p.getVariants().get(0).getSku())) {
                    product = p;
                    break;
                }
            }
            
            Assertions.assertNotNull(product, "Product should exist for SKU: " + feedItem.getWebTagNumber());
            
            // Verify variant options
            boolean hasVariantOptions = product.getOptions() != null && !product.getOptions().isEmpty();
            if (hasVariantOptions) {
                productsWithVariantOptions++;
                
                logger.info("🎯 Verifying variant options for SKU: {}", feedItem.getWebTagNumber());
                
                // Log the options
                logger.info("  Product options ({}):", product.getOptions().size());
                for (Option option : product.getOptions()) {
                    logger.info("    - {} (pos {}): {}", option.getName(), option.getPosition(), 
                               String.join(", ", option.getValues()));
                    
                    if ("Color".equals(option.getName())) {
                        productsWithColorOption++;
                    } else if ("Size".equals(option.getName())) {
                        productsWithSizeOption++;
                    } else if ("Material".equals(option.getName())) {
                        productsWithMaterialOption++;
                    }
                }
                
                // Verify variant option values match feed item attributes
                Variant variant = product.getVariants().get(0);
                logger.info("  Variant option values:");
                logger.info("    - Option1: {}", variant.getOption1());
                logger.info("    - Option2: {}", variant.getOption2());
                logger.info("    - Option3: {}", variant.getOption3());
                
                // Test Color mapping (webWatchDial -> option1)
                if (feedItem.getWebWatchDial() != null && !feedItem.getWebWatchDial().trim().isEmpty()) {
                    Assertions.assertEquals(feedItem.getWebWatchDial(), variant.getOption1(),
                        "Color option should match webWatchDial for SKU: " + feedItem.getWebTagNumber());
                    logger.info("    ✅ Color option verified: {} = {}", feedItem.getWebWatchDial(), variant.getOption1());
                }
                
                // Test Size mapping (webWatchDiameter -> option2 or option3)
                if (feedItem.getWebWatchDiameter() != null && !feedItem.getWebWatchDiameter().trim().isEmpty()) {
                    boolean sizeFound = feedItem.getWebWatchDiameter().equals(variant.getOption2()) ||
                                      feedItem.getWebWatchDiameter().equals(variant.getOption3());
                    Assertions.assertTrue(sizeFound,
                        "Size option should match webWatchDiameter for SKU: " + feedItem.getWebTagNumber() + 
                        " (expected: " + feedItem.getWebWatchDiameter() + ", got option2: " + variant.getOption2() + 
                        ", option3: " + variant.getOption3() + ")");
                    logger.info("    ✅ Size option verified: {}", feedItem.getWebWatchDiameter());
                }
                
                // Test Material mapping (webMetalType -> option2 or option3)
                if (feedItem.getWebMetalType() != null && !feedItem.getWebMetalType().trim().isEmpty()) {
                    boolean materialFound = feedItem.getWebMetalType().equals(variant.getOption2()) ||
                                          feedItem.getWebMetalType().equals(variant.getOption3());
                    Assertions.assertTrue(materialFound,
                        "Material option should match webMetalType for SKU: " + feedItem.getWebTagNumber() + 
                        " (expected: " + feedItem.getWebMetalType() + ", got option2: " + variant.getOption2() + 
                        ", option3: " + variant.getOption3() + ")");
                    logger.info("    ✅ Material option verified: {}", feedItem.getWebMetalType());
                }
                
                logger.info("  ✅ All variant options verified for SKU: {}", feedItem.getWebTagNumber());
            } else {
                logger.info("  ℹ️ No variant options for SKU: {} (this is okay if feed item has no dial/diameter/metal)", 
                           feedItem.getWebTagNumber());
            }
        }
        
        // Summary assertions
        logger.info("=== Variant Options Test Summary ===");
        logger.info("Products with variant options: {}/{}", productsWithVariantOptions, allProducts.size());
        logger.info("Products with Color options: {}", productsWithColorOption);
        logger.info("Products with Size options: {}", productsWithSizeOption);
        logger.info("Products with Material options: {}", productsWithMaterialOption);
        
        // Verify that at least some products have variant options
        Assertions.assertTrue(productsWithVariantOptions > 0,
            "At least one product should have variant options");
        
        // For a watch store, expect meaningful option coverage
        double variantOptionCoverage = (double) productsWithVariantOptions / allProducts.size() * 100;
        logger.info("Variant option coverage: {:.1f}%", variantOptionCoverage);
        
        Assertions.assertTrue(variantOptionCoverage >= 80.0,
            "At least 80% of products should have variant options for a watch store, found: " + 
            String.format("%.1f", variantOptionCoverage) + "%");
        
        logger.info("✅ PASS: Sync properly creates variant options from feed item attributes");
        logger.info("✅ PASS: All variant option mappings are correct (Color, Size, Material)");
        logger.info("✅ PASS: Variant option coverage is appropriate for a watch store");
        logger.info("=== Sync Variant Options Test Complete ===");
    }
} 