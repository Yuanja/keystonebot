package com.gw.service;

import com.gw.Config;
import com.gw.domain.FeedItem;
import com.gw.domain.FeedItemChangeSet;
import com.gw.domain.PredefinedCollection;
import com.gw.domain.keystone.KeyStoneCollections;
import com.gw.services.*;
import com.gw.services.shopifyapi.ShopifyAPIService;
import com.gw.services.shopifyapi.objects.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertNotNull;

@RunWith(SpringRunner.class)
@Import(Config.class)
@SpringBootTest
@ActiveProfiles(profiles = "keystone-dev")
public class KeystoneTest {
	private static Logger logger = LogManager.getLogger(KeystoneTest.class);

    @Autowired
    EmailService emailService;
    
    @Autowired
    ShopifyAPIService shopifyApiService;
    
    @Autowired
    private IShopifySyncService syncService;
    
    @Autowired
    private FreeMakerService freemakerservice;

    @Autowired
    private IFeedService keyStoneFeedService;
    
    @Autowired
    private IShopifyProductFactory ksProductFactoryService;
    
    @Autowired
    private ImageService imageService;

    @Test
    public void getAllProduct() throws Exception {
        List<Product> allProducts = shopifyApiService.getAllProducts();
        Assert.assertTrue(!allProducts.isEmpty());
    }
    
    @Test
    public void removeAllProducts() throws Exception {
        shopifyApiService.removeAllProducts();
        
        List<Product> allProducts = shopifyApiService.getAllProducts();
        Assert.assertTrue(allProducts.isEmpty());
    }
    
    @Test
    public void removeAllCollections() throws Exception{
        shopifyApiService.removeAllCollections();
        
        CustomCollections allCollection = shopifyApiService.getAllCustomCollections();
        Assert.assertTrue(allCollection.getList().isEmpty());
    }
    
    @Test
    /**
     * Make sure #Dev mode
     * dev.mode=1
     * dev.mode.maxReadCount = 10
     * Or else the whole feed will be synced.
     */
    public void syncTest() throws Exception {
        syncService.sync(true);
    }

    @Test
    public void createCustomCollections() throws Exception {
        shopifyApiService.ensureConfiguredCollections(KeyStoneCollections.values());
    }
    
    @Test 
    public void testTemplate() throws Exception {
        FeedItem newfeed = new FeedItem();
        String out = freemakerservice.generateFromTemplate(newfeed);
        Assert.assertTrue(out != null);
    }
    
    @Test
    public void removeAll() throws Exception {
        shopifyApiService.removeAllProducts();
    }

    @Test
    public void testFeedParsing() throws Exception {
        List<FeedItem> feedItems = keyStoneFeedService.loadFromXmlFile("src/test/resources/testShopifyUpdate/feed1.xml");
        for (FeedItem item : feedItems) {
            logger.info(item.toString());
        }
    }
    
    @Test
    public void updateSpecific() throws Exception {
        Map<PredefinedCollection, CustomCollection> collectionByEnum = 
                shopifyApiService.ensureConfiguredCollections(KeyStoneCollections.values());
        
        List<FeedItem> feedItems = keyStoneFeedService.loadFromXmlFile("src/test/resources/keystonefeed.xml");
        Optional<FeedItem> specificToUpdate = 
                keyStoneFeedService.getItemsFromFeed().stream().filter(
                        feedItem -> feedItem.getWebTagNumber().equals("160883")).findAny();
        Assert.assertTrue(specificToUpdate.isPresent());
        FeedItem item = specificToUpdate.get();
        item.setShopifyItemId("7532623036655");
        
        try {
        	//Download images
        	imageService.downloadImages(item);
        	
            Product product = ksProductFactoryService.createProduct(item);
            product.setId(item.getShopifyItemId());
            
            ProductVo existingProductVo = 
                    shopifyApiService.getProductByProductId(item.getShopifyItemId());
            
            Assert.assertTrue(existingProductVo !=null && existingProductVo.get() != null);
            Product existingProduct = existingProductVo.get();
            
            //shopifyApiService.updateProduct(existingProduct);
            logger.info(LogService.toJson(existingProduct));
            ksProductFactoryService.mergeProduct(existingProduct, product);
            logger.info(LogService.toJson(product));

            shopifyApiService.deleteAllImageByProductId(existingProduct.getId());
            shopifyApiService.updateProduct(product);
            
            //Delete existing collects
            shopifyApiService.deleteAllCollectForProductId(item.getShopifyItemId());
            
            //Get the would be collection based on the item;
            List<Collect> updatedCollections = 
                    CollectionUtility.getCollectionForProduct(item.getShopifyItemId(), item, collectionByEnum);
            shopifyApiService.addProductAndCollectionsAssociations(updatedCollections);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    @Test
    public void addSpecific() throws ParserConfigurationException, SAXException, IOException{
    	Map<PredefinedCollection, CustomCollection> collectionByEnum = 
                shopifyApiService.ensureConfiguredCollections(KeyStoneCollections.values());
        
        List<FeedItem> feedItems = keyStoneFeedService.loadFromXmlFile("src/test/resources/keystonefeed2.xml");
        Optional<FeedItem> itemToAdd = 
                keyStoneFeedService.getItemsFromFeed().stream().filter(
                        feedItem -> feedItem.getWebTagNumber().equals("174612")).findAny();
        Assert.assertTrue(itemToAdd.isPresent());
        FeedItem item = itemToAdd.get();
        try {
            Product product = ksProductFactoryService.createProduct(item);
            Product newlyAddedProduct = shopifyApiService.addProduct(product);
            
            String inventoryItemId = newlyAddedProduct.getVariants().get(0).getInventoryItemId();
            //get the inventory and then update the count to 1.
            InventoryLevels levels = shopifyApiService.getInventoryLevelByInventoryItemId(inventoryItemId);
            
            //Get the count from the product, that's created by the factory from the feed item.
            //Set the inventoryItemId for update.
            ksProductFactoryService.mergeInventoryLevels(levels, product.getVariants().get(0).getInventoryLevels());
            shopifyApiService.updateInventoryLevels(product.getVariants().get(0).getInventoryLevels());
            
            shopifyApiService.addProductAndCollectionsAssociations(
                    CollectionUtility.getCollectionForProduct(newlyAddedProduct.getId(), item, collectionByEnum));

            logger.info("Shopify Bot: Sku: " + item.getWebTagNumber() + ", "+ 
                  item.getWebDescriptionShort() +" added to Shopify as: " + newlyAddedProduct.getId());
        
        }catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    @Test
    public void loadFeedFileToDB() throws Exception {
    	List<FeedItem> feedItems = keyStoneFeedService.loadFromXmlFile("src/test/resources/testShopifyUpdate/feed1.xml");
    	FeedItemChangeSet changeSet = syncService.compareFeedItemWithDB(feedItems);
    	syncService.updateDB(changeSet);
    }
    
    @Test
    public void testSingleShopifyRead() throws Exception {
    	ProductVo existingProductVo = 
                shopifyApiService.getProductByProductId("4474562871432");
    	System.out.println( existingProductVo.get().toString());
    	
    	String invlids = existingProductVo.get().getVariants().get(0).getInventoryItemId();
    	InventoryLevels ivls = shopifyApiService.getInventoryLevelByInventoryItemId(invlids);
    	System.out.println( ivls.toString());
    	
    }
    
    @Test
    public void getAsCSV() throws Exception {
        List<FeedItem> allFeedItems = keyStoneFeedService.getItemsFromFeed();
        FileWriter fileWriter = new FileWriter("KeyStone.csv");
        PrintWriter printWriter = new PrintWriter(fileWriter);
       
        for (FeedItem aItem : allFeedItems) {
            printWriter.printf("%s,%s,%s", aItem.getWebTagNumber(), aItem.getWebStatus(), aItem.getWebDescriptionShort());
            printWriter.println();
        }
        printWriter.close();
    }

    @Test
    public void testGetAllLocations() throws Exception {
    	assertNotNull(ksProductFactoryService.getLocations());
    }
    
    @Test
    public void testHandleKeyStoneJob() throws Exception {
        syncService.sync(true);
    }
    
    @Test
    public void testEmail() throws Exception {
    	emailService.sendMessage("Testing", "Just a test from shopify bots.  Ignore");
    }
}


