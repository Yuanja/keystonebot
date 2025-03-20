package com.gw.service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.gw.Config;
import com.gw.domain.FeedItem;
import com.gw.services.jomashop.JomashopSyncService;
import com.gw.services.jomashop.Brand;
import com.gw.services.jomashop.Category;
import com.gw.services.jomashop.Inventory;
import com.gw.services.jomashop.JomaShopApiService;
import com.gw.services.jomashop.JomaShopFeedService;
import com.gw.services.jomashop.JomaShopProductFactory;
import com.gw.services.jomashop.SingleProductRequest;

@RunWith(SpringRunner.class)
@Import(Config.class)
@SpringBootTest
//@ActiveProfiles(profiles = "jomashop-prod")
@ActiveProfiles(profiles = "jomashop-dev")

public class JomaShopTest {

    final static Logger logger = LogManager.getLogger(JomaShopTest.class);
    
    @Autowired
    JomaShopApiService apiService;
    
    @Autowired
    JomaShopProductFactory productFactory;
    
    @Autowired
    private JomashopSyncService jomashopSyncService;
    
    @Autowired
    private JomaShopFeedService jomashopFeedService;
    
    
    @Test
    public void testGetAllBrands() throws Exception{
        List<Brand> allBrands = apiService.getBrands();
        
        Assert.assertTrue(allBrands != null);
        for (Brand brand : allBrands) {
            System.out.println(brand.name);
        }
    }
    
    @Test
    public void testGetAllCategories() throws Exception{
        
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        Category catWatchUsed = apiService.getCategoryByName("Pre-Owned Watches");
        System.out.println(gson.toJson(catWatchUsed));

//        Category catWatch = apiService.getCategoryByName("Watches");
//        System.out.println(gson.toJson(catWatch));
    }
    
    @Test 
    public void testCategoryFileExist() throws Exception {
        Resource resource = new ClassPathResource("WatchCategory.json");
        InputStream resourceInputStream = resource.getInputStream();
        if (resourceInputStream == null) {
            Assert.assertTrue("WatchCategory Configuration could not be loaded from file!",
                    resourceInputStream != null );
        }
        
        Resource resource2 = new ClassPathResource("PreOwnedWatchCategory.json");
        InputStream resourceInputStream2 = resource.getInputStream();
        if (resourceInputStream2 == null) {
            Assert.assertTrue("PreOwnedWatchCategory Configuration could not be loaded from file!",
                    resourceInputStream != null );
        }
    }
    
    @Test
    public void testMakeProduct() throws Exception {
        
        List<FeedItem> feedItems = jomashopFeedService.loadFromXmlFile("src/test/resources/retailFeeds/singleJomaShop.xml");
        for (FeedItem item : feedItems) {
            SingleProductRequest sp = productFactory.makeProduct(item);
            
            if (!sp.product.hasErrors)
                apiService.createProduct(sp);
            else 
                logger.info(sp.product.getErrorMsg());

            Assert.assertFalse("ERRORS in SP.p.", sp.product.hasErrors );
        }
    }
    
    @Test
    public void testFeedModelNumber() throws Exception {
        
        List<FeedItem> feedItems = jomashopFeedService.loadFromXmlFile("src/test/resources/retailFeeds/jomashop.xml");
        Map<String, List<FeedItem>> feedItemByModel = new HashMap<>();

        for (FeedItem item : feedItems) {
            List<FeedItem> items = feedItemByModel.get(item.getWebWatchModel());
            if (items == null) {
                items = new ArrayList<FeedItem>();
                feedItemByModel.put(item.getWebWatchModel(), items);
            }
            items.add(item);
        }
        
        int mapSize = 0;
        for (String key : feedItemByModel.keySet()) {
        	List<FeedItem> list = feedItemByModel.get(key);
        	
        	mapSize += list.size();
        	//StringBuilder sb = new StringBuilder();
        	//sb.append(key + ", ");
        	for (FeedItem item: list) {
        	//	sb.append(item.getWebTagNumber()).append(", ");
        		logger.info(key + ":"+ item.getWebTagNumber() +":"+item.getWebDescriptionShort() + ":" + item.getWebDesigner());
        	}
        	//logger.info(sb);
        }
        
        Assert.assertTrue(feedItems.size() == mapSize);
    }
    
    @Test
    public void testGetInventory() throws Exception {
        Map<String, Inventory> invMap = apiService.getInventoryBySkuMap();
        for (Inventory inv : invMap.values()) {
            System.out.println(inv.sku + " : " + inv.status 
                    +" : "+ inv.quantity);
            
        }
    }
    
    @Test
    public void markAllSold() throws Exception {
        Map<String, Inventory> invMap = apiService.getInventoryBySkuMap();
        for (Inventory inv : invMap.values()) {
            System.out.println(inv.sku + " : " + inv.status 
                    +" : "+ inv.quantity);
            apiService.markSkuSold(inv.sku, inv.price);
        }
        
        invMap = apiService.getInventoryBySkuMap();
        for (Inventory inv : invMap.values()) {
            System.out.println(inv.sku + " : " + inv.status 
                    +" : "+ inv.quantity);
            
        }
    }
    
    @Test
    public void updateProduct() throws Exception {
        Map<String, Inventory> invMap = apiService.getInventoryBySkuMap();
        for (Inventory inv : invMap.values()) {
            System.out.println(inv.sku + " : " + inv.status 
                    +" : "+ inv.quantity);
        }
        
        List<FeedItem> feedItems = jomashopFeedService.loadFromXmlFile("src/test/resources/testShopifyUpdate/feed1.xml");
        for (FeedItem item : feedItems) {
          
            if (invMap.containsKey(item.getWebTagNumber())) {
                System.out.println("Updating Sku:" + item.getWebTagNumber());
                SingleProductRequest sp = productFactory.makeProduct(item);
                sp.inventory.status = "inactive";
                apiService.updateProduct(item.getWebTagNumber(), sp);
            }
        }
    }
    
    @Test
    public void testSync() throws Exception{
        jomashopSyncService.sync(true);
    }
    
    @Test
    public void testSyncSpecific() throws Exception{
        jomashopSyncService.sync("147089");
    }
    
}
