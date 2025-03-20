package com.gw.service;

import com.gw.Config;
import com.gw.domain.FeedItem;
import com.gw.services.GoogleSheetService;
import com.gw.services.ISyncService;
import com.gw.services.whatsApp.BonetaFeedService;
import com.gw.services.whatsApp.WhatsAppService;
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
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;

@RunWith(SpringRunner.class)
@Import(Config.class)
@SpringBootTest
@ActiveProfiles(profiles = "bonetawhatsapp-dev")
public class BonetaWhatsAppTest {
    private static Logger logger = LogManager.getLogger(WhatsAppTest.class);

    @Autowired
    private ISyncService whatsAppSyncService;

    @Autowired
    private WhatsAppService whatsAppService;

    @Autowired 
    private GoogleSheetService gsheetService;

    @Autowired
    private BonetaFeedService bonetaFeedService;
    
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
           "http://ebay.gruenbergwatches.com/gwebaycss/images/watches/102052-1.jpg"
        );
        
        // Test with individual captions
        String caption = "Watch images from whatsapp bot.  Testing multi pline and bold: 1 st line \n 2nd line.. " +
        " first line b <br> second line b.  <b>bold here</b> <strong> strong here </strong> <a href=\"http://BonetaWholesale.com\">BonetaWholesale.com</a> ";
        
        boolean result = whatsAppService.sendGroupMultipleImages(groupId, imageUrls, caption);
        //boolean result = whatsAppService.sendGroupMultipleImagesInSingleMessage(groupId, imageUrls, caption);
        Assert.assertTrue("Failed to send multiple images with individual captions", result);
        
    }

    @Test 
    public void listGroupsTest(){
        whatsAppService.listGroups();
    }

    @Test
    public void readFromGoogleSheet() throws IOException, GeneralSecurityException{
        // yuanja888 test 
        //String url = "https://docs.google.com/spreadsheets/d/1xWmyPAPuNgYuBeAkfP4H2CN-QpmZADgtG0MQ5d7ZCH8/edit?gid=0#gid=0";
        // Boneta feed.
        String url = "https://docs.google.com/spreadsheets/d/1IbxFPAoBwOWkHEGdUqzJxKiamo-DXKP0TcPEpBEsUj4/edit?usp=sharing";
        List<FeedItem> items = bonetaFeedService.mapToFeedItems(gsheetService.fetchAndMapSheetData(url));
        for (FeedItem item : items){
            System.out.println(item);
        }
    }
}


