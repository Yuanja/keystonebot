package com.gw.service;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.io.*;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import com.gw.Config;
import com.gw.services.ImageService;

@RunWith(SpringRunner.class)
@Import(Config.class)
@SpringBootTest
@ActiveProfiles(profiles = "whatsapp-dev")

//@ActiveProfiles(profiles = "gw-prod")
public class ImageServiceTest {

    @Autowired
    private ImageService imageService;
    
    @Test
    public void testIpAddressReplacement() throws Exception{
        String testUrl = "https://172.91.140.131/fmi/xml/cnt/data.jpg?-db=DEG&-lay=INVENTORY_WEB+-+Web+Publishing&-recid=13423&-field=web_image_1";
        String expectedUrl = "https://1.2.1.3/fmi/xml/cnt/data.jpg?-db=DEG&-lay=INVENTORY_WEB+-+Web+Publishing&-recid=13423&-field=web_image_1";
        
        Assert.assertTrue(expectedUrl.equals(imageService.replaceIPAddressInUrl(testUrl, "1.2.1.3")));
    }

    @Test
    public void testCompression() throws Exception{
        imageService.assertImageUnder1MB("/Users/jyuan/Documents/justin/gwebaycss/images/watches/105324-5.jpg");
    }

    @Test
    public void testDetctionHEIC() throws Exception{
        String fileName = "/Users/jyuan/Documents/justin/gwebaycss/images/watches/151231-1.jpg";
        File imageFile = new File(fileName);
        Assert.assertTrue(imageService.isHeicImage(imageFile));

        String tmpHeic = "temp.jpg";
        File imageFileCopy = new File(tmpHeic);

        Files.copy(imageFile.toPath(), 
            imageFileCopy.toPath(), StandardCopyOption.REPLACE_EXISTING);
        Assert.assertTrue(imageService.isHeicImage(imageFileCopy));

        imageService.assertImageIsJPG(imageFileCopy.getAbsolutePath());
        Assert.assertTrue(imageService.isJpg(imageFileCopy));

    }

    @Test
    public void downloadAndTest() throws Exception{
        String urlToUse = "http://ebay.gruenbergwatches.com/gwebaycss/images/watches/198297-3.jpg";
        String imageFileName="tmpImage";

        URL imageUrl = new URL(urlToUse);
        HttpURLConnection connection = (HttpURLConnection) imageUrl.openConnection();
        final int responseCode = connection.getResponseCode();
        if (responseCode == 200) {
            InputStream input = new BufferedInputStream(imageUrl.openStream());
            Files.copy(input, Paths.get(imageFileName), StandardCopyOption.REPLACE_EXISTING);
            
//            assertImageIsJPG(imageFileName);

//            assertImageUnder1MB(imageFileName);

            assertTrue(imageService.isJpg(new File(imageFileName)));

        } else {
            throw new Exception("Image failed to download: " + urlToUse);
        }
        
    }

}
