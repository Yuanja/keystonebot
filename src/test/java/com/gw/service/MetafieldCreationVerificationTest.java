package com.gw.service;

import com.gw.domain.FeedItem;
import com.gw.services.shopifyapi.ShopifyGraphQLService;
import com.gw.services.shopifyapi.objects.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ActiveProfiles("keystone-dev")
@TestPropertySource(properties = {"cron.schedule=0 0 0 29 2 ?"})
public class MetafieldCreationVerificationTest extends BaseGraphqlTest {

    @Test
    /**
     * Comprehensive test to verify metafield creation and visibility
     * This test will help debug why metafields might not be visible on Shopify listings
     */
    public void testMetafieldCreationAndVisibility() throws Exception {
        logger.info("=== Starting Comprehensive Metafield Creation & Visibility Test ===");
        
        // Step 1: Get a test item from live feed
        List<FeedItem> topFeedItems = getTopFeedItems(1);
        Assertions.assertTrue(topFeedItems.size() >= 1, "Should have at least 1 item from live feed");
        
        FeedItem testItem = topFeedItems.get(0);
        String originalSku = testItem.getWebTagNumber();
        
        logger.info("📋 Test Item Details:");
        logger.info("  SKU: " + originalSku);
        logger.info("  Brand: " + testItem.getWebDesigner());
        logger.info("  Model: " + testItem.getWebWatchModel());
        logger.info("  Year: " + testItem.getWebWatchYear());
        logger.info("  Material: " + testItem.getWebMetalType());
        logger.info("  Movement: " + testItem.getWebWatchMovement());
        logger.info("  Condition: " + testItem.getWebWatchCondition());
        logger.info("  Serial: " + testItem.getWebSerialNumber());
        logger.info("  Reference: " + testItem.getWebWatchManufacturerReferenceNumber());
        
        // Step 2: Create and publish the product
        logger.info("🚀 Publishing item to Shopify...");
        syncService.doSyncForFeedItems(Arrays.asList(testItem));
        
        Assertions.assertNotNull(testItem.getShopifyItemId(), "Item should have Shopify ID after publishing");
        String shopifyProductId = testItem.getShopifyItemId();
        logger.info("✅ Product created with Shopify ID: " + shopifyProductId);
        
        // Step 3: Retrieve the product and examine metafields
        logger.info("🔍 Retrieving product from Shopify to verify metafields...");
        Product shopifyProduct = shopifyApiService.getProductByProductId(shopifyProductId);
        Assertions.assertNotNull(shopifyProduct, "Should be able to retrieve product from Shopify");
        
        logger.info("📦 Product Retrieved:");
        logger.info("  ID: " + shopifyProduct.getId());
        logger.info("  Title: " + shopifyProduct.getTitle());
        logger.info("  Handle: " + shopifyProduct.getHandle());
        logger.info("  Status: " + shopifyProduct.getStatus());
        
        // Step 4: Examine all metafields
        List<Metafield> allMetafields = shopifyProduct.getMetafields();
        logger.info("📊 Metafield Analysis:");
        
        if (allMetafields == null || allMetafields.isEmpty()) {
            logger.error("❌ NO METAFIELDS FOUND! This indicates a problem with metafield creation.");
            logger.error("❌ Possible causes:");
            logger.error("   1. Metafields not being created during product creation");
            logger.error("   2. GraphQL query not retrieving metafields properly");
            logger.error("   3. Product creation input not including metafields");
            Assertions.fail("No metafields found on product - this should not happen");
        }
        
        logger.info("  Total metafields found: " + allMetafields.size());
        
        // Step 5: Categorize metafields by namespace
        Map<String, List<Metafield>> metafieldsByNamespace = allMetafields.stream()
            .collect(Collectors.groupingBy(mf -> mf.getNamespace() != null ? mf.getNamespace() : "null"));
        
        logger.info("📂 Metafields by namespace:");
        for (Map.Entry<String, List<Metafield>> entry : metafieldsByNamespace.entrySet()) {
            String namespace = entry.getKey();
            List<Metafield> metafields = entry.getValue();
            logger.info("  " + namespace + ": " + metafields.size() + " metafields");
            
            for (Metafield mf : metafields) {
                logger.info("    - " + mf.getKey() + " = '" + mf.getValue() + "' (type: " + mf.getType() + ")");
            }
        }
        
        // Step 6: Specifically verify eBay metafields
        List<Metafield> ebayMetafields = allMetafields.stream()
            .filter(mf -> "ebay".equals(mf.getNamespace()))
            .collect(Collectors.toList());
        
        logger.info("🏷️ eBay Metafields Analysis:");
        logger.info("  eBay metafields found: " + ebayMetafields.size());
        
        if (ebayMetafields.isEmpty()) {
            logger.error("❌ NO EBAY METAFIELDS FOUND!");
            logger.error("❌ This means the eBay metafield creation logic is not working properly");
            Assertions.fail("No eBay metafields found - check KeyStoneShopifyProductFactoryService.addEbayMetafields()");
        }
        
        // Step 7: Verify specific eBay metafields that should exist based on feed data
        logger.info("🔍 Verifying specific eBay metafields:");
        
        int expectedMetafields = 0;
        int foundMetafields = 0;
        
        if (testItem.getWebDesigner() != null) {
            expectedMetafields++;
            if (hasEbayMetafield(ebayMetafields, "brand", testItem.getWebDesigner())) {
                foundMetafields++;
                logger.info("  ✅ Brand metafield verified");
            } else {
                logger.error("  ❌ Brand metafield missing or incorrect");
            }
        }
        
        if (testItem.getWebWatchModel() != null) {
            expectedMetafields++;
            if (hasEbayMetafield(ebayMetafields, "model", testItem.getWebWatchModel())) {
                foundMetafields++;
                logger.info("  ✅ Model metafield verified");
            } else {
                logger.error("  ❌ Model metafield missing or incorrect");
            }
        }
        
        if (testItem.getWebWatchYear() != null) {
            expectedMetafields++;
            if (hasEbayMetafield(ebayMetafields, "year", testItem.getWebWatchYear())) {
                foundMetafields++;
                logger.info("  ✅ Year metafield verified");
            } else {
                logger.error("  ❌ Year metafield missing or incorrect");
            }
        }
        
        if (testItem.getWebMetalType() != null) {
            expectedMetafields++;
            if (hasEbayMetafield(ebayMetafields, "case_material", testItem.getWebMetalType())) {
                foundMetafields++;
                logger.info("  ✅ Case material metafield verified");
            } else {
                logger.error("  ❌ Case material metafield missing or incorrect");
            }
        }
        
        if (testItem.getWebWatchMovement() != null) {
            expectedMetafields++;
            if (hasEbayMetafield(ebayMetafields, "movement", testItem.getWebWatchMovement())) {
                foundMetafields++;
                logger.info("  ✅ Movement metafield verified");
            } else {
                logger.error("  ❌ Movement metafield missing or incorrect");
            }
        }
        
        logger.info("📈 Metafield Summary:");
        logger.info("  Expected eBay metafields: " + expectedMetafields);
        logger.info("  Found eBay metafields: " + foundMetafields);
        logger.info("  Total eBay metafields: " + ebayMetafields.size());
        
        // Step 8: Assertions
        Assertions.assertTrue(ebayMetafields.size() > 0, "Should have at least one eBay metafield");
        Assertions.assertTrue(foundMetafields > 0, "Should have found at least one expected eBay metafield");
        
        // Step 9: Test metafield accessibility via different methods
        logger.info("🔧 Testing metafield accessibility:");
        
        // Try retrieving product again to ensure metafields persist
        Product retrievedAgain = shopifyApiService.getProductByProductId(shopifyProductId);
        Assertions.assertNotNull(retrievedAgain, "Should be able to retrieve product again");
        
        List<Metafield> retrievedMetafields = retrievedAgain.getMetafields();
        if (retrievedMetafields != null && !retrievedMetafields.isEmpty()) {
            logger.info("  ✅ Metafields persist on re-retrieval (" + retrievedMetafields.size() + " metafields)");
        } else {
            logger.error("  ❌ Metafields lost on re-retrieval!");
        }
        
        // Step 10: Shopify Admin Interface Visibility Check
        logger.info("🖥️ Shopify Admin Visibility Information:");
        logger.info("  To check metafields in Shopify Admin:");
        logger.info("  1. Go to Products > All products");
        logger.info("  2. Find product: " + shopifyProduct.getTitle());
        logger.info("  3. Click on the product to open details");
        logger.info("  4. Scroll down to 'Metafields' section");
        logger.info("  5. Look for namespace 'ebay' with " + ebayMetafields.size() + " fields");
        
        logger.info("  Expected eBay metafields to see:");
        for (Metafield ebayMf : ebayMetafields) {
            logger.info("    - ebay." + ebayMf.getKey() + " = " + ebayMf.getValue());
        }
        
        logger.info("=== Metafield Creation & Visibility Test Complete ===");
        logger.info("✅ Product ID: " + shopifyProductId);
        logger.info("✅ Total metafields: " + allMetafields.size());
        logger.info("✅ eBay metafields: " + ebayMetafields.size());
        logger.info("✅ Test passed - metafields are properly created and retrievable via API");
    }
    
    @Test
    /**
     * Test to verify metafield types and structure
     */
    public void testMetafieldTypesAndStructure() throws Exception {
        logger.info("=== Starting Metafield Types & Structure Test ===");
        
        // Get a test item and publish it
        List<FeedItem> topFeedItems = getTopFeedItems(1);
        Assertions.assertTrue(topFeedItems.size() >= 1, "Should have at least 1 item from live feed");
        
        FeedItem testItem = topFeedItems.get(0);
        syncService.publishItemToShopify(testItem);
        
        Assertions.assertNotNull(testItem.getShopifyItemId(), "Item should have Shopify ID");
        
        // Retrieve and analyze metafield structure
        Product shopifyProduct = shopifyApiService.getProductByProductId(testItem.getShopifyItemId());
        List<Metafield> allMetafields = shopifyProduct.getMetafields();
        
        if (allMetafields != null && !allMetafields.isEmpty()) {
            logger.info("🔍 Analyzing metafield structure:");
            
            for (Metafield mf : allMetafields) {
                logger.info("  Metafield: " + mf.getNamespace() + "." + mf.getKey());
                logger.info("    ID: " + mf.getId());
                logger.info("    Type: " + mf.getType());
                logger.info("    Value: " + mf.getValue());
                logger.info("    Description: " + mf.getDescription());
                
                // Verify required fields
                Assertions.assertNotNull(mf.getNamespace(), "Metafield namespace should not be null");
                Assertions.assertNotNull(mf.getKey(), "Metafield key should not be null");
                Assertions.assertNotNull(mf.getValue(), "Metafield value should not be null");
                Assertions.assertNotNull(mf.getType(), "Metafield type should not be null");
                
                // Verify eBay metafield specifics
                if ("ebay".equals(mf.getNamespace())) {
                    Assertions.assertTrue(mf.getType().equals("single_line_text_field") || 
                                        mf.getType().equals("number_decimal"), 
                                        "eBay metafield should have valid type (single_line_text_field or number_decimal)");
                }
            }
            
            logger.info("✅ All metafields have proper structure");
        }
        
        logger.info("=== Metafield Types & Structure Test Complete ===");
    }
    
    /**
     * Helper method to check if a specific eBay metafield exists with the expected value
     */
    private boolean hasEbayMetafield(List<Metafield> ebayMetafields, String expectedKey, String expectedValue) {
        return ebayMetafields.stream()
            .anyMatch(mf -> expectedKey.equals(mf.getKey()) && expectedValue.equals(mf.getValue()));
    }
    
    @Test
    /**
     * Test Shopify taxonomy search functionality to find the "Watches" category ID
     */
    public void testShopifyTaxonomySearchForWatches() throws Exception {
        logger.info("=== Starting Shopify Taxonomy Search Test for 'Watches' ===");
        
        // Test searching for "Watches" category
        logger.info("🔍 Searching for 'Watches' category in Shopify taxonomy...");
        String watchesCategoryId = shopifyApiService.searchTaxonomyCategory("Watches");
        
        logger.info("📋 Search Results:");
        logger.info("  Search term: 'Watches'");
        logger.info("  Found category ID: " + watchesCategoryId);
        
        // Verify we got a valid result
        Assertions.assertNotNull(watchesCategoryId, "Should find a category ID for 'Watches'");
        Assertions.assertTrue(watchesCategoryId.startsWith("gid://shopify/TaxonomyCategory/"), 
                            "Category ID should be a valid Shopify taxonomy GID");
        
        // Test that it matches our expected static ID
        String expectedWatchesId = ShopifyGraphQLService.getWatchesCategoryId();
        logger.info("  Expected category ID: " + expectedWatchesId);
        
        Assertions.assertEquals(expectedWatchesId, watchesCategoryId, 
                              "Found category ID should match the expected Watches category ID");
        
        // Test some variations of the search
        logger.info("🔍 Testing search variations...");
        
        // Test lowercase
        String lowercaseResult = shopifyApiService.searchTaxonomyCategory("watches");
        logger.info("  'watches' (lowercase) result: " + lowercaseResult);
        
        // Test with partial match
        String partialResult = shopifyApiService.searchTaxonomyCategory("Watch");
        logger.info("  'Watch' (partial) result: " + partialResult);
        
        // Test case-insensitive search should work
        if (lowercaseResult != null) {
            Assertions.assertEquals(expectedWatchesId, lowercaseResult, 
                                  "Lowercase search should return the same category ID");
        }
        
        // Test non-existent category
        logger.info("🔍 Testing non-existent category search...");
        String nonExistentResult = shopifyApiService.searchTaxonomyCategory("NonExistentCategory12345");
        logger.info("  'NonExistentCategory12345' result: " + nonExistentResult);
        Assertions.assertNull(nonExistentResult, "Non-existent category should return null");
        
        // Get detailed information about the Watches category
        if (watchesCategoryId != null) {
            logger.info("🔍 Getting detailed information about the Watches category...");
            try {
                Map<String, Object> categoryDetails = shopifyApiService.getTaxonomyCategoryDetails(watchesCategoryId);
                if (categoryDetails != null) {
                    logger.info("📋 Category Details:");
                    logger.info("  ID: " + categoryDetails.get("id"));
                    logger.info("  Name: " + categoryDetails.get("name"));
                    logger.info("  Full Name: " + categoryDetails.get("fullName"));
                    logger.info("  Is Leaf: " + categoryDetails.get("isLeaf"));
                    logger.info("  Is Root: " + categoryDetails.get("isRoot"));
                } else {
                    logger.warn("⚠️ Could not retrieve detailed information for category: " + watchesCategoryId);
                }
            } catch (Exception e) {
                logger.warn("⚠️ Error retrieving category details: " + e.getMessage());
            }
        }
        
        logger.info("=== Shopify Taxonomy Search Test Complete ===");
        logger.info("✅ Successfully found Watches category ID: " + watchesCategoryId);
        logger.info("✅ Taxonomy search functionality is working correctly");
    }
} 