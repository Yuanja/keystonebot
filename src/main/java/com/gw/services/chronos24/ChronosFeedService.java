package com.gw.services.chronos24;

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
@Profile({"Chronos-prod", "Chronos-dev"})
public class ChronosFeedService extends BaseFeedService {
    private static Logger logger = LogManager.getLogger(ChronosFeedService.class);
    
    @Override
    public boolean toAcceptFromFeed(FeedItem feedItem) {
        if (feedItem.getWebPriceChronos() == null || feedItem.getWebPriceChronos().equals("0")) {
            String errorString = "Sku: " + feedItem.getWebTagNumber() + " has invalid Chronos price of 0";
            logger.error(errorString);
            return false;
        }
        
        if (!feedItem.getWebCategory().equalsIgnoreCase("watches")){
            return false;
        }

        return true;
    }
    
}
