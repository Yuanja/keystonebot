package com.gw.services.whatsApp;

import java.text.DecimalFormat;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.gw.domain.FeedItem;

/**
 * @author jyuan
 *
 */
@Component
@Profile({"whatsapp-prod", "whatsapp-dev"})
public class WhatsAppSyncService extends BaseWhatsAppSyncService {
    
    @Override
    public String getCaptionMessageFromItem(FeedItem feedItem){
        DecimalFormat formatter = new DecimalFormat("$#,###");
        String formattedPrice = formatter.format(Double.parseDouble(feedItem.getWebPriceSale()));
        return feedItem.getWebDescriptionShort() + " " + formattedPrice;
    }
    
}
