package com.gw.services.product;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.gw.domain.FeedItem;
import com.gw.services.FreeMakerService;
import com.gw.services.constants.ShopifyConstants;
import com.gw.services.shopifyapi.ShopifyGraphQLService;
import com.gw.services.shopifyapi.objects.Image;
import com.gw.services.shopifyapi.objects.Product;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Unified Product Creation Pipeline
 * 
 * Consolidated functionality from former IShopifyProductFactory, BaseShopifyProductFactory, and ProductCreationPipeline.
 * Handles complete product creation workflow:
 * 1. Product template building with metafields, variants, and images
 * 2. Complex 3-step creation process (basic product → options → images)
 * 3. Reusable across different product types
 * 
 * Benefits:
 * - Single responsibility for all product creation
 * - Eliminates unnecessary factory interface/implementation indirection
 * - Clean separation of concerns with specialized services
 * - Testable individual steps with consistent error handling
 * - Extensible for different product types via inheritance
 */
@Component
public class ProductCreationService {
    
    private static final Logger logger = LoggerFactory.getLogger(ProductCreationService.class);
    
    @Autowired
    private FreeMakerService freeMakerService;
    
    @Autowired 
    private VariantService variantService;
    
    @Autowired
    private MetadataService metadataService;
    
    @Autowired
    private ProductImageService productImageService;
    
    @Autowired
    private ShopifyGraphQLService shopifyGraphQLService;
    
    /**
     * Create a product template with all metadata, variants, and images
     * This is the base method that can be overridden by subclasses for specific product types
     * 
     * @param feedItem The feed item with source data
     * @return Product template ready for creation
     * @throws Exception if template building fails
     */
    public Product createProduct(FeedItem feedItem) throws Exception {
        logger.debug("🏗️ Building product template for SKU: {}", feedItem.getWebTagNumber());
        
        Product product = new Product();
        
        // Set basic product information
        setBasicProductInfo(product, feedItem);
        
        // Use specialized services to build the product
        productImageService.setProductImages(product, feedItem);
        variantService.createDefaultVariant(product, feedItem, shopifyGraphQLService.getAllLocations());
        metadataService.setProductMetadata(product, feedItem);
        
        logger.debug("✅ Product template built for SKU: {} - {} variants, {} metafields", 
            feedItem.getWebTagNumber(), 
            product.getVariants() != null ? product.getVariants().size() : 0,
            product.getMetafields() != null ? product.getMetafields().size() : 0);
        
        return product;
    }
    
    /**
     * Creates product using clean 3-step pipeline approach
     * This is the main method for creating products with options and images
     * 
     * @param feedItem The feed item with source data
     * @return The fully created product with ID, options, and images
     * @throws Exception if product creation fails
     */
    public Product createProductWithOptions(FeedItem feedItem) throws Exception {
        logger.info("🚀 Creating product with options for SKU: {}", feedItem.getWebTagNumber());
        
        // Step 1: Build product template (calls createProduct which can be overridden)
        Product productTemplate = this.createProduct(feedItem);
        
        // Step 2: Execute the clean creation pipeline
        ProductCreationResult result = executeCreation(productTemplate, feedItem);
        
        // Step 3: Handle result
        if (result.isSuccess()) {
            logger.info("✅ Product creation completed successfully for SKU: {}", feedItem.getWebTagNumber());
            return result.getProduct();
        } else {
            logger.error("❌ Product creation failed for SKU: {} - {}", 
                feedItem.getWebTagNumber(), result.getError().getMessage());
            throw new RuntimeException("Product creation failed", result.getError());
        }
    }
    
    /**
     * Sets basic product information from feed item
     * Clean, focused method with proper error handling
     */
    private void setBasicProductInfo(Product product, FeedItem feedItem) {
        // Generate description using template service
        try {
            String bodyHtml = freeMakerService.generateFromTemplate(feedItem);
            product.setBodyHtml(bodyHtml);
        } catch (Exception e) {
            logger.warn("⚠️ Failed to generate template for SKU: {} - using empty description", 
                feedItem.getWebTagNumber());
            product.setBodyHtml(""); // Graceful fallback
        }
        
        // Set basic product fields
        product.setTitle(feedItem.getWebDescriptionShort());
        product.setVendor(feedItem.getWebDesigner());
        product.setProductType(feedItem.getWebCategory());
        product.setPublishedScope(ShopifyConstants.PUBLISHED_SCOPE_GLOBAL);
        
        logger.debug("📋 Basic product info set for SKU: {} - Title: '{}', Vendor: '{}'", 
            feedItem.getWebTagNumber(), 
            feedItem.getWebDescriptionShort(), 
            feedItem.getWebDesigner());
    }
    
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
        
        Product created = shopifyGraphQLService.addProduct(cleanTemplate);
        validateProductCreation(created, sku);
        
        logger.info("✅ Step 1: Basic product created - ID: {}", created.getId());
        return created;
    }
    
    /**
     * Step 2: Add variant options to the created product
     */
    private void addVariantOptions(String productId, FeedItem feedItem) {
        logger.info("🎛️ Step 2: Adding variant options to product ID: {}", productId);
        
        boolean optionsAdded = shopifyGraphQLService.createProductOptions(productId, feedItem);
        
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
            shopifyGraphQLService.addImagesToProduct(productId, images);
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
        
        Product complete = shopifyGraphQLService.getProductByProductId(productId);
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