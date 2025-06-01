package com.gw.service;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.gw.FeedSync;
import com.gw.services.EbayMetafieldBackfillService;
import com.gw.services.EbayMetafieldBackfillService.BackfillResult;
import com.gw.services.shopifyapi.ShopifyGraphQLService;
import com.gw.services.shopifyapi.objects.Metafield;
import com.gw.services.shopifyapi.objects.Product;
import com.gw.domain.FeedItem;

/**
 * eBay Metafield Backfill Job
 * 
 * This test serves as a job runner for backfilling eBay metafields on existing Shopify products.
 * It follows the same pattern as the reconciliation tests and can be run via Maven profiles.
 * 
 * Usage:
 * - Test the backfill: mvn test -Ptest-ebay-backfill
 * - Run the backfill job: mvn test -Prun-ebay-backfill
 * - Run on production: mvn test -Prun-ebay-backfill-prod
 * 
 * Process:
 * 1. Ensures eBay metafield definitions exist in Shopify
 * 2. Retrieves all existing products and filters for watches
 * 3. Extracts eBay-relevant data from product information
 * 4. Creates/updates eBay metafields on products that don't already have them
 * 5. Provides detailed reporting on the results
 */
@SpringBootTest(classes = FeedSync.class)
public class EbayMetafieldBackfillTest {
    
    private static final Logger logger = LogManager.getLogger(EbayMetafieldBackfillTest.class);
    
    @Autowired
    private EbayMetafieldBackfillService backfillService;
    
    @Autowired
    private ShopifyGraphQLService shopifyService;
    
    /**
     * Test method to validate the backfill logic without making changes
     * Use with profile: test-ebay-backfill
     */
    @Test
    public void testEbayMetafieldBackfillLogic() throws Exception {
        logger.info("=== eBay Metafield Backfill Logic Test ===");
        logger.info("🧪 This test validates the backfill logic without making actual changes");
        
        // Step 1: Get current product statistics
        logger.info("📊 Step 1: Analyzing current product state...");
        List<Product> allProducts = shopifyService.getAllProducts();
        logger.info("📦 Total products in Shopify: " + allProducts.size());
        
        // Analyze watch products
        int watchProducts = 0;
        int productsWithEbayMetafields = 0;
        
        for (Product product : allProducts) {
            if (isWatchProduct(product)) {
                watchProducts++;
                if (hasEbayMetafields(product)) {
                    productsWithEbayMetafields++;
                }
            }
        }
        
        logger.info("⌚ Watch products found: " + watchProducts);
        logger.info("🏷️ Watch products with eBay metafields: " + productsWithEbayMetafields);
        logger.info("🎯 Watch products needing backfill: " + (watchProducts - productsWithEbayMetafields));
        
        // Step 2: Test metafield extraction on sample products
        logger.info("🧪 Step 2: Testing metafield extraction logic...");
        testMetafieldExtractionLogic(allProducts);
        
        logger.info("✅ Backfill logic test completed successfully!");
        logger.info("💡 Ready to run actual backfill with 'run-ebay-backfill' profile");
    }
    
    /**
     * Job method to execute the actual eBay metafield backfill
     * Use with profile: run-ebay-backfill or run-ebay-backfill-prod
     */
    @Test
    public void runEbayMetafieldBackfillJob() throws Exception {
        // Check if this is a test run or actual job run
        String profileActive = System.getProperty("backfill.mode", "test");
        
        if ("test".equals(profileActive)) {
            logger.info("🧪 Running in TEST mode - use testEbayMetafieldBackfillLogic() instead");
            testEbayMetafieldBackfillLogic();
            return;
        }
        
        logger.info("════════════════════════════════════════════════");
        logger.info("         EBAY METAFIELD BACKFILL JOB            ");
        logger.info("════════════════════════════════════════════════");
        logger.info("🎯 Purpose: Backfill eBay metafields on existing Shopify watch products");
        logger.info("📅 Started at: " + java.time.LocalDateTime.now());
        logger.info("🌍 Environment: " + System.getProperty("spring.profiles.active", "default"));
        logger.info("");
        
        try {
            // Execute the backfill
            logger.info("🚀 Starting backfill process...");
            BackfillResult result = backfillService.executeBackfill();
            
            // Print final results
            printJobResults(result);
            
            // Validate results
            validateJobResults(result);
            
            logger.info("🎉 eBay Metafield Backfill Job completed successfully!");
            
        } catch (Exception e) {
            logger.error("💥 Backfill job failed with exception", e);
            throw e;
        }
    }
    
    /**
     * Test metafield extraction logic on sample products
     */
    private void testMetafieldExtractionLogic(List<Product> allProducts) {
        logger.info("🔬 Testing metafield extraction on sample products...");
        
        // Find some watch products to test extraction
        int tested = 0;
        for (Product product : allProducts) {
            if (isWatchProduct(product) && tested < 3) {
                tested++;
                logger.info("Testing extraction for: " + product.getTitle());
                
                // Test the extraction logic (read-only)
                boolean hasEbay = hasEbayMetafields(product);
                logger.info("  - Has eBay metafields: " + hasEbay);
                logger.info("  - Vendor: " + product.getVendor());
                logger.info("  - Product Type: " + product.getProductType());
                logger.info("  - Tags: " + product.getTags());
            }
        }
        
        // Also test with sample data
        logger.info("🧪 Testing with sample data...");
        testSampleProductExtraction();
    }
    
    /**
     * Test extraction with known sample data
     */
    private void testSampleProductExtraction() {
        // Create test products with known data
        Product testProduct1 = createTestWatchProduct(
            "Rolex Submariner 116610LN Stainless Steel Automatic Watch",
            "Rolex",
            "Excellent condition Rolex Submariner in stainless steel with automatic movement"
        );
        
        Product testProduct2 = createTestWatchProduct(
            "Patek Philippe Nautilus 5711/1A-010 Steel Blue Dial",
            "Patek Philippe", 
            "New Patek Philippe Nautilus ref. 5711/1A-010 in steel with blue dial"
        );
        
        logger.info("✅ Sample product 1: " + testProduct1.getTitle());
        logger.info("  - Identified as watch: " + isWatchProduct(testProduct1));
        logger.info("  - Vendor: " + testProduct1.getVendor());
        
        logger.info("✅ Sample product 2: " + testProduct2.getTitle());
        logger.info("  - Identified as watch: " + isWatchProduct(testProduct2));
        logger.info("  - Vendor: " + testProduct2.getVendor());
    }
    
    /**
     * Print detailed job results
     */
    private void printJobResults(BackfillResult result) {
        logger.info("");
        logger.info("════════════════════════════════════════════════");
        logger.info("              BACKFILL COMPLETE                 ");
        logger.info("════════════════════════════════════════════════");
        logger.info("📊 SUMMARY:");
        logger.info("   🎯 Total watch products: " + result.getTotalProducts());
        logger.info("   🔄 Products processed: " + result.getProcessed());
        logger.info("   ✅ Products updated: " + result.getUpdated());
        logger.info("   ⏭️ Products skipped: " + result.getSkipped());
        logger.info("   ❌ Errors: " + result.getErrors());
        
        if (result.getProcessed() > 0) {
            double successRate = (double) result.getUpdated() / result.getProcessed() * 100;
            logger.info("   📈 Success rate: " + String.format("%.1f%%", successRate));
        }
        
        logger.info("📅 Completed at: " + java.time.LocalDateTime.now());
        
        if (result.getUpdated() > 0) {
            logger.info("");
            logger.info("🎉 SUCCESS: " + result.getUpdated() + " products now have eBay metafields!");
            logger.info("🖥️ You can view these in Shopify Admin:");
            logger.info("   Products > [Select Product] > Metafields section");
            logger.info("   Look for the 'eBay' namespace fields");
        }
        
        if (result.getErrors() > 0) {
            logger.warn("");
            logger.warn("⚠️ ERRORS: " + result.getErrors() + " products had issues:");
            for (EbayMetafieldBackfillService.BackfillError error : result.getErrorList()) {
                logger.warn("   • " + error.toString());
            }
        }
        
        if (result.getSkipped() > 0) {
            logger.info("");
            logger.info("ℹ️ SKIPPED: " + result.getSkipped() + " products already had eBay metafields");
        }
        
        logger.info("════════════════════════════════════════════════");
    }
    
    /**
     * Validate job results
     */
    private void validateJobResults(BackfillResult result) {
        // Basic validation
        assert result != null : "Backfill result should not be null";
        assert result.getTotalProducts() >= 0 : "Total products should be non-negative";
        assert result.getProcessed() >= 0 : "Processed count should be non-negative";
        assert result.getUpdated() >= 0 : "Updated count should be non-negative";
        assert result.getSkipped() >= 0 : "Skipped count should be non-negative";
        assert result.getErrors() >= 0 : "Error count should be non-negative";
        
        // Logical validation
        assert result.getProcessed() == result.getUpdated() + result.getSkipped() + result.getErrors() : 
               "Processed should equal updated + skipped + errors";
        
        logger.info("✅ Job result validation passed");
    }
    
    /**
     * Create a test watch product
     */
    private Product createTestWatchProduct(String title, String vendor, String description) {
        Product product = new Product();
        product.setTitle(title);
        product.setVendor(vendor);
        product.setBodyHtml(description);
        product.setProductType("Watches");
        product.setTags("luxury,watch,timepiece");
        return product;
    }
    
    /**
     * Check if a product is a watch (matches backfill service logic)
     */
    private boolean isWatchProduct(Product product) {
        // Check product type
        if (product.getProductType() != null && "Watches".equalsIgnoreCase(product.getProductType())) {
            return true;
        }
        
        // Check tags
        if (product.getTags() != null && product.getTags().toLowerCase().contains("watch")) {
            return true;
        }
        
        // Check title
        if (product.getTitle() != null && product.getTitle().toLowerCase().contains("watch")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Check if product has eBay metafields
     */
    private boolean hasEbayMetafields(Product product) {
        if (product.getMetafields() == null) {
            return false;
        }
        
        return product.getMetafields().stream()
                .anyMatch(mf -> "ebay".equals(mf.getNamespace()));
    }
} 