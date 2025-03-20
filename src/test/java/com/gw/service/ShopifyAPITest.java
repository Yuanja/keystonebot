package com.gw.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
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
import com.gw.services.CollectionUtility;
import com.gw.services.gruenbergwatches.GWFeedService;
import com.gw.services.gruenbergwatches.GWShopifyProductFactoryService;
import com.gw.services.shopifyapi.ShopifyAPIService;
import com.gw.services.shopifyapi.objects.Collect;
import com.gw.services.shopifyapi.objects.CustomCollection;
import com.gw.services.shopifyapi.objects.CustomCollections;
import com.gw.services.shopifyapi.objects.Image;
import com.gw.services.shopifyapi.objects.Product;
import com.gw.services.shopifyapi.objects.ProductVo;
import com.gw.services.shopifyapi.objects.Variant;

@RunWith(SpringRunner.class)
@Import(Config.class)
@SpringBootTest
@ActiveProfiles(profiles = "gw-dev")
public class ShopifyAPITest {

    @Autowired
    ShopifyAPIService apiService;
    
    @Autowired
    GWFeedService feedService;
    
    @Autowired
    private GWShopifyProductFactoryService gwProductFactoryService;
    
    @Test
    public void testDupeFeed() throws Exception {
        //Read feed from test
        List<FeedItem> feedItems = feedService.loadFromXmlFile("src/test/resources/dupe-pretty.xml");
        //Check to see if there are dupes.
        Map<String, FeedItem> itemsBySku = new HashMap<String, FeedItem>();
        feedItems.stream().forEach(c->{
            if (itemsBySku.containsKey(c.getWebTagNumber())) {
                throw new IllegalArgumentException("Feed has duplicate sku! Skipping this schedule.");
            } else {
                itemsBySku.put(c.getWebTagNumber(), c);
            }
        });
    }
    
    @Test
    public void testSpecificItemCollection() throws Exception{
        List<FeedItem> feedItems = feedService.getItemsFromFeed();
        
        Optional<FeedItem> newItemHolder = feedItems.stream().filter(c-> c.getWebTagNumber().equals("124804")).findFirst();
        if (newItemHolder.isPresent()) {
            Map<PredefinedCollection, CustomCollection> collectionByEnum =
                    apiService.ensureConfiguredCollections(GruenbergWatchesCollections.values());
            
            List<Collect> collections = CollectionUtility
                    .getCollectionForProduct("testId", newItemHolder.get(), collectionByEnum);
            Assert.assertTrue(!collections.isEmpty());
        }
    }
    
    @Test
    public void testSpecificItemFromFeed() throws Exception {
        //Read feed from test
        List<FeedItem> feedItems = feedService.loadFromXmlFile("src/test/resources/fmresultset.xml");
        //List<FeedItem> feedItems = feedService.getItemsFromFeed();
        
        Optional<FeedItem> newItemHolder = feedItems.stream().filter(c-> c.getWebTagNumber().equals("124560")).findFirst();
        if (newItemHolder.isPresent()) {
            FeedItem newItem = newItemHolder.get();
            
            Product p = apiService.addProduct(gwProductFactoryService.createProduct(newItem));
            String pid = p.getId();
            Map<PredefinedCollection, CustomCollection> collectionByEnum =
                    apiService.ensureConfiguredCollections(GruenbergWatchesCollections.values());
            List<Collect> inscollects = CollectionUtility.getCollectionForProduct(pid, newItem, collectionByEnum);
            apiService.addProductAndCollectionsAssociations(inscollects);
            
            try {
                List<Collect> collects = apiService.getCollectsForProductId(pid);
                
                Assert.assertTrue(collects.size() > 0);
                apiService.deleteAllCollectForProductId(pid);
                List<Collect> emptyCollects = apiService.getCollectsForProductId(pid);
                Assert.assertTrue(emptyCollects.size() == 0);
                
                //reset the collect
                collects.stream().forEach(c-> {
                    c.setCreatedAt(null);
                    c.setId(null);
                    c.setPosition(null);
                    c.setSortValue(null);
                    c.setUpdatedAt(null);
                });
                apiService.addProductAndCollectionsAssociations(collects);
                List<Collect> addedCollects = apiService.getCollectsForProductId(pid);
                
                Assert.assertTrue(collects.size() == addedCollects.size());
            } finally {
                apiService.deleteProductById(pid);
            }
        }
    }
    
    @Test
    public void testRecreateCollection() throws Exception {
        //Read feed from test
        List<FeedItem> feedItems = feedService.loadFromXmlFile("src/test/resources/testShopifyUpdate/feed1.xml");
        FeedItem newItem = feedItems.get(0);
        Product p = apiService.addProduct(gwProductFactoryService.createProduct(newItem));
        String pid = p.getId();
        Map<PredefinedCollection, CustomCollection> collectionByEnum =
                apiService.ensureConfiguredCollections(GruenbergWatchesCollections.values());
        List<Collect> inscollects = CollectionUtility.getCollectionForProduct(pid, newItem, collectionByEnum);
        apiService.addProductAndCollectionsAssociations(inscollects);
        
        try {
            List<Collect> collects = apiService.getCollectsForProductId(pid);
            
            Assert.assertTrue(collects.size() > 0);
            apiService.deleteAllCollectForProductId(pid);
            List<Collect> emptyCollects = apiService.getCollectsForProductId(pid);
            Assert.assertTrue(emptyCollects.size() == 0);
            
            //reset the collect
            collects.stream().forEach(c-> {
                c.setCreatedAt(null);
                c.setId(null);
                c.setPosition(null);
                c.setSortValue(null);
                c.setUpdatedAt(null);
            });
            apiService.addProductAndCollectionsAssociations(collects);
            List<Collect> addedCollects = apiService.getCollectsForProductId(pid);
            
            Assert.assertTrue(collects.size() == addedCollects.size());
        } finally {
            apiService.deleteProductById(pid);
        }
    }
    
    @Test 
    public void testGetAllCollections() throws Exception {
        CustomCollections allCollections = apiService.getAllCustomCollections();
        Assert.assertNotNull(allCollections);
    }
    
    @Test
    public void testAddProductAndFindProductBySku() throws Exception {
        
        Product newProduct = createTestProduct();
        
        Product resultNewlyInsertedProduct = apiService.addProduct(newProduct);

        
        Assert.assertNotNull(resultNewlyInsertedProduct);
        ProductVo newlyInserted = apiService.getProductByProductId(resultNewlyInsertedProduct.getId());
        
        try {
            Assert.assertNotNull(newlyInserted.get().getVariants());
            List<Variant> variantAdded = newlyInserted.get().getVariants();
            Assert.assertTrue("variant size is not 1", variantAdded.size() == 1);
            String sku = (String)variantAdded.get(0).getSku();
            Assert.assertEquals("Sku doesn't match! ", "testABC", sku);
            
            //Update the product.
            Product updatedProduct = createTestProduct();
            updatedProduct.setId(newlyInserted.get().getId());
            updatedProduct.setTitle("updated board");
            updatedProduct.getVariants().get(0).setPrice("9");
            apiService.updateProduct(updatedProduct);
            ProductVo newlyUpdated = apiService.getProductByProductId(resultNewlyInsertedProduct.getId());
            
            Assert.assertTrue("price should be 9", 
                    newlyUpdated.get().getVariants().get(0).getPrice().equals("9.00"));
            Assert.assertTrue("title should be updated", 
                    newlyUpdated.get().getTitle().equals("updated board"));
            
        } finally {
            apiService.deleteProductById(resultNewlyInsertedProduct.getId());
        }
    }
    
    @Test
    public void testUpdateProduct() throws Exception {
        //Read feed from test
        List<FeedItem> feedItems = feedService.loadFromXmlFile("src/test/resources/testShopifyUpdate/feed1.xml");
        FeedItem newItem = feedItems.get(0);
        Product existingProduct = apiService.addProduct(gwProductFactoryService.createProduct(newItem));
        try {
            newItem.setShopifyItemId(existingProduct.getId());
            newItem.setWebStatus("Sold");
            
            Product updatedProduct  = gwProductFactoryService.createProduct(newItem);
            gwProductFactoryService.mergeProduct(existingProduct, updatedProduct);
            Image aimage = updatedProduct.getImages().get(0);
            aimage.setSrc("https://172.91.140.131/fmi/xml/cnt/data.jpg?-db=DEG&-lay=INVENTORY_WEB+-+Web+Publishing&-recid=8153&-field=web_image_1");
            List<Image> newImgs = new ArrayList<Image>();
            newImgs.add(aimage);
            updatedProduct.setImages(newImgs);
            apiService.deleteAllImageByProductId(updatedProduct.getId());
            apiService.updateProduct(updatedProduct);
        } finally{
            apiService.deleteProductById(existingProduct.getId());
        }
    }
    
    private Product createTestProduct() {
        Product newProduct = new Product();
        
        newProduct.setTitle("Burton Custom Freestyle");
        newProduct.setBodyHtml("Good snowboard!");
        newProduct.setVendor("Burton");
        newProduct.setPublishedScope("global");
        newProduct.setProductType("Snowboard");
        
        Variant variant = new Variant();
        variant.setSku("testABC");
        //variant.setInventoryQuantity("1");
        variant.setTaxable("true");
        variant.setInventoryManagement("shopify");
        variant.setInventoryPolicy("deny");
        
        newProduct.setVariants(Arrays.asList(variant));
        return newProduct;
    }
}
