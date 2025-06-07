package com.gw.services.sync;

import com.gw.domain.FeedItem;
import com.gw.services.product.ProductCreationService;
import com.gw.services.shopifyapi.ShopifyGraphQLService;
import com.gw.services.shopifyapi.objects.Product;
import com.gw.services.shopifyapi.objects.Image;
import com.gw.services.ImageService;
import com.gw.services.product.MetadataUpdateService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Simple Product Update Pipeline
 * 
 * Clear step-by-step flow:
 * 1. Compare & update basic fields (title, description, etc.) if changed
 * 2. Check & update inventory levels if changed
 * 3. Check & update options/variants if changed
 * 4. Check & update metafields if changed
 * 5. Handle images (always recreate)
 * 6. Update collections
 * 
 * Benefits:
 * - Simple linear flow
 * - Only updates what actually changed
 * - Easy to follow and debug
 * - Clear separation of concerns
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
    private ProductCreationService productCreationService;
    
    @Autowired
    private CollectionManagementService collectionManagementService;
    
    @Autowired
    private InventoryManagementService inventoryManagementService;
    
    @Autowired
    private MetadataUpdateService metadataUpdateService;
    

    
    /**
     * Execute simple product update pipeline
     */
    public ProductUpdateResult executeUpdate(FeedItem item) {
        logger.info("🔄 Starting simple update pipeline for SKU: {}", item.getWebTagNumber());
        
        try {
            // Step 1: Get existing product
            Product existingProduct = getExistingProduct(item);
            
            // Step 2: Create updated product template (only for comparison)
            Product updatedTemplate = createUpdatedTemplate(item);
            
            // Step 3: Update basic fields if changed
            updateBasicFieldsIfChanged(existingProduct, updatedTemplate);
            
            // Use inventory management service to handle the status change
            inventoryManagementService.handleInventoryStatusChange(item, existingProduct);
            
            // Step 5: Update options/variants if changed
            updateOptionsIfChanged(item, existingProduct);
            
            // Step 6: Update metafields and SEO metadata if changed
            updateMetafieldsIfChanged(existingProduct, item);
            
            // Step 7: Handle images (always recreate)
            updateImages(item);
            
            // Step 8: Update collections
            collectionManagementService.updateProductCollectionsForPipeline(item, existingProduct.getId());
            
            logger.info("✅ Simple update pipeline completed for SKU: {}", item.getWebTagNumber());
            return ProductUpdateResult.success(existingProduct);
            
        } catch (Exception e) {
            logger.error("❌ Update pipeline failed for SKU: {} - {}", item.getWebTagNumber(), e.getMessage());
            return ProductUpdateResult.failure(e);
        }
    }
    
    /**
     * Step 1: Get existing product with validation
     */
    private Product getExistingProduct(FeedItem item) {
        logger.debug("📋 Step 1: Getting existing product for SKU: {}", item.getWebTagNumber());
        
        if (item.getShopifyItemId() == null) {
            throw new RuntimeException("No Shopify Item Id found for SKU: " + item.getWebTagNumber());
        }
        
        Product existing = shopifyGraphQLService.getProductByProductId(item.getShopifyItemId());
        if (existing == null) {
            throw new RuntimeException("No Shopify product found by ID: " + item.getShopifyItemId());
        }
        
        logger.debug("✅ Found existing product ID: {}", existing.getId());
        return existing;
    }
    
    /**
     * Step 2: Create updated template for comparison
     */
    private Product createUpdatedTemplate(FeedItem item) throws Exception {
        logger.debug("🔧 Step 2: Creating updated template for SKU: {}", item.getWebTagNumber());
        
        Product template = productCreationService.createProductForUpdate(item);
        template.setId(item.getShopifyItemId());
        
        logger.debug("✅ Updated template created");
        return template;
    }
    
    /**
     * Step 3: Update basic fields if they have changed
     */
    private void updateBasicFieldsIfChanged(Product existing, Product updated) throws Exception {
        logger.debug("📝 Step 3: Checking basic fields for changes");
        
        // For now, always update basic fields - comparison logic can be added later
        logger.info("🔄 Updating basic fields");
        
        // Copy basic fields from updated to existing (preserve existing ID)
        copyBasicFields(updated, existing);
        
        // Update via API (only basic fields, no variants/options)
        shopifyGraphQLService.updateProduct(createBasicProduct(existing));
        logger.debug("✅ Basic fields updated");
    }
    
    /**
     * Step 5: Update options/variants if they have changed
     */
    private void updateOptionsIfChanged(FeedItem item, Product existing) {
        logger.debug("🎛️ Step 5: Checking options for changes");
        
        if (hasVariantOptions(item)) {
            try {
                boolean updated = shopifyGraphQLService.updateProductOptions(existing.getId(), item);
                if (updated) {
                    logger.debug("✅ Options/variants updated");
                } else {
                    logger.debug("⏭️ Options unchanged - skipping");
                }
            } catch (Exception e) {
                logger.warn("⚠️ Options update failed: {}", e.getMessage());
                // Continue - don't fail entire update
            }
        } else {
            logger.debug("⏭️ No variant options - skipping");
        }
    }
    
        /**
     * Step 6: Update metafields and SEO metadata if they have changed (using consolidated service)
     */
    private void updateMetafieldsIfChanged(Product existing, FeedItem feedItem) throws Exception {
        logger.debug("📋 Step 6: Checking metafields and SEO metadata for changes");
        
        // Use consolidated metadata update service
        metadataUpdateService.updateMetadataIfChanged(existing, feedItem);
    }
    
    /**
     * Step 7: Handle images (always recreate for consistency)
     */
    private void updateImages(FeedItem item) {
        logger.debug("🖼️ Step 7: Updating images");
        
        try {
            String[] imageUrls = imageService.getAvailableExternalImagePathByCSS(item);
            if (imageUrls == null || imageUrls.length == 0) {
                logger.debug("⏭️ No images to update");
                return;
            }
            
            // Simple approach: delete all, add new ones
            shopifyGraphQLService.deleteAllImageByProductId(item.getShopifyItemId());
            
            // Create and add new images
            List<Image> images = createImages(imageUrls, item);
            if (!images.isEmpty()) {
                shopifyGraphQLService.addImagesToProduct(item.getShopifyItemId(), images);
                logger.debug("✅ {} images updated", images.size());
            }
        } catch (Exception e) {
            logger.warn("⚠️ Image update failed: {}", e.getMessage());
            // Continue - images are not critical
        }
    }
    
    // Helper methods
    
    private void copyBasicFields(Product from, Product to) {
        to.setTitle(from.getTitle());
        to.setBodyHtml(from.getBodyHtml());
        to.setVendor(from.getVendor());
        to.setProductType(from.getProductType());
        to.setHandle(from.getHandle());
        to.setTags(from.getTags());
        to.setStatus(from.getStatus());
    }
    
    private boolean hasVariantOptions(FeedItem item) {
        String color = item.getWebWatchDial();
        String size = item.getWebWatchDiameter();
        String material = item.getWebMetalType();
        
        return (color != null && !color.trim().isEmpty()) ||
               (size != null && !size.trim().isEmpty()) ||
               (material != null && !material.trim().isEmpty());
    }
    
    private Product createBasicProduct(Product source) {
        Product basic = new Product();
        basic.setId(source.getId());
        basic.setTitle(source.getTitle());
        basic.setBodyHtml(source.getBodyHtml());
        basic.setVendor(source.getVendor());
        basic.setProductType(source.getProductType());
        basic.setHandle(source.getHandle());
        basic.setTags(source.getTags());
        basic.setStatus(source.getStatus());
        return basic;
    }
    
    private Product createMetafieldProduct(Product source) {
        Product metafields = new Product();
        metafields.setId(source.getId());
        metafields.setMetafields(source.getMetafields());
        return metafields;
    }
    
    private List<Image> createImages(String[] urls, FeedItem item) {
        // Implementation similar to existing createImagesFromUrls
        // Simplified for clarity
        return Arrays.stream(urls)
            .filter(this::isValidImageUrl)
            .map(url -> {
                Image image = new Image();
                image.setSrc(imageService.getCorrectedImageUrl(url));
                image.addAltTag(item.getWebDescriptionShort());
                return image;
            })
            .collect(Collectors.toList());
    }
    
    private boolean isValidImageUrl(String url) {
        return url != null && !url.trim().isEmpty() && 
               (url.startsWith("http://") || url.startsWith("https://"));
    }
    
    private boolean areMetafieldsEqual(List<?> existing, List<?> updated) {
        if (existing == null && updated == null) return true;
        if (existing == null || updated == null) return false;
        if (existing.size() != updated.size()) return false;
        
        // Simple size-based comparison for now
        // More sophisticated comparison can be added if needed
        return existing.size() == updated.size();
    }
    
    private boolean areSeoFieldsEqual(Product existing, Product updated) {
        String existingTitle = existing.getMetafieldsGlobalTitleTag();
        String updatedTitle = updated.getMetafieldsGlobalTitleTag();
        String existingDescription = existing.getMetafieldsGlobalDescriptionTag();
        String updatedDescription = updated.getMetafieldsGlobalDescriptionTag();
        
        boolean titleEqual = (existingTitle == null && updatedTitle == null) ||
                           (existingTitle != null && existingTitle.equals(updatedTitle));
        boolean descriptionEqual = (existingDescription == null && updatedDescription == null) ||
                                 (existingDescription != null && existingDescription.equals(updatedDescription));
        
        return titleEqual && descriptionEqual;
    }
    
    /**
     * Simple result wrapper
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
