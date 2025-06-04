package com.gw.services.sync;

import com.gw.domain.FeedItem;
import com.gw.services.ImageService;
import com.gw.services.shopifyapi.ShopifyGraphQLService;
import com.gw.services.shopifyapi.objects.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Centralized Image Pipeline Service
 * 
 * Provides reusable image processing workflows for sync operations:
 * - Unified image download handling
 * - Shopify image upload management
 * - Error handling with graceful fallbacks
 * 
 * Benefits:
 * - DRY: Eliminates duplicate image handling code
 * - Consistent: Same image processing logic across all pipelines
 * - Testable: Centralized logic can be tested independently
 * - Maintainable: Single place to update image handling
 */
@Service
public class ImagePipelineService {
    
    private static final Logger logger = LoggerFactory.getLogger(ImagePipelineService.class);
    
    @Autowired
    private ImageService imageService;
    
    @Autowired
    private ShopifyGraphQLService shopifyGraphQLService;
    
    /**
     * Handle complete image processing workflow for any feed item
     * 
     * @param item The feed item to process images for
     * @return ImageProcessingResult with success/failure details
     */
    public ImageService.ImageProcessingResult handleImageProcessing(FeedItem item) {
        return imageService.handleImageProcessing(item, item.getWebTagNumber());
    }
    
    /**
     * Handle image uploads to Shopify product
     * Used by publish pipeline for new products
     * 
     * @param product The product to add images to
     */
    public void handleImageUpload(Product product) {
        logger.debug("🖼️ Handling image upload for product: {}", product.getId());
        
        if (product.getImages() != null && !product.getImages().isEmpty()) {
            logger.info("Uploading {} images to product", product.getImages().size());
            
            try {
                shopifyGraphQLService.addImagesToProduct(product.getId(), product.getImages());
                logger.debug("✅ Images uploaded successfully");
            } catch (Exception e) {
                logger.error("❌ Failed to upload images to product ID: {} - {}", product.getId(), e.getMessage());
                // Continue - image failure shouldn't stop the process
            }
        } else {
            logger.debug("⏭️ Skipping image upload - no images available");
        }
    }
    
    /**
     * Handle image updates for existing product
     * Used by update pipeline for existing products
     * 
     * @param existingProduct The existing product
     * @param updatedProduct The updated product
     */
    public void handleImageUpdate(Product existingProduct, Product updatedProduct) {
        logger.debug("🖼️ Handling image updates for product: {}", updatedProduct.getId());
        
        // For now, simplified image update logic
        // Could be enhanced to:
        // - Compare existing vs new images
        // - Only update changed images
        // - Handle image reordering
        // - Remove unused images
        
        try {
            if (updatedProduct.getImages() != null && !updatedProduct.getImages().isEmpty()) {
                logger.debug("Updating {} images for existing product", updatedProduct.getImages().size());
                shopifyGraphQLService.addImagesToProduct(updatedProduct.getId(), updatedProduct.getImages());
                logger.debug("✅ Images updated successfully");
            } else {
                logger.debug("⏭️ No image updates needed");
            }
        } catch (Exception e) {
            logger.warn("⚠️ Failed to update images for product ID: {} - {}", updatedProduct.getId(), e.getMessage());
            // Continue - image updates are not critical
        }
    }
    
    /**
     * Validate that images are ready for upload
     * 
     * @param product The product to validate images for
     * @return true if images are ready, false otherwise
     */
    public boolean validateImagesReady(Product product) {
        if (product.getImages() == null || product.getImages().isEmpty()) {
            logger.debug("No images to validate for product: {}", product.getId());
            return false;
        }
        
        // Could add more validation:
        // - Check image URLs are accessible
        // - Validate image formats
        // - Check image sizes
        
        return true;
    }
} 