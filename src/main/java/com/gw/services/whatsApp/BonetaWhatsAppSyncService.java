package com.gw.services.whatsApp;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.gw.domain.FeedItem;
import com.gw.services.ImageService;

/**
 * @author jyuan
 *
 */
@Component
@Profile({"bonetawhatsapp-prod", "bonetawhatsapp-dev", "mafiabonetawhatsapp-prod", "mafiabonetawhatsapp-dev"})
public class BonetaWhatsAppSyncService extends BaseWhatsAppSyncService {

    @Autowired
    private ImageService imageService;

    @Override
    public boolean sendCaptionAsSeparateMessage(){
        return true;
    }


    @Override
    public String getCaptionMessageFromItem(FeedItem feedItem){
        return feedItem.getWebDescriptionShort();
    }

    @Override   
    public String[] getImagesFromItem(FeedItem feedItem){
        return imageService.getRawImageUrls(feedItem);
    }
    
}
