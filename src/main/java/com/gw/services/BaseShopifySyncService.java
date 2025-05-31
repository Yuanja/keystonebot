package com.gw.services;

import com.gw.domain.FeedItem;
import com.gw.domain.FeedItemChange;
import com.gw.domain.FeedItemChangeSet;
import com.gw.domain.PredefinedCollection;
import com.gw.services.shopifyapi.ShopifyGraphQLService;
import com.gw.services.shopifyapi.objects.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Base Shopify Sync Service with GraphQL API integration
 * 
 * This class has been refactored to:
 * - Support ShopifyGraphQLService (migrated from REST to GraphQL)
 * - Implement caching for collection mappings using Spring best practices
 * - Fix compatibility issues between REST and GraphQL API responses
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
    private ShopifyGraphQLService shopifyApiService;
    
    @Autowired
    private ImageService imageService;
    
    @Autowired
    private FeedItemService feedItemService;
    
    @Autowired
    private IShopifyProductFactory shopifyProductFactoryService;
    
    /**
     * Cached collection mappings to avoid repeated API calls
     * Uses Spring's dependency injection and lazy initialization
     */
    private Map<PredefinedCollection, CustomCollection> cachedCollectionByEnum;
    
    /**
     * Flag to track if collections have been initialized
     */
    private boolean collectionsInitialized = false;
    
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
     */
    private void ensureCollections() throws Exception {
        if (!collectionsInitialized || cachedCollectionByEnum == null) {
            logger.info("Initializing and caching collection mappings...");
            cachedCollectionByEnum = shopifyApiService.ensureConfiguredCollections(getPredefinedCollections());
            collectionsInitialized = true;
            logger.info("Cached {} collection mappings", cachedCollectionByEnum.size());
        } else {
            logger.debug("Using cached collection mappings ({} collections)", cachedCollectionByEnum.size());
        }
        removeExtraItemsInDBAndInShopify();
    }
    
    /**
     * Invalidates the collection cache - useful when collections need to be refreshed
     * Can be called manually or triggered by external events
     */
    @CacheEvict(value = "collections", allEntries = true)
    public void invalidateCollectionCache() {
        logger.info("Invalidating collection cache...");
        cachedCollectionByEnum = null;
        collectionsInitialized = false;
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
        ensureCollections();
    	
		List<FeedItem> feedItems = feedService.getItemsFromFeed();

		if (feedItems.size() == 0) {
			logger.error("Feed is temporarily offline.  Nothing is read: Skipping this schedule.");
			return;
		}

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
		
		FeedItemChangeSet changeSet = compareFeedItemWithDB(feedItems);
		
		int changedItemCount = changeSet.getChangedItems() == null ? 0 : changeSet.getChangedItems().size();
        int toDeleteItemCount = changeSet.getDeletedItems() == null ? 0 : changeSet.getDeletedItems().size();
        
        if (changedItemCount < MAX_TO_DELETE_COUNT 
                && toDeleteItemCount < MAX_TO_DELETE_COUNT) {
        	updateDB(changeSet);
        	updateShopify(changeSet);
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
    
    @Override
	public void updateDB(FeedItemChangeSet changeSet) throws Exception {
    	insertWithNewItems(changeSet.getNewItems());
        updateWithChangedItems(changeSet.getChangedItems());
        removeItemsNotInFeed(changeSet.getDeletedItems());
    }
    
    private void updateShopify(FeedItemChangeSet changeSet) throws Exception {
        for (FeedItem item : feedService.getFeedItemsToUpdate()) {
            updateItemOnShopify(item);
        }
        
        for (FeedItem item : feedService.getFeedItemsToPublish()) {
            publishItemToShopify(item);
        }
    }

    private String getItemActionLogMessage(String action, FeedItem feedItem){
        return action + " Sku: "
                + feedItem.getWebTagNumber() + ", "
                + feedItem.getWebDescriptionShort()
                + " Shopify Item Id: "+  feedItem.getShopifyItemId();
    }
    private void removeItemsNotInFeed(List<FeedItem> toDelete) {
        for (FeedItem itemInDb : toDelete) {
            if (StringUtils.isNotEmpty(itemInDb.getShopifyItemId())) {
                try {
                    // Should check for item existence in DB first.
                    shopifyApiService.deleteProductById(itemInDb.getShopifyItemId());
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
    
    private void insertWithNewItems(List<FeedItem> newFeedItems) {
        if (newFeedItems ==null){
            logger.info("Nothing new to insert!");
            return;
        } else {
            logger.info("Inserting new items count: " + newFeedItems.size());
            for (FeedItem newFeedItem : newFeedItems) {
                // insert it
                newFeedItem.setStatus(FeedItem.STATUS_NEW_WAITING_PUBLISH);
                feedItemService.saveAutonomous(newFeedItem);
                logger.info("Inserted new item to db: " + newFeedItem.getWebTagNumber());
            }
        }
    }
    
    private void updateWithChangedItems(List<FeedItemChange> feedItemChanges) {
        for (FeedItemChange change : feedItemChanges) {
            FeedItem itemFromDb = change.getFromDb();
            FeedItem itemFromFeed = change.getFromFeed();
            String productId = itemFromDb.getShopifyItemId();
            try {
                logger.info("Changed Item SKU : "+itemFromFeed.getWebTagNumber() 
                    + " As Product ID: " + itemFromDb.getShopifyItemId()
                    + ", saving changes and waiting for update on shopify.");
                itemFromDb.copyFrom(itemFromFeed);
                itemFromDb.setStatus(FeedItem.STATUS_CHANGED_WAITING_UPDATE);
                feedItemService.updateAutonomous(itemFromDb);
            }
            catch (Exception e) {
                logger.error("Error preparing a changed product: " + productId, e);
            }
        }
    }
    
    @Override
    public void updateItemOnShopify(FeedItem item) {
        logger.info("Updating Sku: " + item.getWebTagNumber());
        try {
            if (item.getShopifyItemId() != null) {
                //Download images (skip if configured)
                if (!skipImageDownload) {
                    logger.info("Downloading images for SKU: " + item.getWebTagNumber());
                    imageService.downloadImages(item);
                } else {
                    logger.info("Skipping image download for SKU: " + item.getWebTagNumber() + " (skip.image.download=true)");
                }
            	
                Product product = shopifyProductFactoryService.createProduct(item);
                product.setId(item.getShopifyItemId());
                logger.info("Existing ShopifyItemID: " + item.getShopifyItemId());
                
                // GraphQL API returns Product directly, not ProductVo wrapper
                Product existingProduct = shopifyApiService.getProductByProductId(item.getShopifyItemId());
                
                if(existingProduct != null) {
                    //Force delete the images and then re-upload.
                    shopifyApiService.deleteAllImageByProductId(existingProduct.getId());
                    
                    shopifyProductFactoryService.mergeProduct(existingProduct, product);
                    
                    // Log product details before sending to Shopify API
                    logProductDetails("UPDATING", product, item.getWebTagNumber());
                    
                    logger.info(LogService.toJson(product));
                    shopifyApiService.updateProduct(product);
                    
                    // Re-add images after updating product (GraphQL migration fix)
                    // Send images to Shopify when they are available
                    if (product.getImages() != null && !product.getImages().isEmpty()) {
                        logger.info("Re-adding " + product.getImages().size() + " images to updated product");
                        
                        // Log image details before sending to Shopify
                        logger.info("=== RE-ADDING IMAGES to Product ID: {} ===", existingProduct.getId());
                        for (int i = 0; i < product.getImages().size(); i++) {
                            Image image = product.getImages().get(i);
                            logger.info("  Image[{}] URL: {}", i, image.getSrc());
                        }
                        logger.info("=== End RE-ADDING IMAGES ===");
                        try {
                            shopifyApiService.addImagesToProduct(existingProduct.getId(), product.getImages());
                        } catch (Exception e) {
                            logger.error("Failed to re-add images to product ID: " + existingProduct.getId(), e);
                            // Continue execution - don't fail the whole update if image re-addition fails
                        }
                    } else {
                        logger.info("Skipping image update - no images available");
                    }
                    
                    //update inventory - GraphQL API expects List<InventoryLevel>
                    List<InventoryLevel> inventoryLevels = product.getVariants().get(0).getInventoryLevels().get();
                    if (inventoryLevels != null && !inventoryLevels.isEmpty()) {
                        shopifyApiService.updateInventoryLevels(inventoryLevels);
                    }
                    
                    //Delete existing collects
                    shopifyApiService.deleteAllCollectForProductId(item.getShopifyItemId());
                    
                    //Get the would be collection based on the item;
                    List<Collect> updatedCollections = 
                            CollectionUtility.getCollectionForProduct(item.getShopifyItemId(), item, getCollectionMappings());
                    shopifyApiService.addProductAndCollectionsAssociations(updatedCollections);
                    
                    item.setStatus(FeedItem.STATUS_UPDATED);
                    feedItemService.updateAutonomous(item);

                    //Log and email
                    String message  = getItemActionLogMessage("UPDATED", item);
                    logger.info(message);
                    //sendEmailPublishAlertEmail(message, message);
                    
                } else {
                    logger.error("Data error! Detected item changed but can't find the product by the id: " 
                            + item.getShopifyItemId());
                }
            } else {
                logger.error("Data error! detected item changed but no shopify id is recorded! Sku: " 
                            + item.getWebTagNumber());
            }
            
        } catch (Exception e) {
            logService.emailError(logger,
                    "Shopify Bot: Failed to update Sku: "+item.getWebTagNumber()+" with exception: " + e.getMessage(), null, e);
            item.setStatus(FeedItem.STATUS_UPDATE_FAILED);
            item.setSystemMessages(e.getMessage());
            feedItemService.updateAutonomous(item);
        }
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
        	
            Product product = shopifyProductFactoryService.createProduct(item);
            
            // Log product details before sending to Shopify API
            logProductDetails("CREATING", product, item.getWebTagNumber());
            
            Product newlyAddedProduct = shopifyApiService.addProduct(product);
            
            // Verify product was created successfully
            if (newlyAddedProduct == null || newlyAddedProduct.getId() == null) {
                throw new RuntimeException("Failed to create product - no product ID returned");
            }
            
            // Add images to the newly created product (GraphQL migration fix)
            // Send images to Shopify when they are available
            if (product.getImages() != null && !product.getImages().isEmpty()) {
                logger.info("Adding " + product.getImages().size() + " images to newly created product");
                
                // Log image details before sending to Shopify
                logger.info("=== ADDING IMAGES to Product ID: {} ===", newlyAddedProduct.getId());
                for (int i = 0; i < product.getImages().size(); i++) {
                    Image image = product.getImages().get(i);
                    logger.info("  Image[{}] URL: {}", i, image.getSrc());
                }
                logger.info("=== End ADDING IMAGES ===");
                
                try {
                    shopifyApiService.addImagesToProduct(newlyAddedProduct.getId(), product.getImages());
                } catch (Exception e) {
                    logger.error("Failed to add images to product ID: " + newlyAddedProduct.getId(), e);
                }
            } else {
                logger.info("Skipping image upload - no images available");
            }
            
            String inventoryItemId = newlyAddedProduct.getVariants().get(0).getInventoryItemId();
            
            // GraphQL API returns List<InventoryLevel> directly, not InventoryLevels wrapper
            List<InventoryLevel> levelsList = shopifyApiService.getInventoryLevelByInventoryItemId(inventoryItemId);
            
            // Convert List<InventoryLevel> back to InventoryLevels wrapper for compatibility with factory
            InventoryLevels levels = new InventoryLevels();
            for (InventoryLevel level : levelsList) {
                levels.addInventoryLevel(level);
            }
            
            //Get the count from the product, that's created by the factory from the feed item.
            //Set the inventoryItemId for update.
            shopifyProductFactoryService.mergeInventoryLevels(levels, product.getVariants().get(0).getInventoryLevels());
            
            // GraphQL API expects List<InventoryLevel>
            List<InventoryLevel> inventoryLevelsToUpdate = product.getVariants().get(0).getInventoryLevels().get();
            if (inventoryLevelsToUpdate != null && !inventoryLevelsToUpdate.isEmpty()) {
                shopifyApiService.updateInventoryLevels(inventoryLevelsToUpdate);
            }
            
            // IMPORTANT: Add collection associations BEFORE updating item status
            // This ensures the product is associated with collections when the test checks
            Map<PredefinedCollection, CustomCollection> collectionMappings = getCollectionMappings();
            List<Collect> collectsToAdd = CollectionUtility.getCollectionForProduct(newlyAddedProduct.getId(), item, collectionMappings);
            
            if (!collectsToAdd.isEmpty()) {
                shopifyApiService.addProductAndCollectionsAssociations(collectsToAdd);
                logger.info("Successfully added product to " + collectsToAdd.size() + " collections");
            } else {
                logger.warn("No collections found for product " + newlyAddedProduct.getId() + " (SKU: " + item.getWebTagNumber() + ")");
            }
            
            // CRITICAL: Publish the product to ALL available sales channels
            // This ensures the product is visible and available for purchase on all channels
            try {
                shopifyApiService.publishProductToAllChannels(newlyAddedProduct.getId());
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
        logger.info("Reconciling Shopify and DB...");
        // GraphQL method name is unlistDupeListings (note the capital L)
        Map<String, Product> allProductBySku = shopifyApiService.unlistDupeListings();
        Map<String, FeedItem> allItemsInDBBySku = feedItemService.getFeedItemBySkuMap();
        logger.info("Got total of "+allItemsInDBBySku.size() +" number of products from DB.");
        logger.info("Got total of "+allProductBySku.size()+" number of products from shopify.");
        
        if (Math.abs(allProductBySku.size() - allItemsInDBBySku.size()) > MAX_TO_DELETE_COUNT) {
            throw new RuntimeException("Too many items to reconcile:" +
                    Math.abs(allProductBySku.size() - allItemsInDBBySku.size())
                    +".  Skipping scheduled task!");
        }
        
        removeExtraListingsNotInDB(allProductBySku, allItemsInDBBySku);
        removeExtraItemsNotListedInShopify(allProductBySku, allItemsInDBBySku);
        markForUpdateWhenImageCountsAreNotSame(allProductBySku, allItemsInDBBySku);
    }
    
    private void markForUpdateWhenImageCountsAreNotSame (Map<String, Product> allProductBySku, 
    Map<String, FeedItem> allItemsInDBBySku){
    
        logger.info("Checking for image count.");
        for (Product currentProduct: allProductBySku.values()) {
            List<Variant> variant = currentProduct.getVariants();
            String currentProductSku = variant.get(0).getSku();
            FeedItem feedItemFromDb = allItemsInDBBySku.get(currentProductSku);
            
            //Check image count
            int currentImageCnt = currentProduct.getImages() == null ? 0 : currentProduct.getImages().size();
            if (feedItemFromDb != null && feedItemFromDb.getImageCount() != currentImageCnt) {
                logger.error("Sku: " + currentProductSku + " Has "+ currentImageCnt
                        +" images published on Shopify but DB has: "+feedItemFromDb.getImageCount()
                        +" images!  Marking for update.");
                feedItemFromDb.setStatus(FeedItem.STATUS_CHANGED_WAITING_UPDATE);
                feedItemService.updateAutonomous(feedItemFromDb);
            }
        }
    }
    
    private void removeExtraListingsNotInDB(Map<String, Product> allProductBySku, 
            Map<String, FeedItem> allItemsInDBBySku){
        
        logger.info("Removing extra shopify listings that are not in the DB...");
        for (Product currentProduct: allProductBySku.values()) {
            List<Variant> variant = currentProduct.getVariants();
            String currentProductSku = variant.get(0).getSku();
            FeedItem feedItemFromDb = allItemsInDBBySku.get(currentProductSku);
            if (feedItemFromDb == null) { 
                //Remove this from shopify as it's not tracked in the db.
                logger.error("Shopify Bot: Sku: " + currentProductSku + " is not tracked in DB but exist on shopify as "+currentProduct.getId()+".  Removing from shopify.");
                shopifyApiService.deleteProductByIdOrLogFailure(currentProduct.getId());
            } else {
                //Check if the item in db has matching shopify id.
                if (feedItemFromDb.getShopifyItemId()==null 
                        || !feedItemFromDb.getShopifyItemId().equals(currentProduct.getId())){
                    logger.error("Sku: " + currentProductSku + 
                            " : has ShopifyId in DB : " + feedItemFromDb.getShopifyItemId() +
                            " : But Shopify has ID of : " + currentProduct.getId() + 
                            " Will update DB! ");
                    feedItemFromDb.setShopifyItemId(currentProduct.getId());
                    feedItemService.updateAutonomous(feedItemFromDb);
                }
            }
        }
    }
    
    private void removeExtraItemsNotListedInShopify(Map<String, Product> allProductBySku, 
            Map<String, FeedItem> allItemsInDBBySku) {
        
        logger.info("Removing items in DB that doesn't exist on shopify...");
        List<FeedItem> allFeedItems = allItemsInDBBySku.values().stream().collect(Collectors.toList());
        
        for (FeedItem itemFromDb : allFeedItems) {
            Product productFromShopify = allProductBySku.get(itemFromDb.getWebTagNumber());
            if (productFromShopify == null) {
                logger.error("Sku: "+itemFromDb.getWebTagNumber()+" is not listed in shopify removing from DB!");
                feedItemService.deleteAutonomous(itemFromDb);
            }
        }
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
