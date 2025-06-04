package com.gw.services;

import com.gw.domain.FeedItem;
import com.gw.domain.FeedItemChange;
import com.gw.domain.FeedItemChangeSet;
import com.gw.domain.PredefinedCollection;
import com.gw.services.shopifyapi.ShopifyGraphQLService;
import com.gw.services.sync.ProductUpdatePipeline;
import com.gw.services.sync.ProductPublishPipeline;
import com.gw.services.sync.SyncConfigurationService;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import java.util.*;

/**
 * Base Shopify Sync Service with GraphQL API integration
 * 
 * REFACTORED to use specialized pipeline services:
 * - ProductUpdatePipeline: Handles complete product update workflow
 * - ProductPublishPipeline: Handles complete product publishing workflow  
 * - CollectionManagementService: Manages collection operations
 * - InventoryManagementService: Manages inventory operations
 * - SyncConfigurationService: Handles initialization and configuration
 * 
 * This class now focuses on:
 * - High-level sync orchestration
 * - Feed processing and change detection
 * - Delegating to specialized services
 * - Error handling and logging
 * 
 * Benefits:
 * - Clean separation of concerns
 * - Testable individual workflows
 * - Reusable pipeline components
 * - Simplified main service logic
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

    @Autowired
    private EmailService emailService;

    @Autowired
    private LogService logService;
    
    @Autowired(required=false)
    private IFeedService feedService;
    
    @Autowired
    private ShopifyGraphQLService shopifyGraphQLService;
    
    @Autowired
    private FeedItemService feedItemService;
    
    // Specialized Pipeline Services
    @Autowired
    private ProductUpdatePipeline productUpdatePipeline;
    
    @Autowired
    private ProductPublishPipeline productPublishPipeline;
    
    @Autowired
    private SyncConfigurationService syncConfigurationService;
    
    @Override 
    public abstract PredefinedCollection[] getPredefinedCollections();

    @Override
	public void sync(boolean feedReady) throws Exception {
        if (feedReady){
            doSync();
        }
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
		        
        // Use SyncConfigurationService for initialization
        syncConfigurationService.ensureCollections(getPredefinedCollections());
        syncConfigurationService.ensureMetafieldDefinitions();

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
        logger.info("🔄 Delegating product update to ProductUpdatePipeline for SKU: {}", item.getWebTagNumber());
        
        ProductUpdatePipeline.ProductUpdateResult result = productUpdatePipeline.executeUpdate(item);
        
        if (result.isSuccess()) {
            // Update item status and finalize
            item.setStatus(FeedItem.STATUS_UPDATED);
            feedItemService.updateAutonomous(item);
            
            String message = getItemActionLogMessage("UPDATED", item);
            logger.info(message);
        } else {
            // Handle failure
            Exception error = result.getError();
            logService.emailError(logger,
                "Shopify Bot: Failed to update Sku: " + item.getWebTagNumber() + " with exception: " + error.getMessage(), 
                null, error);
            item.setStatus(FeedItem.STATUS_UPDATE_FAILED);
            item.setSystemMessages(error.getMessage());
            feedItemService.updateAutonomous(item);
        }
    }
    
    @Override
    public void publishItemToShopify(FeedItem item){
        logger.info("🚀 Delegating product publish to ProductPublishPipeline for SKU: {}", item.getWebTagNumber());
        
        ProductPublishPipeline.ProductPublishResult result = productPublishPipeline.executePublish(item);
        
        if (result.isSuccess()) {
            logger.info("✅ Product successfully published for SKU: {}", item.getWebTagNumber());
        } else {
            logger.error("❌ Product publish failed for SKU: {}", item.getWebTagNumber());
            // Error handling is already done in the pipeline, but we could add additional logic here if needed
        }
    }
    
    @Override
	public void removeExtraItemsInDBAndInShopify() {

    }
    
    private void sendEmailPublishAlertEmail(String title, String body){
        if (this.emailPublishEnabled)
            emailService.sendMessage(this.emailPublishSendTo, title, body);
    }
}
