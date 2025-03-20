package com.gw.services;
 
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.gw.domain.FeedItem;
import com.gw.domain.FeedItemChange;
import com.gw.domain.FeedItemChangeSet;
import com.gw.domain.FeedItemDao;
@Component
@Transactional
public class FeedItemService {
    final static Logger logger = LogManager.getLogger(FeedItemService.class);
    
    @Autowired
    FeedItemDao feedItemDao;
 
        /* 
     * Returns 3 lists,
     * first is the new items that didn't exist in the db,
     * second is the list of items updated,
     * third is items in DB but not in feed.
     */
	public FeedItemChangeSet compareFeedItemWithDB(boolean forceUpdate, final List<FeedItem> feedItems){
        
        List<FeedItem> newItems = new ArrayList<FeedItem>();
        List<FeedItemChange> changedItems = new ArrayList<FeedItemChange>();
        
        for (FeedItem itemFromFeed : feedItems){
            FeedItem itemFromDb = findByWebTagNumber(itemFromFeed.getWebTagNumber());
            if (itemFromDb != null){
                if(forceUpdate || !itemFromDb.equalsForShopify(itemFromFeed)){
                    //Item is not the same in the DB.
                	logger.info("Force update : " + forceUpdate);
                    logger.info("IN DB  : " + itemFromDb);
                    logger.info("IN FEED: " + itemFromFeed);
                    changedItems.add(new FeedItemChange(itemFromDb, itemFromFeed));
                }
            } else {
                newItems.add(itemFromFeed);
            }
        }
        
        List<FeedItem> toDeleteFeedItems = getItemsFromDBNotInFeed(feedItems);
        
        return new FeedItemChangeSet(newItems, changedItems, toDeleteFeedItems);
    }

    public List<FeedItem> getItemsFromDBNotInFeed(final List<FeedItem> feedItems) {
        List<FeedItem> allItemsInDb = findAll();
        Map<String, FeedItem> feedItemByWebRecordId = new HashMap<String, FeedItem>();
        
        feedItems.stream().forEach(c -> {feedItemByWebRecordId.put(c.getWebTagNumber(), c);} );
        
        List<FeedItem> toDelete = new ArrayList<FeedItem>();
        for (FeedItem itemInDb : allItemsInDb) {
            if (!feedItemByWebRecordId.containsKey(itemInDb.getWebTagNumber())) {
                toDelete.add(itemInDb);
            }
        }
        return toDelete;
    }
    
    public Map<String, FeedItem> getFeedItemBySkuMap(){
        List<FeedItem> allItemsInDb = findAll();
        return allItemsInDb.stream().collect(Collectors.toMap(FeedItem::getWebTagNumber, c->c));
    }
    
    /* (non-Javadoc)
     * @see com.gw.components.FeedItemService#findAll()
     */
    public List<FeedItem> findAll() {
        return feedItemDao.findAll();
    }

    public List<FeedItem> findAllByMinPublishedDate(Date minPublishedDate){
        return feedItemDao.findByMinLastPublishedDate(minPublishedDate);
    }

    public List<FeedItem> findByNotPublishedDate(){
        return feedItemDao.findByNotPublished();
    }
    
    @Transactional(readOnly = false, propagation = Propagation.REQUIRES_NEW)
    public void deleteAutonomous(FeedItem feedItem) {
        feedItemDao.delete(feedItem.getWebTagNumber());
    }
    
    @Transactional(readOnly = false, propagation = Propagation.REQUIRES_NEW)
    public void deleteAllAutonomous() {
        feedItemDao.deleteAll();
    }
    
    public List<FeedItem> findByStatus(String status){
        return feedItemDao.findByStatus(status);
    }
    
    public List<FeedItem> findByWebDescriptionShort(String searchString){
        return feedItemDao.findByWebDescriptionShort(searchString);
    }
    
    @Transactional(readOnly = false, propagation = Propagation.REQUIRES_NEW)
    public void updateAutonomous(FeedItem item){
        feedItemDao.update(item);
    }
    
    public FeedItem findByWebTagNumber(String webTagNumber){
        return feedItemDao.findByWebTagNumber(webTagNumber);
    }
    
    @Transactional(readOnly = false, propagation = Propagation.REQUIRES_NEW)
    public void saveAutonomous(FeedItem item){
        feedItemDao.save(item);
    }
    
    @Transactional(readOnly = false, propagation = Propagation.REQUIRES_NEW)
    public void deleteByEbayItemIdAutonomous(String ebayItemId) {
        feedItemDao.deleteByEbayItemId(ebayItemId);
    }
}
