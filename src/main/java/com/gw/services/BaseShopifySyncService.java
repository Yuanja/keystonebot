package com.gw.services;

import com.gw.domain.FeedItem;
import com.gw.domain.FeedItemChange;
import com.gw.domain.FeedItemChangeSet;
import com.gw.domain.PredefinedCollection;
import com.gw.services.shopifyapi.ShopifyAPIService;
import com.gw.services.shopifyapi.objects.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author jyuan
 *
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

    @Autowired
    private EmailService emailService;

    @Autowired
    private LogService logService;
    
    @Autowired(required=false)
    private IFeedService feedService;
    
    @Autowired
    private ShopifyAPIService shopifyApiService;
    
    @Autowired
    private ImageService imageService;
    
    @Autowired
    private FeedItemService feedItemService;
    
    @Autowired
    private IShopifyProductFactory shopifyProductFactoryService;
    
  
    
    @Override 
    public abstract PredefinedCollection[] getPredefinedCollections();

    @Override
	public void sync(boolean feedReady) throws Exception {
        if (feedReady){
            doSync();
        }
    }

    private void doSync() throws Exception {
        Map<PredefinedCollection, CustomCollection>  collectionByEnum = shopifyApiService.ensureConfiguredCollections(getPredefinedCollections());
    	removeExtraItemsInDBAndInShopify(collectionByEnum);
    	
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
        	updateShopify(collectionByEnum, changeSet);
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
    
    private void updateShopify(Map<PredefinedCollection, CustomCollection> collectionByEnum, FeedItemChangeSet changeSet) throws Exception {
        for (FeedItem item : feedService.getFeedItemsToUpdate()) {
            updateItemOnShopify(item, collectionByEnum);
        }
        
        for (FeedItem item : feedService.getFeedItemsToPublish()) {
            publishItemToShopify(item, collectionByEnum);
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
    
    private void updateItemOnShopify(FeedItem item, Map<PredefinedCollection, CustomCollection> collectionByEnum) {
        logger.info("Updating Sku: " + item.getWebTagNumber());
        try {
            if (item.getShopifyItemId() != null) {
                //Download images
            	imageService.downloadImages(item);
            	
                Product product = shopifyProductFactoryService.createProduct(item);
                product.setId(item.getShopifyItemId());
                logger.info("Existing ShopifyItemID: " + item.getShopifyItemId());
                ProductVo existingProductVo = 
                        shopifyApiService.getProductByProductId(item.getShopifyItemId());
                
                if(existingProductVo !=null && existingProductVo.get() != null) {
                    Product existingProduct = existingProductVo.get();
                    
                    //Force delete the images and then re-upload.
                    shopifyApiService.deleteAllImageByProductId(existingProduct.getId());
                    
                    shopifyProductFactoryService.mergeProduct(existingProduct, product);
                    logger.info(LogService.toJson(product));
                    shopifyApiService.updateProduct(product);
                    
                    //update inventory
                    shopifyApiService.updateInventoryLevels(product.getVariants().get(0).getInventoryLevels());
                    
                    //Delete existing collects
                    shopifyApiService.deleteAllCollectForProductId(item.getShopifyItemId());
                    
                    //Get the would be collection based on the item;
                    List<Collect> updatedCollections = 
                            CollectionUtility.getCollectionForProduct(item.getShopifyItemId(), item, collectionByEnum);
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
    
    private void publishItemToShopify(FeedItem item, Map<PredefinedCollection, CustomCollection> collectionByEnum){
        try {
            logger.info("Publishing Sku: " + item.getWebTagNumber());
            
            //Download images
        	imageService.downloadImages(item);
        	
            Product product = shopifyProductFactoryService.createProduct(item);
            Product newlyAddedProduct = shopifyApiService.addProduct(product);
            
            String inventoryItemId = newlyAddedProduct.getVariants().get(0).getInventoryItemId();
            //get the inventory and then update the count to 1.
            InventoryLevels levels = shopifyApiService.getInventoryLevelByInventoryItemId(inventoryItemId);
            
            //Get the count from the product, that's created by the factory from the feed item.
            //Set the inventoryItemId for update.
            shopifyProductFactoryService.mergeInventoryLevels(levels, product.getVariants().get(0).getInventoryLevels());
            shopifyApiService.updateInventoryLevels(product.getVariants().get(0).getInventoryLevels());
            
            item.setStatus(FeedItem.STATUS_PUBLISHED);
            item.setShopifyItemId(newlyAddedProduct.getId());
            item.setPublishedDate(new Date());
            feedItemService.updateAutonomous(item);
            
            shopifyApiService.addProductAndCollectionsAssociations(
                    CollectionUtility.getCollectionForProduct(newlyAddedProduct.getId(), item, collectionByEnum));

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
	public void removeExtraItemsInDBAndInShopify(Map<PredefinedCollection, CustomCollection> collectionByEnum) {
        logger.info("Reconciling Shopify and DB...");
        Map<String, Product> allProductBySku = shopifyApiService.unlistDupelistings();
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
}
