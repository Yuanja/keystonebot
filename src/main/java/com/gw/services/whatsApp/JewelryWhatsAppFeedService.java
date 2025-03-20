package com.gw.services.whatsApp;

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
@Profile({"jewelrywhatsapp-prod", "jewelrywhatsapp-dev"})
public class JewelryWhatsAppFeedService extends BaseFeedService {

    private static Logger logger = LogManager.getLogger(JewelryWhatsAppFeedService.class);
    
    @Override
    public boolean toAcceptFromFeed(FeedItem feedItem) {
        
        if (feedItem.getWebStatus() == null) {
            String errorString = "WhatsApp BOT: Sku: " + feedItem.getWebTagNumber() +" can't list because status is null.";
            logService.emailError(logger, errorString, errorString, null);
            return false;
        }
        
        if (feedItem.getWebStatus().equalsIgnoreCase("Sold")) {
            String errorString = "WhatsApp BOT: Sku: " + feedItem.getWebTagNumber() +" can't list because status is Sold.";
            logService.emailError(logger, errorString, errorString, null);
            return false;
        }

        if (feedItem.getWebStatus().contains("on Memo")) {
            String errorString = "WhatsApp BOT: Sku: " + feedItem.getWebTagNumber() +" can't list because status is on Memo.";
            logService.emailError(logger, errorString, errorString, null);
            return false;
        }

        if (feedItem.getWebPriceWholesale() == null || feedItem.getWebPriceWholesale().isEmpty()) {
            String errorString = "WhatsApp BOT: Sku: " + feedItem.getWebTagNumber() +" can't list because getWebPriceWholesale is missing.";
            logService.emailError(logger, errorString, errorString, null);
            return false;
        }
        
        return true;
    }
}
