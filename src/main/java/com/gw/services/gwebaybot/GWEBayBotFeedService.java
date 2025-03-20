package com.gw.services.gwebaybot;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.gw.domain.FeedItem;
import com.gw.services.BaseFeedService;

/**
 * @author jyuan
 *
 */
@Component
@Profile({"gwebay-prod", "gwebay-dev"})
public class GWEBayBotFeedService extends BaseFeedService {

    private static Logger logger = LogManager.getLogger(GWEBayBotFeedService.class);
    
    @Override
    public boolean toAcceptFromFeed(FeedItem feedItem) {
        
        if (feedItem.getWebFlagEbayauction() == null || !feedItem.getWebFlagEbayauction().equals("1")) {
            if ((feedItem.getWebPriceEbay() == null || feedItem.getWebPriceEbay().equals("0"))) {
                String errorString = "EBAY BOT: Sku: " + feedItem.getWebTagNumber() + " has invalid ebay price of 0";
                logService.emailError(logger, errorString, errorString, null);
                return false;
            }
            
            if (feedItem.getCostInvoiced() == null || feedItem.getCostInvoiced().equalsIgnoreCase("0")) {
                String errorString = "EBAY BOT: Sku: " + feedItem.getWebTagNumber() +" can't list because invoice price is null or 0";
                logService.emailError(logger, errorString, errorString, null);
                return false;
            }
        }
        
        if (feedItem.getWebStatus() == null) {
            String errorString = "EBAY BOT: Sku: " + feedItem.getWebTagNumber() +" can't list because status is null";
            logService.emailError(logger, errorString, errorString, null);
            return false;
        }
        
        if (feedItem.getWebStatus().equalsIgnoreCase("Sold")) {
            String errorString = "EBAY BOT: Sku: " + feedItem.getWebTagNumber() +" can't list because status is Sold";
            logService.emailError(logger, errorString, errorString, null);
            return false;
        }
        
        if (feedItem.getWebWatchCondition() == null) {
            String errorString = "EBAY BOT: Sku: " + feedItem.getWebTagNumber() +" can't list because condition is not set.";
            logService.emailError(logger, errorString, errorString, null);
            return false;
        }
        
        return true;
    }
}
