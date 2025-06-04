package com.gw.services.product;

import com.gw.domain.FeedItem;
import com.gw.services.ImageService;
import com.gw.services.shopifyapi.objects.Image;
import com.gw.services.shopifyapi.objects.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service responsible for processing and managing product images
 * Handles image creation, validation, and merging logic
 */
@Service
public class ProductImageService {
    
    private static final Logger logger = LoggerFactory.getLogger(ProductImageService.class);
    
    @Autowired
    private ImageService imageService;
    
    @Autowired
    private MetadataService metadataService;
    
    /**
     * Sets product images based on feed item image paths
     * 
     * @param product The product to set images on
     * @param feedItem The feed item with image data
     */
    public void setProductImages(Product product, FeedItem feedItem) {
        logger.debug("Setting product images for SKU: {}", feedItem.getWebTagNumber());
        
        String[] externalImageUrls = imageService.getAvailableExternalImagePathByCSS(feedItem);
        if (externalImageUrls == null || externalImageUrls.length == 0) {
            logger.warn("No external image URLs found for SKU: {}", feedItem.getWebTagNumber());
            return;
        }
        
        List<Image> images = createImagesFromUrls(externalImageUrls, feedItem);
        product.setImages(images);
        
        logger.debug("Set {} images for SKU: {}", images.size(), feedItem.getWebTagNumber());
    }
    
    /**
     * Creates Image objects from external image URLs
     * 
     * @param imageUrls Array of external image URLs
     * @param feedItem Feed item for alt text generation
     * @return List of Image objects
     */
    private List<Image> createImagesFromUrls(String[] imageUrls, FeedItem feedItem) {
        List<Image> images = new ArrayList<>();
        String altText = metadataService.buildMetaDescription(feedItem);
        
        for (int i = 0; i < imageUrls.length; i++) {
            String imageUrl = imageUrls[i];
            if (isValidImageUrl(imageUrl)) {
                Image image = createImage(imageUrl, altText, i + 1);
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
     * Creates a single Image object with proper metadata
     * 
     * @param imageUrl The image URL
     * @param altText Alt text for the image
     * @param position Position index (1-based)
     * @return Configured Image object
     */
    private Image createImage(String imageUrl, String altText, int position) {
        Image image = new Image();
        image.setSrc(imageService.getCorrectedImageUrl(imageUrl));
        image.addAltTag(altText);
        image.setPosition(String.valueOf(position));
        
        logger.debug("Created image at position {} with alt text length: {}", 
            position, altText != null ? altText.length() : 0);
        
        return image;
    }
    
    /**
     * Validates that an image URL is properly formatted and accessible
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
     * Merges existing images with new images, preserving IDs where URLs match
     * 
     * @param productId The product ID to set on images
     * @param existingImages Current images from Shopify
     * @param newImages New images from feed item
     */
    public void mergeImages(String productId, List<Image> existingImages, List<Image> newImages) {
        // Handle null safety for existing images
        if (existingImages == null) {
            existingImages = new ArrayList<>();
        }
        
        // Handle null safety for new images
        if (newImages == null) {
            logger.debug("No new images to merge for product: {} (newImages is null)", productId);
            return;
        }
        
        logger.debug("Merging {} existing images with {} new images for product: {}", 
            existingImages.size(), newImages.size(), productId);
        
        // Set product ID on all new images
        newImages.forEach(image -> image.setProductId(productId));
        
        // Create lookup map for existing images by URL
        Map<String, Image> existingImagesByUrl = existingImages.stream()
            .collect(Collectors.toMap(Image::getSrc, image -> image));
        
        // Merge IDs for matching URLs
        for (Image newImage : newImages) {
            Image existingImage = existingImagesByUrl.get(newImage.getSrc());
            if (existingImage != null) {
                logger.debug("Preserving image ID {} for URL: {}", 
                    existingImage.getId(), newImage.getSrc());
                newImage.setId(existingImage.getId());
                newImage.setProductId(productId);
            } else {
                logger.debug("New image URL: {} - no existing image to merge", newImage.getSrc());
            }
        }
        
        logger.debug("Completed image merge for product: {}", productId);
    }
    
    /**
     * Validates that images have valid URLs for Shopify processing
     * 
     * @param images List of images to validate
     * @return true if all images are valid, false otherwise
     */
    public boolean hasValidImageUrls(List<Image> images) {
        if (images == null || images.isEmpty()) {
            logger.debug("No images to validate");
            return false;
        }
        
        for (Image image : images) {
            if (!isValidImageUrl(image.getSrc())) {
                logger.warn("Invalid image URL found: {}", image.getSrc());
                return false;
            }
        }
        
        logger.debug("All {} images have valid URLs", images.size());
        return true;
    }
    
    /**
     * Logs detailed information about product images for debugging
     * 
     * @param images List of images to log
     * @param operation The operation being performed (e.g., "CREATING", "UPDATING")
     * @param sku The product SKU for context
     */
    public void logImageDetails(List<Image> images, String operation, String sku) {
        if (images == null || images.isEmpty()) {
            logger.debug("{} operation - No images for SKU: {}", operation, sku);
            return;
        }
        
        logger.debug("=== {} IMAGES for SKU: {} ===", operation, sku);
        logger.debug("Images count: {}", images.size());
        
        for (int i = 0; i < images.size(); i++) {
            Image image = images.get(i);
            logger.debug("  Image[{}]: ID={}, URL={}, Position={}", 
                i, image.getId(), image.getSrc(), image.getPosition());
        }
        
        logger.debug("=== End {} IMAGES ===", operation);
    }
} 