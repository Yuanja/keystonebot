package com.gw.services;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.ebay.soap.eBLBaseComponents.ItemType;
import com.gw.domain.FeedItem;
import com.gw.services.ebayapi.EBayApiService;
import com.gw.services.gwebaybot.GWEBayBotFeedService;

/**
 * @author jyuan
 *
 */
@Component
@Profile("gwebay-prod")
public class EBaySyncService implements ISyncService {
    
    final static Logger logger = LogManager.getLogger(EBaySyncService.class);

    private @Value("${MAX_TO_DELETE_COUNT}") int maxDeletePerFeed;

    @Autowired
    private LogService logService;
    
    @Autowired
    private EBayApiService ebayService;
    
    @Autowired(required=false)
    private GWEBayBotFeedService feedService;
    
    @Autowired
    private FeedItemService feedItemService;
    
    @Autowired
    private ImageService imageService;
    
    @Autowired
    private BaseEBayItemFactory ebayItemFactory; 
    
    /* (non-Javadoc)
     * @see com.gw.components.IGWEbayBotService#readFeedAndPost()
     */
    @Override
	public void sync(boolean feedReady) throws Exception{
        if (feedReady){
            doSync();
        }
    }

    private void doSync() throws Exception {
        List<FeedItem> listFromFeed = feedService.getItemsFromFeed();
        
        if (feedService.hasDupes(listFromFeed)) {
            logger.error("Feed has dupes: Skipping dupes:");
            return;
        }
        
        reconcileEBayAgainstDB(listFromFeed);
        endListingsNotOnFeed(listFromFeed);
        insertNewItemsFromFeed(listFromFeed);
        listItems(feedService.getFeedItemsToPublish());
        logger.info("Finished feed processing. Waiting for the next schedule.");
        
    }
    
    public void handleSpecificSku(String sku) throws Exception{
        logger.info("Retrieving Sku from feed: " + sku);
        List<FeedItem> listFromFeed = feedService.getItemsFromFeed();
        java.util.Optional<FeedItem> foundFeedItemOpt = listFromFeed.stream().filter(c -> c.getWebTagNumber().equals(sku)).findAny();
        FeedItem fromFeed = null, fromDb = null;
        
        fromDb = feedItemService.findByWebTagNumber(sku);
        if (fromDb == null) {
            logger.info("SKU wasn't found in db.");
        }
        
        if (foundFeedItemOpt.isPresent()) {
            fromFeed = foundFeedItemOpt.get();
        } else {
            logger.info("SKU wasn't found in feed.");
        }
        
        if (fromDb == null && fromFeed == null) {
            logger.info("SKU wasn't found in feed and db, nothing to do.");
            return; 
        } else if (fromDb == null && fromFeed != null){
            feedItemService.saveAutonomous(fromFeed);
        } 
        
        fromDb = feedItemService.findByWebTagNumber(sku);
        //if they are different save it.  Else publish.
        
        listItem(fromFeed);
    }
    
    private void reconcileEBayAgainstDB(final List<FeedItem> itemsFromFeed) throws Exception {
        List<FeedItem> allItemsInDb = feedItemService.findAll();
        Set<String> activeEBayItemIds = ebayService.getAllActiveEBayItemIds();
        if (activeEBayItemIds == null) {
            logger.error("Can't perform reconcile as EBay listing read failed.");
        }
        
        logger.info("Active EBay listing count: " + activeEBayItemIds.size());
        //Avoid mass delete or delist on ebay due to EBay API failures.
        if (Math.abs(activeEBayItemIds.size() - allItemsInDb.size()) > this.maxDeletePerFeed) {
            logService.emailError(logger, "More than "+this.maxDeletePerFeed +" items to reconcile against EBay detected! ", 
                    "Too many EBay items to reconcile.  EBay Active Count: " + activeEBayItemIds.size()
                    + " Tracked In DB count: " + allItemsInDb.size() 
                    + " If this is accurate, up the maxDeletedPerFeed and restart the app.", null);
            return;
        }
        
        //Delist items that are not in the DB.
        Map<String, FeedItem> itemsInDBByEbayId = allItemsInDb.stream().filter(c-> c.getEbayItemId() != null)
                .collect(Collectors.toMap(FeedItem::getEbayItemId, Function.identity()));
        
        logger.info("Delisting from EBay not tracked in db.");
        Set<String> delistedItemsNotInDB = new HashSet<String>();
        for (String ebayId : activeEBayItemIds) {
            if (!itemsInDBByEbayId.containsKey(ebayId)) {
                logger.info("Delisting eBay item id: "+ ebayId);
                try {
                    ebayService.endItemByEBayId(ebayId);
                    delistedItemsNotInDB.add(ebayId);
                } catch (Exception e) {
                    logger.error("Error trying to end EBay listing: " + ebayId , e);
                }
            }
        }
        
        activeEBayItemIds.removeAll(delistedItemsNotInDB);
        //Remove items in DB that's not on EBay active list.  Marking it not active. 
        logger.info("Deleting items from DB where not on EBay Active-list.");
        for (FeedItem itemInDB : allItemsInDb){
            if (!activeEBayItemIds.contains(itemInDB.getEbayItemId())) {
                logger.info("Deleting from DB: "+ itemInDB.getWebTagNumber() 
                    + " : Status in DB: " + itemInDB.getStatus());
                feedItemService.deleteAutonomous(itemInDB);
            }
        }
    }
    
    private void endListingsNotOnFeed(List<FeedItem> itemsFromFeed) {
        List<FeedItem> allItemsInDb = feedItemService.findAll();
        Map<String, FeedItem> itemsFromFeedBySku = itemsFromFeed.stream()
                .collect(Collectors.toMap(FeedItem::getWebTagNumber, Function.identity()));
        
        Set<FeedItem> toDelist = new HashSet<FeedItem>();
        for (FeedItem itemInDb : allItemsInDb) {
            if (!itemsFromFeedBySku.containsKey(itemInDb.getWebTagNumber())){
                toDelist.add(itemInDb);
            }
        }
        
        //Avoid mass delist because the feed can be partial and we don't want to process those.
        if (toDelist.size() > this.maxDeletePerFeed) {
            //Way too many delisted at once must be an error.
            StringBuffer toDeleteSkuStringBuf = new StringBuffer();
            for (FeedItem toDeleteFeedItem : toDelist){
                toDeleteSkuStringBuf.append("Sku: "+toDeleteFeedItem.getWebTagNumber() + "\n");
            }
            logService.emailError(logger, 
                    "More than "+this.maxDeletePerFeed +" items to remove/delist detected! ", 
                    "Feed processing skipped.  If this is intentional, up the maxDeletePerFeed and restart!\n" 
                                    + toDeleteSkuStringBuf.toString(), null);
            return;
        }
        
        //Delist items that fell off the feed.
        logger.info("Delisting from EBay not on feed.");
        for (FeedItem itemToDelist : toDelist) {    
            ebayService.endItem(itemToDelist);
        }
    }
    
    /* (non-Javadoc)
     * @see com.gw.components.IGWFeedService#detectChangesAgainsItemsInDB()
     */
    private void insertNewItemsFromFeed(final List<FeedItem> feedItems){
        for (FeedItem itemFromFeed : feedItems){
            FeedItem itemFromDb = feedItemService.findByWebTagNumber(itemFromFeed.getWebTagNumber());
            if (itemFromDb != null){
                //Item is not the same in the DB.
                if(!itemFromDb.equals(itemFromFeed)){
                    //update the item in the db from the one in the feed.
                    itemFromDb.copyFrom(itemFromFeed);
                    feedItemService.updateAutonomous(itemFromDb);
                    logger.info("Item changed in feed, updated in db: " + itemFromDb.getWebTagNumber() 
                        + " Ebay status is: " + itemFromDb.getStatus());
                } else {
                    logger.trace("Item didn't change: " + itemFromFeed.getWebTagNumber() );
                }
            } else {
                //insert it
                itemFromFeed.setStatus(FeedItem.STATUS_NEW_WAITING_PUBLISH);
                feedItemService.saveAutonomous(itemFromFeed);
                logger.info("Inserted new item to db: " + itemFromFeed.getWebTagNumber());
            }
        }
    }
    
    
    /* (non-Javadoc)
     * @see com.gw.components.IEbayItemService#listItems(java.util.List)
     */
    public void listItems(List<FeedItem> feedItems){
        for (FeedItem feedItem : feedItems){
            listItem(feedItem);
        }
    }
    
    public void listItem(FeedItem feedItem) {
        try {
            logger.info("Listing item: " + feedItem.getWebTagNumber());
            imageService.downloadImages(feedItem);
            ItemType ebayItem = ebayItemFactory.buildItem(feedItem);
            ebayService.listItem(ebayItem);
        } catch (Throwable e) {
            logService.emailError(logger, 
                    "General Error during listing SKU: " + feedItem.getWebTagNumber(),
                    "Error: " + e.getMessage(),
                    e);
        }
    }
    
}
