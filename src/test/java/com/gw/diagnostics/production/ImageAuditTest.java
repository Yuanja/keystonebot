package com.gw.diagnostics.production;

import com.gw.domain.FeedItem;
import com.gw.services.FeedItemService;
import com.gw.services.ImageService;
import com.gw.services.shopifyapi.ShopifyGraphQLService;
import com.gw.services.shopifyapi.objects.Product;
import com.gw.services.shopifyapi.objects.Image;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Image Audit Test for Production
 * 
 * This test provides comprehensive image auditing and fixing capabilities:
 * 1. Report products with missing images compared to feed
 * 2. Identify specific missing images for each product
 * 3. Fix products by re-uploading all images
 * 4. Detailed logging and statistics
 * 
 * Usage:
 * 
 * 1. Audit all products and report missing images (read-only):
 *    mvn test -Dtest=ImageAuditTest#auditAllProductImages -Dspring.profiles.active=keystone-prod
 * 
 * 2. Fix all products with missing images:
 *    mvn test -Dtest=ImageAuditTest#fixAllMissingImages -Dspring.profiles.active=keystone-prod
 * 
 * 3. Fix specific product by web_tag_number:
 *    mvn test -Dtest=ImageAuditTest#fixSpecificProductImages -Dspring.profiles.active=keystone-prod -Dweb_tag_number=205053
 * 
 * 4. Dry run (show what would be fixed without actually fixing):
 *    mvn test -Dtest=ImageAuditTest#fixAllMissingImages -Dspring.profiles.active=keystone-prod -Ddry.run=true
 */
@SpringJUnitConfig
@SpringBootTest
@TestPropertySource(properties = {
    "cron.schedule=0 0 0 31 2 ?"  // Disable cron during image audit (Feb 31st never exists)
})
public class ImageAuditTest {
    
    private static final Logger logger = LogManager.getLogger(ImageAuditTest.class);
    
    @Autowired
    private ShopifyGraphQLService shopifyApiService;
    
    @Autowired
    private FeedItemService feedItemService;
    
    @Autowired
    private ImageService imageService;
    
    /**
     * Audit all products and report missing images (read-only)
     */
    @Test
    public void auditAllProductImages() throws Exception {
        logger.info("=== Starting Image Audit (Read-Only) ===");
        logger.info("📊 This will analyze all products and report missing images");
        
        // Get all products from Shopify
        logger.info("🛍️ Fetching all products from Shopify...");
        List<Product> allProducts = shopifyApiService.getAllProducts();
        logger.info("📊 Found {} products in Shopify", allProducts.size());
        
        // Get all feed items
        logger.info("📡 Fetching all feed items from database...");
        List<FeedItem> allFeedItems = feedItemService.findAll();
        logger.info("📊 Found {} items in database", allFeedItems.size());
        
        // Create lookup map for fast access
        Map<String, FeedItem> feedItemMap = allFeedItems.stream()
            .filter(item -> item.getWebTagNumber() != null)
            .collect(Collectors.toMap(FeedItem::getWebTagNumber, item -> item));
        
        // Analyze each product
        ImageAuditResult auditResult = analyzeProductImages(allProducts, feedItemMap);
        
        // Display results
        displayAuditResults(auditResult);
        
        logger.info("=== Image Audit Complete ===");
    }
    
    /**
     * Fix all products with missing images
     */
    @Test
    public void fixAllMissingImages() throws Exception {
        String dryRunParam = System.getProperty("dry.run");
        boolean isDryRun = dryRunParam != null && ("true".equalsIgnoreCase(dryRunParam) || "1".equals(dryRunParam));
        
        logger.info("=== Starting Image Fix for All Products ===");
        if (isDryRun) {
            logger.warn("🧪 DRY RUN MODE - No actual changes will be made");
        }
        
        // Get all products from Shopify
        logger.info("🛍️ Fetching all products from Shopify...");
        List<Product> allProducts = shopifyApiService.getAllProducts();
        logger.info("📊 Found {} products in Shopify", allProducts.size());
        
        // Get all feed items
        logger.info("📡 Fetching all feed items from database...");
        List<FeedItem> allFeedItems = feedItemService.findAll();
        logger.info("📊 Found {} items in database", allFeedItems.size());
        
        // Create lookup map
        Map<String, FeedItem> feedItemMap = allFeedItems.stream()
            .filter(item -> item.getWebTagNumber() != null)
            .collect(Collectors.toMap(FeedItem::getWebTagNumber, item -> item));
        
        // Analyze to find products needing fixes
        ImageAuditResult auditResult = analyzeProductImages(allProducts, feedItemMap);
        
        if (auditResult.productsWithMissingImages.isEmpty()) {
            logger.info("✅ No products found with missing images - all good!");
            return;
        }
        
        logger.info("📋 Found {} products with missing images", auditResult.productsWithMissingImages.size());
        
        if (isDryRun) {
            logger.info("🧪 DRY RUN: Would fix {} products", auditResult.productsWithMissingImages.size());
            displayAuditResults(auditResult);
            return;
        }
        
        // Fix all products with missing images
        fixProductImages(auditResult.productsWithMissingImages, feedItemMap);
        
        logger.info("=== Image Fix Complete ===");
    }
    
    /**
     * Fix specific product images by web_tag_number
     */
    @Test
    public void fixSpecificProductImages() throws Exception {
        String targetWebTagNumber = System.getProperty("web_tag_number");
        
        if (targetWebTagNumber == null || targetWebTagNumber.trim().isEmpty()) {
            throw new IllegalArgumentException(
                "❌ Missing required parameter: web_tag_number\n" +
                "Usage: mvn test -Dtest=ImageAuditTest#fixSpecificProductImages " +
                "-Dspring.profiles.active=keystone-prod -Dweb_tag_number=205053");
        }
        
        logger.info("=== Starting Image Fix for Specific Product ===");
        logger.info("🎯 Target web_tag_number: {}", targetWebTagNumber);
        
        // Get feed item
        FeedItem feedItem = feedItemService.findByWebTagNumber(targetWebTagNumber);
        if (feedItem == null) {
            throw new IllegalArgumentException("❌ Feed item not found: " + targetWebTagNumber);
        }
        
        if (feedItem.getShopifyItemId() == null || feedItem.getShopifyItemId().trim().isEmpty()) {
            throw new IllegalArgumentException("❌ Feed item has no Shopify ID: " + targetWebTagNumber);
        }
        
        // Get product from Shopify
        logger.info("🛍️ Fetching product from Shopify...");
        Product product = shopifyApiService.getProductByProductId(feedItem.getShopifyItemId());
        
        // Analyze images
        int expectedImageCount = getExpectedImageCount(feedItem);
        int actualImageCount = product.getImages() != null ? product.getImages().size() : 0;
        
        logger.info("📊 Image Analysis:");
        logger.info("  - Expected images: {}", expectedImageCount);
        logger.info("  - Actual images: {}", actualImageCount);
        logger.info("  - Missing images: {}", Math.max(0, expectedImageCount - actualImageCount));
        
        if (actualImageCount >= expectedImageCount) {
            logger.info("✅ Product already has all expected images - no fix needed!");
            return;
        }
        
        // Fix the product
        logger.info("🔧 Fixing images for product: {}", targetWebTagNumber);
        fixProductImage(product, feedItem);
        
        // Verify fix
        Product updatedProduct = shopifyApiService.getProductByProductId(feedItem.getShopifyItemId());
        int newImageCount = updatedProduct.getImages() != null ? updatedProduct.getImages().size() : 0;
        
        logger.info("✅ Image fix complete:");
        logger.info("  - Before: {} images", actualImageCount);
        logger.info("  - After: {} images", newImageCount);
        logger.info("  - Expected: {} images", expectedImageCount);
        
        if (newImageCount >= expectedImageCount) {
            logger.info("🎉 SUCCESS: Product now has all expected images!");
        } else {
            logger.warn("⚠️ WARNING: Product still missing {} images", expectedImageCount - newImageCount);
        }
    }
    
    /**
     * Analyze all products and identify missing images
     */
    private ImageAuditResult analyzeProductImages(List<Product> products, Map<String, FeedItem> feedItemMap) {
        logger.info("🔍 Analyzing product images...");
        
        ImageAuditResult result = new ImageAuditResult();
        
        for (Product product : products) {
            // Get SKU
            String sku = null;
            if (product.getVariants() != null && !product.getVariants().isEmpty()) {
                sku = product.getVariants().get(0).getSku();
            }
            
            if (sku == null) {
                result.productsWithoutSku++;
                continue;
            }
            
            // Get corresponding feed item
            FeedItem feedItem = feedItemMap.get(sku);
            if (feedItem == null) {
                result.productsWithoutFeedItem++;
                logger.debug("⚠️ No feed item found for SKU: {}", sku);
                continue;
            }
            
            result.productsAnalyzed++;
            
            // Count expected images from feed
            int expectedImageCount = getExpectedImageCount(feedItem);
            
            // Count actual images in Shopify
            int actualImageCount = product.getImages() != null ? product.getImages().size() : 0;
            
            result.totalExpectedImages += expectedImageCount;
            result.totalActualImages += actualImageCount;
            
            if (actualImageCount < expectedImageCount) {
                int missingCount = expectedImageCount - actualImageCount;
                result.productsWithMissingImages.add(new ProductImageIssue(
                    product, feedItem, expectedImageCount, actualImageCount, missingCount
                ));
                result.totalMissingImages += missingCount;
            } else if (actualImageCount == expectedImageCount) {
                result.productsWithCorrectImages++;
            } else {
                result.productsWithExtraImages++;
            }
        }
        
        return result;
    }
    
    /**
     * Count expected images from feed item
     */
    private int getExpectedImageCount(FeedItem feedItem) {
        int count = 0;
        if (feedItem.getWebImagePath1() != null) count++;
        if (feedItem.getWebImagePath2() != null) count++;
        if (feedItem.getWebImagePath3() != null) count++;
        if (feedItem.getWebImagePath4() != null) count++;
        if (feedItem.getWebImagePath5() != null) count++;
        if (feedItem.getWebImagePath6() != null) count++;
        if (feedItem.getWebImagePath7() != null) count++;
        if (feedItem.getWebImagePath8() != null) count++;
        if (feedItem.getWebImagePath9() != null) count++;
        return count;
    }
    
    /**
     * Display audit results
     */
    private void displayAuditResults(ImageAuditResult result) {
        logger.info("╔══════════════════════════════════════════════════════════════╗");
        logger.info("║           IMAGE AUDIT RESULTS                                ║");
        logger.info("╚══════════════════════════════════════════════════════════════╝");
        logger.info("");
        logger.info("📊 Overall Statistics:");
        logger.info("  - Products analyzed: {}", result.productsAnalyzed);
        logger.info("  - Products with correct images: {} ({:.1f}%)", 
            result.productsWithCorrectImages,
            result.productsAnalyzed > 0 ? (double) result.productsWithCorrectImages / result.productsAnalyzed * 100 : 0);
        logger.info("  - Products with missing images: {} ({:.1f}%)", 
            result.productsWithMissingImages.size(),
            result.productsAnalyzed > 0 ? (double) result.productsWithMissingImages.size() / result.productsAnalyzed * 100 : 0);
        logger.info("  - Products with extra images: {}", result.productsWithExtraImages);
        logger.info("");
        logger.info("🖼️ Image Statistics:");
        logger.info("  - Total expected images: {}", result.totalExpectedImages);
        logger.info("  - Total actual images: {}", result.totalActualImages);
        logger.info("  - Total missing images: {}", result.totalMissingImages);
        logger.info("  - Image completion rate: {:.1f}%", 
            result.totalExpectedImages > 0 ? (double) result.totalActualImages / result.totalExpectedImages * 100 : 0);
        logger.info("");
        
        if (result.productsWithoutSku > 0) {
            logger.warn("⚠️ Products without SKU: {}", result.productsWithoutSku);
        }
        if (result.productsWithoutFeedItem > 0) {
            logger.warn("⚠️ Products without feed item: {}", result.productsWithoutFeedItem);
        }
        
        if (!result.productsWithMissingImages.isEmpty()) {
            logger.info("");
            logger.info("❌ Products with Missing Images (Top 20):");
            logger.info("┌────────────┬─────────┬────────┬─────────┐");
            logger.info("│ SKU        │ Expected│ Actual │ Missing │");
            logger.info("├────────────┼─────────┼────────┼─────────┤");
            
            result.productsWithMissingImages.stream()
                .sorted((a, b) -> Integer.compare(b.missingCount, a.missingCount))
                .limit(20)
                .forEach(issue -> {
                    logger.info("│ {:10} │ {:7} │ {:6} │ {:7} │",
                        issue.feedItem.getWebTagNumber(),
                        issue.expectedCount,
                        issue.actualCount,
                        issue.missingCount);
                });
            
            logger.info("└────────────┴─────────┴────────┴─────────┘");
            
            if (result.productsWithMissingImages.size() > 20) {
                logger.info("... and {} more products with missing images", 
                    result.productsWithMissingImages.size() - 20);
            }
        }
    }
    
    /**
     * Fix images for multiple products
     */
    private void fixProductImages(List<ProductImageIssue> issues, Map<String, FeedItem> feedItemMap) throws Exception {
        logger.info("🔧 Fixing images for {} products...", issues.size());
        
        int totalFixed = 0;
        int totalFailed = 0;
        
        // Sort by missing count (worst first)
        List<ProductImageIssue> sortedIssues = issues.stream()
            .sorted((a, b) -> Integer.compare(b.missingCount, a.missingCount))
            .collect(Collectors.toList());
        
        for (int i = 0; i < sortedIssues.size(); i++) {
            ProductImageIssue issue = sortedIssues.get(i);
            
            logger.info("🔧 [{}/{}] Fixing SKU: {} (missing {} images)", 
                i + 1, sortedIssues.size(), issue.feedItem.getWebTagNumber(), issue.missingCount);
            
            try {
                fixProductImage(issue.product, issue.feedItem);
                totalFixed++;
                logger.info("✅ Fixed: {}", issue.feedItem.getWebTagNumber());
            } catch (Exception e) {
                totalFailed++;
                logger.error("❌ Failed to fix {}: {}", issue.feedItem.getWebTagNumber(), e.getMessage());
            }
            
            // Small delay to avoid overwhelming the API
            if (i < sortedIssues.size() - 1) {
                Thread.sleep(500);
            }
        }
        
        logger.info("📊 Fix Results:");
        logger.info("  - Total products processed: {}", sortedIssues.size());
        logger.info("  - Successfully fixed: {}", totalFixed);
        logger.info("  - Failed: {}", totalFailed);
        logger.info("  - Success rate: {:.1f}%", 
            sortedIssues.size() > 0 ? (double) totalFixed / sortedIssues.size() * 100 : 0);
    }
    
    /**
     * Fix images for a single product
     */
    private void fixProductImage(Product product, FeedItem feedItem) throws Exception {
        logger.debug("🔧 Fixing images for product ID: {}, SKU: {}", 
            product.getId(), feedItem.getWebTagNumber());
        
        // Step 1: Download images (if not skipped)
        ImageService.ImageProcessingResult downloadResult = 
            imageService.handleImageProcessing(feedItem, feedItem.getWebTagNumber());
        
        if (!downloadResult.isSkipped() && !downloadResult.isSuccess()) {
            logger.warn("⚠️ Image download failed for {}, continuing with external URLs", 
                feedItem.getWebTagNumber());
        }
        
        // Step 2: Get external image URLs
        String[] imageUrls = imageService.getAvailableExternalImagePathByCSS(feedItem);
        if (imageUrls == null || imageUrls.length == 0) {
            throw new Exception("No image URLs available for SKU: " + feedItem.getWebTagNumber());
        }
        
        // Step 3: Delete all existing images
        logger.debug("🗑️ Deleting existing images for product: {}", product.getId());
        shopifyApiService.deleteAllImageByProductId(product.getId());
        
        // Step 4: Create new images
        List<Image> images = createImages(imageUrls, feedItem);
        
        // Step 5: Upload new images
        if (!images.isEmpty()) {
            logger.debug("📤 Uploading {} images for product: {}", images.size(), product.getId());
            shopifyApiService.addImagesToProduct(product.getId(), images);
        }
    }
    
    /**
     * Create Image objects from URLs
     */
    private List<Image> createImages(String[] urls, FeedItem feedItem) {
        return Arrays.stream(urls)
            .filter(url -> url != null && !url.trim().isEmpty())
            .map(url -> {
                Image image = new Image();
                image.setSrc(imageService.getCorrectedImageUrl(url));
                if (feedItem.getWebDescriptionShort() != null) {
                    image.addAltTag(feedItem.getWebDescriptionShort());
                }
                return image;
            })
            .collect(Collectors.toList());
    }
    
    /**
     * Result class for image audit
     */
    private static class ImageAuditResult {
        int productsAnalyzed = 0;
        int productsWithCorrectImages = 0;
        int productsWithExtraImages = 0;
        int productsWithoutSku = 0;
        int productsWithoutFeedItem = 0;
        int totalExpectedImages = 0;
        int totalActualImages = 0;
        int totalMissingImages = 0;
        List<ProductImageIssue> productsWithMissingImages = new ArrayList<>();
    }
    
    /**
     * Class representing a product with image issues
     */
    private static class ProductImageIssue {
        final Product product;
        final FeedItem feedItem;
        final int expectedCount;
        final int actualCount;
        final int missingCount;
        
        ProductImageIssue(Product product, FeedItem feedItem, int expectedCount, int actualCount, int missingCount) {
            this.product = product;
            this.feedItem = feedItem;
            this.expectedCount = expectedCount;
            this.actualCount = actualCount;
            this.missingCount = missingCount;
        }
    }
}

