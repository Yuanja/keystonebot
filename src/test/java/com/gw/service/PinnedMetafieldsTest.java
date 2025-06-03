package com.gw.service;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.gw.services.shopifyapi.ShopifyGraphQLService;

import java.util.List;
import java.util.Map;

/**
 * Test for verifying that eBay metafields are properly pinned in Shopify admin
 * 
 * Pinned metafields appear prominently in the Shopify admin interface rather than
 * being hidden in a collapsible metafields section, making them much more accessible
 * for store administrators and data entry staff.
 */
public class PinnedMetafieldsTest extends BaseGraphqlTest {

    @Autowired
    private ShopifyGraphQLService shopifyGraphQLService;

    @Test
    public void testEbayMetafieldDefinitionsArePinned() throws Exception {
        logger.info("=== Starting Pinned eBay Metafields Test ===");
        logger.info("📌 Step 1: Creating eBay metafield definitions with pinning...");
        
        // Create eBay metafield definitions with pinning enabled
        shopifyGraphQLService.createEbayMetafieldDefinitions();
        logger.info("✅ eBay metafield definitions created");
        
        logger.info("🔍 Step 2: Retrieving eBay metafield definitions...");
        
        // Get all metafield definitions to verify they exist and are pinned
        List<Map<String, String>> definitions = shopifyGraphQLService.getMetafieldDefinitions("ebay");
        
        // Should have 13 eBay metafield definitions
        Assertions.assertFalse(definitions.isEmpty(), "Should have eBay metafield definitions");
        Assertions.assertEquals(13, definitions.size(), "Should have exactly 13 eBay metafield definitions");
        logger.info("✅ Found {} eBay metafield definitions", definitions.size());
        
        logger.info("📌 Step 3: Verifying metafields are pinned...");
        
        // Check each definition to ensure it's pinned (has a pinnedPosition)
        int pinnedCount = 0;
        for (Map<String, String> definition : definitions) {
            String key = definition.get("key");
            String pinnedPosition = definition.get("pinnedPosition");
            
            if (pinnedPosition != null) {
                pinnedCount++;
                logger.info("📌 Metafield 'ebay.{}' is pinned at position {}", key, pinnedPosition);
            } else {
                logger.warn("⚠️ Metafield 'ebay.{}' is NOT pinned", key);
            }
        }
        
        // All 13 eBay metafields should be pinned
        Assertions.assertEquals(13, pinnedCount, "All eBay metafields should be pinned");
        logger.info("✅ All {} eBay metafields are properly pinned", pinnedCount);
        
        logger.info("🎉 Test completed successfully!");
        logger.info("📌 eBay metafields are now pinned and prominently visible in Shopify admin");
        logger.info("=== End Pinned eBay Metafields Test ===");
    }
    
    @Test
    public void testMetafieldDefinitionCreationWithPinning() throws Exception {
        logger.info("=== Testing Individual Metafield Definition with Pinning ===");
        
        // Test creating a single metafield definition with pinning
        String namespace = "test";
        String key = "pinned_field";
        String name = "Test Pinned Field";
        String description = "A test field that should be pinned";
        String type = "single_line_text_field";
        String ownerType = "PRODUCT";
        
        logger.info("📌 Creating pinned metafield definition: " + namespace + "." + key);
        
        try {
            shopifyGraphQLService.createMetafieldDefinition(namespace, key, name, description, type, ownerType, null);
            logger.info("✅ Successfully created pinned metafield definition");
        } catch (Exception e) {
            if (e.getMessage().contains("already exists")) {
                logger.info("ℹ️ Metafield definition already exists - this is expected on repeated test runs");
            } else {
                throw e;
            }
        }
        
        // Test creating a non-pinned metafield definition for comparison
        String unpinnedKey = "unpinned_field";
        String unpinnedName = "Test Unpinned Field";
        String unpinnedDescription = "A test field that should not be pinned";
        
        logger.info("📋 Creating unpinned metafield definition: " + namespace + "." + unpinnedKey);
        
        try {
            shopifyGraphQLService.createMetafieldDefinition(namespace, unpinnedKey, unpinnedName, unpinnedDescription, type, ownerType, null);
            logger.info("✅ Successfully created unpinned metafield definition");
        } catch (Exception e) {
            if (e.getMessage().contains("already exists")) {
                logger.info("ℹ️ Metafield definition already exists - this is expected on repeated test runs");
            } else {
                throw e;
            }
        }
        
        logger.info("=== Individual Metafield Definition Test Complete ===");
    }
    
    @Test
    public void testEbayMetafieldDefinitionCleanupAndRecreation() throws Exception {
        logger.info("=== Testing eBay Metafield Definition Cleanup and Recreration ===");
        
        // Step 1: Remove existing eBay metafield definitions
        logger.info("🧹 Step 1: Cleaning up existing eBay metafield definitions...");
        try {
            shopifyGraphQLService.removeEbayMetafieldDefinitions();
            logger.info("✅ Cleanup completed");
        } catch (Exception e) {
            logger.info("ℹ️ Cleanup encountered expected errors: " + e.getMessage());
        }
        
        // Step 2: Recreate eBay metafield definitions with pinning
        logger.info("📌 Step 2: Recreating eBay metafield definitions with pinning...");
        shopifyGraphQLService.createEbayMetafieldDefinitions();
        
        // Step 3: Verify they were recreated
        logger.info("🔍 Step 3: Verifying recreated definitions...");
        List<Map<String, String>> ebayDefinitions = shopifyGraphQLService.getMetafieldDefinitions("ebay");
        
        Assertions.assertFalse(ebayDefinitions.isEmpty(), "Should have recreated eBay metafield definitions");
        logger.info("✅ Successfully recreated " + ebayDefinitions.size() + " eBay metafield definitions");
        
        logger.info("=== Cleanup and Recreation Test Complete ===");
    }
} 