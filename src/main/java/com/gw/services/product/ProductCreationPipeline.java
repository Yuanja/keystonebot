package com.gw.services.product;

import com.gw.domain.FeedItem;
import com.gw.services.shopifyapi.ShopifyGraphQLService;
import com.gw.services.shopifyapi.objects.Image;
import com.gw.services.shopifyapi.objects.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Reusable Product Creation Pipeline
 * 
 * Handles the complex 3-step product creation process:
 * 1. Create basic product structure
 * 2. Add variant options 
 * 3. Add images
 * 
 * Benefits:
 * - Clean separation of concerns
 * - Reusable across different product types
 * - Testable individual steps
 * - Consistent error handling
 * - Clear logging and progress tracking
 */
@Component
public class ProductCreationPipeline {
    
    private static final Logger logger = LoggerFactory.getLogger(ProductCreationPipeline.class);
    
    @Autowired
    private ShopifyGraphQLService shopifyService;
    
    /**
     * Execute the complete product creation pipeline
     * 
     * @param productTemplate The prepared product template
     * @param feedItem The source feed item for options/images
     * @return The fully created product with ID, options, and images
     */
    public ProductCreationResult executeCreation(Product productTemplate, FeedItem feedItem) {
        String sku = feedItem.getWebTagNumber();
        logger.info("🚀 Starting product creation pipeline for SKU: {}", sku);
        
        try {
            // Step 1: Create basic product
            Product basicProduct = createBasicProduct(productTemplate, sku);
            
            // Step 2: Add variant options
            addVariantOptions(basicProduct.getId(), feedItem);
            
            // Step 3: Add images
            addProductImages(basicProduct.getId(), productTemplate.getImages());
            
            // Fetch final product with all components
            Product finalProduct = fetchCompleteProduct(basicProduct.getId());
            
            logger.info("✅ Product creation pipeline completed successfully for SKU: {}", sku);
            return ProductCreationResult.success(finalProduct);
            
        } catch (Exception e) {
            logger.error("❌ Product creation pipeline failed for SKU: {} - {}", sku, e.getMessage());
            return ProductCreationResult.failure(e);
        }
    }
    
    /**
     * Step 1: Create basic product structure without options or images
     */
    private Product createBasicProduct(Product template, String sku) throws Exception {
        logger.info("📦 Step 1: Creating basic product structure for SKU: {}", sku);
        
        // Clean template for basic creation
        Product cleanTemplate = createCleanTemplate(template);
        
        Product created = shopifyService.addProduct(cleanTemplate);
        validateProductCreation(created, sku);
        
        logger.info("✅ Step 1: Basic product created - ID: {}", created.getId());
        return created;
    }
    
    /**
     * Step 2: Add variant options to the created product
     */
    private void addVariantOptions(String productId, FeedItem feedItem) {
        logger.info("🎛️ Step 2: Adding variant options to product ID: {}", productId);
        
        boolean optionsAdded = shopifyService.createProductOptions(productId, feedItem);
        
        if (optionsAdded) {
            logger.info("✅ Step 2: Variant options added successfully");
        } else {
            logger.warn("⚠️ Step 2: No variant options added (may not be needed)");
        }
    }
    
    /**
     * Step 3: Add images to the created product
     */
    private void addProductImages(String productId, List<Image> images) {
        if (images == null || images.isEmpty()) {
            logger.info("ℹ️ Step 3: No images to add for product ID: {}", productId);
            return;
        }
        
        logger.info("🖼️ Step 3: Adding {} images to product ID: {}", images.size(), productId);
        
        try {
            shopifyService.addImagesToProduct(productId, images);
            logger.info("✅ Step 3: {} images added successfully", images.size());
        } catch (Exception e) {
            logger.warn("⚠️ Step 3: Failed to add images - {}", e.getMessage());
            // Don't fail the entire creation for image issues
        }
    }
    
    /**
     * Fetch the complete product with all components
     */
    private Product fetchCompleteProduct(String productId) throws Exception {
        logger.debug("🔄 Fetching complete product structure for ID: {}", productId);
        
        Product complete = shopifyService.getProductByProductId(productId);
        if (complete == null) {
            throw new RuntimeException("Failed to fetch complete product after creation");
        }
        
        logProductSummary(complete);
        return complete;
    }
    
    /**
     * Create a clean template for basic product creation
     */
    private Product createCleanTemplate(Product template) {
        Product clean = new Product();
        
        // Copy only basic fields
        clean.setTitle(template.getTitle());
        clean.setBodyHtml(template.getBodyHtml());
        clean.setVendor(template.getVendor());
        clean.setProductType(template.getProductType());
        clean.setPublishedScope(template.getPublishedScope());
        clean.setTags(template.getTags());
        clean.setMetafields(template.getMetafields());
        clean.setVariants(template.getVariants());
        
        // Clean variants of option values (these are added in Step 2)
        if (clean.getVariants() != null) {
            clean.getVariants().forEach(variant -> {
                variant.setOption1(null);
                variant.setOption2(null);
                variant.setOption3(null);
            });
        }
        
        // Exclude images and options (added in separate steps)
        clean.setImages(null);
        clean.setOptions(null);
        
        return clean;
    }
    
    /**
     * Validate that product was created successfully
     */
    private void validateProductCreation(Product created, String sku) throws Exception {
        if (created == null || created.getId() == null) {
            throw new RuntimeException("Product creation failed - no product ID returned for SKU: " + sku);
        }
    }
    
    /**
     * Log summary of the complete product
     */
    private void logProductSummary(Product product) {
        logger.info("📊 Final Product Summary - ID: {}, Options: {}, Variants: {}, Metafields: {}, Images: {}", 
            product.getId(),
            product.getOptions() != null ? product.getOptions().size() : 0,
            product.getVariants() != null ? product.getVariants().size() : 0,
            product.getMetafields() != null ? product.getMetafields().size() : 0,
            product.getImages() != null ? product.getImages().size() : 0);
    }
    
    /**
     * Result wrapper for product creation operations
     */
    public static class ProductCreationResult {
        private final Product product;
        private final Exception error;
        private final boolean success;
        
        private ProductCreationResult(Product product, Exception error, boolean success) {
            this.product = product;
            this.error = error;
            this.success = success;
        }
        
        public static ProductCreationResult success(Product product) {
            return new ProductCreationResult(product, null, true);
        }
        
        public static ProductCreationResult failure(Exception error) {
            return new ProductCreationResult(null, error, false);
        }
        
        public boolean isSuccess() { return success; }
        public Product getProduct() { return product; }
        public Exception getError() { return error; }
    }
} 