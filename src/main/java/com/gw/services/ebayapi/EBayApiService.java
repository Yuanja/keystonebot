package com.gw.services.ebayapi;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ebay.sdk.ApiException;
import com.ebay.sdk.SdkException;
import com.ebay.sdk.TimeFilter;
import com.ebay.sdk.call.AddItemCall;
import com.ebay.sdk.call.EndFixedPriceItemCall;
import com.ebay.sdk.call.GetSellerListCall;
import com.ebay.sdk.util.eBayUtil;
import com.ebay.soap.eBLBaseComponents.DetailLevelCodeType;
import com.ebay.soap.eBLBaseComponents.EndReasonCodeType;
import com.ebay.soap.eBLBaseComponents.FeesType;
import com.ebay.soap.eBLBaseComponents.ItemType;
import com.ebay.soap.eBLBaseComponents.ListingStatusCodeType;
import com.ebay.soap.eBLBaseComponents.PaginationType;
import com.ebay.soap.eBLBaseComponents.SellingStatusType;
import com.gw.domain.FeedItem;
import com.gw.services.FeedItemService;
import com.gw.services.LogService;

/**
 * @author jyuan
 */
@Component
public class EBayApiService {

    final static Logger logger = LogManager.getLogger(EBayApiService.class);
    
    @Autowired
    private EBayAppContextService ebayAppContextService;
    
    @Autowired
    private FeedItemService feedItemService;    

    @Autowired
    private LogService logService;
    
    public FeesType callAddItem(AddItemCall api) throws ApiException, SdkException, Exception {
        return api.addItem();
    }
    
    public void listItem(ItemType toList) {
        String errorMsg = null;
        logger.info("Calling ebay API: " + toList.getSellerInventoryID() + " Title: " + toList.getTitle());
        FeedItem item = feedItemService.findByWebTagNumber(toList.getSellerInventoryID());
        try{
            FeesType fees = null; 
            AddItemCall api = new AddItemCall(ebayAppContextService.getApiContext());   ;   
            api.setItem(toList);
            fees = callAddItem(api);
            
            double listingFee = 0.0;
            if (fees != null){
                listingFee = eBayUtil.findFeeByName(fees.getFee(), "ListingFee").getFee().getValue();
            }
            logger.info("Listing fee is: " + listingFee);
            logger.info("Listed Item ID: " + toList.getItemID());
            logger.info("List ends: " + api.getReturnedEndTime().getTime().toString());
            logger.info("The list was listed successfully!");
            
            item.setEbayFees(listingFee);
            item.setEbayItemEndDate(api.getReturnedEndTime().getTime());
            item.setEbayItemId(toList.getItemID());
            item.setSystemMessages("Listed Successful");
            item.setStatus(FeedItem.STATUS_PUBLISHED);
            feedItemService.updateAutonomous(item);
        } catch (Exception e){
            if (e.getMessage().contains("It looks like this listing is for an item you already have on eBay")) {
                item.setSystemMessages("already Listed!");
                item.setStatus(FeedItem.STATUS_PUBLISHED);
                feedItemService.updateAutonomous(item);
                logService.emailError(logger, "Item is already listed : " + item.getWebTagNumber(), null, null);
            } else {
                errorMsg = new String ("Fail to list the item. " + e.getMessage());
                logger.error(errorMsg);
                item.setSystemMessages(" EBay Error: " + e.getMessage());
                item.setStatus(FeedItem.STATUS_PUBLISHED_FAILED);
                feedItemService.updateAutonomous(item);
                logService.emailError(logger, 
                        "Failed to list SKU: " + toList.getSellerInventoryID(), 
                        "EBay Error: " + e.getMessage(),
                        e);
            }
        }
    }
    
    public void endItem(FeedItem feedItem) {
        EndFixedPriceItemCall api = null;
        logger.info("Calling ebay API EndFixedPriceItem: " + feedItem.getEbayItemId());
        try {
            api = new EndFixedPriceItemCall(ebayAppContextService.getApiContext());
            api.setItemID(feedItem.getEbayItemId());
            api.setEndingReason(EndReasonCodeType.NOT_AVAILABLE);
            api.endFixedPriceItem();
            feedItemService.deleteByEbayItemIdAutonomous(feedItem.getEbayItemId());
            logger.info("Item ended successfully: " + feedItem.getWebTagNumber() +
                    " ebayItemId: " + feedItem.getEbayItemId());
        } catch (Exception e) {
            if (e.getMessage().contains("The auction has already been closed")) {
                feedItemService.deleteByEbayItemIdAutonomous(feedItem.getEbayItemId());
                logger.info("Item ended successfully: " + feedItem.getWebTagNumber() +
                    " ebayItemId: " + feedItem.getEbayItemId() + " already ended!");
                logService.emailInfo(logger, 
                        "Item ended successfully, was already ended! SKU: "+feedItem.getWebTagNumber(), 
                        "EBay Item Id: " + feedItem.getEbayItemId());
            } else {
                logger.error(e.getMessage());
                feedItem.setStatus(FeedItem.EBAY_STATUS_END_ITEM_FAILED);
                feedItem.setSystemMessages(e.getMessage());
                feedItemService.updateAutonomous(feedItem);
                
                logService.emailError(logger, "EBay End Item Failed for sku: " + feedItem.getWebTagNumber(),
                        "EBay End Item Failed for sku: " + feedItem.getWebTagNumber() 
                        + " EBay error: " + e.getMessage(), e);
            }
        }
    }
    
    public void endItemByEBayId(String ebayId){
        EndFixedPriceItemCall api = null;
        logger.info("Calling ebay API EndFixedPriceItem EBay ID: " + ebayId);
        try {
            api = new EndFixedPriceItemCall(ebayAppContextService.getApiContext());
            api.setItemID(ebayId);
            api.setEndingReason(EndReasonCodeType.NOT_AVAILABLE);
            api.endFixedPriceItem();
            logger.info("Item ended successfully EBay ID: " + ebayId);
        } catch (Exception e) {
            if (e.getMessage().contains("The auction has already been closed")) {
                logger.info("Item ended successfully EBay ID: " + ebayId + " already ended!");
            } else {
                logger.error("Failed to end item EBay ID: " + ebayId + " " +  e.getMessage());
            }
        }
    }

    public Set<String> getAllActiveEBayItemIds() throws Exception{
        GetSellerListCall api = null;
        logger.info("Calling EBay API GetSellerList ");
        Set<String> activeItemIds = new HashSet<String>();
        try {
            api = new GetSellerListCall(ebayAppContextService.getApiContext());
            DetailLevelCodeType[] detailLevels = new DetailLevelCodeType[] {
                    DetailLevelCodeType.RETURN_ALL
            };
            api.setDetailLevel(detailLevels);
            // Setting the time range
            java.util.Calendar calTo = java.util.Calendar.getInstance();
            java.util.Calendar calFrom = java.util.Calendar.getInstance();
            calTo.add(Calendar.DATE, +90);
            calFrom.add(Calendar.DATE, -30);
            
            TimeFilter tf = new TimeFilter(calFrom, calTo);

            PaginationType pt = new PaginationType();
            pt.setEntriesPerPage(200);
            int page = 1;
            pt.setPageNumber(page);
            
            api.setPagination(pt);
            api.setStartTimeFilter(tf);
            api.setEndTimeFilter(tf); 
            
            ItemType[] retItems = api.getSellerList();
            boolean hasMorePages = true;
            while (hasMorePages) {
                for (ItemType item : retItems) {
                    String itemId = item.getItemID();
                    SellingStatusType sst = item.getSellingStatus();
                    ListingStatusCodeType lsct = sst.getListingStatus();
                    if (lsct.equals(ListingStatusCodeType.ACTIVE)) {
                        activeItemIds.add(itemId);
                    }
                }
                if (api.getPaginationResult().getTotalNumberOfPages() > page){
                    pt.setPageNumber(++page);
                    api.setPagination(pt);
                    retItems = api.getSellerList();
                } else {
                    hasMorePages = false;
                }
            }
        } catch (Exception e) {
            logger.error("Error retrieving all active listings: " + e.getMessage(), e);
            return null;
        }
        return activeItemIds;
    }
}
