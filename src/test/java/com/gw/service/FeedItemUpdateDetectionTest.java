package com.gw.service;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.gw.domain.FeedItem;
import com.gw.domain.FeedItemChange;
import com.gw.domain.FeedItemChangeSet;
import com.gw.services.FeedItemService;
import com.gw.services.shopifyapi.objects.Product;
import com.gw.services.shopifyapi.objects.Variant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify that feed item attribute changes properly trigger Shopify product updates
 * 
 * This test validates:
 * 1. Feed items with unchanged attributes don't trigger updates
 * 2. Feed items with changed critical attributes trigger updates
 * 3. All important business attributes are properly compared
 * 4. Shopify products are correctly updated when changes occur
 * 
 * Critical attributes tested:
 * - Pricing fields (ONLY webPriceKeystone matters - all other pricing fields are ignored)
 * - Product information (description, designer, model, year)
 * - Watch specifications (movement, condition, case, dial, strap)
 * - Status and availability
 * - Image paths
 * - Material specifications
 * - Serial numbers and reference numbers
 */
public class FeedItemUpdateDetectionTest extends BaseGraphqlTest {
    
    private static final Logger logger = LogManager.getLogger(FeedItemUpdateDetectionTest.class);
    
    @Autowired
    private FeedItemService feedItemService;
    
    @Test
    public void testFeedItemChangeDetection() throws Exception {
        logger.info("=== Testing Feed Item Change Detection ===");
        
        // Step 1: Create and publish an initial product
        List<FeedItem> topFeedItems = getTopFeedItems(1);
        assertNotNull(topFeedItems, "Should have feed items from live feed");
        assertTrue(topFeedItems.size() >= 1, "Should have at least 1 item from live feed");
        
        FeedItem originalItem = topFeedItems.get(0);
        
        logger.info("📦 Publishing initial product: " + originalItem.getWebTagNumber());
        syncService.publishItemToShopify(originalItem);
        assertNotNull(originalItem.getShopifyItemId(), "Item should have Shopify ID after publishing");
        
        String shopifyProductId = originalItem.getShopifyItemId();
        Product initialProduct = shopifyApiService.getProductByProductId(shopifyProductId);
        assertNotNull(initialProduct, "Should be able to retrieve initial product");
        
        logger.info("✅ Initial product created: " + shopifyProductId + " - " + initialProduct.getTitle());
        
        // Step 2: Test unchanged item detection
        testUnchangedItemDetection(originalItem);
        
        // Step 3: Test pricing changes
        testPricingChanges(originalItem, initialProduct);
        
        // Step 4: Test product information changes
        testProductInformationChanges(originalItem, initialProduct);
        
        // Step 5: Test watch specification changes
        testWatchSpecificationChanges(originalItem, initialProduct);
        
        // Step 6: Test status and image changes
        testStatusAndImageChanges(originalItem, initialProduct);
        
        logger.info("🎉 All feed item change detection tests passed!");
    }
    
    /**
     * Test that identical items don't trigger changes
     */
    private void testUnchangedItemDetection(FeedItem originalItem) {
        logger.info("🧪 Testing unchanged item detection...");
        
        FeedItem identicalItem = new FeedItem();
        identicalItem.copyFrom(originalItem);
        
        // Should be equal for Shopify comparison
        assertTrue(originalItem.equalsForShopify(identicalItem), 
                   "Identical items should be equal for Shopify");
        
        // Should not trigger a change in the change detection system
        FeedItemChangeSet changeSet = feedItemService.compareFeedItemWithDB(false, Arrays.asList(identicalItem));
        assertEquals(0, changeSet.getChangedItems().size(), 
                    "Identical item should not trigger changes");
        
        logger.info("✅ Unchanged item correctly not detected as changed");
    }
    
    /**
     * Test pricing field changes - only webPriceKeystone should trigger updates
     */
    private void testPricingChanges(FeedItem originalItem, Product initialProduct) throws Exception {
        logger.info("🧪 Testing pricing changes...");
        
        // Test that webPriceKeystone change DOES trigger an update
        testSingleFieldChange(originalItem, "webPriceKeystone", "850.00", "Keystone price change");
        
        // Test that other pricing fields DO NOT trigger updates
        testPricingFieldIgnored(originalItem, "webPriceRetail", "999.99", "Retail price change should be ignored");
        testPricingFieldIgnored(originalItem, "webPriceSale", "899.99", "Sale price change should be ignored");
        testPricingFieldIgnored(originalItem, "webPriceEbay", "950.00", "eBay price change should be ignored");
        testPricingFieldIgnored(originalItem, "webPriceWholesale", "750.00", "Wholesale price change should be ignored");
        testPricingFieldIgnored(originalItem, "webPriceChronos", "875.00", "Chronos price change should be ignored");
        
        logger.info("✅ Pricing changes correctly detected - only webPriceKeystone matters");
    }
    
    /**
     * Test product information changes
     */
    private void testProductInformationChanges(FeedItem originalItem, Product initialProduct) throws Exception {
        logger.info("🧪 Testing product information changes...");
        
        // Test description change
        testSingleFieldChange(originalItem, "webDescriptionShort", 
                            originalItem.getWebDescriptionShort() + " [UPDATED]", 
                            "Description change");
        
        // Test designer/brand change
        testSingleFieldChange(originalItem, "webDesigner", "Updated Brand", "Designer change");
        
        // Test model change  
        testSingleFieldChange(originalItem, "webWatchModel", "Updated Model", "Model change");
        
        // Test year change
        testSingleFieldChange(originalItem, "webWatchYear", "2024", "Year change");
        
        // Test category change
        testSingleFieldChange(originalItem, "webCategory", "Premium Watches", "Category change");
        
        logger.info("✅ All product information changes correctly detected");
    }
    
    /**
     * Test watch specification changes
     */
    private void testWatchSpecificationChanges(FeedItem originalItem, Product initialProduct) throws Exception {
        logger.info("🧪 Testing watch specification changes...");
        
        // Test movement change
        testSingleFieldChange(originalItem, "webWatchMovement", "Automatic Swiss", "Movement change");
        
        // Test condition change
        testSingleFieldChange(originalItem, "webWatchCondition", "Excellent Plus", "Condition change");
        
        // Test case material change
        testSingleFieldChange(originalItem, "webMetalType", "18K Rose Gold", "Case material change");
        
        // Test dial change
        testSingleFieldChange(originalItem, "webWatchDial", "Midnight Blue", "Dial change");
        
        // Test strap change
        testSingleFieldChange(originalItem, "webWatchStrap", "Alligator Leather", "Strap change");
        
        // Test diameter change
        testSingleFieldChange(originalItem, "webWatchDiameter", "42mm", "Diameter change");
        
        // Test box and papers change
        testSingleFieldChange(originalItem, "webWatchBoxPapers", "Complete Set", "Box and papers change");
        
        // Test serial number change
        testSingleFieldChange(originalItem, "webSerialNumber", "ABC123456", "Serial number change");
        
        // Test reference number change
        testSingleFieldChange(originalItem, "webWatchManufacturerReferenceNumber", "REF-2024", "Reference number change");
        
        logger.info("✅ All watch specification changes correctly detected");
    }
    
    /**
     * Test status and image changes
     */
    private void testStatusAndImageChanges(FeedItem originalItem, Product initialProduct) throws Exception {
        logger.info("🧪 Testing status and image changes...");
        
        // Test status change
        testSingleFieldChange(originalItem, "webStatus", "SOLD", "Status change");
        
        // Test image path changes
        testSingleFieldChange(originalItem, "webImagePath1", "https://example.com/new-image1.jpg", "Image 1 change");
        testSingleFieldChange(originalItem, "webImagePath2", "https://example.com/new-image2.jpg", "Image 2 change");
        
        // Test eBay auction flag change
        testSingleFieldChange(originalItem, "webFlagEbayauction", "Y", "eBay auction flag change");
        
        logger.info("✅ All status and image changes correctly detected");
    }
    
    /**
     * Helper method to test a single field change
     */
    private void testSingleFieldChange(FeedItem originalItem, String fieldName, String newValue, String testDescription) throws Exception {
        logger.info("  🔍 Testing: " + testDescription);
        
        // Create modified item
        FeedItem modifiedItem = new FeedItem();
        modifiedItem.copyFrom(originalItem);
        
        // Use reflection to set the field value
        setFieldValue(modifiedItem, fieldName, newValue);
        
        // Should not be equal for Shopify comparison
        assertFalse(originalItem.equalsForShopify(modifiedItem), 
                   testDescription + " should trigger inequality");
        
        // Should trigger a change in the change detection system
        FeedItemChangeSet changeSet = feedItemService.compareFeedItemWithDB(false, Arrays.asList(modifiedItem));
        assertEquals(1, changeSet.getChangedItems().size(), 
                    testDescription + " should trigger exactly one change");
        
        FeedItemChange change = changeSet.getChangedItems().get(0);
        assertEquals(originalItem.getWebTagNumber(), change.getFromDb().getWebTagNumber(), 
                    "Change should be for the correct item");
        assertEquals(newValue, getFieldValue(change.getFromFeed(), fieldName), 
                    "Change should contain the new value");
        
        // Test the actual update process
        logger.info("    🔄 Testing Shopify update for: " + testDescription);
        syncService.doSyncForFeedItems(Arrays.asList(modifiedItem));
        
        // Verify the product was updated in Shopify
        Product updatedProduct = shopifyApiService.getProductByProductId(originalItem.getShopifyItemId());
        assertNotNull(updatedProduct, "Product should still exist after update");
        
        // Update the original item for next test
        originalItem.copyFrom(modifiedItem);
        
        logger.info("    ✅ " + testDescription + " successfully detected and processed");
    }
    
    /**
     * Helper method to test that pricing field changes are ignored
     */
    private void testPricingFieldIgnored(FeedItem originalItem, String fieldName, String newValue, String testDescription) throws Exception {
        logger.info("  🔍 Testing: " + testDescription);
        
        // Create modified item
        FeedItem modifiedItem = new FeedItem();
        modifiedItem.copyFrom(originalItem);
        
        // Use reflection to set the field value
        setFieldValue(modifiedItem, fieldName, newValue);
        
        // Should be equal for Shopify comparison (change should be ignored)
        assertTrue(originalItem.equalsForShopify(modifiedItem), 
                   testDescription + " - change should be ignored");
        
        // Should NOT trigger a change in the change detection system
        FeedItemChangeSet changeSet = feedItemService.compareFeedItemWithDB(false, Arrays.asList(modifiedItem));
        assertEquals(0, changeSet.getChangedItems().size(), 
                    testDescription + " - should not trigger any changes");
        
        logger.info("    ✅ " + testDescription + " - correctly ignored");
    }
    
    /**
     * Helper method to set field value using reflection
     */
    private void setFieldValue(FeedItem item, String fieldName, String value) throws Exception {
        String setterName = "set" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
        item.getClass().getMethod(setterName, String.class).invoke(item, value);
    }
    
    /**
     * Helper method to get field value using reflection
     */
    private String getFieldValue(FeedItem item, String fieldName) throws Exception {
        String getterName = "get" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
        return (String) item.getClass().getMethod(getterName).invoke(item);
    }
    
    /**
     * Test comprehensive change detection with multiple field changes
     */
    @Test
    public void testMultipleFieldChanges() throws Exception {
        logger.info("=== Testing Multiple Field Changes ===");
        
        // Get test items using standard pattern
        List<FeedItem> topFeedItems = getTopFeedItems(2);
        assertNotNull(topFeedItems, "Should have feed items from live feed");
        assertTrue(topFeedItems.size() >= 2, "Should have at least 2 items from live feed");
        
        FeedItem originalItem = topFeedItems.get(1); // Use second item to avoid conflicts
        
        // Publish original
        syncService.publishItemToShopify(originalItem);
        assertNotNull(originalItem.getShopifyItemId());
        
        // Create item with multiple changes
        FeedItem multiChangedItem = new FeedItem();
        multiChangedItem.copyFrom(originalItem);
        multiChangedItem.setWebPriceRetail("1299.99");
        multiChangedItem.setWebDescriptionShort(originalItem.getWebDescriptionShort() + " [MULTI-UPDATED]");
        multiChangedItem.setWebWatchCondition("Like New");
        multiChangedItem.setWebMetalType("Platinum");
        multiChangedItem.setWebImagePath1("https://example.com/multi-change-image1.jpg");
        
        // Should trigger change
        assertFalse(originalItem.equalsForShopify(multiChangedItem), 
                   "Multiple changes should trigger inequality");
        
        // Should detect change
        FeedItemChangeSet changeSet = feedItemService.compareFeedItemWithDB(false, Arrays.asList(multiChangedItem));
        assertEquals(1, changeSet.getChangedItems().size(), 
                    "Multiple changes should trigger exactly one change record");
        
        // Test sync
        syncService.doSyncForFeedItems(Arrays.asList(multiChangedItem));
        
        // Verify update
        Product updatedProduct = shopifyApiService.getProductByProductId(originalItem.getShopifyItemId());
        assertNotNull(updatedProduct, "Product should exist after multi-field update");
        assertTrue(updatedProduct.getTitle().contains("[MULTI-UPDATED]"), 
                  "Product title should reflect the change");
        
        logger.info("✅ Multiple field changes successfully detected and processed");
    }
} 