package com.gw.services.sync;

import com.gw.domain.FeedItem;
import com.gw.services.product.ProductCreationService;
import com.gw.services.shopifyapi.ShopifyGraphQLService;
import com.gw.services.shopifyapi.objects.Product;
import com.gw.services.shopifyapi.objects.Image;
import com.gw.services.ImageService;
import com.gw.services.product.ProductMergeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.ArrayList;

/**
 * Reusable Product Update Pipeline
 * 
 * Handles the complete product update process with clean separation of concerns:
 * - Product validation and retrieval
 * - Image download management
 * - Product creation and merging
 * - Shopify API updates (basic product + variant options)
 * - Image updates and inventory management
 * - Collection associations
 * 
 * Benefits:
 * - Single Responsibility: Focused only on product updates
 * - Reusable: Can be used by different sync services
 * - Testable: Each step can be tested independently
 * - Clean error handling with detailed logging
 * - Consistent update workflow
 */
@Service
public class ProductUpdatePipeline {
    
    private static final Logger logger = LoggerFactory.getLogger(ProductUpdatePipeline.class);
    
    @Autowired
    private ShopifyGraphQLService shopifyGraphQLService;
    
    @Autowired
    private ImageService imageService;
    
    @Autowired
    @Qualifier("keyStoneShopifyProductFactoryService")
    private ProductCreationService shopifyProductFactoryService;
    
    @Autowired
    private CollectionManagementService collectionManagementService;
    
    @Autowired
    private InventoryManagementService inventoryManagementService;
    
    @Autowired
    private ProductMergeService productMergeService;
    
    /**
     * Execute the complete product update pipeline
     * 
     * @param item The feed item to update
     * @return UpdateResult with success status and details
     */
    public ProductUpdateResult executeUpdate(FeedItem item) {
        logger.info("🔄 Starting product update pipeline for SKU: {}", item.getWebTagNumber());
        
        try {
                    // Validate item for update
        validateItemForUpdate(item);
        
        // Retrieve existing product
            Product existingProduct = retrieveExistingProduct(item);
            
            // Handle image processing
            handleImageProcessing(item);
            
            // Create updated product and merge
            Product updatedProduct = createAndMergeUpdatedProduct(item, existingProduct);
            
            // Update product on Shopify
            updateProductOnShopify(updatedProduct, item);
            
            // Handle image updates
            handleImageUpdates(item, existingProduct);
            
            // Update inventory
            updateInventory(item, updatedProduct);
            
            // Update collection associations
            updateCollectionAssociations(item);
            
            logger.info("✅ Product update pipeline completed successfully for SKU: {}", item.getWebTagNumber());
            return ProductUpdateResult.success(updatedProduct);
            
        } catch (Exception e) {
            logger.error("❌ Product update pipeline failed for SKU: {} - {}", item.getWebTagNumber(), e.getMessage());
            return ProductUpdateResult.failure(e);
        }
    }
    
    /**
     * Validate that the item has required data for updating
     */
    private void validateItemForUpdate(FeedItem item) {
        logger.debug("📋 Validating item for update: {}", item.getWebTagNumber());
        
        if (item.getShopifyItemId() == null) {
            throw new RuntimeException("No Shopify Item Id found for Sku: " + item.getWebTagNumber());
        }
    }
    
    /**
     * Retrieve the existing product from Shopify with validation
     */
    private Product retrieveExistingProduct(FeedItem item) {
        logger.debug("🔍 Retrieving existing product for SKU: {}", item.getWebTagNumber());
        
        Product existingProduct = shopifyGraphQLService.getProductByProductId(item.getShopifyItemId());
        if (existingProduct == null) {
            throw new RuntimeException("No Shopify product found by the id: " + item.getShopifyItemId());
        }
        
        logger.debug("✅ Retrieved existing product ID: {}", existingProduct.getId());
        return existingProduct;
    }
    
    /**
     * Handle image processing using centralized service
     */
    private void handleImageProcessing(FeedItem item) {
        ImageService.ImageProcessingResult result = imageService.handleImageProcessing(item, item.getWebTagNumber());
        
        if (result.isSkipped()) {
            logger.debug("⏭️ Image processing skipped for SKU: {}", item.getWebTagNumber());
        } else if (!result.isSuccess()) {
            logger.warn("⚠️ Image processing failed for SKU: {} - {}", 
                item.getWebTagNumber(), result.getError().getMessage());
            // Continue without failing the whole update - images are not critical for updating
        }
    }
    
    /**
     * Create updated product and merge with existing product data
     */
    private Product createAndMergeUpdatedProduct(FeedItem item, Product existingProduct) throws Exception {
        logger.debug("🔧 Creating and merging updated product for SKU: {}", item.getWebTagNumber());
        
        // Build product structure with all metafields and options but don't send to Shopify
        Product updatedProduct = shopifyProductFactoryService.createProduct(item);
        
        // Set the existing product ID to ensure we update the correct product
        updatedProduct.setId(item.getShopifyItemId());
        
        // Merge existing product data with updated data
        productMergeService.mergeProducts(existingProduct, updatedProduct);
        
        logger.debug("✅ Product created and merged for ID: {}", updatedProduct.getId());
        return updatedProduct;
    }
    
    /**
     * Update the product on Shopify
     * Always updates basic product information, handles variant options separately
     */
    private void updateProductOnShopify(Product product, FeedItem item) throws Exception {
        logger.debug("💾 Updating product on Shopify ID: {}", product.getId());
        
        // Update basic product information (title, vendor, productType, description, metafields, etc.)
        Product basicUpdateProduct = createBasicUpdateProduct(product);
        shopifyGraphQLService.updateProduct(basicUpdateProduct);
        logger.debug("✅ Basic product information updated");
        
        // Handle variant options updates separately
        try {
            boolean variantOptionsUpdated = handleVariantOptionsUpdate(product, item);
            if (variantOptionsUpdated) {
                logger.debug("✅ Variant options also updated");
            }
        } catch (Exception e) {
            logger.warn("⚠️ Failed to update variant options (continuing): {}", e.getMessage());
            // Don't fail the entire update if variant options fail
        }
    }
    
    /**
     * Create a clean product object for basic updates that excludes variant/option information
     */
    private Product createBasicUpdateProduct(Product fullProduct) {
        Product basicProduct = new Product();
        
        // Copy only basic product fields, excluding variants and options
        basicProduct.setId(fullProduct.getId());
        basicProduct.setTitle(fullProduct.getTitle());
        basicProduct.setBodyHtml(fullProduct.getBodyHtml());
        basicProduct.setVendor(fullProduct.getVendor());
        basicProduct.setProductType(fullProduct.getProductType());
        basicProduct.setHandle(fullProduct.getHandle());
        basicProduct.setTags(fullProduct.getTags());
        basicProduct.setStatus(fullProduct.getStatus());
        basicProduct.setMetafields(fullProduct.getMetafields());
        
        // Explicitly exclude variants and options to avoid conflicts
        basicProduct.setVariants(null);
        basicProduct.setOptions(null);
        
        return basicProduct;
    }
    
    /**
     * Handle variant options updates by comparing existing options with new options
     */
    private boolean handleVariantOptionsUpdate(Product product, FeedItem item) {
        // Use the remove-and-recreate approach for updating variant options
        try {
            if (hasVariantOptions(item)) {
                logger.debug("🎛️ Updating variant options for product: {}", product.getId());
                return shopifyGraphQLService.updateProductOptions(product.getId(), item);
            }
            return false;
        } catch (Exception e) {
            logger.warn("Failed to update variant options: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Check if feed item has variant options
     */
    private boolean hasVariantOptions(FeedItem item) {
        String newColor = item.getWebWatchDial();
        String newSize = item.getWebWatchDiameter();
        String newMaterial = item.getWebMetalType();
        
        return (newColor != null && !newColor.trim().isEmpty()) ||
               (newSize != null && !newSize.trim().isEmpty()) ||
               (newMaterial != null && !newMaterial.trim().isEmpty());
    }
    
    /**
     * Handle image updates using direct FeedItem approach
     * This properly replaces images instead of duplicating them
     */
    private void handleImageUpdates(FeedItem item, Product existingProduct) {
        logger.debug("🖼️ Handling image updates for SKU: {}", item.getWebTagNumber());
        
        try {
            // Get external image URLs from feed item
            String[] externalImageUrls = imageService.getAvailableExternalImagePathByCSS(item);
            if (externalImageUrls == null || externalImageUrls.length == 0) {
                logger.debug("⏭️ No images to update for SKU: {}", item.getWebTagNumber());
                return;
            }
            
            // Log existing image count for comparison
            int existingImageCount = existingProduct.getImages() != null ? existingProduct.getImages().size() : 0;
            logger.debug("Replacing {} existing images with {} new images for SKU: {}", 
                existingImageCount, externalImageUrls.length, item.getWebTagNumber());
            
            // Remove all existing images first
            if (existingImageCount > 0) {
                logger.debug("🗑️ Removing {} existing images", existingImageCount);
                shopifyGraphQLService.deleteAllImageByProductId(item.getShopifyItemId());
            }
            
            // Add new images using the direct approach
            // Create Image objects from URLs and upload to Shopify
            List<Image> images = createImagesFromUrls(externalImageUrls, item);
            if (!images.isEmpty()) {
                logger.info("Uploading {} images to product {}", images.size(), item.getShopifyItemId());
                shopifyGraphQLService.addImagesToProduct(item.getShopifyItemId(), images);
            }
            
            logger.debug("✅ Images updated successfully - {} images replaced", externalImageUrls.length);
        } catch (Exception e) {
            logger.warn("⚠️ Failed to update images for SKU: {} - {}", item.getWebTagNumber(), e.getMessage());
            // Continue - image updates are not critical
        }
    }
    
    /**
     * Creates Image objects from external image URLs
     * Private helper method for image updates
     * 
     * @param imageUrls Array of external image URLs
     * @param feedItem Feed item for context
     * @return List of Image objects
     */
    private List<Image> createImagesFromUrls(String[] imageUrls, FeedItem feedItem) {
        List<Image> images = new ArrayList<>();
        
        for (int i = 0; i < imageUrls.length; i++) {
            String imageUrl = imageUrls[i];
            if (isValidImageUrl(imageUrl)) {
                Image image = new Image();
                image.setSrc(imageService.getCorrectedImageUrl(imageUrl));
                image.addAltTag(feedItem.getWebDescriptionShort()); // Use description as alt text
                image.setPosition(String.valueOf(i + 1));
                images.add(image);
                
                logger.debug("Created image {} for SKU: {} - URL: {}", 
                    i + 1, feedItem.getWebTagNumber(), imageUrl);
            } else {
                logger.warn("Skipping invalid image URL for SKU: {} - URL: {}", 
                    feedItem.getWebTagNumber(), imageUrl);
            }
        }
        
        return images;
    }
    
    /**
     * Validates that an image URL is properly formatted and accessible
     * Private helper method for image validation
     * 
     * @param imageUrl The URL to validate
     * @return true if valid, false otherwise
     */
    private boolean isValidImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            return false;
        }
        
        String trimmedUrl = imageUrl.trim();
        return trimmedUrl.startsWith("http://") || trimmedUrl.startsWith("https://");
    }
    
    /**
     * Update inventory after product update
     */
    private void updateInventory(FeedItem item, Product updatedProduct) throws Exception {
        logger.debug("📦 Updating inventory for product: {}", updatedProduct.getId());
        
        // Delegate to specialized inventory service
        inventoryManagementService.updateInventoryAfterProductUpdate(item, updatedProduct);
        logger.debug("✅ Inventory updated successfully");
    }
    
    /**
     * Update collection associations
     */
    private void updateCollectionAssociations(FeedItem item) throws Exception {
        logger.debug("🏷️ Updating collection associations for SKU: {}", item.getWebTagNumber());
        
        try {
            // Delegate to specialized collection service
            collectionManagementService.updateProductCollections(item);
            logger.debug("✅ Collection associations updated");
        } catch (Exception e) {
            // Enhanced error logging for collection association failures
            logger.error("❌ Failed to update collection associations for SKU: {} (Product ID: {})", 
                item.getWebTagNumber(), item.getShopifyItemId(), e);
            
            // Add specific context about the item to help with debugging
            logger.error("🔍 DEBUG Context - Item details:");
            logger.error("  - SKU: {}", item.getWebTagNumber());
            logger.error("  - Shopify Product ID: {}", item.getShopifyItemId());
            logger.error("  - Category: {}", item.getWebCategory());
            logger.error("  - Brand: {}", item.getWebDesigner());
            logger.error("  - Status: {}", item.getStatus());
            
            throw e;
        }
    }
    
    /**
     * Result wrapper for product update operations
     */
    public static class ProductUpdateResult {
        private final Product product;
        private final Exception error;
        private final boolean success;
        
        private ProductUpdateResult(Product product, Exception error, boolean success) {
            this.product = product;
            this.error = error;
            this.success = success;
        }
        
        public static ProductUpdateResult success(Product product) {
            return new ProductUpdateResult(product, null, true);
        }
        
        public static ProductUpdateResult failure(Exception error) {
            return new ProductUpdateResult(null, error, false);
        }
        
        public boolean isSuccess() { return success; }
        public Product getProduct() { return product; }
        public Exception getError() { return error; }
    }
}
