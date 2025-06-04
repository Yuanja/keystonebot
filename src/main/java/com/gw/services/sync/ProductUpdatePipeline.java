package com.gw.services.sync;

import com.gw.domain.FeedItem;
import com.gw.services.IShopifyProductFactory;
import com.gw.services.shopifyapi.ShopifyGraphQLService;
import com.gw.services.shopifyapi.objects.InventoryLevel;
import com.gw.services.shopifyapi.objects.Product;
import com.gw.services.ImageService;
import com.gw.services.product.ProductMergeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

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
    private IShopifyProductFactory shopifyProductFactoryService;
    
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
            // Step 1: Validate item for update
            validateItemForUpdate(item);
            
            // Step 2: Retrieve existing product
            Product existingProduct = retrieveExistingProduct(item);
            
            // Step 3: Handle image processing
            handleImageProcessing(item);
            
            // Step 4: Create updated product and merge
            Product updatedProduct = createAndMergeUpdatedProduct(item, existingProduct);
            
            // Step 5: Update product on Shopify
            updateProductOnShopify(updatedProduct, item);
            
            // Step 6: Handle image updates
            imageService.handleImageUpdate(existingProduct, updatedProduct);
            
            // Step 7: Update inventory
            updateInventory(item, updatedProduct);
            
            // Step 8: Update collection associations
            updateCollectionAssociations(item);
            
            logger.info("✅ Product update pipeline completed successfully for SKU: {}", item.getWebTagNumber());
            return ProductUpdateResult.success(updatedProduct);
            
        } catch (Exception e) {
            logger.error("❌ Product update pipeline failed for SKU: {} - {}", item.getWebTagNumber(), e.getMessage());
            return ProductUpdateResult.failure(e);
        }
    }
    
    /**
     * Step 1: Validate that the item has required data for updating
     */
    private void validateItemForUpdate(FeedItem item) {
        logger.debug("📋 Validating item for update: {}", item.getWebTagNumber());
        
        if (item.getShopifyItemId() == null) {
            throw new RuntimeException("No Shopify Item Id found for Sku: " + item.getWebTagNumber());
        }
    }
    
    /**
     * Step 2: Retrieve the existing product from Shopify with validation
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
     * Step 3: Handle image processing using centralized service
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
     * Step 4: Create updated product and merge with existing product data
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
     * Step 5: Update the product on Shopify
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
        // Delegate to specialized variant handling logic
        // This would need to be implemented based on the existing complex logic
        // For now, simplified version
        try {
            if (hasVariantOptions(item)) {
                logger.debug("🎛️ Updating variant options for product: {}", product.getId());
                return shopifyGraphQLService.createProductOptions(product.getId(), item);
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
     * Step 7: Update inventory after product update
     */
    private void updateInventory(FeedItem item, Product updatedProduct) throws Exception {
        logger.debug("📦 Updating inventory for product: {}", updatedProduct.getId());
        
        // Delegate to specialized inventory service
        inventoryManagementService.updateInventoryAfterProductUpdate(item, updatedProduct);
        logger.debug("✅ Inventory updated successfully");
    }
    
    /**
     * Step 8: Update collection associations
     */
    private void updateCollectionAssociations(FeedItem item) throws Exception {
        logger.debug("🏷️ Updating collection associations for SKU: {}", item.getWebTagNumber());
        
        // Delegate to specialized collection service
        collectionManagementService.updateProductCollections(item);
        logger.debug("✅ Collection associations updated");
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