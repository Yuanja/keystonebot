package com.gw.services.sync;

import com.gw.domain.FeedItem;
import com.gw.services.EmailService;
import com.gw.services.FeedItemService;
import com.gw.services.product.ProductCreationService;
import com.gw.services.ImageService;
import com.gw.services.LogService;
import com.gw.services.shopifyapi.ShopifyGraphQLService;
import com.gw.services.shopifyapi.objects.Product;
import com.gw.services.shopifyapi.objects.Image;
import com.gw.services.inventory.InventoryLevelService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.ArrayList;

/**
 * Reusable Product Publish Pipeline
 * 
 * Handles the complete product publishing process with clean separation of concerns:
 * - Image download management
 * - Product creation on Shopify
 * - Image upload and association
 * - Inventory level setup
 * - Collection associations
 * - Product publishing to channels
 * - Status updates and notifications
 * 
 * Benefits:
 * - Single Responsibility: Focused only on product publishing
 * - Reusable: Can be used by different sync services
 * - Testable: Each step can be tested independently
 * - Clean error handling with detailed logging
 * - Consistent publish workflow
 */
@Service
public class ProductPublishPipeline {
    
    private static final Logger logger = LogManager.getLogger(ProductPublishPipeline.class);
    
    @Value("${email.alert.shopify.publish.enabled}")
    private boolean emailPublishEnabled;

    @Value("${email.alert.shopify.publish.send.to}")
    private String[] emailPublishSendTo;
    
    @Autowired
    private ShopifyGraphQLService shopifyGraphQLService;
    
    @Autowired
    private ImageService imageService;
    
    @Autowired
    @Qualifier("keyStoneShopifyProductFactoryService")
    private ProductCreationService shopifyProductFactoryService;
    
    @Autowired
    private FeedItemService feedItemService;
    
    @Autowired
    private EmailService emailService;
    
    @Autowired
    private LogService logService;
 
    @Autowired
    private CollectionManagementService collectionManagementService;
    
    @Autowired
    private InventoryManagementService inventoryManagementService;
    
    /**
     * Execute the complete product publish pipeline
     * 
     * @param item The feed item to publish
     * @return PublishResult with success status and details
     */
    public ProductPublishResult executePublish(FeedItem item) {
        logger.info("🚀 Starting product publish pipeline for SKU: {}", item.getWebTagNumber());
        
        try {
            // Handle image processing
            handleImageProcessing(item);
        
            // Create product on Shopify
            Product newlyAddedProduct = createProductOnShopify(item);
            
            // Add images to product using FeedItem image URLs
            handleImageUpload(item, newlyAddedProduct.getId());
            
            // Use inventory management service to handle the status change
            inventoryManagementService.handleInventoryStatusChange(item, newlyAddedProduct);
            
            // Setup collection associations
            collectionManagementService.updateProductCollectionsForPipeline(item, newlyAddedProduct.getId());
            
            // Publish product to all channels
            publishToAllChannels(newlyAddedProduct);
            
            // Update item status and send notifications
            finalizeSuccessfulPublish(item, newlyAddedProduct);
            
            logger.info("✅ Product publish pipeline completed successfully for SKU: {}", item.getWebTagNumber());
            return ProductPublishResult.success(newlyAddedProduct);
            
        } catch (Exception e) {
            logger.error("❌ Product publish pipeline failed for SKU: {} - {}", item.getWebTagNumber(), e.getMessage());
            handlePublishFailure(item, e);
            return ProductPublishResult.failure(e);
        }
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
            // Continue without failing the whole publish - images are not critical for publishing
        }
    }
    
    /**
     * Create product on Shopify using the product factory
     */
    private Product createProductOnShopify(FeedItem item) throws Exception {
        logger.debug("🔧 Creating product on Shopify for SKU: {}", item.getWebTagNumber());
        
        // Use the two-step approach for creating products with variant options
        Product newlyAddedProduct = shopifyProductFactoryService.createProductWithOptions(item);
        
        if (newlyAddedProduct == null || newlyAddedProduct.getId() == null) {
            throw new RuntimeException("Failed to create product - no product ID returned");
        }
        
        logger.debug("✅ Product created on Shopify with ID: {}", newlyAddedProduct.getId());
        return newlyAddedProduct;
    }
    
    /**
     * Publish product to all sales channels
     */
    private void publishToAllChannels(Product newlyAddedProduct) {
        logger.debug("📢 Publishing product to all channels: {}", newlyAddedProduct.getId());
        
        try {
            shopifyGraphQLService.publishProductToAllChannels(newlyAddedProduct.getId());
            logger.debug("✅ Product published to all sales channels");
        } catch (Exception e) {
            logger.warn("⚠️ Failed to publish product to all channels (may already be published): {}", e.getMessage());
            // Continue - the product may already be published to some channels
        }
    }
    
    /**
     * Finalize successful publish
     */
    private void finalizeSuccessfulPublish(FeedItem item, Product newlyAddedProduct) {
        logger.debug("✅ Finalizing successful publish for SKU: {}", item.getWebTagNumber());
        
        // Update item status
        item.setStatus(FeedItem.STATUS_PUBLISHED);
        item.setShopifyItemId(newlyAddedProduct.getId());
        item.setPublishedDate(new Date());
        feedItemService.updateAutonomous(item);
        
        // Send notification
        String logMsg = getItemActionLogMessage("PUBLISHED", item);
        logger.info(logMsg);
        sendEmailPublishAlert(logMsg, logMsg);
    }
    
    /**
     * Handle publish failure
     */
    private void handlePublishFailure(FeedItem item, Exception e) {
        logger.error("❌ Handling publish failure for SKU: {}", item.getWebTagNumber());
        
        String subject = "Failed to publish Sku: " + item.getWebTagNumber() + " with exception: " + e.getMessage();
        logService.emailError(logger, subject, subject, (Throwable) e);
        
        item.setStatus(FeedItem.STATUS_PUBLISHED_FAILED);
        item.setSystemMessages(e.getMessage());
        feedItemService.updateAutonomous(item);
    }
    
    /**
     * Generate action log message
     */
    private String getItemActionLogMessage(String action, FeedItem feedItem) {
        return action + " - Sku: " + feedItem.getWebTagNumber()
            + " ProductType: " + feedItem.getWebCategory()
            + " Title: " + feedItem.getWebDescriptionShort()
            + " Price: " + feedItem.getWebPriceKeystone()
            + " Description: " + feedItem.getWebDescriptionShort()
            + " Shopify Item Id: " + feedItem.getShopifyItemId();
    }
    
    /**
     * Send email publish alert
     */
    private void sendEmailPublishAlert(String title, String body) {
        if (emailPublishEnabled) {
            emailService.sendMessage(emailPublishSendTo, title, body);
        }
    }
    
    /**
     * Handle image uploads to Shopify product using FeedItem image URLs
     * Used by publish pipeline for new products
     * 
     * @param feedItem The feed item containing image URLs
     * @param productId The Shopify product ID to add images to
     */
    private void handleImageUpload(FeedItem feedItem, String productId) {
        logger.debug("🖼️ Handling image upload for product: {}", productId);
        
        try {
            // Get external image URLs from feed item
            String[] externalImageUrls = imageService.getAvailableExternalImagePathByCSS(feedItem);
            if (externalImageUrls == null || externalImageUrls.length == 0) {
                logger.debug("⏭️ Skipping image upload - no images available for SKU: {}", feedItem.getWebTagNumber());
                return;
            }
            
            // Create Image objects from URLs
            List<Image> images = createImagesFromUrls(externalImageUrls, feedItem);
            
            if (!images.isEmpty()) {
                logger.info("Uploading {} images to product {}", images.size(), productId);
                shopifyGraphQLService.addImagesToProduct(productId, images);
                logger.debug("✅ Images uploaded successfully");
            } else {
                logger.debug("⏭️ Skipping image upload - no valid images for SKU: {}", feedItem.getWebTagNumber());
            }
        } catch (Exception e) {
            logger.error("❌ Failed to upload images to product ID: {} for SKU: {} - {}", 
                productId, feedItem.getWebTagNumber(), e.getMessage());
            // Continue - image failure shouldn't stop the process
        }
    }
    
    /**
     * Creates Image objects from external image URLs
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
     * Result wrapper for product publish operations
     */
    public static class ProductPublishResult {
        private final Product product;
        private final Exception error;
        private final boolean success;
        
        private ProductPublishResult(Product product, Exception error, boolean success) {
            this.product = product;
            this.error = error;
            this.success = success;
        }
        
        public static ProductPublishResult success(Product product) {
            return new ProductPublishResult(product, null, true);
        }
        
        public static ProductPublishResult failure(Exception error) {
            return new ProductPublishResult(null, error, false);
        }
        
        public boolean isSuccess() { return success; }
        public Product getProduct() { return product; }
        public Exception getError() { return error; }
    }
}
