package com.gw.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@ActiveProfiles("keystone-dev")
@TestPropertySource(properties = {"cron.schedule=0 0 0 29 2 ?"})
public class EbayMetafieldDefinitionsTest extends BaseGraphqlTest {

    @Test
    /**
     * Create eBay metafield definitions to make them visible in Shopify Admin
     * This test will remove existing definitions first, then create new ones
     */
    public void testCreateEbayMetafieldDefinitions() throws Exception {
        logger.info("=== Starting eBay Metafield Definitions Creation Test ===");
        
        // First remove any existing eBay metafield definitions
        logger.info("🧹 Removing any existing eBay metafield definitions...");
        shopifyApiService.removeEbayMetafieldDefinitions();
        
        // Create all eBay metafield definitions
        logger.info("🏗️ Creating eBay metafield definitions...");
        shopifyApiService.createEbayMetafieldDefinitions();
        
        logger.info("=== eBay Metafield Definitions Test Complete ===");
        logger.info("🎉 eBay metafields should now be visible in Shopify Admin!");
        logger.info("📍 To verify:");
        logger.info("  1. Go to your Shopify Admin");
        logger.info("  2. Navigate to Settings > Metafields");
        logger.info("  3. Look for 'ebay' namespace with 24+ field definitions");
        logger.info("  4. Or go to any product and scroll to the Metafields section");
    }
} 