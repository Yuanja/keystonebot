package com.gw.services.sync;

import com.gw.domain.FeedItem;
import com.gw.domain.PredefinedCollection;
import com.gw.services.CollectionUtility;
import com.gw.services.EmailService;
import com.gw.services.FeedItemService;
import com.gw.services.IShopifyProductFactory;
import com.gw.services.ImageService;
import com.gw.services.LogService;
import com.gw.services.shopifyapi.ShopifyGraphQLService;
import com.gw.services.shopifyapi.objects.Collect;
import com.gw.services.shopifyapi.objects.CustomCollection;
import com.gw.services.shopifyapi.objects.InventoryLevel;
import com.gw.services.shopifyapi.objects.InventoryLevels;
import com.gw.services.shopifyapi.objects.Product;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Map;

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
    private IShopifyProductFactory shopifyProductFactoryService;
    
    @Autowired
    private FeedItemService feedItemService;
    
    @Autowired
    private EmailService emailService;
    
    @Autowired
    private LogService logService;
    
    @Autowired
    private SyncConfigurationService syncConfigurationService;
    
    /**
     * Execute the complete product publish pipeline
     * 
     * @param item The feed item to publish
     * @return PublishResult with success status and details
     */
    public ProductPublishResult executePublish(FeedItem item) {
        logger.info("🚀 Starting product publish pipeline for SKU: {}", item.getWebTagNumber());
        
        try {
            // Step 1: Handle image processing
            handleImageProcessing(item);
            
            // Step 2: Create product on Shopify
            Product newlyAddedProduct = createProductOnShopify(item);
            
            // Step 3: Add images to product
            imageService.handleImageUpload(newlyAddedProduct);
            
            // Step 4: Setup inventory levels
            setupInventoryLevels(newlyAddedProduct);
            
            // Step 5: Add collection associations
            addCollectionAssociations(item, newlyAddedProduct);
            
            // Step 6: Publish product to all channels
            publishToAllChannels(newlyAddedProduct);
            
            // Step 7: Update item status and send notifications
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
     * Step 1: Handle image processing using centralized service
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
     * Step 2: Create product on Shopify using the product factory
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
     * Step 4: Setup inventory levels for the new product
     */
    private void setupInventoryLevels(Product newlyAddedProduct) throws Exception {
        logger.debug("📦 Setting up inventory levels for product: {}", newlyAddedProduct.getId());
        
        if (newlyAddedProduct.getVariants() == null || newlyAddedProduct.getVariants().isEmpty()) {
            logger.warn("No variants found for inventory setup");
            return;
        }
        
        String inventoryItemId = newlyAddedProduct.getVariants().get(0).getInventoryItemId();
        if (inventoryItemId == null) {
            logger.warn("No inventory item ID found for inventory setup");
            return;
        }
        
        // Get current inventory levels from Shopify
        List<InventoryLevel> levelsList = shopifyGraphQLService.getInventoryLevelByInventoryItemId(inventoryItemId);
        
        // Convert to wrapper for compatibility
        InventoryLevels levels = new InventoryLevels();
        for (InventoryLevel level : levelsList) {
            levels.addInventoryLevel(level);
        }
        
        // Merge with product inventory data
        shopifyProductFactoryService.mergeInventoryLevels(levels, newlyAddedProduct.getVariants().get(0).getInventoryLevels());
        
        // Update inventory levels
        List<InventoryLevel> inventoryLevelsToUpdate = newlyAddedProduct.getVariants().get(0).getInventoryLevels().get();
        if (validateInventoryLevels(inventoryLevelsToUpdate)) {
            shopifyGraphQLService.updateInventoryLevels(inventoryLevelsToUpdate);
            logger.debug("✅ Inventory levels updated for {} locations", inventoryLevelsToUpdate.size());
        } else {
            logger.warn("⚠️ Skipping inventory update due to invalid inventory level data");
        }
    }
    
    /**
     * Validate inventory levels before update
     */
    private boolean validateInventoryLevels(List<InventoryLevel> inventoryLevels) {
        if (inventoryLevels == null || inventoryLevels.isEmpty()) {
            return false;
        }
        
        for (InventoryLevel level : inventoryLevels) {
            if (level.getInventoryItemId() == null || level.getLocationId() == null || level.getAvailable() == null) {
                logger.error("Invalid inventory level detected - InventoryItemId: {}, LocationId: {}, Available: {}", 
                    level.getInventoryItemId(), level.getLocationId(), level.getAvailable());
                return false;
            }
        }
        return true;
    }
    
    /**
     * Step 5: Add collection associations
     */
    private void addCollectionAssociations(FeedItem item, Product newlyAddedProduct) {
        logger.debug("🏷️ Adding collection associations for product: {}", newlyAddedProduct.getId());
        
        try {
            Map<PredefinedCollection, CustomCollection> collectionMappings = 
                syncConfigurationService.getCollectionMappings();
            
            List<Collect> collectsToAdd = CollectionUtility.getCollectionForProduct(
                newlyAddedProduct.getId(), item, collectionMappings);
            
            if (!collectsToAdd.isEmpty()) {
                shopifyGraphQLService.addProductAndCollectionsAssociations(collectsToAdd);
                logger.debug("✅ Added product to {} collections", collectsToAdd.size());
            } else {
                logger.warn("⚠️ No collections found for product SKU: {}", item.getWebTagNumber());
            }
        } catch (Exception e) {
            logger.error("Failed to add product to collections for SKU: {}", item.getWebTagNumber(), e);
            // Continue - collection association failure shouldn't stop the publish process
        }
    }
    
    /**
     * Step 6: Publish product to all sales channels
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
     * Step 7: Finalize successful publish
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