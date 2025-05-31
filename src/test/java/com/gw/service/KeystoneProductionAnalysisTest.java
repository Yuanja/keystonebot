package com.gw.service;

import com.gw.services.shopifyapi.ShopifyGraphQLService;
import com.gw.services.shopifyapi.objects.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Keystone Production Analysis Test Suite - SHOPIFY PRODUCTION DATA
 * 
 * PURPOSE: Readonly analysis of production Shopify data to understand:
 * - Inventory levels structure and issues
 * - Images metadata and structure  
 * - Differences between REST API (old) and GraphQL API (new) data
 * - Product status and collection associations
 * 
 * IMPORTANT: This test suite is READONLY - it makes NO modifications to production data
 * This version uses production Shopify credentials with dev database for safety
 */
@SpringJUnitConfig
@SpringBootTest
@ActiveProfiles({"keystone-dev"}) // Use dev profile as base
@TestPropertySource(properties = {
    // Override with production Shopify credentials
    "SHOPIFY_REST_URL=https://max-abbott.myshopify.com",
    "SHOPIFY_AUTH_USER=74aebc7bea7d25e1725e8beb266cde35", 
    "SHOPIFY_AUTH_PASSWD=e281655a902aa09bad458ec0b7a0860e",
    // Keep dev database settings for safety
    "spring.datasource.url=jdbc:mysql://localhost:3306/keystone?allowPublicKeyRetrieval=true&serverTimezone=UTC&useSSL=false&createDatabaseIfNotExist=true",
    "spring.datasource.username=root",
    "spring.datasource.password=password",
    // Disable scheduling during tests
    "cron.schedule=0 0 0 29 2 ?"
})
public class KeystoneProductionAnalysisTest {
    private static Logger logger = LogManager.getLogger(KeystoneProductionAnalysisTest.class);

    @Autowired
    private ShopifyGraphQLService shopifyApiService;

    @Test
    /**
     * Test basic Shopify API connectivity with production credentials
     */
    public void testShopifyConnectivity() throws Exception {
        logger.info("=== SHOPIFY API CONNECTIVITY TEST ===");
        logger.info("🔧 Using PRODUCTION Shopify API with DEV database for safety");
        
        // Verify we can connect to Shopify
        try {
            List<CustomCollection> testCollections = shopifyApiService.getAllCustomCollections();
            logger.info("✅ Successfully connected to Shopify API - found " + testCollections.size() + " collections");
            
            List<Location> locations = shopifyApiService.getAllLocations();
            logger.info("✅ Successfully retrieved " + locations.size() + " locations");
            
            List<Product> products = shopifyApiService.getAllProducts();
            logger.info("✅ Successfully retrieved " + products.size() + " products");
            
        } catch (Exception e) {
            logger.error("❌ Failed to connect to Shopify API: " + e.getMessage());
            throw new RuntimeException("Cannot proceed without Shopify connectivity", e);
        }
        
        logger.info("=== SHOPIFY API CONNECTIVITY TEST COMPLETE ===");
    }

    @Test
    /**
     * Test and analyze custom collections in production
     */
    public void testCustomCollections() throws Exception {
        logger.info("=== CUSTOM COLLECTIONS ANALYSIS TEST ===");
        logger.info("⚠️ READONLY MODE - NO MODIFICATIONS WILL BE MADE");
        
        List<CustomCollection> collections = shopifyApiService.getAllCustomCollections();
        logger.info("Total collections in production: " + collections.size());
        
        if (collections.isEmpty()) {
            logger.warn("No collections found in production");
            return;
        }
        
        logger.info("--- COLLECTIONS DETAIL ---");
        for (int i = 0; i < Math.min(10, collections.size()); i++) {
            CustomCollection collection = collections.get(i);
            logger.info("Collection " + (i + 1) + ":");
            logger.info("  ID: " + collection.getId());
            logger.info("  Title: " + collection.getTitle());
            logger.info("  Handle: " + collection.getHandle());
            logger.info("  Published: " + collection.getPublished());
            logger.info("  Published At: " + collection.getPublishedAt());
            logger.info("  Sort Order: " + collection.getSortOrder());
            logger.info("  Updated At: " + collection.getUpdatedAt());
        }
        
        if (collections.size() > 10) {
            logger.info("... and " + (collections.size() - 10) + " more collections");
        }
        
        // Test collection associations for a sample product
        List<Product> products = shopifyApiService.getAllProducts();
        if (!products.isEmpty()) {
            Product sampleProduct = products.get(0);
            logger.info("--- COLLECTION ASSOCIATIONS TEST ---");
            logger.info("Testing collection associations for product: " + sampleProduct.getId());
            
            try {
                List<Collect> collects = shopifyApiService.getCollectsForProductId(sampleProduct.getId());
                logger.info("Product " + sampleProduct.getId() + " is associated with " + 
                    (collects != null ? collects.size() : "NULL") + " collections");
                
                if (collects != null && !collects.isEmpty()) {
                    for (int c = 0; c < Math.min(5, collects.size()); c++) {
                        Collect collect = collects.get(c);
                        logger.info("  Association " + (c + 1) + ":");
                        logger.info("    Collect ID: " + collect.getId());
                        logger.info("    Collection ID: " + collect.getCollectionId());
                        logger.info("    Position: " + collect.getPosition());
                        logger.info("    Sort Value: " + collect.getSortValue());
                    }
                }
            } catch (Exception e) {
                logger.error("❌ Failed to retrieve collection associations: " + e.getMessage());
            }
        }
        
        logger.info("=== CUSTOM COLLECTIONS ANALYSIS COMPLETE ===");
    }

    @Test
    /**
     * Test and analyze all products summary
     */
    public void testAllProductsSummary() throws Exception {
        logger.info("=== ALL PRODUCTS SUMMARY ANALYSIS TEST ===");
        logger.info("⚠️ READONLY MODE - NO MODIFICATIONS WILL BE MADE");
        
        List<Product> allProducts = shopifyApiService.getAllProducts();
        logger.info("Found " + allProducts.size() + " total products in production");
        
        if (allProducts.isEmpty()) {
            logger.info("No products found in production");
            return;
        }
        
        // Count products with/without images
        long productsWithImages = allProducts.stream()
            .filter(p -> p.getImages() != null && !p.getImages().isEmpty())
            .count();
        long productsWithoutImages = allProducts.size() - productsWithImages;
        
        // Count products with/without variants
        long productsWithVariants = allProducts.stream()
            .filter(p -> p.getVariants() != null && !p.getVariants().isEmpty())
            .count();
        long productsWithoutVariants = allProducts.size() - productsWithVariants;
        
        // Count by status
        Map<String, Long> statusCounts = allProducts.stream()
            .collect(Collectors.groupingBy(
                p -> p.getStatus() != null ? p.getStatus() : "NULL",
                Collectors.counting()
            ));
        
        // Count by vendor
        Map<String, Long> vendorCounts = allProducts.stream()
            .collect(Collectors.groupingBy(
                p -> p.getVendor() != null ? p.getVendor() : "NULL",
                Collectors.counting()
            ));
        
        // Count by product type
        Map<String, Long> typeCounts = allProducts.stream()
            .collect(Collectors.groupingBy(
                p -> p.getProductType() != null ? p.getProductType() : "NULL",
                Collectors.counting()
            ));
        
        logger.info("=== PRODUCTS SUMMARY ===");
        logger.info("Total products: " + allProducts.size());
        logger.info("Products with images: " + productsWithImages + " (" + String.format("%.1f%%", (double)productsWithImages / allProducts.size() * 100) + ")");
        logger.info("Products without images: " + productsWithoutImages + " (" + String.format("%.1f%%", (double)productsWithoutImages / allProducts.size() * 100) + ")");
        logger.info("Products with variants: " + productsWithVariants);
        logger.info("Products without variants: " + productsWithoutVariants);
        
        logger.info("--- PRODUCT STATUS DISTRIBUTION ---");
        statusCounts.forEach((status, count) -> 
            logger.info("  " + status + ": " + count + " products"));
        
        logger.info("--- VENDOR DISTRIBUTION ---");
        vendorCounts.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(10)
            .forEach(entry -> logger.info("  " + entry.getKey() + ": " + entry.getValue() + " products"));
        
        logger.info("--- PRODUCT TYPE DISTRIBUTION ---");
        typeCounts.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(10)
            .forEach(entry -> logger.info("  " + entry.getKey() + ": " + entry.getValue() + " products"));
        
        // Sample a few products for basic info
        logger.info("--- SAMPLE PRODUCTS ---");
        for (int i = 0; i < Math.min(5, allProducts.size()); i++) {
            Product product = allProducts.get(i);
            logger.info("Product " + (i + 1) + ":");
            logger.info("  ID: " + product.getId());
            logger.info("  Title: " + (product.getTitle() != null && product.getTitle().length() > 50 ? 
                product.getTitle().substring(0, 50) + "..." : product.getTitle()));
            logger.info("  Status: " + product.getStatus());
            logger.info("  Vendor: " + product.getVendor());
            logger.info("  Images: " + (product.getImages() != null ? product.getImages().size() : 0));
            logger.info("  Variants: " + (product.getVariants() != null ? product.getVariants().size() : 0));
        }
        
        logger.info("=== ALL PRODUCTS SUMMARY ANALYSIS COMPLETE ===");
    }

    @Test
    /**
     * Test and analyze images of existing products
     */
    public void testProductImages() throws Exception {
        logger.info("=== PRODUCT IMAGES ANALYSIS TEST ===");
        logger.info("⚠️ READONLY MODE - NO MODIFICATIONS WILL BE MADE");
        
        List<Product> allProducts = shopifyApiService.getAllProducts();
        if (allProducts.isEmpty()) {
            logger.info("No products found for image analysis");
            return;
        }
        
        // Find products with images for detailed analysis
        List<Product> productsWithImages = allProducts.stream()
            .filter(p -> p.getImages() != null && !p.getImages().isEmpty())
            .limit(3) // Analyze first 3 products with images
            .collect(Collectors.toList());
        
        if (productsWithImages.isEmpty()) {
            logger.warn("No products with images found!");
            return;
        }
        
        logger.info("Analyzing images for " + productsWithImages.size() + " products:");
        
        for (int p = 0; p < productsWithImages.size(); p++) {
            Product product = productsWithImages.get(p);
            logger.info("\n--- PRODUCT " + (p + 1) + " IMAGES ANALYSIS ---");
            logger.info("Product ID: " + product.getId());
            logger.info("Title: " + (product.getTitle() != null && product.getTitle().length() > 30 ? 
                product.getTitle().substring(0, 30) + "..." : product.getTitle()));
            logger.info("Number of images: " + product.getImages().size());
            
            for (int img = 0; img < Math.min(5, product.getImages().size()); img++) {
                Image image = product.getImages().get(img);
                logger.info("  Image " + (img + 1) + ":");
                logger.info("    ID: " + image.getId());
                logger.info("    Product ID: " + image.getProductId());
                logger.info("    Position: " + image.getPosition());
                logger.info("    Created At: " + image.getCreatedAt());
                logger.info("    Updated At: " + image.getUpdatedAt());
                logger.info("    Src: " + (image.getSrc() != null && image.getSrc().length() > 60 ? 
                    image.getSrc().substring(0, 60) + "..." : image.getSrc()));
                
                // Check if image has variant IDs (linked to specific variants)
                if (image.getVariantIds() != null && image.getVariantIds().length > 0) {
                    logger.info("    Linked to variants: " + Arrays.toString(image.getVariantIds()));
                } else {
                    logger.info("    Not linked to specific variants");
                }
            }
            
            if (product.getImages().size() > 5) {
                logger.info("  ... and " + (product.getImages().size() - 5) + " more images");
            }
        }
        
        // Image statistics
        logger.info("\n--- IMAGE STATISTICS ---");
        int totalImages = allProducts.stream()
            .mapToInt(p -> p.getImages() != null ? p.getImages().size() : 0)
            .sum();
        
        double avgImagesPerProduct = allProducts.size() > 0 ? (double) totalImages / allProducts.size() : 0;
        
        logger.info("Total images across all products: " + totalImages);
        logger.info("Average images per product: " + String.format("%.2f", avgImagesPerProduct));
        
        // Find products without images
        long productsWithoutImages = allProducts.stream()
            .filter(p -> p.getImages() == null || p.getImages().isEmpty())
            .count();
        
        logger.info("Products without images: " + productsWithoutImages);
        
        if (productsWithoutImages > 0) {
            logger.info("--- SAMPLE PRODUCTS WITHOUT IMAGES ---");
            allProducts.stream()
                .filter(p -> p.getImages() == null || p.getImages().isEmpty())
                .limit(3)
                .forEach(p -> logger.info("  Product " + p.getId() + ": " + 
                    (p.getTitle() != null && p.getTitle().length() > 40 ? 
                    p.getTitle().substring(0, 40) + "..." : p.getTitle())));
        }
        
        logger.info("=== PRODUCT IMAGES ANALYSIS COMPLETE ===");
    }

    @Test
    /**
     * Test and analyze inventory levels of existing products
     */
    public void testProductInventory() throws Exception {
        logger.info("=== PRODUCT INVENTORY ANALYSIS TEST ===");
        logger.info("⚠️ READONLY MODE - NO MODIFICATIONS WILL BE MADE");
        
        List<Product> allProducts = shopifyApiService.getAllProducts();
        if (allProducts.isEmpty()) {
            logger.info("No products found for inventory analysis");
            return;
        }
        
        // Find products with variants for inventory analysis
        List<Product> productsWithVariants = allProducts.stream()
            .filter(p -> p.getVariants() != null && !p.getVariants().isEmpty())
            .limit(5) // Analyze first 5 products with variants
            .collect(Collectors.toList());
        
        if (productsWithVariants.isEmpty()) {
            logger.warn("No products with variants found!");
            return;
        }
        
        logger.info("Analyzing inventory for " + productsWithVariants.size() + " products:");
        
        int totalVariantsChecked = 0;
        int variantsWithInventoryLevels = 0;
        int variantsWithInventoryIssues = 0;
        
        for (int p = 0; p < productsWithVariants.size(); p++) {
            Product product = productsWithVariants.get(p);
            logger.info("\n--- PRODUCT " + (p + 1) + " INVENTORY ANALYSIS ---");
            logger.info("Product ID: " + product.getId());
            logger.info("Title: " + (product.getTitle() != null && product.getTitle().length() > 30 ? 
                product.getTitle().substring(0, 30) + "..." : product.getTitle()));
            logger.info("Number of variants: " + product.getVariants().size());
            
            for (int v = 0; v < product.getVariants().size(); v++) {
                Variant variant = product.getVariants().get(v);
                totalVariantsChecked++;
                
                logger.info("  Variant " + (v + 1) + ":");
                logger.info("    ID: " + variant.getId());
                logger.info("    SKU: " + variant.getSku());
                logger.info("    Price: " + variant.getPrice());
                logger.info("    Inventory Item ID: " + variant.getInventoryItemId());
                logger.info("    Inventory Management: " + variant.getInventoryManagement());
                logger.info("    Inventory Policy: " + variant.getInventoryPolicy());
                
                // CRITICAL: Analyze inventory levels for this variant
                if (variant.getInventoryItemId() != null) {
                    logger.info("    --- INVENTORY LEVELS DETAIL ---");
                    try {
                        List<InventoryLevel> inventoryLevels = shopifyApiService.getInventoryLevelByInventoryItemId(variant.getInventoryItemId());
                        logger.info("    Inventory Levels found: " + (inventoryLevels != null ? inventoryLevels.size() : "NULL"));
                        
                        if (inventoryLevels != null && !inventoryLevels.isEmpty()) {
                            variantsWithInventoryLevels++;
                            
                            for (int il = 0; il < inventoryLevels.size(); il++) {
                                InventoryLevel level = inventoryLevels.get(il);
                                logger.info("      Level " + (il + 1) + ":");
                                logger.info("        Inventory Item ID: " + level.getInventoryItemId());
                                logger.info("        Location ID: " + level.getLocationId());
                                logger.info("        Available: " + level.getAvailable() + " (type: " + 
                                    (level.getAvailable() != null ? level.getAvailable().getClass().getSimpleName() : "null") + ")");
                                logger.info("        Updated At: " + level.getUpdatedAt());
                            }
                        } else {
                            variantsWithInventoryIssues++;
                            logger.error("    ❌ INVENTORY LEVELS ARE NULL OR EMPTY for variant: " + variant.getSku());
                            logger.error("    This explains the NullPointerException in updates!");
                        }
                    } catch (Exception e) {
                        variantsWithInventoryIssues++;
                        logger.error("    ❌ FAILED to retrieve inventory levels for SKU: " + variant.getSku() + " - " + e.getMessage());
                    }
                } else {
                    logger.info("    No Inventory Item ID - skipping inventory levels analysis");
                }
            }
        }
        
        // Overall inventory analysis for all products
        logger.info("\n--- OVERALL INVENTORY ANALYSIS ---");
        logger.info("Checking inventory levels across ALL " + allProducts.size() + " products...");
        
        int allVariantsChecked = 0;
        int allVariantsWithInventoryLevels = 0;
        int allVariantsWithInventoryIssues = 0;
        
        for (Product product : allProducts) {
            if (product.getVariants() != null) {
                for (Variant variant : product.getVariants()) {
                    allVariantsChecked++;
                    if (variant.getInventoryItemId() != null) {
                        try {
                            List<InventoryLevel> levels = shopifyApiService.getInventoryLevelByInventoryItemId(variant.getInventoryItemId());
                            if (levels != null && !levels.isEmpty()) {
                                allVariantsWithInventoryLevels++;
                            } else {
                                allVariantsWithInventoryIssues++;
                                if (allVariantsWithInventoryIssues <= 5) { // Log first 5 problematic SKUs
                                    logger.error("❌ Inventory issue with SKU: " + variant.getSku() + " (Product: " + product.getId() + ")");
                                }
                            }
                        } catch (Exception e) {
                            allVariantsWithInventoryIssues++;
                            if (allVariantsWithInventoryIssues <= 5) { // Log first 5 problematic SKUs
                                logger.error("❌ Inventory exception with SKU: " + variant.getSku() + " - " + e.getMessage());
                            }
                        }
                    }
                }
            }
        }
        
        logger.info("=== INVENTORY LEVELS SUMMARY ===");
        logger.info("Sample analysis - Total variants checked: " + totalVariantsChecked);
        logger.info("Sample analysis - Variants with inventory levels: " + variantsWithInventoryLevels);
        logger.info("Sample analysis - Variants with inventory issues: " + variantsWithInventoryIssues);
        
        logger.info("Overall analysis - Total variants checked: " + allVariantsChecked);
        logger.info("Overall analysis - Variants with inventory levels: " + allVariantsWithInventoryLevels);
        logger.info("Overall analysis - Variants with inventory issues: " + allVariantsWithInventoryIssues);
        logger.info("Overall inventory success rate: " + 
            (allVariantsChecked > 0 ? String.format("%.1f%%", (double)allVariantsWithInventoryLevels / allVariantsChecked * 100) : "N/A"));
        
        if (allVariantsWithInventoryIssues > 0) {
            logger.error("❌ Found " + allVariantsWithInventoryIssues + " variants with inventory level issues");
            logger.error("❌ This explains the NullPointerException during updates!");
        } else {
            logger.info("✅ All variants have proper inventory levels");
        }
        
        // Check locations for inventory context
        logger.info("--- LOCATIONS ANALYSIS ---");
        List<Location> locations = shopifyApiService.getAllLocations();
        logger.info("Total locations available: " + locations.size());
        
        for (Location location : locations) {
            logger.info("  Location: " + location.getName() + " (ID: " + location.getId() + ") - Active: " + location.getActive());
        }
        
        logger.info("=== PRODUCT INVENTORY ANALYSIS COMPLETE ===");
    }

    @Test
    /**
     * Test GraphQL vs REST API compatibility by analyzing data structures
     */
    public void testGraphQLCompatibilityAnalysis() throws Exception {
        logger.info("=== GRAPHQL COMPATIBILITY ANALYSIS TEST ===");
        logger.info("⚠️ READONLY MODE - Testing GraphQL API data structures");
        
        // Get a small sample for detailed analysis
        List<Product> products = shopifyApiService.getAllProducts();
        if (products.isEmpty()) {
            logger.info("No products found for compatibility analysis");
            return;
        }
        
        Product sampleProduct = products.get(0);
        logger.info("Analyzing product: " + sampleProduct.getId() + " - " + sampleProduct.getTitle());
        
        // Test GraphQL data structure compatibility
        logger.info("--- GRAPHQL DATA STRUCTURE ANALYSIS ---");
        
        // Test variants structure
        if (sampleProduct.getVariants() != null && !sampleProduct.getVariants().isEmpty()) {
            Variant variant = sampleProduct.getVariants().get(0);
            logger.info("Variant structure:");
            logger.info("  ID: " + variant.getId() + " (type: " + (variant.getId() != null ? variant.getId().getClass().getSimpleName() : "null") + ")");
            logger.info("  SKU: " + variant.getSku());
            logger.info("  Price: " + variant.getPrice() + " (type: " + (variant.getPrice() != null ? variant.getPrice().getClass().getSimpleName() : "null") + ")");
            logger.info("  Inventory Item ID: " + variant.getInventoryItemId());
            
            // Test inventory levels structure (GraphQL returns List<InventoryLevel> vs REST InventoryLevels wrapper)
            if (variant.getInventoryItemId() != null) {
                try {
                    List<InventoryLevel> levels = shopifyApiService.getInventoryLevelByInventoryItemId(variant.getInventoryItemId());
                    logger.info("  Inventory Levels (GraphQL): " + (levels != null ? levels.getClass().getSimpleName() + " with " + levels.size() + " items" : "null"));
                    
                    if (levels != null && !levels.isEmpty()) {
                        InventoryLevel level = levels.get(0);
                        logger.info("    Sample level structure:");
                        logger.info("      Inventory Item ID: " + level.getInventoryItemId());
                        logger.info("      Location ID: " + level.getLocationId());
                        logger.info("      Available: " + level.getAvailable() + " (type: " + (level.getAvailable() != null ? level.getAvailable().getClass().getSimpleName() : "null") + ")");
                        logger.info("      Updated At: " + level.getUpdatedAt());
                    }
                } catch (Exception e) {
                    logger.error("❌ GraphQL inventory levels error: " + e.getMessage());
                }
            }
        }
        
        // Test images structure  
        if (sampleProduct.getImages() != null && !sampleProduct.getImages().isEmpty()) {
            Image image = sampleProduct.getImages().get(0);
            logger.info("Image structure:");
            logger.info("  ID: " + image.getId() + " (type: " + (image.getId() != null ? image.getId().getClass().getSimpleName() : "null") + ")");
            logger.info("  Product ID: " + image.getProductId());
            logger.info("  Position: " + image.getPosition());
            logger.info("  Src: " + (image.getSrc() != null && image.getSrc().length() > 30 ? image.getSrc().substring(0, 30) + "..." : image.getSrc()));
            logger.info("  Variant IDs: " + (image.getVariantIds() != null ? Arrays.toString(image.getVariantIds()) : "null"));
        }
        
        logger.info("=== GRAPHQL COMPATIBILITY ANALYSIS COMPLETE ===");
    }

    @Test
    /**
     * Simple readonly test to check total product count in production using GraphQL query
     */
    public void testProductCount() throws Exception {
        logger.info("=== PRODUCT COUNT TEST (using GraphQL query) ===");
        logger.info("⚠️ READONLY MODE - NO MODIFICATIONS WILL BE MADE");
        
        int totalProducts = 0;
        List<Product> allProducts = null;
        
        try {
            // Try to use the GraphQL method to get product count directly
            logger.info("Attempting GraphQL query to get product count...");
            totalProducts = shopifyApiService.getProductCount();
            logger.info("✅ GraphQL count successful: " + totalProducts + " products");
            
        } catch (Exception e) {
            logger.warn("⚠️ GraphQL count failed: " + e.getMessage());
            logger.info("Falling back to fetching all products to count them...");
            
            // Fallback: fetch all products and count them
            allProducts = shopifyApiService.getAllProducts();
            totalProducts = allProducts.size();
            logger.info("✅ Fallback count successful: " + totalProducts + " products");
        }
        
        logger.info("✅ Total products in production: " + totalProducts);
        
        if (totalProducts == 0) {
            logger.warn("⚠️ No products found in production!");
        } else if (totalProducts < 10) {
            logger.warn("⚠️ Low product count - only " + totalProducts + " products found");
        } else {
            logger.info("✅ Healthy product count detected");
        }
        
        try {
            // For status breakdown, we need to fetch product data if we haven't already
            if (allProducts == null) {
                logger.info("Getting product status breakdown...");
                allProducts = shopifyApiService.getAllProducts();
            }
            
            // Count by status using stream operations
            long activeProducts = allProducts.stream()
                .filter(p -> "active".equalsIgnoreCase(p.getStatus()))
                .count();
            long draftProducts = allProducts.stream()
                .filter(p -> "draft".equalsIgnoreCase(p.getStatus()))
                .count();
            long archivedProducts = allProducts.stream()
                .filter(p -> "archived".equalsIgnoreCase(p.getStatus()))
                .count();
            
            logger.info("--- PRODUCT STATUS BREAKDOWN ---");
            logger.info("Active products: " + activeProducts);
            logger.info("Draft products: " + draftProducts);
            logger.info("Archived products: " + archivedProducts);
            logger.info("Other status: " + (totalProducts - activeProducts - draftProducts - archivedProducts));
            
            // Validate count consistency
            if (allProducts.size() != totalProducts) {
                logger.warn("⚠️ Count mismatch: GraphQL count=" + totalProducts + ", fetched count=" + allProducts.size());
            } else {
                logger.info("✅ Count validation passed");
            }
            
        } catch (Exception e) {
            logger.error("❌ Failed to get product status breakdown: " + e.getMessage());
            // Don't re-throw here since we already have the count
        }
        
        logger.info("=== PRODUCT COUNT TEST COMPLETE ===");
    }

    @Test
    /**
     * Readonly test to check how many products have inventory level of 1 using optimized approach
     */
    public void testProductsWithInventoryOne() throws Exception {
        logger.info("=== PRODUCTS WITH INVENTORY = 1 TEST (optimized approach) ===");
        logger.info("⚠️ READONLY MODE - NO MODIFICATIONS WILL BE MADE");
        
        try {
            // First get the total count for progress tracking
            int totalProductCount = shopifyApiService.getProductCount();
            logger.info("Total products to check: " + totalProductCount);
            
            // Get products and check inventory in an optimized way
            List<Product> allProducts = shopifyApiService.getAllProducts();
            logger.info("Checking inventory levels for " + allProducts.size() + " products...");
            
            int productsWithInventoryOne = 0;
            int variantsWithInventoryOne = 0;
            int totalVariantsChecked = 0;
            int inventoryCheckErrors = 0;
            
            // Track some examples for logging
            List<String> exampleSkusWithInventoryOne = new ArrayList<>();
            
            // Process in smaller batches to be more efficient and provide progress updates
            int batchSize = 25; // Smaller batches for better progress reporting
            int totalBatches = (int) Math.ceil((double) allProducts.size() / batchSize);
            
            for (int i = 0; i < allProducts.size(); i += batchSize) {
                int endIndex = Math.min(i + batchSize, allProducts.size());
                List<Product> productBatch = allProducts.subList(i, endIndex);
                int currentBatch = (i / batchSize) + 1;
                
                logger.info("Processing batch " + currentBatch + "/" + totalBatches + " (" + productBatch.size() + " products)...");
                
                for (Product product : productBatch) {
                    if (product.getVariants() == null || product.getVariants().isEmpty()) {
                        continue;
                    }
                    
                    boolean productHasInventoryOne = false;
                    
                    for (Variant variant : product.getVariants()) {
                        totalVariantsChecked++;
                        
                        if (variant.getInventoryItemId() == null) {
                            continue;
                        }
                        
                        try {
                            List<InventoryLevel> inventoryLevels = shopifyApiService.getInventoryLevelByInventoryItemId(variant.getInventoryItemId());
                            
                            if (inventoryLevels != null && !inventoryLevels.isEmpty()) {
                                for (InventoryLevel level : inventoryLevels) {
                                    // Check if available inventory equals 1
                                    if (level.getAvailable() != null && level.getAvailable().equals(1)) {
                                        variantsWithInventoryOne++;
                                        productHasInventoryOne = true;
                                        
                                        // Collect examples (limit to first 10)
                                        if (exampleSkusWithInventoryOne.size() < 10) {
                                            exampleSkusWithInventoryOne.add(variant.getSku() + " (Product: " + product.getId() + ")");
                                        }
                                        break; // Found inventory = 1 for this variant
                                    }
                                }
                            }
                        } catch (Exception e) {
                            inventoryCheckErrors++;
                            if (inventoryCheckErrors <= 3) { // Log first 3 errors
                                logger.error("❌ Error checking inventory for SKU " + variant.getSku() + ": " + e.getMessage());
                            }
                        }
                    }
                    
                    if (productHasInventoryOne) {
                        productsWithInventoryOne++;
                    }
                }
                
                // Log progress every 5 batches or at the end
                if (currentBatch % 5 == 0 || currentBatch == totalBatches) {
                    double progressPercent = (double) currentBatch / totalBatches * 100;
                    logger.info("Progress: " + String.format("%.1f%%", progressPercent) + 
                        " - Found " + productsWithInventoryOne + " products with inventory=1 so far...");
                }
            }
            
            logger.info("=== INVENTORY = 1 ANALYSIS RESULTS ===");
            logger.info("Total products checked: " + allProducts.size());
            logger.info("Total variants checked: " + totalVariantsChecked);
            logger.info("Products with inventory = 1: " + productsWithInventoryOne);
            logger.info("Variants with inventory = 1: " + variantsWithInventoryOne);
            logger.info("Inventory check errors: " + inventoryCheckErrors);
            
            if (productsWithInventoryOne > 0) {
                double percentageProducts = (double) productsWithInventoryOne / allProducts.size() * 100;
                double percentageVariants = totalVariantsChecked > 0 ? (double) variantsWithInventoryOne / totalVariantsChecked * 100 : 0;
                
                logger.info("Percentage of products with inventory = 1: " + String.format("%.1f%%", percentageProducts));
                logger.info("Percentage of variants with inventory = 1: " + String.format("%.1f%%", percentageVariants));
                
                logger.info("--- EXAMPLE SKUS WITH INVENTORY = 1 ---");
                for (String example : exampleSkusWithInventoryOne) {
                    logger.info("  " + example);
                }
                if (variantsWithInventoryOne > exampleSkusWithInventoryOne.size()) {
                    logger.info("  ... and " + (variantsWithInventoryOne - exampleSkusWithInventoryOne.size()) + " more variants");
                }
            } else {
                logger.info("❌ No products found with inventory level = 1");
            }
            
            if (inventoryCheckErrors > 0) {
                logger.warn("⚠️ " + inventoryCheckErrors + " inventory check errors occurred");
                double errorRate = (double) inventoryCheckErrors / totalVariantsChecked * 100;
                logger.warn("⚠️ Inventory check error rate: " + String.format("%.1f%%", errorRate));
            } else {
                logger.info("✅ No inventory check errors");
            }
            
        } catch (Exception e) {
            logger.error("❌ Failed to check products with inventory = 1: " + e.getMessage());
            throw e;
        }
        
        logger.info("=== PRODUCTS WITH INVENTORY = 1 TEST COMPLETE ===");
    }

    @Test
    /**
     * Analyze the top 10 most recently added products from production
     */
    public void testRecentProducts() throws Exception {
        logger.info("=== TOP 10 RECENT PRODUCTS ANALYSIS TEST ===");
        logger.info("⚠️ READONLY MODE - NO MODIFICATIONS WILL BE MADE");
        
        try {
            // Get the 10 most recently added products
            logger.info("Fetching top 10 most recently added products...");
            List<Product> recentProducts = shopifyApiService.getRecentProducts(10);
            
            logger.info("✅ Retrieved " + recentProducts.size() + " recent products");
            
            if (recentProducts.isEmpty()) {
                logger.warn("⚠️ No recent products found!");
                return;
            }
            
            logger.info("=== RECENT PRODUCTS DETAILED ANALYSIS ===");
            
            for (int i = 0; i < recentProducts.size(); i++) {
                Product product = recentProducts.get(i);
                logger.info("\n--- PRODUCT " + (i + 1) + " (Most Recent) ---");
                logger.info("Product ID: " + product.getId());
                logger.info("Title: " + product.getTitle());
                logger.info("Handle: " + product.getHandle());
                logger.info("Status: " + product.getStatus());
                logger.info("Vendor: " + product.getVendor());
                logger.info("Product Type: " + product.getProductType());
                logger.info("Created At: " + product.getCreatedAt());
                logger.info("Updated At: " + product.getUpdatedAt());
                logger.info("Published At: " + product.getPublishedAt());
                
                // Tags analysis
                if (product.getTags() != null && !product.getTags().isEmpty()) {
                    logger.info("Tags: " + String.join(", ", product.getTags()));
                } else {
                    logger.info("Tags: None");
                }
                
                // Variants analysis
                if (product.getVariants() != null && !product.getVariants().isEmpty()) {
                    logger.info("--- VARIANTS ANALYSIS ---");
                    logger.info("Number of variants: " + product.getVariants().size());
                    
                    for (int v = 0; v < Math.min(3, product.getVariants().size()); v++) {
                        Variant variant = product.getVariants().get(v);
                        logger.info("  Variant " + (v + 1) + ":");
                        logger.info("    ID: " + variant.getId());
                        logger.info("    SKU: " + variant.getSku());
                        logger.info("    Price: " + variant.getPrice());
                        logger.info("    Inventory Item ID: " + variant.getInventoryItemId());
                        
                        // Check inventory for this variant
                        if (variant.getInventoryItemId() != null) {
                            try {
                                List<InventoryLevel> inventoryLevels = shopifyApiService.getInventoryLevelByInventoryItemId(variant.getInventoryItemId());
                                if (inventoryLevels != null && !inventoryLevels.isEmpty()) {
                                    for (InventoryLevel level : inventoryLevels) {
                                        logger.info("    Inventory - Location " + level.getLocationId() + ": " + level.getAvailable() + " available");
                                    }
                                } else {
                                    logger.warn("    ⚠️ No inventory levels found");
                                }
                            } catch (Exception e) {
                                logger.error("    ❌ Error checking inventory: " + e.getMessage());
                            }
                        }
                    }
                    
                    if (product.getVariants().size() > 3) {
                        logger.info("  ... and " + (product.getVariants().size() - 3) + " more variants");
                    }
                } else {
                    logger.info("--- NO VARIANTS FOUND ---");
                }
                
                // Images analysis
                if (product.getImages() != null && !product.getImages().isEmpty()) {
                    logger.info("--- IMAGES ANALYSIS ---");
                    logger.info("Number of images: " + product.getImages().size());
                    
                    for (int img = 0; img < Math.min(3, product.getImages().size()); img++) {
                        Image image = product.getImages().get(img);
                        logger.info("  Image " + (img + 1) + ":");
                        logger.info("    ID: " + image.getId());
                        logger.info("    Position: " + image.getPosition());
                        logger.info("    Src: " + (image.getSrc() != null && image.getSrc().length() > 60 ? 
                            image.getSrc().substring(0, 60) + "..." : image.getSrc()));
                    }
                    
                    if (product.getImages().size() > 3) {
                        logger.info("  ... and " + (product.getImages().size() - 3) + " more images");
                    }
                } else {
                    logger.info("--- NO IMAGES FOUND ---");
                }
                
                // Collection associations
                try {
                    List<Collect> collects = shopifyApiService.getCollectsForProductId(product.getId());
                    if (collects != null && !collects.isEmpty()) {
                        logger.info("--- COLLECTIONS ---");
                        logger.info("Associated with " + collects.size() + " collections");
                        for (int c = 0; c < Math.min(3, collects.size()); c++) {
                            Collect collect = collects.get(c);
                            logger.info("  Collection ID: " + collect.getCollectionId());
                        }
                        if (collects.size() > 3) {
                            logger.info("  ... and " + (collects.size() - 3) + " more collections");
                        }
                    } else {
                        logger.info("--- NOT ASSOCIATED WITH ANY COLLECTIONS ---");
                    }
                } catch (Exception e) {
                    logger.error("❌ Failed to get collection associations: " + e.getMessage());
                }
                
                logger.info("--- END PRODUCT " + (i + 1) + " ANALYSIS ---");
            }
            
            // Summary analysis
            logger.info("\n=== RECENT PRODUCTS SUMMARY ===");
            
            // Status distribution
            Map<String, Long> statusCounts = recentProducts.stream()
                .collect(Collectors.groupingBy(
                    p -> p.getStatus() != null ? p.getStatus() : "NULL",
                    Collectors.counting()
                ));
            
            logger.info("Status distribution:");
            statusCounts.forEach((status, count) -> 
                logger.info("  " + status + ": " + count + " products"));
            
            // Vendor distribution
            Map<String, Long> vendorCounts = recentProducts.stream()
                .collect(Collectors.groupingBy(
                    p -> p.getVendor() != null ? p.getVendor() : "NULL",
                    Collectors.counting()
                ));
            
            logger.info("Vendor distribution:");
            vendorCounts.forEach((vendor, count) -> 
                logger.info("  " + vendor + ": " + count + " products"));
            
            // Products with/without images
            long productsWithImages = recentProducts.stream()
                .filter(p -> p.getImages() != null && !p.getImages().isEmpty())
                .count();
            
            logger.info("Products with images: " + productsWithImages + "/" + recentProducts.size());
            logger.info("Products without images: " + (recentProducts.size() - productsWithImages) + "/" + recentProducts.size());
            
            // Products with/without variants
            long productsWithVariants = recentProducts.stream()
                .filter(p -> p.getVariants() != null && !p.getVariants().isEmpty())
                .count();
            
            logger.info("Products with variants: " + productsWithVariants + "/" + recentProducts.size());
            logger.info("Products without variants: " + (recentProducts.size() - productsWithVariants) + "/" + recentProducts.size());
            
        } catch (Exception e) {
            logger.error("❌ Failed to analyze recent products: " + e.getMessage());
            throw e;
        }
        
        logger.info("=== TOP 10 RECENT PRODUCTS ANALYSIS COMPLETE ===");
    }
} 