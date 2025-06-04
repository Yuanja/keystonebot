package com.gw.services;

import com.gw.domain.FeedItem;
import com.gw.domain.FeedItemChange;
import com.gw.domain.FeedItemChangeSet;
import com.gw.domain.PredefinedCollection;
import com.gw.domain.EbayMetafieldDefinition;
import com.gw.services.shopifyapi.ShopifyGraphQLService;
import com.gw.services.shopifyapi.objects.*;
import com.gw.services.product.ProductMergeService;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;

import java.util.*;
import java.util.stream.Collectors;
import java.util.function.Function;

/**
 * Base Shopify Sync Service with GraphQL API integration
 * 
 * This class handles regular Shopify synchronization operations:
 * - Processing feed items for new, changed, and deleted products
 * - Publishing new products to Shopify
 * - Updating existing products on Shopify
 * - Managing product collections and inventory
 * - Handling image uploads and updates
 * 
 * RECONCILIATION MOVED:
 * - Reconciliation logic has been extracted to ReconciliationService
 * - This service now focuses only on regular sync operations
 * - For reconciliation, use the dedicated ReconciliationService and tests
 * 
 * CACHING STRATEGY:
 * - Collection mappings are cached in memory during the first initialization
 * - Subsequent calls use the cached data to avoid expensive API calls
 * - Thread-safe lazy initialization ensures collections are loaded only once
 * - Cache can be manually invalidated using invalidateCollectionCache() method
 * - Cache is automatically invalidated when Spring context refreshes
 * 
 * MIGRATION NOTES:
 * - GraphQL API returns List<InventoryLevel> instead of InventoryLevels wrapper
 * - GraphQL API returns Product directly instead of ProductVo wrapper
 * - Method names have been updated (unlistDupeListings vs unlistDupelistings)
 * - Inventory updates now require explicit null checks for better reliability
 * 
 * @author jyuan
 */
public abstract class BaseShopifySyncService implements IShopifySyncService {
    
    private static Logger logger = LogManager.getLogger(BaseShopifySyncService.class);
    
    private @Value("${MAX_TO_DELETE_COUNT}") int MAX_TO_DELETE_COUNT;
    
    private @Value("${dev.mode}") boolean devMode;
    private @Value("${dev.mode.maxReadCount}") int devModeMaxReadCount;
    private @Value("${shopify.force.update}") boolean forceUpdate;

    @Value("${email.alert.shopify.publish.enabled}")
    private boolean emailPublishEnabled;

    @Value("${email.alert.shopify.publish.send.to}")
    private String[] emailPublishSendTo;

    @Value("${skip.image.download:false}")
    private boolean skipImageDownload;

    @Autowired
    private EmailService emailService;

    @Autowired
    private LogService logService;
    
    @Autowired(required=false)
    private IFeedService feedService;
    
    @Autowired
    private ShopifyGraphQLService shopifyGraphQLService;
    
    @Autowired
    private ImageService imageService;
    
    @Autowired
    private FeedItemService feedItemService;
    
    @Autowired
    private IShopifyProductFactory shopifyProductFactoryService;
    
    @Autowired
    private ProductMergeService productMergeService;
    
    /**
     * Cached collection mappings to avoid repeated API calls
     * Uses Spring's dependency injection and lazy initialization
     */
    private Map<PredefinedCollection, CustomCollection> cachedCollectionByEnum;
    
    /**
     * Flag to track if collections have been initialized
     */
    private boolean collectionsInitialized = false;
    
    /**
     * Flag to track if metafield definitions have been initialized
     */
    private boolean metafieldDefinitionsInitialized = false;
    
    @Override 
    public abstract PredefinedCollection[] getPredefinedCollections();

    @Override
	public void sync(boolean feedReady) throws Exception {
        if (feedReady){
            doSync();
        }
    }

    /**
     * Ensures collections are configured and caches the result
     * Uses Spring best practices for caching expensive operations
     * Only performs actions when collections are missing or cache needs refresh
     */
    private void ensureCollections() throws Exception {
        if (!collectionsInitialized || cachedCollectionByEnum == null) {
            logger.info("🔍 Checking collection configuration status...");
            
            try {
                // First, check if we actually need to create collections
                List<CustomCollection> allCollectionsFromShopify = shopifyGraphQLService.getAllCustomCollections();
                Map<String, CustomCollection> collectionByTitleFromShopify = 
                    allCollectionsFromShopify.stream().collect(Collectors.toMap(CustomCollection::getTitle, Function.identity()));
                
                // Check if all required collections already exist
                PredefinedCollection[] requiredCollections = getPredefinedCollections();
                int existingCount = 0;
                for (PredefinedCollection collectionEnum : requiredCollections) {
                    if (collectionByTitleFromShopify.containsKey(collectionEnum.getTitle())) {
                        existingCount++;
                    }
                }
                
                boolean allCollectionsExist = (existingCount == requiredCollections.length);
                
                if (allCollectionsExist) {
                    logger.info("✅ All " + requiredCollections.length + " required collections already exist - using existing ones");
                    
                    // Build the mapping using existing collections
                    cachedCollectionByEnum = new HashMap<>();
                    for (PredefinedCollection collectionEnum : requiredCollections) {
                        CustomCollection existingCollection = collectionByTitleFromShopify.get(collectionEnum.getTitle());
                        cachedCollectionByEnum.put(collectionEnum, existingCollection);
                    }
                } else {
                    logger.info("🔧 Missing collections detected (" + existingCount + "/" + requiredCollections.length + ") - ensuring configuration...");
                    // Only call ensure if collections are missing - this will create missing ones and use existing ones
                    cachedCollectionByEnum = shopifyGraphQLService.ensureConfiguredCollections(requiredCollections);
                    logger.info("✅ Successfully ensured " + cachedCollectionByEnum.size() + " collection mappings");
                }
                
                collectionsInitialized = true;
                logger.info("📊 Cached " + cachedCollectionByEnum.size() + " collection mappings");
                
            } catch (Exception e) {
                logger.error("❌ Error ensuring collections: " + e.getMessage(), e);
                throw e;
            }
        } else {
            logger.debug("✅ Using cached collection mappings ({} collections) - no check needed", cachedCollectionByEnum.size());
        }
    }
    
    /**
     * Ensures eBay metafield definitions are configured
     * Uses caching to avoid repeated expensive API calls
     * Only performs actions when metafield definitions are missing or invalid
     */
    private void ensureMetafieldDefinitions() throws Exception {
        if (!metafieldDefinitionsInitialized) {
            logger.info("🔍 Checking eBay metafield definitions status...");
            
            try {
                // First, check if eBay metafield definitions already exist
                List<Map<String, String>> existingEbayMetafields = shopifyGraphQLService.getMetafieldDefinitions("ebay");
                
                // Check if all expected eBay metafields exist (using enum count)
                int expectedCount = EbayMetafieldDefinition.getCount();
                boolean allMetafieldsExist = (existingEbayMetafields.size() == expectedCount);
                
                if (allMetafieldsExist) {
                    // Validate the structure to ensure they're correct
                    boolean structureValid = validateMetafieldStructure(existingEbayMetafields);
                    
                    if (structureValid) {
                        logger.info("✅ All " + expectedCount + " eBay metafield definitions already exist with correct structure - no action needed");
                        metafieldDefinitionsInitialized = true;
                        return;
                    } else {
                        logger.info("🔧 eBay metafield definitions exist but structure is invalid - will recreate");
                    }
                } else {
                    logger.info("🔧 Missing eBay metafield definitions detected (" + existingEbayMetafields.size() + "/" + expectedCount + ") - creating...");
                }
                
                // Create the metafield definitions (this handles creation and pinning)
                shopifyGraphQLService.createEbayMetafieldDefinitions();
                metafieldDefinitionsInitialized = true;
                logger.info("✅ eBay metafield definitions ensured and cached");
                
            } catch (Exception e) {
                // Log but don't fail - definitions may already exist or there may be temporary issues
                logger.info("ℹ️ eBay metafield definitions status check completed: " + e.getMessage());
                metafieldDefinitionsInitialized = true; // Mark as initialized to avoid repeated attempts
            }
        } else {
            logger.debug("✅ eBay metafield definitions already initialized - no check needed");
        }
    }
    
    /**
     * Validate the structure and content of eBay metafield definitions
     * Ensures all expected metafield keys are present using the EbayMetafieldDefinition enum
     */
    private boolean validateMetafieldStructure(List<Map<String, String>> metafields) {
        int expectedCount = EbayMetafieldDefinition.getCount();
        if (metafields.size() != expectedCount) {
            logger.warn("⚠️ Expected " + expectedCount + " eBay metafields, found " + metafields.size());
            return false;
        }
        
        // Get expected keys from enum (already sorted)
        List<String> expectedKeys = EbayMetafieldDefinition.getAllKeys();
        
        // Get current keys from existing metafields (sorted)
        List<String> currentKeys = metafields.stream()
            .map(m -> m.get("key"))
            .sorted()
            .collect(Collectors.toList());
        
        boolean structureValid = currentKeys.equals(expectedKeys);
        
        if (!structureValid) {
            logger.warn("⚠️ Metafield structure validation failed");
            logger.warn("  Expected keys: " + expectedKeys);
            logger.warn("  Current keys: " + currentKeys);
        } else {
            logger.info("✅ Metafield structure validation passed - all " + expectedCount + " expected keys found");
        }
        
        return structureValid;
    }
    
    /**
     * Invalidates the collection cache - useful when collections need to be refreshed
     * Can be called manually or triggered by external events
     * Also invalidates metafield definition cache
     */
    @CacheEvict(value = "collections", allEntries = true)
    public void invalidateCollectionCache() {
        logger.info("Invalidating collection and metafield definition cache...");
        cachedCollectionByEnum = null;
        collectionsInitialized = false;
        metafieldDefinitionsInitialized = false;
    }
    
    /**
     * Gets the cached collection mappings, initializing if necessary
     * Thread-safe lazy initialization
     */
    private synchronized Map<PredefinedCollection, CustomCollection> getCollectionMappings() throws Exception {
        if (!collectionsInitialized || cachedCollectionByEnum == null) {
            ensureCollections();
        }
        return cachedCollectionByEnum;
    }

    private void doSync() throws Exception {
        List<FeedItem> feedItems = feedService.getItemsFromFeed();
		if (feedItems.size() == 0) {
			logger.error("Feed is temporarily offline.  Nothing is read: Skipping this schedule.");
			return;
		}
        doSyncForFeedItems(feedItems);
    }

    @Override
    public void doSyncForFeedItems(List<FeedItem> feedItems) throws Exception {
		// Check to see if there are dupes.
		Map<String, FeedItem> itemsBySku = new HashMap<String, FeedItem>();
		List<FeedItem> dupes = new ArrayList<FeedItem>();
		feedItems.stream().forEach(c -> {
			if (itemsBySku.containsKey(c.getWebTagNumber())) {
				dupes.add(c);
			} else {
				itemsBySku.put(c.getWebTagNumber(), c);
			}
		});

		if (dupes.size() > 0) {
			logger.error("Feed has dupes: Skipping dupes:");
			for (FeedItem dupeSku : dupes) {
				logger.error("dupe: " + dupeSku.getWebTagNumber());
				itemsBySku.remove(dupeSku.getWebTagNumber());
			}
		}
		        
        ensureCollections();
        
        // Ensure eBay metafield definitions exist for proper product metadata
        ensureMetafieldDefinitions();

        logger.info("Detecting any new Feed Items or changes...");
		FeedItemChangeSet changeSet = compareFeedItemWithDB(feedItems);
		int changedItemCount = changeSet.getChangedItems() == null ? 0 : changeSet.getChangedItems().size();
        int toDeleteItemCount = changeSet.getDeletedItems() == null ? 0 : changeSet.getDeletedItems().size();
        
        if (changedItemCount < MAX_TO_DELETE_COUNT 
                && toDeleteItemCount < MAX_TO_DELETE_COUNT) {
            handleDeletedItems(changeSet.getDeletedItems());
            handleNewItems(changeSet.getNewItems());
            handleChangedItems(changeSet.getChangedItems());
            
        } else {
            logger.error("Skipping delete as more feed changed too much :" +
                    " to delete item count: "+ toDeleteItemCount 
                    +" to update item count: "+ changedItemCount
                    + " surpassing the limit of:"
                    + MAX_TO_DELETE_COUNT + ".  Confirm with Justin and up the MAX_TO_DELETE_COUNT");
        }

        logger.info("Finished feed processing. Waiting for the next schedule.");
        return;
    }
    
    private void handleDeletedItems(List<FeedItem> deletedItems) {
        logger.info("Handling deleted items: " + deletedItems.size());
        for (FeedItem itemInDb : deletedItems) {
            if (StringUtils.isNotEmpty(itemInDb.getShopifyItemId())) {
                try {
                    // Should check for item existence in DB first.
                    shopifyGraphQLService.deleteProductById(itemInDb.getShopifyItemId());
                    feedItemService.deleteAutonomous(itemInDb);

                    String removeItemMessage = getItemActionLogMessage("REMOVED", itemInDb);
                    logger.info(removeItemMessage);
                    this.sendEmailPublishAlertEmail(removeItemMessage,removeItemMessage);
                }
                catch (Exception e) {
                    logService.emailError(logger,
                            "Shopify Bot: Error trying to unpublish Shopify Item Id: " + itemInDb.getShopifyItemId(), null, e);
                }
            }
            else {
                logger.info("Item Sku: " + itemInDb.getWebTagNumber() + " is not in the feed and is not known to be in shopify, removing from db.");
                feedItemService.deleteAutonomous(itemInDb);
            }
        }
    }
    
    private void handleNewItems(List<FeedItem> newFeedItems) {
        logger.info("Inserting new items count: " + newFeedItems.size());
        for (FeedItem newFeedItem : newFeedItems) {
            logger.info("Inserted new item to db: " + newFeedItem.getWebTagNumber());
            //Publish method will also save the feedItem to the db.
            publishItemToShopify(newFeedItem);
        }
    }
    
    private void handleChangedItems(List<FeedItemChange> changedItems) {
        logger.info("Handling changed items: " + changedItems.size());
        for (FeedItemChange change : changedItems) {
            FeedItem itemFromDb = change.getFromDb();
            FeedItem itemFromFeed = change.getFromFeed();
            try {
                logger.info("Changed Item SKU : "+itemFromFeed.getWebTagNumber() 
                    + " As Product ID: " + itemFromDb.getShopifyItemId()
                );
                itemFromDb.copyFrom(itemFromFeed);
                updateItemOnShopify(itemFromDb);
            }
            catch (Exception e) {
                logger.error("Error updating a changed feedItem: ", e);
            }
        }
    }
    
    /* 
     * Returns 3 lists,
     * first is the new items that didn't exist in the db,
     * second is the list of items updated,
     * third is items in DB but not in feed.
     */
    @Override
	public FeedItemChangeSet compareFeedItemWithDB(final List<FeedItem> feedItems){
        return feedItemService.compareFeedItemWithDB(forceUpdate, feedItems);
    }

    private String getItemActionLogMessage(String action, FeedItem feedItem){
        return action + " Sku: "
                + feedItem.getWebTagNumber() + ", "
                + feedItem.getWebDescriptionShort()
                + " Shopify Item Id: "+  feedItem.getShopifyItemId();
    }
    
    @Override
    public void updateItemOnShopify(FeedItem item) {
        logger.info("Updating Sku: " + item.getWebTagNumber());
        try {
            validateItemForUpdate(item);
            
            // Get existing product from Shopify
            Product existingProduct = retrieveExistingProduct(item);
            
            // Download images if not skipped
            handleImageDownload(item);
            
            // Create updated product and merge with existing
            Product updatedProduct = createAndMergeUpdatedProduct(item, existingProduct);
            
            // Update the product on Shopify
            updateProductOnShopify(updatedProduct, item);
            
            // Handle image updates (delete and recreate)
            handleImageUpdatesForExistingProduct(existingProduct, updatedProduct);
            
            // Update inventory levels with refreshed data
            updateInventoryAfterProductUpdate(item, updatedProduct);
            
            // Update collection associations
            updateCollectionAssociations(item);
            
            // Update item status and log success
            finalizeSuccessfulUpdate(item);
            
        } catch (Exception e) {
            handleUpdateFailure(item, e);
        }
    }
    
    /**
     * Validates that the item has required data for updating
     */
    private void validateItemForUpdate(FeedItem item) {
        if (item.getShopifyItemId() == null) {
            throw new RuntimeException("No Shopify Item Id found for Sku: " + item.getWebTagNumber());
        }
    }
    
    /**
     * Retrieves the existing product from Shopify with validation
     */
    private Product retrieveExistingProduct(FeedItem item) {
        Product existingProduct = shopifyGraphQLService.getProductByProductId(item.getShopifyItemId());
        if (existingProduct == null) {
            throw new RuntimeException("No Shopify product found by the id: " + item.getShopifyItemId());
        }
        return existingProduct;
    }
    
    /**
     * Handles image download if not configured to skip
     */
    private void handleImageDownload(FeedItem item) {
        if (!skipImageDownload) {
            logger.info("Downloading images for SKU: " + item.getWebTagNumber());
            try {
                imageService.downloadImages(item);
            } catch (Exception e) {
                logger.warn("Failed to download images for SKU: " + item.getWebTagNumber() + " - " + e.getMessage());
                // Continue without failing the whole update
            }
        } else {
            logger.info("Skipping image download for SKU: " + item.getWebTagNumber() + " (skip.image.download=true)");
        }
    }
    
    /**
     * Creates updated product and merges with existing product data
     * FIXED: No longer calls createProductWithOptions which creates new products
     * Instead builds product object directly for updating existing products
     */
    private Product createAndMergeUpdatedProduct(FeedItem item, Product existingProduct) throws Exception {
        // FIXED: Use createProduct to build object without creating new product on Shopify
        // This builds the product structure with all metafields and options but doesn't send to Shopify
        Product updatedProduct = shopifyProductFactoryService.createProduct(item);
        
        // Set the existing product ID to ensure we update the correct product
        updatedProduct.setId(item.getShopifyItemId());
        logger.info("Existing ShopifyItemID: " + item.getShopifyItemId());
        
        // Merge existing product data with updated data
        productMergeService.mergeProducts(existingProduct, updatedProduct);
        logProductDetails("UPDATING", updatedProduct, item.getWebTagNumber());
        
        return updatedProduct;
    }
    
    /**
     * Updates the product on Shopify
     * Always updates basic product information, handles variant options separately
     */
    private void updateProductOnShopify(Product product, FeedItem item) throws Exception {
        logger.info(LogService.toJson(product));
        
        // ALWAYS update basic product information (title, vendor, productType, description, metafields, etc.)
        // Create a clean product object that excludes variant/option information to avoid conflicts
        logger.info("Updating basic product information for product ID: {}", product.getId());
        Product basicUpdateProduct = createBasicUpdateProduct(product);
        shopifyGraphQLService.updateProduct(basicUpdateProduct);
        logger.info("✅ Successfully updated basic product information");
        
        // SEPARATELY handle variant options updates if needed
        // This is independent of the basic product update
        try {
            boolean variantOptionsUpdated = handleVariantOptionsUpdate(product, item);
            if (variantOptionsUpdated) {
                logger.info("✅ Variant options were also updated");
            } else {
                logger.debug("No variant option updates were needed");
            }
        } catch (Exception e) {
            logger.warn("Failed to update variant options (continuing with basic product update): " + e.getMessage());
            // Don't fail the entire update if variant options fail - basic product info was already updated
        }
    }
    
    /**
     * Creates a clean product object for basic updates that excludes variant/option information
     * This prevents conflicts when updating basic product fields
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
        // These are handled separately in handleVariantOptionsUpdate
        basicProduct.setVariants(null);
        basicProduct.setOptions(null);
        
        logger.debug("Created basic update product with fields: title, description, vendor, productType, handle, tags, status, metafields");
        return basicProduct;
    }
    
    /**
     * Handles variant options updates by comparing existing options with new options
     * Uses productOptionsUpdate to modify existing option values - cannot delete options
     */
    private boolean handleVariantOptionsUpdate(Product product, FeedItem item) {
        try {
            if (product.getId() == null) {
                logger.debug("Product ID is null, cannot update variant options");
                return false;
            }
            
            // Check if the feed item has variant attributes
            String newColor = item.getWebWatchDial();
            String newSize = item.getWebWatchDiameter();
            String newMaterial = item.getWebMetalType();
            
            boolean hasVariantOptions = (newColor != null && !newColor.trim().isEmpty()) ||
                                      (newSize != null && !newSize.trim().isEmpty()) ||
                                      (newMaterial != null && !newMaterial.trim().isEmpty());
            
            if (!hasVariantOptions) {
                logger.debug("No variant options in feed item for product: {}", product.getId());
                return false;
            }
            
            // Get current product from Shopify to compare options
            Product currentProduct = shopifyGraphQLService.getProductByProductId(product.getId());
            if (currentProduct == null) {
                logger.warn("Could not fetch current product from Shopify for ID: {}", product.getId());
                return false;
            }
            
            // Check if options need updating
            boolean optionsNeedUpdate = false;
            if (currentProduct.getOptions() == null || currentProduct.getOptions().isEmpty()) {
                // No existing options, create them
                logger.info("No existing options found, creating new options for product: {}", product.getId());
                shopifyGraphQLService.createProductOptions(product.getId(), item);
                return true;
            }
            
            // Compare existing option values with new values
            for (Option existingOption : currentProduct.getOptions()) {
                String existingValue = getFirstOptionValue(existingOption);
                String newValue = null;
                
                if ("Color".equalsIgnoreCase(existingOption.getName())) {
                    newValue = newColor;
                } else if ("Size".equalsIgnoreCase(existingOption.getName())) {
                    newValue = newSize;
                } else if ("Material".equalsIgnoreCase(existingOption.getName())) {
                    newValue = newMaterial;
                }
                
                if (newValue != null && !newValue.equals(existingValue)) {
                    logger.info("Option '{}' value changed from '{}' to '{}' for product: {}", 
                              existingOption.getName(), existingValue, newValue, product.getId());
                    optionsNeedUpdate = true;
                    break;
                }
            }
            
            if (optionsNeedUpdate) {
                logger.info("Updating variant options for product: {}", product.getId());
                boolean success = shopifyGraphQLService.updateProductOptions(product.getId(), item);
                
                if (success) {
                    logger.info("✅ Successfully updated variant options for product: {}", product.getId());
                    return true;
                } else {
                    logger.error("❌ Failed to update variant options for product: {}", product.getId());
                    return false;
                }
            } else {
                logger.debug("No variant option updates needed for product: {}", product.getId());
                return false;
            }
            
        } catch (Exception e) {
            logger.error("Error handling variant options update for product: " + product.getId(), e);
            return false;
        }
    }
    
    /**
     * Helper method to get the first option value from an option
     */
    private String getFirstOptionValue(Option option) {
        if (option.getValues() != null && !option.getValues().isEmpty()) {
            return option.getValues().get(0);
        }
        return null;
    }
    
    /**
     * Handles image updates by deleting existing images and re-adding them
     */
    private void handleImageUpdatesForExistingProduct(Product existingProduct, Product updatedProduct) {
        try {
            // Force delete the images and then re-upload
            shopifyGraphQLService.deleteAllImageByProductId(existingProduct.getId());
            
            if (updatedProduct.getImages() != null && !updatedProduct.getImages().isEmpty()) {
                logger.info("Re-adding " + updatedProduct.getImages().size() + " images to updated product");
                
                logImageDetailsForUpdate(existingProduct.getId(), updatedProduct.getImages());
                
                try {
                    shopifyGraphQLService.addImagesToProduct(existingProduct.getId(), updatedProduct.getImages());
                } catch (Exception e) {
                    logger.error("Failed to re-add images to product ID: " + existingProduct.getId(), e);
                    // Continue execution - don't fail the whole update if image re-addition fails
                }
            } else {
                logger.info("Skipping image update - no images available");
            }
        } catch (Exception e) {
            logger.error("Failed to handle image updates for product ID: " + existingProduct.getId(), e);
            // Continue without failing the whole update process
        }
    }
    
    /**
     * Logs image details for debugging during updates
     */
    private void logImageDetailsForUpdate(String productId, List<Image> images) {
        logger.info("=== RE-ADDING IMAGES to Product ID: {} ===", productId);
        for (int i = 0; i < images.size(); i++) {
            Image image = images.get(i);
            logger.info("  Image[{}] URL: {}", i, image.getSrc());
        }
        logger.info("=== End RE-ADDING IMAGES ===");
    }
    
    /**
     * Updates inventory levels after product update, ensuring current inventory item IDs are used
     */
    private void updateInventoryAfterProductUpdate(FeedItem item, Product updatedProduct) throws Exception {
        // CRITICAL FIX: Re-fetch the product after update to get current inventory item ID
        logger.info("Re-fetching product after update to get current inventory item ID...");
        Product refreshedProduct = shopifyGraphQLService.getProductByProductId(item.getShopifyItemId());
        
        if (refreshedProduct == null || refreshedProduct.getVariants().isEmpty()) {
            logger.error("❌ Failed to re-fetch product after update - cannot update inventory");
            throw new RuntimeException("Product not found after update: " + item.getShopifyItemId());
        }
        
        updateInventoryWithRefreshedData(refreshedProduct, updatedProduct);
    }
    
    /**
     * Updates inventory levels using refreshed product data
     */
    private void updateInventoryWithRefreshedData(Product refreshedProduct, Product updatedProduct) throws Exception {
        String currentInventoryItemId = refreshedProduct.getVariants().get(0).getInventoryItemId();
        logger.info("Current inventory item ID after update: " + currentInventoryItemId);
        
        List<InventoryLevel> inventoryLevels = updatedProduct.getVariants().get(0).getInventoryLevels().get();
        if (inventoryLevels != null && !inventoryLevels.isEmpty()) {
            logger.info("Updating inventory levels for " + inventoryLevels.size() + " locations");
            
            // Update each inventory level with the current inventory item ID
            for (InventoryLevel level : inventoryLevels) {
                level.setInventoryItemId(currentInventoryItemId);
                logger.info("Updated inventory level - LocationId: " + level.getLocationId() + 
                           ", InventoryItemId: " + level.getInventoryItemId() + 
                           ", Available: " + level.getAvailable());
            }
            
            if (validateInventoryLevels(inventoryLevels)) {
                shopifyGraphQLService.updateInventoryLevels(inventoryLevels);
                logger.info("✅ Successfully updated inventory levels");
            } else {
                logger.error("❌ Skipping inventory update due to invalid inventory level data");
            }
        } else {
            logger.warn("⚠️ No inventory levels found to update for product: " + updatedProduct.getId());
        }
    }
    
    /**
     * Validates that all inventory levels have required fields
     */
    private boolean validateInventoryLevels(List<InventoryLevel> inventoryLevels) {
        boolean allLevelsValid = true;
        for (InventoryLevel level : inventoryLevels) {
            if (level.getInventoryItemId() == null || level.getLocationId() == null || level.getAvailable() == null) {
                logger.error("❌ Invalid inventory level detected - InventoryItemId: " + level.getInventoryItemId() + 
                           ", LocationId: " + level.getLocationId() + ", Available: " + level.getAvailable());
                allLevelsValid = false;
            }
        }
        return allLevelsValid;
    }
    
    /**
     * Updates collection associations for the product
     */
    private void updateCollectionAssociations(FeedItem item) throws Exception {
        // Delete existing collects
        shopifyGraphQLService.deleteAllCollectForProductId(item.getShopifyItemId());
        
        // Get updated collections and create associations
        List<Collect> updatedCollections = 
            CollectionUtility.getCollectionForProduct(item.getShopifyItemId(), item, getCollectionMappings());
        shopifyGraphQLService.addProductAndCollectionsAssociations(updatedCollections);
    }
    
    /**
     * Finalizes successful update by updating item status and logging
     */
    private void finalizeSuccessfulUpdate(FeedItem item) {
        item.setStatus(FeedItem.STATUS_UPDATED);
        feedItemService.updateAutonomous(item);
        
        String message = getItemActionLogMessage("UPDATED", item);
        logger.info(message);
    }
    
    /**
     * Handles update failure by logging error and updating item status
     */
    private void handleUpdateFailure(FeedItem item, Exception e) {
        logService.emailError(logger,
            "Shopify Bot: Failed to update Sku: " + item.getWebTagNumber() + " with exception: " + e.getMessage(), 
            null, e);
        item.setStatus(FeedItem.STATUS_UPDATE_FAILED);
        item.setSystemMessages(e.getMessage());
        feedItemService.updateAutonomous(item);
    }
    
    @Override
    public void publishItemToShopify(FeedItem item){
        try {
            logger.info("Publishing Sku: " + item.getWebTagNumber());
            
            //Download images (skip if configured)
            if (!skipImageDownload) {
                logger.info("Downloading images for SKU: " + item.getWebTagNumber());
                imageService.downloadImages(item);
            } else {
                logger.info("Skipping image download for SKU: " + item.getWebTagNumber() + " (skip.image.download=true)");
            }
        	
            // Use the two-step approach for creating products with variant options
            // This method creates the product in Shopify AND adds options, returning the complete product
            Product newlyAddedProduct = shopifyProductFactoryService.createProductWithOptions(item);
            
            // Verify product was created successfully
            if (newlyAddedProduct == null || newlyAddedProduct.getId() == null) {
                throw new RuntimeException("Failed to create product - no product ID returned");
            }
            
            // Log product details after creation
            logProductDetails("CREATED", newlyAddedProduct, item.getWebTagNumber());
            
            // Add images to the newly created product (GraphQL migration fix)
            // Send images to Shopify when they are available
            if (newlyAddedProduct.getImages() != null && !newlyAddedProduct.getImages().isEmpty()) {
                logger.info("Adding " + newlyAddedProduct.getImages().size() + " images to newly created product");
                
                // Log image details before sending to Shopify
                logger.info("=== ADDING IMAGES to Product ID: {} ===", newlyAddedProduct.getId());
                for (int i = 0; i < newlyAddedProduct.getImages().size(); i++) {
                    Image image = newlyAddedProduct.getImages().get(i);
                    logger.info("  Image[{}] URL: {}", i, image.getSrc());
                }
                logger.info("=== End ADDING IMAGES ===");
                
                try {
                    shopifyGraphQLService.addImagesToProduct(newlyAddedProduct.getId(), newlyAddedProduct.getImages());
                } catch (Exception e) {
                    logger.error("Failed to add images to product ID: " + newlyAddedProduct.getId(), e);
                }
            } else {
                logger.info("Skipping image upload - no images available");
            }
            
            String inventoryItemId = newlyAddedProduct.getVariants().get(0).getInventoryItemId();
            
            // GraphQL API returns List<InventoryLevel> directly, not InventoryLevels wrapper
            List<InventoryLevel> levelsList = shopifyGraphQLService.getInventoryLevelByInventoryItemId(inventoryItemId);
            
            // Convert List<InventoryLevel> back to InventoryLevels wrapper for compatibility with factory
            InventoryLevels levels = new InventoryLevels();
            for (InventoryLevel level : levelsList) {
                levels.addInventoryLevel(level);
            }
            
            //Get the count from the product, that's created by the factory from the feed item.
            //Set the inventoryItemId for update.
            shopifyProductFactoryService.mergeInventoryLevels(levels, newlyAddedProduct.getVariants().get(0).getInventoryLevels());
            
            // GraphQL API expects List<InventoryLevel>
            List<InventoryLevel> inventoryLevelsToUpdate = newlyAddedProduct.getVariants().get(0).getInventoryLevels().get();
            if (inventoryLevelsToUpdate != null && !inventoryLevelsToUpdate.isEmpty()) {
                logger.info("Updating inventory levels for " + inventoryLevelsToUpdate.size() + " locations");
                
                // Validate that each inventory level has required fields
                boolean allLevelsValid = true;
                for (InventoryLevel level : inventoryLevelsToUpdate) {
                    if (level.getInventoryItemId() == null || level.getLocationId() == null || level.getAvailable() == null) {
                        logger.error("❌ Invalid inventory level detected - InventoryItemId: " + level.getInventoryItemId() + 
                                   ", LocationId: " + level.getLocationId() + ", Available: " + level.getAvailable());
                        allLevelsValid = false;
                    }
                }
                
                if (allLevelsValid) {
                    try {
                        shopifyGraphQLService.updateInventoryLevels(inventoryLevelsToUpdate);
                        logger.info("✅ Successfully updated inventory levels for new product");
                    } catch (Exception e) {
                        logger.error("❌ Failed to update inventory levels for new product: " + e.getMessage());
                        // Continue - inventory update failure shouldn't stop the publish process
                    }
                } else {
                    logger.error("❌ Skipping inventory update due to invalid inventory level data");
                    logger.error("❌ This may indicate an issue with inventory level merging");
                }
            } else {
                logger.warn("⚠️ No inventory levels found to update for new product: " + newlyAddedProduct.getId());
            }
            
            // IMPORTANT: Add collection associations BEFORE updating item status
            // This ensures the product is associated with collections when the test checks
            try {
                Map<PredefinedCollection, CustomCollection> collectionMappings = getCollectionMappings();
                List<Collect> collectsToAdd = CollectionUtility.getCollectionForProduct(newlyAddedProduct.getId(), item, collectionMappings);
                
                if (!collectsToAdd.isEmpty()) {
                    shopifyGraphQLService.addProductAndCollectionsAssociations(collectsToAdd);
                    logger.info("Successfully added product to " + collectsToAdd.size() + " collections");
                } else {
                    logger.warn("No collections found for product " + newlyAddedProduct.getId() + " (SKU: " + item.getWebTagNumber() + ")");
                }
            } catch (Exception e) {
                logger.error("Failed to add product to collections for SKU: " + item.getWebTagNumber(), e);
                // Continue - collection association failure shouldn't stop the publish process
            }
            
            // CRITICAL: Publish the product to ALL available sales channels
            // This ensures the product is visible and available for purchase on all channels
            try {
                shopifyGraphQLService.publishProductToAllChannels(newlyAddedProduct.getId());
                logger.info("Successfully published product to all sales channels");
            } catch (Exception e) {
                logger.warn("Failed to publish product to all channels (product may already be published): " + e.getMessage());
                // Continue - the product may already be published to some channels
            }
            
            item.setStatus(FeedItem.STATUS_PUBLISHED);
            item.setShopifyItemId(newlyAddedProduct.getId());
            item.setPublishedDate(new Date());
            feedItemService.updateAutonomous(item);

            String logMsg = getItemActionLogMessage("PUBLISHED", item);
            logger.info(logMsg);
            this.sendEmailPublishAlertEmail(logMsg, logMsg);
            
        }catch (Exception e) {
            logService.emailError(logger,
                    "Failed to publish Sku: "+item.getWebTagNumber()+" with exception: " + e.getMessage(), null, e);
            item.setStatus(FeedItem.STATUS_PUBLISHED_FAILED);
            item.setSystemMessages(e.getMessage());
            feedItemService.updateAutonomous(item);
        }
    }
    
    @Override
	public void removeExtraItemsInDBAndInShopify() {

    }
    
    private void sendEmailPublishAlertEmail(String title, String body){
        if (this.emailPublishEnabled)
            emailService.sendMessage(this.emailPublishSendTo, title, body);
    }
    
    /**
     * Check if images have valid URLs that can be downloaded by Shopify
     * This helps avoid sending invalid or empty URLs
     */
    private boolean hasValidImageUrls(List<Image> images) {
        if (images == null || images.isEmpty()) {
            return false;
        }
        
        for (Image image : images) {
            String src = image.getSrc();
            if (src == null || src.trim().isEmpty()) {
                return false;
            }
            // Only filter out obviously invalid URLs
            if (!src.startsWith("http://") && !src.startsWith("https://")) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Log comprehensive product details including all image URLs for debugging
     * This helps track what data is being sent to Shopify API calls
     */
    private void logProductDetails(String operation, Product product, String sku) {
        logger.info("=== {} Product Details for SKU: {} ===", operation, sku);
        logger.info("Product ID: {}", product.getId());
        logger.info("Title: {}", product.getTitle());
        logger.info("Handle: {}", product.getHandle());
        logger.info("Vendor: {}", product.getVendor());
        logger.info("Product Type: {}", product.getProductType());
        logger.info("Tags: {}", product.getTags());
        logger.info("Status: {}", product.getStatus());
        
        // Log description (truncated if too long)
        String description = product.getBodyHtml();
        if (description != null && description.length() > 200) {
            logger.info("Description: {}... (truncated, length={})", description.substring(0, 200), description.length());
        } else {
            logger.info("Description: {}", description);
        }
        
        // Log variants
        if (product.getVariants() != null && !product.getVariants().isEmpty()) {
            logger.info("Variants count: {}", product.getVariants().size());
            for (int i = 0; i < product.getVariants().size(); i++) {
                Variant variant = product.getVariants().get(i);
                logger.info("  Variant[{}]: SKU={}, Price={}, ID={}", 
                    i, variant.getSku(), variant.getPrice(), variant.getId());
            }
        }
        
        // Log images with full details
        if (product.getImages() != null && !product.getImages().isEmpty()) {
            logger.info("Images count: {}", product.getImages().size());
            for (int i = 0; i < product.getImages().size(); i++) {
                Image image = product.getImages().get(i);
                logger.info("  Image[{}]: ID={}, URL={}, Position={}", 
                    i, image.getId(), image.getSrc(), image.getPosition());
            }
        } else {
            logger.info("Images: None");
        }
        
        logger.info("=== End {} Product Details ===", operation);
    }
}
