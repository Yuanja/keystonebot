package com.gw.service;

import com.gw.domain.FeedItem;
import com.gw.services.shopifyapi.objects.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class EbayMetafieldsTest extends BaseGraphqlTest {

    @Test
    /**
     * Test that eBay metafields are properly created with all watch fields
     * Verifies that metafields with namespace "ebay" are associated with products
     */
    public void testEbayMetafieldsCreation() throws Exception {
        logger.info("=== Starting eBay Metafields Test ===");
        
        // Get a single item from live feed for testing
        List<FeedItem> topFeedItems = getTopFeedItems(1);
        Assertions.assertTrue(topFeedItems.size() >= 1, "Should have at least 1 item from live feed");
        
        FeedItem testItem = topFeedItems.get(0);
        logger.info("Testing eBay metafields with item SKU: " + testItem.getWebTagNumber());
        logger.info("Item details:");
        logger.info("  Brand: " + testItem.getWebDesigner());
        logger.info("  Model: " + testItem.getWebWatchModel());
        logger.info("  Year: " + testItem.getWebWatchYear());
        logger.info("  Material: " + testItem.getWebMetalType());
        logger.info("  Condition: " + testItem.getWebWatchCondition());
        
        // Sync the item to Shopify
        logger.info("Publishing item to Shopify with eBay metafields...");
        syncService.publishItemToShopify(testItem);
        
        // Verify item was published
        Assertions.assertNotNull(testItem.getShopifyItemId(), "Item should have Shopify ID after publishing");
        logger.info("✅ Item published with Shopify ID: " + testItem.getShopifyItemId());
        
        // Retrieve the product from Shopify to verify metafields
        Product shopifyProduct = shopifyApiService.getProductByProductId(testItem.getShopifyItemId());
        Assertions.assertNotNull(shopifyProduct, "Should be able to retrieve product from Shopify");
        
        // Verify eBay metafields exist
        List<Metafield> metafields = shopifyProduct.getMetafields();
        Assertions.assertNotNull(metafields, "Product should have metafields");
        
        // Filter eBay metafields
        List<Metafield> ebayMetafields = metafields.stream()
            .filter(mf -> "ebay".equals(mf.getNamespace()))
            .toList();
        
        logger.info("Found " + ebayMetafields.size() + " eBay metafields:");
        for (Metafield metafield : ebayMetafields) {
            logger.info("  " + metafield.getKey() + " = " + metafield.getValue() + 
                       " (type: " + metafield.getType() + ")");
        }
        
        Assertions.assertTrue(ebayMetafields.size() > 0, "Should have at least one eBay metafield");
        
        // Verify specific eBay metafields based on feed item data
        if (testItem.getWebDesigner() != null) {
            verifyEbayMetafield(ebayMetafields, "brand", testItem.getWebDesigner());
        }
        if (testItem.getWebWatchModel() != null) {
            verifyEbayMetafield(ebayMetafields, "model", testItem.getWebWatchModel());
        }
        if (testItem.getWebWatchYear() != null) {
            verifyEbayMetafield(ebayMetafields, "year", testItem.getWebWatchYear());
        }
        if (testItem.getWebMetalType() != null) {
            verifyEbayMetafield(ebayMetafields, "case_material", testItem.getWebMetalType());
        }
        if (testItem.getWebWatchCondition() != null) {
            verifyEbayMetafield(ebayMetafields, "condition", testItem.getWebWatchCondition());
        }
        if (testItem.getWebWatchDiameter() != null) {
            verifyEbayMetafield(ebayMetafields, "diameter", testItem.getWebWatchDiameter());
        }
        
        logger.info("=== eBay Metafields Test Summary ===");
        logger.info("✅ Product created successfully with Shopify ID: " + testItem.getShopifyItemId());
        logger.info("✅ Total metafields found: " + metafields.size());
        logger.info("✅ eBay metafields found: " + ebayMetafields.size());
        logger.info("✅ All expected eBay metafields verified successfully");
        logger.info("=== eBay Metafields Test Complete ===");
    }
    
    /**
     * Helper method to verify a specific eBay metafield exists with the expected value
     */
    private void verifyEbayMetafield(List<Metafield> ebayMetafields, String expectedKey, String expectedValue) {
        Metafield found = ebayMetafields.stream()
            .filter(mf -> expectedKey.equals(mf.getKey()))
            .findFirst()
            .orElse(null);
        
        Assertions.assertNotNull(found, "eBay metafield '" + expectedKey + "' should exist");
        Assertions.assertEquals(expectedValue, found.getValue(), 
            "eBay metafield '" + expectedKey + "' should have correct value");
        Assertions.assertEquals("ebay", found.getNamespace(), 
            "eBay metafield '" + expectedKey + "' should have 'ebay' namespace");
        
        logger.info("✅ Verified eBay metafield: " + expectedKey + " = " + expectedValue);
    }
} 