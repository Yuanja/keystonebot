package com.gw.services.product;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.gw.domain.FeedItem;
import com.gw.services.FreeMakerService;
import com.gw.services.constants.ShopifyConstants;
import com.gw.services.shopifyapi.ShopifyGraphQLService;
import com.gw.services.shopifyapi.objects.Product;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Unified Product Creation Service
 * 
 * Consolidated functionality from former IShopifyProductFactory, BaseShopifyProductFactory, and ProductCreationPipeline.
 * Handles product structure creation workflow:
 * 1. Product template building with metafields and variants
 * 2. Clean 2-step creation process (basic product → options)
 * 3. Images handled separately by ImageService to avoid duplication
 * 
 * Benefits:
 * - Single responsibility for product structure creation
 * - Eliminates unnecessary factory interface/implementation indirection
 * - Clean separation: ProductCreationService handles structure, ImageService handles images
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
     * Create a product template for UPDATES with correct inventory based on feedItem
     * The newVariant will have correct inventory levels, mergeVariants preserves inventory IDs
     * 
     * @param feedItem The feed item with source data (source of truth for inventory)
     * @return Product template ready for update with correct inventory levels
     * @throws Exception if template building fails
     */
    public Product createProductForUpdate(FeedItem feedItem) throws Exception {
        logger.debug("🔄 Building update product template for SKU: {} with correct inventory", feedItem.getWebTagNumber());
        
        Product product = new Product();
        
        // Set basic product information
        setBasicProductInfo(product, feedItem);
        
        // Use specialized services - create variant WITH correct inventory based on feedItem
        productImageService.setProductImages(product, feedItem);
        variantService.createDefaultVariant(product, feedItem, shopifyGraphQLService.getAllLocations());
        metadataService.setProductMetadata(product, feedItem);
        
        logger.debug("✅ Update product template built for SKU: {} - {} variants, {} metafields (with correct inventory)", 
            feedItem.getWebTagNumber(), 
            product.getVariants() != null ? product.getVariants().size() : 0,
            product.getMetafields() != null ? product.getMetafields().size() : 0);
        
        return product;
    }
    
    /**
     * Creates product using clean 2-step pipeline approach
     * This is the main method for creating product structure with options
     * Images are handled separately by ImageService to avoid duplication
     * 
     * @param feedItem The feed item with source data
     * @return The fully created product with ID and options (images added separately)
     * @throws Exception if product creation fails
     */
    public Product createProductWithOptions(FeedItem feedItem) throws Exception {
        logger.info("🚀 Creating product structure with options for SKU: {}", feedItem.getWebTagNumber());
        
        // Build product template (calls createProduct which can be overridden)
        Product productTemplate = this.createProduct(feedItem);
        
        // Execute the clean creation pipeline (structure only)
        ProductCreationResult result = executeCreation(productTemplate, feedItem);
        
        // Handle result
        if (result.isSuccess()) {
            logger.info("✅ Product structure creation completed successfully for SKU: {}", feedItem.getWebTagNumber());
            return result.getProduct();
        } else {
            logger.error("❌ Product structure creation failed for SKU: {} - {}", 
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
     * @param feedItem The source feed item for options
     * @return The fully created product with ID and options (images handled separately)
     */
    public ProductCreationResult executeCreation(Product productTemplate, FeedItem feedItem) {
        String sku = feedItem.getWebTagNumber();
        logger.info("🚀 Starting product creation pipeline for SKU: {}", sku);
        
        try {
            // Create basic product
            Product basicProduct = createBasicProduct(productTemplate, sku);
            
            // Add variant options
            addVariantOptions(basicProduct.getId(), feedItem);
            
            // Note: Images are handled separately by ImageService in the publish pipeline
            // This eliminates duplicate image uploads
            
            // Fetch final product with all components (except images)
            Product finalProduct = fetchCompleteProduct(basicProduct.getId());
            
            logger.info("✅ Product creation pipeline completed successfully for SKU: {}", sku);
            return ProductCreationResult.success(finalProduct);
            
        } catch (Exception e) {
            logger.error("❌ Product creation pipeline failed for SKU: {} - {}", sku, e.getMessage());
            return ProductCreationResult.failure(e);
        }
    }
    
    /**
     * Create basic product structure without options or images
     */
    private Product createBasicProduct(Product template, String sku) throws Exception {
        logger.info("📦 Creating basic product structure for SKU: {}", sku);
        
        // Clean template for basic creation
        Product cleanTemplate = createCleanTemplate(template);
        
        Product created = shopifyGraphQLService.addProduct(cleanTemplate);
        validateProductCreation(created, sku);
        
        logger.info("✅ Basic product created - ID: {}", created.getId());
        return created;
    }
    
    /**
     * Add variant options to the created product
     */
    private void addVariantOptions(String productId, FeedItem feedItem) {
        logger.info("🎛️ Adding variant options to product ID: {}", productId);
        
        boolean optionsAdded = shopifyGraphQLService.createProductOptions(productId, feedItem);
        
        if (optionsAdded) {
            logger.info("✅ Variant options added successfully");
        } else {
            logger.warn("⚠️ No variant options added (may not be needed)");
        }
    }
    
    /**
     * Fetch the complete product structure (images handled separately)
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
