package com.gw.service;

import com.gw.Config;
import com.gw.services.GoogleSheetScheduleService;
import com.gw.services.whatsApp.WhatsAppService;
import com.gw.services.whatsApp.WhatsAppSyncService;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import java.util.List;
import java.util.Arrays;

@RunWith(SpringRunner.class)
@Import(Config.class)
@SpringBootTest
@ActiveProfiles(profiles = "whatsapp-dev")
public class WhatsAppTest {
	private static Logger logger = LogManager.getLogger(WhatsAppTest.class);

    @Autowired
    private WhatsAppSyncService whatsAppSyncService;

    @Autowired
    private WhatsAppService whatsAppService;

    @Autowired
    private GoogleSheetScheduleService googleSheetScheduleService;


    @Test
    /**
     * Make sure #Dev mode
     * dev.mode=1
     * dev.mode.maxReadCount = 10
     * Or else the whole feed will be synced.
     */
    public void syncTest() throws Exception {
        whatsAppSyncService.sync(true);
    }

    @Test
    public void syncByTmpFileTest() throws Exception {
        whatsAppSyncService.sync(false);
    }

    @Test
    public void testSendGroupMultipleImages() {
        String groupId = "120363391367123986@g.us";
        List<String> imageUrls = Arrays.asList(
           "http://ebay.gruenbergwatches.com/gwebaycss/images/watches/102052-1.jpg",
            "http://ebay.gruenbergwatches.com/gwebaycss/images/watches/146033-2.jpg"
        );
        
        // Test with individual captions
        String caption = "Watch images from whatsapp bot.";
        
        boolean result = whatsAppService.sendGroupMultipleImages(groupId, imageUrls, caption);
        //boolean result = whatsAppService.sendGroupMultipleImagesInSingleMessage(groupId, imageUrls, caption);
        Assert.assertTrue("Failed to send multiple images with individual captions", result);
        
    }

    @Test
    public void testGoogleSheetScheduleService() {
        int frequency = googleSheetScheduleService.getCurrentFrequency();
        logger.info("Current frequency: {}", frequency);
    }

}




