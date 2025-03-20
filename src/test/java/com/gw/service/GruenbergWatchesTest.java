package com.gw.service;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import com.gw.Config;
import com.gw.domain.FeedItem;
import com.gw.domain.PredefinedCollection;
import com.gw.domain.gruenbergwatches.GruenbergWatchesCollections;
import com.gw.domain.keystone.KeyStoneCollections;
import com.gw.services.CollectionUtility;
import com.gw.services.FeedItemService;
import com.gw.services.FeedReadynessService;
import com.gw.services.FreeMakerService;
import com.gw.services.gruenbergwatches.GWFeedService;
import com.gw.services.gruenbergwatches.GWShopifyProductFactoryService;
import com.gw.services.shopifyapi.ShopifyAPIService;
import com.gw.services.shopifyapi.objects.Collect;
import com.gw.services.shopifyapi.objects.CustomCollection;
import com.gw.services.shopifyapi.objects.CustomCollections;
import com.gw.services.shopifyapi.objects.Product;
import com.gw.services.shopifyapi.objects.ProductVo;

@RunWith(SpringRunner.class)
@Import(Config.class)
@SpringBootTest
//@ActiveProfiles(profiles = "gw-dev")
@ActiveProfiles(profiles = "gw-prod")
public class GruenbergWatchesTest {

    @Autowired
    private FreeMakerService freemakerservice;
    
    @Autowired
    ShopifyAPIService shopifyApiService;
    
    @Autowired
    GWFeedService feedService;
    
    @Autowired
    FeedItemService feedItemService;
    
    @Autowired
    GWShopifyProductFactoryService factoryService;
    
    @Autowired
    FeedReadynessService feedReadynessService;

    @Test
    public void createCustomCollections() throws Exception {
        Map<PredefinedCollection, CustomCollection> collectionByEnum =
                shopifyApiService.ensureConfiguredCollections(GruenbergWatchesCollections.values());
    }
    
    @Test 
    public void testTemplate() throws Exception {
        FeedItem newfeed = new FeedItem();
        String out = freemakerservice.generateFromTemplate(newfeed);
        Assert.assertTrue(out != null);
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
    public void testTotalCount() throws Exception {
        Integer total = shopifyApiService.getProductCount();
        List<Product> allProducts = shopifyApiService.getAllProducts();
        Assert.assertTrue("Count of all product should match the list of all prodcut fetched",
                total.intValue() == allProducts.size());
        
    }
    
    @Test
    public void testCleanedProductBySkuMap() throws Exception{
        shopifyApiService.unlistDupelistings();
    }
    
    @Test
    public void testSpecificItemChanged() throws Exception {
        //Read from feed and save to db.
        List<FeedItem> feedItems = feedService.getItemsFromFeed();
        Optional<FeedItem> newItemHolder = feedItems.stream()
                .filter(c-> c.getWebTagNumber().equals("124746"))
                .findFirst();
        
        if (newItemHolder.isPresent()) {
            FeedItem fromFeed = newItemHolder.get();
            feedItemService.updateAutonomous(fromFeed);
            
            FeedItem itemFromDb = feedItemService.findByWebTagNumber(fromFeed.getWebTagNumber());
            System.out.println(itemFromDb);
            System.out.println(fromFeed);
            
            fromFeed.equals(itemFromDb);
        }
    }
    
    @Test
    public void insertSpecific() throws Exception {
        //139008
        Map<PredefinedCollection, CustomCollection> collectionByEnum = 
                shopifyApiService.ensureConfiguredCollections(KeyStoneCollections.values());
        
        Optional<FeedItem> specificToUpdate = 
                feedService.getItemsFromFeed().stream().filter(
                        feedItem -> feedItem.getWebTagNumber().equals("139008")).findAny();
        
        Product product = factoryService.createProduct(specificToUpdate.get());
        
        shopifyApiService.addProduct(product);
        
    }
    
    
    @Test
    public void updateSpecific() throws Exception {
        Map<PredefinedCollection, CustomCollection> collectionByEnum = 
                shopifyApiService.ensureConfiguredCollections(KeyStoneCollections.values());
        
        Optional<FeedItem> specificToUpdate = 
                feedService.getItemsFromFeed().stream().filter(
                        feedItem -> feedItem.getWebTagNumber().equals("127714")).findAny();
        
//        Optional<FeedItem> specificToUpdate = 
//                feedItemService.findAll().stream().filter(
//                        feedItem -> feedItem.getWebTagNumber().equals("127714")).findAny();
        
        
        if (specificToUpdate.isPresent()) {
            FeedItem item = specificToUpdate.get();
            item.setShopifyItemId("113647976457");
            try {
                if (item.getShopifyItemId() != null) {
                    Product product = factoryService.createProduct(item);
                    product.setId(item.getShopifyItemId());
                    
                    ProductVo existingProductVo = 
                            shopifyApiService.getProductByProductId(item.getShopifyItemId());
                    
                    if(existingProductVo !=null && existingProductVo.get() != null) {
                        Product existingProduct = existingProductVo.get();
                        
                        //Force delete the images and then re-upload.
                        shopifyApiService.deleteAllImageByProductId(existingProduct.getId());
                        factoryService.mergeProduct(existingProduct, product);
                        
                        shopifyApiService.updateProduct(product);
                        
                        //Delete existing collects
                        shopifyApiService.deleteAllCollectForProductId(item.getShopifyItemId());
                        
                        //Get the would be collection based on the item;
                        List<Collect> updatedCollections = 
                                CollectionUtility.getCollectionForProduct(item.getShopifyItemId(), item, collectionByEnum);
                        shopifyApiService.addProductAndCollectionsAssociations(updatedCollections);
                        
                    } 
                } 
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    
    @Test
    public void getAsCSV() throws Exception {
        List<FeedItem> allFeedItems = feedService.getItemsFromFeed();
        FileWriter fileWriter = new FileWriter("GruenbergWatches.csv");
        PrintWriter printWriter = new PrintWriter(fileWriter);
       
        for (FeedItem aItem : allFeedItems) {
            printWriter.printf("%s,%s,%s", aItem.getWebTagNumber(), aItem.getWebStatus(), aItem.getWebDescriptionShort());
            printWriter.println();
        }
        printWriter.close();
    }
    
    
    @Test
    public void testFeedReadyness() throws Exception {
    	feedReadynessService.isFeedReady();
    }
}
