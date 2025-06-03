package com.gw.service;

import com.gw.domain.FeedItem;
import com.gw.services.shopifyapi.objects.*;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class VariantOptionsUpdateTest extends BaseGraphqlTest {
    
    private static final Logger logger = LoggerFactory.getLogger(VariantOptionsUpdateTest.class);

    @Test
    /**
     * Test that variant options are properly updated when feedItem fields change
     * This specifically tests the remove-and-recreate approach for product options
     */
    public void testVariantOptionsUpdate() throws Exception {
        logger.info("🔍 Starting variant options update test");
        
        // Step 1: Get a test item and create initial product
        List<FeedItem> topFeedItems = getTopFeedItems(1);
        FeedItem testItem = topFeedItems.get(0);
        
        logger.info("📝 Test item: {} - Initial values:", testItem.getWebTagNumber());
        logger.info("  - webWatchDial (Color): '{}'", testItem.getWebWatchDial());
        logger.info("  - webWatchDiameter (Size): '{}'", testItem.getWebWatchDiameter());
        logger.info("  - webMetalType (Material): '{}'", testItem.getWebMetalType());
        logger.info("  - webWatchModel (Metafield): '{}'", testItem.getWebWatchModel());
        
        // Step 2: Create initial product
        syncService.publishItemToShopify(testItem);
        Thread.sleep(2000); // Wait for creation
        
        // Step 3: Get initial product state
        Product initialProduct = shopifyApiService.getProductByProductId(testItem.getShopifyItemId());
        assertNotNull(initialProduct, "Product should have been created");
        assertNotNull(initialProduct.getVariants(), "Product should have variants");
        assertFalse(initialProduct.getVariants().isEmpty(), "Product should have at least one variant");
        
        Variant initialVariant = initialProduct.getVariants().get(0);
        logger.info("📊 Initial variant options:");
        logger.info("  - Color (option1): '{}'", initialVariant.getOption1());
        logger.info("  - Size (option2): '{}'", initialVariant.getOption2());
        logger.info("  - Material (option3): '{}'", initialVariant.getOption3());
        
        // Step 4: Modify feedItem fields to trigger variant option changes
        testItem.setWebWatchDial(testItem.getWebWatchDial() + " [UPDATED-TEST]");
        testItem.setWebWatchDiameter(testItem.getWebWatchDiameter() + " [UPDATED-TEST]");
        testItem.setWebMetalType(testItem.getWebMetalType() + " [UPDATED-TEST]");
        testItem.setWebWatchModel(testItem.getWebWatchModel() + " [UPDATED-TEST]");
        
        logger.info("🔄 Modified test item values:");
        logger.info("  - webWatchDial (Color): '{}'", testItem.getWebWatchDial());
        logger.info("  - webWatchDiameter (Size): '{}'", testItem.getWebWatchDiameter());
        logger.info("  - webMetalType (Material): '{}'", testItem.getWebMetalType());
        logger.info("  - webWatchModel (Metafield): '{}'", testItem.getWebWatchModel());
        
        // Step 5: Update the product (this should trigger remove-and-recreate options logic)
        syncService.publishItemToShopify(testItem);
        Thread.sleep(3000); // Wait for update propagation
        
        // Step 6: Verify the variant options were updated
        Product updatedProduct = shopifyApiService.getProductByProductId(testItem.getShopifyItemId());
        assertNotNull(updatedProduct, "Updated product should exist");
        assertNotNull(updatedProduct.getVariants(), "Updated product should have variants");
        assertFalse(updatedProduct.getVariants().isEmpty(), "Updated product should have at least one variant");
        
        Variant updatedVariant = updatedProduct.getVariants().get(0);
        logger.info("📊 Updated variant options:");
        logger.info("  - Color (option1): '{}'", updatedVariant.getOption1());
        logger.info("  - Size (option2): '{}'", updatedVariant.getOption2());
        logger.info("  - Material (option3): '{}'", updatedVariant.getOption3());
        
        // Step 7: Assert variant options were updated correctly
        assertEquals(testItem.getWebWatchDial(), updatedVariant.getOption1(), 
            "Color option should be updated to match webWatchDial");
        assertEquals(testItem.getWebWatchDiameter(), updatedVariant.getOption2(), 
            "Size option should be updated to match webWatchDiameter");
        assertEquals(testItem.getWebMetalType(), updatedVariant.getOption3(), 
            "Material option should be updated to match webMetalType");
        
        // Step 8: Verify metafields were also updated
        List<Metafield> metafields = updatedProduct.getMetafields();
        assertNotNull(metafields, "Product should have metafields");
        
        boolean modelMetafieldFound = false;
        for (Metafield metafield : metafields) {
            if ("model".equals(metafield.getKey())) {
                assertEquals(testItem.getWebWatchModel(), metafield.getValue(), 
                    "Model metafield should be updated");
                modelMetafieldFound = true;
                logger.info("✅ Model metafield updated correctly: '{}'", metafield.getValue());
                break;
            }
        }
        assertTrue(modelMetafieldFound, "Model metafield should exist and be updated");
        
        logger.info("✅ Variant options update test completed successfully!");
    }
} 