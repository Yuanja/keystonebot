package com.gw.services.gruenbergwatches;

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
@Profile({"gw-prod", "gw-dev"})
public class GWFeedService extends BaseFeedService {

    private static Logger logger = LogManager.getLogger(GWFeedService.class);
    
    @Override
    public boolean toAcceptFromFeed(FeedItem feedItem) {
        if (feedItem.getWebPriceEbay() == null || feedItem.getWebPriceEbay().equals("0")){
            String errorString = "Shopify Bot: Sku: " + feedItem.getWebTagNumber() + " has invalid ebay price of 0";
            logService.emailError(logger, errorString, errorString, null);
            return false;
        }
        
        if (feedItem.getWebWatchCondition() == null ) {
            String errorString = "Shopify Bot: Sku: " + feedItem.getWebTagNumber() + " has invalid condition.";
            logService.emailError(logger, errorString, errorString, null);
            return false;
        }
        
        if (feedItem.getWebWatchDial() == null) {
            String errorString = "Shopify Bot: Sku: " + feedItem.getWebTagNumber() + " has invalid dial color.";
            logService.emailError(logger, errorString, errorString, null);
            return false;
        }
        
        if (feedItem.getWebWatchDiameter() == null) {
            String errorString = "Shopify Bot: Sku: " + feedItem.getWebTagNumber() + " has invalid diameter.";
            logService.emailError(logger, errorString, errorString, null);
            return false;   
        }
        
        if (feedItem.getWebMetalType() == null) {
            String errorString = "Shopify Bot: Sku: " + feedItem.getWebTagNumber() + " has invalid metal type.";
            logService.emailError(logger, errorString, errorString, null);
            return false;
        }
        
        return true;
    }
}
