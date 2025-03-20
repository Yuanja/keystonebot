package com.gw.services.jomashop;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.gw.domain.FeedItem;
import com.gw.services.BaseFeedService;
import com.gw.services.LogService;

/**
 * @author jyuan
 *
 */
@Component
@Profile({"jomashop-prod", "jomashop-dev"})
public class JomaShopFeedService extends BaseFeedService {

    private static Logger logger = LogManager.getLogger(JomaShopFeedService.class);

    @Autowired
    private LogService logService;
    
    @Autowired
    private JomaShopProductFactory productFactory;
    
    @Override
    public boolean toAcceptFromFeed(FeedItem feedItem) {
        
        try {
            SingleProductRequest sp = productFactory.makeProduct(feedItem);
            if (sp.product.hasErrors) {
                //Email and skip.
                String tmpErrorStr = "FIX DATA: Failed to validate: " + feedItem.getWebTagNumber();
                logService.emailError(logger, tmpErrorStr, sp.product.getErrorMsg(), null);
            
                return false;
            }
        } catch (Exception e) {
            logger.error("feedItem throw exception on make product: "+ feedItem.getWebTagNumber() + " Exception: "+ e.getMessage());
            e.printStackTrace();
            return false;
        }
        
        return true;
    }
}
