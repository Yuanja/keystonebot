package com.gw.services.whatsApp;

import java.sql.Date;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import com.gw.domain.FeedItem;
import com.gw.domain.FeedItemChange;
import com.gw.domain.FeedItemChangeSet;
import com.gw.services.FeedItemService;
import com.gw.services.IFeedService;
import com.gw.services.ISyncService;
import com.gw.services.ImageService;
import com.gw.services.LogService;

/**
 * @author jyuan
 *
 */
public class BaseWhatsAppSyncService implements ISyncService {
    
    final static Logger logger = LogManager.getLogger(BaseWhatsAppSyncService.class);
    
    @Autowired
    private LogService logService;

    
    @Autowired(required=false)
    private IFeedService feedService;

    @Autowired(required=false)
    private FeedItemService feedItemService;

    @Autowired
    private ImageService imageService;

    @Autowired
    private WhatsAppService whatsAppService;

    /* (non-Javadoc)
     * @see com.gw.components.IGWEbayBotService#readFeedAndPost()
     */
    @Override
	public void sync(boolean feedReady) throws Exception{
        List<FeedItem> listFromFeed = null;
        if (feedReady){
            listFromFeed = feedService.getItemsFromFeed();
        } else {
            logger.info("Reading from previously downloaded files.");
            listFromFeed = feedService.getItemsFromTempFiles();
        }

        if (feedService.hasDupes(listFromFeed)) {
            logger.error("Feed has dupes: Skipping dupes:");
            		// Check to see if there are dupes.
            listFromFeed = feedService.trimDupes(listFromFeed);
        }

        //Reconcile with database on these records.
        FeedItemChangeSet changes = feedItemService.compareFeedItemWithDB(false, listFromFeed);
        applyChangesToDB(changes);

        if (listFromFeed != null && !listFromFeed.isEmpty()) 
            listItem(selectItem(72));
        logger.info("Finished feed processing. Waiting for the next schedule.");
    }

    private void applyChangesToDB(FeedItemChangeSet changes) {
        //Apply changes to database
        if (changes.getNewItems() != null && !changes.getNewItems().isEmpty()) {
            logger.info("Adding " + changes.getNewItems().size() + " new items to database");
            for (FeedItem item : changes.getNewItems()){
                item.setStatus(FeedItem.STATUS_NEW_WAITING_PUBLISH);
                feedItemService.saveAutonomous(item);   
                logger.info("New Item inserted: " + item);
                logger.info("Inserted item: " + item.toString());
            }
        }

        if (changes.getChangedItems() != null && !changes.getChangedItems().isEmpty()) {
            logger.info("Updating " + changes.getChangedItems().size() + " items in database");
            for (FeedItemChange item : changes.getChangedItems()){
                item.getFromDb().copyFrom(item.getFromFeed());
                item.getFromDb().setStatus(FeedItem.STATUS_CHANGED_WAITING_UPDATE);
                
                feedItemService.updateAutonomous(item.getFromDb());
                logger.info("Updated item: " + item.toString());
            }
        }

        if (changes.getDeletedItems() != null && !changes.getDeletedItems().isEmpty()) {
            logger.info("Deleting " + changes.getDeletedItems().size() + " items from database");
            for (FeedItem item : changes.getDeletedItems()){
                feedItemService.deleteAutonomous(item);
                logger.info("Deleted item: " + item.toString());
            }
        }
    }

    private FeedItem selectItem(int minHours_delay){
        //First get a list of items that are not published yet.
        List<FeedItem> notPublishedItems = feedItemService.findByNotPublishedDate();
        if (notPublishedItems != null && !notPublishedItems.isEmpty()){
            if (notPublishedItems.size() == 1){
                return notPublishedItems.get(0);
            } else {
                return notPublishedItems.get(ThreadLocalRandom.current().nextInt(notPublishedItems.size()));
            }
        } else {
            logger.info("Can't find any unpublished items. Trying to select a already published one.");
        }

        Date minPublishedDate = new Date(System.currentTimeMillis() - (minHours_delay * 60 * 60 * 1000));
        List<FeedItem> allPublishableFeedItems = feedItemService.findAllByMinPublishedDate(minPublishedDate);
        if (allPublishableFeedItems != null && !allPublishableFeedItems.isEmpty()){
            return allPublishableFeedItems.get(ThreadLocalRandom.current().nextInt(allPublishableFeedItems.size()));
        } 
        
        logger.info("Can't find any qualified items to choose to publish.");
        return null;
        
    }
    // private FeedItem selectItem(){
    //     List<FeedItem> allPublishableFeedItems = feedItemService.findAll();
    //     if (allPublishableFeedItems != null && !allPublishableFeedItems.isEmpty()){
    //         return allPublishableFeedItems.get(ThreadLocalRandom.current().nextInt(allPublishableFeedItems.size()));
    //     } else {
    //         logger.info("Can't find any qualified items to choose to publish.");
    //         return null;
    //     }
    // }
    
    public String[] getImagesFromItem(FeedItem feedItem) throws Exception{
        String[] imagePaths = null;
        imageService.downloadImages(feedItem);
        imagePaths = imageService.getAvailableExternalImagePathByCSS(feedItem);
        return imagePaths;
    }


    private void listItem(FeedItem feedItem) {
        if (feedItem == null){
            return;
        }

        try {
            logger.info("Listing item: " + feedItem.getWebTagNumber());
            String[] imagePaths = getImagesFromItem(feedItem);

            if (imagePaths != null){
                whatsAppService.sendGroupMultipleImages(null, 
                    new ArrayList<>(Arrays.asList(imagePaths)), 
                    getCaptionMessageFromItem(feedItem),
                    sendCaptionAsSeparateMessage()
                    );
                feedItem.setStatus(FeedItem.STATUS_PUBLISHED);
                feedItem.setPublishedDate(new Date(System.currentTimeMillis()));
                feedItemService.updateAutonomous(feedItem);
            } else {
                logger.info("Not listing, no images found.");
            }
        } catch (Throwable e) {
            logService.emailError(logger, 
                    "General Error during publishing SKU: " + feedItem.getWebTagNumber(),
                    "Error: " + e.getMessage(),
                    e);
        }
    }

    public String getCaptionMessageFromItem(FeedItem feedItem){
        return null;
    }

    public boolean sendCaptionAsSeparateMessage(){
        return false;
    }
    
}
