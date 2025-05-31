package com.gw.service;

import com.gw.services.shopifyapi.ShopifyGraphQLService;
import com.gw.services.shopifyapi.objects.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Keystone Production Analysis Test Suite - SHOPIFY ONLY
 * 
 * PURPOSE: Readonly analysis of production Shopify data to understand:
 * - Inventory levels structure and issues
 * - Images metadata and structure  
 * - Differences between REST API (old) and GraphQL API (new) data
 * - Product status and collection associations
 * 
 * IMPORTANT: This test suite is READONLY - it makes NO modifications to production data
 * This version focuses only on Shopify API analysis without requiring database connectivity
 */
public class KeystoneProductionAnalysisTest {
    private static Logger logger = LogManager.getLogger(KeystoneProductionAnalysisTest.class);

    // Note: For this simplified version, you would manually initialize the ShopifyGraphQLService
    // with production credentials before running these methods
    private ShopifyGraphQLService shopifyApiService;

    /**
     * Manual setup method - call this before running tests with production Shopify credentials
     */
    public void setupProductionShopifyService(ShopifyGraphQLService service) {
        this.shopifyApiService = service;
    }

    @Test
    /**
     * READONLY Production Analysis Test - SHOPIFY ONLY
     * Examines existing production products (published via old REST API) to understand:
     * 1. Inventory levels structure and data
     * 2. Images structure and metadata
     * 3. Differences between REST API and GraphQL API data structures
     * 
     * NOTE: This test is READONLY - it makes NO modifications to production
     * NOTE: Requires manual setup of ShopifyGraphQLService with production credentials
     */
    public void testProductionAnalysisReadonly() throws Exception {
        if (shopifyApiService == null) {
            logger.warn("=== SKIPPING PRODUCTION ANALYSIS TEST ===");
            logger.warn("ShopifyGraphQLService not initialized. This test requires manual setup with production credentials.");
            logger.warn("To run this test, initialize the service with production Shopify API credentials first.");
            return;
        }

        logger.info("=== READONLY PRODUCTION SHOPIFY ANALYSIS TEST ===");
        logger.info("⚠️ READONLY MODE - NO MODIFICATIONS WILL BE MADE TO PRODUCTION");
        
        // Get all existing products from production
        logger.info("Step 1: Retrieving all products from production Shopify...");
        List<Product> allProducts = shopifyApiService.getAllProducts();
        logger.info("Found " + allProducts.size() + " total products in production");
        
        if (allProducts.isEmpty()) {
            logger.info("No products found in production - analysis complete");
            return;
        }
        
        // Analyze a sample of products (first 10 to avoid overwhelming logs)
        int sampleSize = Math.min(10, allProducts.size());
        logger.info("Step 2: Analyzing sample of " + sampleSize + " products for detailed inspection...");
        
        for (int i = 0; i < sampleSize; i++) {
            Product product = allProducts.get(i);
            logger.info("\n=== PRODUCT " + (i + 1) + " ANALYSIS ===");
            
            // Basic product information
            logger.info("Product ID: " + product.getId());
            logger.info("Title: " + product.getTitle());
            logger.info("Handle: " + product.getHandle());
            logger.info("Status: " + product.getStatus());
            logger.info("Vendor: " + product.getVendor());
            logger.info("Product Type: " + product.getProductType());
            logger.info("Created At: " + product.getCreatedAt());
            logger.info("Updated At: " + product.getUpdatedAt());
            logger.info("Published At: " + product.getPublishedAt());
            
            // Variants analysis
            if (product.getVariants() != null && !product.getVariants().isEmpty()) {
                logger.info("--- VARIANTS ANALYSIS ---");
                logger.info("Number of variants: " + product.getVariants().size());
                
                for (int v = 0; v < product.getVariants().size(); v++) {
                    Variant variant = product.getVariants().get(v);
                    logger.info("  Variant " + (v + 1) + ":");
                    logger.info("    ID: " + variant.getId());
                    logger.info("    SKU: " + variant.getSku());
                    logger.info("    Price: " + variant.getPrice());
                    logger.info("    Inventory Item ID: " + variant.getInventoryItemId());
                    logger.info("    Inventory Management: " + variant.getInventoryManagement());
                    logger.info("    Inventory Policy: " + variant.getInventoryPolicy());
                    
                    // CRITICAL: Analyze inventory levels for this variant
                    if (variant.getInventoryItemId() != null) {
                        logger.info("    --- INVENTORY LEVELS ANALYSIS ---");
                        try {
                            List<InventoryLevel> inventoryLevels = shopifyApiService.getInventoryLevelByInventoryItemId(variant.getInventoryItemId());
                            logger.info("    Inventory Levels found: " + (inventoryLevels != null ? inventoryLevels.size() : "NULL"));
                            
                            if (inventoryLevels != null && !inventoryLevels.isEmpty()) {
                                for (int il = 0; il < inventoryLevels.size(); il++) {
                                    InventoryLevel level = inventoryLevels.get(il);
                                    logger.info("      Level " + (il + 1) + ":");
                                    logger.info("        Inventory Item ID: " + level.getInventoryItemId());
                                    logger.info("        Location ID: " + level.getLocationId());
                                    logger.info("        Available: " + level.getAvailable());
                                    logger.info("        Updated At: " + level.getUpdatedAt());
                                }
                            } else {
                                logger.error("    ❌ INVENTORY LEVELS ARE NULL OR EMPTY for variant: " + variant.getSku());
                                logger.error("    This explains the NullPointerException in updates!");
                            }
                        } catch (Exception e) {
                            logger.error("    ❌ FAILED to retrieve inventory levels: " + e.getMessage());
                        }
                    } else {
                        logger.info("    No Inventory Item ID - skipping inventory analysis");
                    }
                }
            } else {
                logger.info("--- NO VARIANTS FOUND ---");
            }
            
            // Images analysis
            if (product.getImages() != null && !product.getImages().isEmpty()) {
                logger.info("--- IMAGES ANALYSIS ---");
                logger.info("Number of images: " + product.getImages().size());
                
                for (int img = 0; img < product.getImages().size(); img++) {
                    Image image = product.getImages().get(img);
                    logger.info("  Image " + (img + 1) + ":");
                    logger.info("    ID: " + image.getId());
                    logger.info("    Position: " + image.getPosition());
                    logger.info("    Created At: " + image.getCreatedAt());
                    logger.info("    Updated At: " + image.getUpdatedAt());
                    logger.info("    Src: " + image.getSrc());
                    
                    // Check if image has variant IDs (linked to specific variants)
                    if (image.getVariantIds() != null && image.getVariantIds().length > 0) {
                        logger.info("    Linked to variants: " + Arrays.toString(image.getVariantIds()));
                    } else {
                        logger.info("    Not linked to specific variants");
                    }
                }
            } else {
                logger.info("--- NO IMAGES FOUND ---");
                logger.info("❌ This product has no images - this might explain image count mismatches");
            }
            
            // Collection associations analysis
            try {
                List<Collect> collects = shopifyApiService.getCollectsForProductId(product.getId());
                logger.info("--- COLLECTIONS ANALYSIS ---");
                logger.info("Number of collection associations: " + (collects != null ? collects.size() : "NULL"));
                
                if (collects != null && !collects.isEmpty()) {
                    for (int c = 0; c < collects.size(); c++) {
                        Collect collect = collects.get(c);
                        logger.info("  Collection " + (c + 1) + ":");
                        logger.info("    Collect ID: " + collect.getId());
                        logger.info("    Product ID: " + collect.getProductId());
                        logger.info("    Collection ID: " + collect.getCollectionId());
                        logger.info("    Position: " + collect.getPosition());
                        logger.info("    Sort Value: " + collect.getSortValue());
                        logger.info("    Created At: " + collect.getCreatedAt());
                        logger.info("    Updated At: " + collect.getUpdatedAt());
                    }
                }
            } catch (Exception e) {
                logger.error("Failed to retrieve collection associations: " + e.getMessage());
            }
            
            logger.info("=== END PRODUCT " + (i + 1) + " ANALYSIS ===\n");
        }
        
        // Summary analysis
        logger.info("=== PRODUCTION ANALYSIS SUMMARY ===");
        
        // Count products with/without images
        long productsWithImages = allProducts.stream()
            .filter(p -> p.getImages() != null && !p.getImages().isEmpty())
            .count();
        long productsWithoutImages = allProducts.size() - productsWithImages;
        
        logger.info("Products with images: " + productsWithImages);
        logger.info("Products without images: " + productsWithoutImages);
        
        // Count products with/without variants
        long productsWithVariants = allProducts.stream()
            .filter(p -> p.getVariants() != null && !p.getVariants().isEmpty())
            .count();
        long productsWithoutVariants = allProducts.size() - productsWithVariants;
        
        logger.info("Products with variants: " + productsWithVariants);
        logger.info("Products without variants: " + productsWithoutVariants);
        
        // Count by status
        Map<String, Long> statusCounts = allProducts.stream()
            .collect(Collectors.groupingBy(
                p -> p.getStatus() != null ? p.getStatus() : "NULL",
                Collectors.counting()
            ));
        
        logger.info("Product status distribution:");
        statusCounts.forEach((status, count) -> 
            logger.info("  " + status + ": " + count + " products"));
        
        // Sample inventory analysis across all products
        logger.info("Step 3: Checking inventory levels across all products...");
        int productsWithInventoryIssues = 0;
        int totalVariantsChecked = 0;
        int variantsWithInventoryLevels = 0;
        
        for (Product product : allProducts) {
            if (product.getVariants() != null) {
                for (Variant variant : product.getVariants()) {
                    totalVariantsChecked++;
                    if (variant.getInventoryItemId() != null) {
                        try {
                            List<InventoryLevel> levels = shopifyApiService.getInventoryLevelByInventoryItemId(variant.getInventoryItemId());
                            if (levels != null && !levels.isEmpty()) {
                                variantsWithInventoryLevels++;
                            } else {
                                productsWithInventoryIssues++;
                            }
                        } catch (Exception e) {
                            productsWithInventoryIssues++;
                        }
                    }
                }
            }
        }
        
        logger.info("=== INVENTORY LEVELS SUMMARY ===");
        logger.info("Total variants checked: " + totalVariantsChecked);
        logger.info("Variants with inventory levels: " + variantsWithInventoryLevels);
        logger.info("Variants with inventory issues: " + productsWithInventoryIssues);
        logger.info("Inventory success rate: " + 
            (totalVariantsChecked > 0 ? String.format("%.1f%%", (double)variantsWithInventoryLevels / totalVariantsChecked * 100) : "N/A"));
        
        if (productsWithInventoryIssues > 0) {
            logger.error("❌ Found " + productsWithInventoryIssues + " variants with inventory level issues");
            logger.error("❌ This explains the NullPointerException during updates!");
        } else {
            logger.info("✅ All variants have proper inventory levels");
        }
        
        // Check collections
        logger.info("Step 4: Analyzing collections...");
        List<CustomCollection> collections = shopifyApiService.getAllCustomCollections();
        logger.info("Total collections in production: " + collections.size());
        
        for (CustomCollection collection : collections) {
            logger.info("Collection: " + collection.getTitle() + " (ID: " + collection.getId() + ")");
        }
        
        logger.info("=== READONLY PRODUCTION ANALYSIS COMPLETE ===");
        logger.info("⚠️ NO MODIFICATIONS WERE MADE TO PRODUCTION");
        logger.info("Key Findings:");
        logger.info("- Total products: " + allProducts.size());
        logger.info("- Products with images: " + productsWithImages + " (" + String.format("%.1f%%", (double)productsWithImages / allProducts.size() * 100) + ")");
        logger.info("- Products without images: " + productsWithoutImages + " (" + String.format("%.1f%%", (double)productsWithoutImages / allProducts.size() * 100) + ")");
        logger.info("- Variants with inventory issues: " + productsWithInventoryIssues + " out of " + totalVariantsChecked);
        logger.info("- Total collections: " + collections.size());
    }

    @Test
    /**
     * Analyze specific products that are known to have issues
     * This can be used to deep-dive into problematic products
     */
    public void testSpecificProductAnalysis() throws Exception {
        if (shopifyApiService == null) {
            logger.warn("=== SKIPPING SPECIFIC PRODUCT ANALYSIS TEST ===");
            logger.warn("ShopifyGraphQLService not initialized. This test requires manual setup with production credentials.");
            return;
        }

        logger.info("=== SPECIFIC PRODUCT ANALYSIS TEST ===");
        logger.info("⚠️ READONLY MODE - NO MODIFICATIONS WILL BE MADE TO PRODUCTION");
        
        // Example: Analyze products by SKU or product ID
        // This method can be customized to look at specific problematic products
        
        List<Product> allProducts = shopifyApiService.getAllProducts();
        logger.info("Found " + allProducts.size() + " total products in production");
        
        // Example: Find products with specific SKUs that might have issues
        String[] targetSkus = {"160883", "200738", "201414"}; // Add specific SKUs to analyze
        
        for (String targetSku : targetSkus) {
            logger.info("\n--- Searching for product with SKU: " + targetSku + " ---");
            
            Product foundProduct = allProducts.stream()
                .filter(p -> p.getVariants() != null)
                .filter(p -> p.getVariants().stream()
                    .anyMatch(v -> targetSku.equals(v.getSku())))
                .findFirst()
                .orElse(null);
            
            if (foundProduct != null) {
                logger.info("✅ Found product with SKU " + targetSku);
                logger.info("Product ID: " + foundProduct.getId());
                logger.info("Title: " + foundProduct.getTitle());
                logger.info("Status: " + foundProduct.getStatus());
                
                // Deep analysis of this specific product
                Variant targetVariant = foundProduct.getVariants().stream()
                    .filter(v -> targetSku.equals(v.getSku()))
                    .findFirst()
                    .orElse(null);
                
                if (targetVariant != null && targetVariant.getInventoryItemId() != null) {
                    try {
                        List<InventoryLevel> levels = shopifyApiService.getInventoryLevelByInventoryItemId(targetVariant.getInventoryItemId());
                        logger.info("Inventory levels for SKU " + targetSku + ": " + 
                            (levels != null ? levels.size() + " levels found" : "NULL"));
                        
                        if (levels != null && !levels.isEmpty()) {
                            for (InventoryLevel level : levels) {
                                logger.info("  Location " + level.getLocationId() + ": " + level.getAvailable() + " available");
                            }
                        }
                    } catch (Exception e) {
                        logger.error("❌ Failed to get inventory levels for SKU " + targetSku + ": " + e.getMessage());
                    }
                }
                
                // Image analysis for this product
                if (foundProduct.getImages() != null) {
                    logger.info("Images for SKU " + targetSku + ": " + foundProduct.getImages().size() + " images");
                } else {
                    logger.info("❌ No images found for SKU " + targetSku);
                }
                
            } else {
                logger.info("❌ Product with SKU " + targetSku + " not found in production");
            }
        }
        
        logger.info("=== SPECIFIC PRODUCT ANALYSIS COMPLETE ===");
    }
} 