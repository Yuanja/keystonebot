package com.gw.services.keystone;

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
@Profile({"keystone-prod", "keystone-dev"})
public class KeyStoneFeedService extends BaseFeedService {

    private static Logger logger = LogManager.getLogger(KeyStoneFeedService.class);
    
    
    @Override
    public boolean toAcceptFromFeed(FeedItem feedItem) {
        
        if (!(feedItem.getWebCategory().equalsIgnoreCase("watches") ||
                feedItem.getWebCategory().equalsIgnoreCase("jewelry"))
        ){
            String errorString = "Shopify Bot: Sku: " + feedItem.getWebTagNumber() + " has web category of "
                    + feedItem.getWebCategory() + ".  Must be either 'watches' or 'jewelry'.";
            logService.emailError(logger, errorString, errorString, null);
            return false;
        }

        if (feedItem.getWebCategory().equalsIgnoreCase("jewelry")){
            if (feedItem.getWebDescriptionShort() == null ||
                feedItem.getWebDesigner() == null ||
                feedItem.getWebMetalType() == null){
                String errorString = "Shopify Bot: Sku: " + feedItem.getWebTagNumber() + " has web category of "
                        + feedItem.getWebCategory() + ".  Must have short description, designer and metal type specified.";
                logService.emailError(logger, errorString, errorString, null);
                return false;
            }
        }
        
        if (feedItem.getWebWatchDial() == null) {
            String errorString = "Shopify Bot: Sku: " + feedItem.getWebTagNumber() + " has null dial color.";
            logService.emailError(logger, errorString, errorString, null);
            return false;
        }
        
        if (feedItem.getWebWatchDiameter() == null) {
            String errorString = "Shopify Bot: Sku: " + feedItem.getWebTagNumber() + " has null diameter.";
            logService.emailError(logger, errorString, errorString, null);
            return false;   
        }
        
        if (feedItem.getWebMetalType() == null) {
            String errorString = "Shopify Bot: Sku: " + feedItem.getWebTagNumber() + " has null metal type.";
            logService.emailError(logger, errorString, errorString, null);
            return false;
        }
        
        return true;
    }
}
