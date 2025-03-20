package com.gw.service;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import com.ebay.sdk.ApiException;
import com.ebay.sdk.SdkException;
import com.ebay.sdk.call.AddItemCall;
import com.ebay.sdk.call.GetCategoryFeaturesCall;
import com.ebay.sdk.call.GetCategorySpecificsCall;
import com.ebay.sdk.util.eBayUtil;
import com.ebay.soap.eBLBaseComponents.DetailLevelCodeType;
import com.ebay.soap.eBLBaseComponents.ErrorParameterType;
import com.ebay.soap.eBLBaseComponents.ErrorType;
import com.ebay.soap.eBLBaseComponents.FeatureIDCodeType;
import com.ebay.soap.eBLBaseComponents.FeesType;
import com.ebay.soap.eBLBaseComponents.ItemType;
import com.ebay.soap.eBLBaseComponents.NameRecommendationType;
import com.ebay.soap.eBLBaseComponents.RecommendationsType;
import com.ebay.soap.eBLBaseComponents.ValueRecommendationType;
import com.gw.Config;
import com.gw.domain.FeedItem;
import com.gw.services.EBaySyncService;
import com.gw.services.EmailService;
import com.gw.services.ebayapi.EBayApiService;
import com.gw.services.ebayapi.EBayAppContextService;
import com.gw.services.gwebaybot.EbaySellingSummaryService;
import com.gw.services.gwebaybot.GWEBayBotFeedService;
import com.gw.services.gwebaybot.GWEBayItemFactoryService;

/**
 * 
 * @author jyuan
 */
@RunWith(SpringRunner.class)
@Import(Config.class)
@SpringBootTest
//@ActiveProfiles(profiles = "ebay-dev")
@ActiveProfiles(profiles = "ebay-prod")
public class GWEbayApiTest {
    final static Logger logger = LogManager.getLogger(GWEbayApiTest.class);
    
    @Autowired
    EBaySyncService ebaySyncService;
    
    @Autowired
    EBayAppContextService ebayAppContextService;
    
    @Autowired
    EmailService emailService;
    
    @Autowired
    EbaySellingSummaryService summaryService;
    
    @Autowired
    GWEBayItemFactoryService gwEBayItemfactory;
    
    @Autowired
    EBayApiService apiService;
    
    @Autowired
    private GWEBayBotFeedService feedService;
    
    @Test
    public void testFeedAndPost() throws Exception {

        ebaySyncService.sync(true);
    }
    
    @Test
    public void testSingleSku() throws Exception {
        ebaySyncService.handleSpecificSku("103489");
    }
    
    @Test
    /* (non-Javadoc)
     * @see com.gw.components.IEbayItemService#printOutRecommendedCategorySpecifics()
     */
    public void testGetRecommendedCategorySpecifics(){
        
        try{
            GetCategorySpecificsCall categoryCall = new GetCategorySpecificsCall(ebayAppContextService.getApiContext());
            categoryCall.setCategoryID(new String[] {"31387"});
            RecommendationsType[] allSpecifics = categoryCall.getCategorySpecifics();
            for (RecommendationsType aspecific : allSpecifics){
                for (NameRecommendationType nameRec : aspecific.getNameRecommendation()){
                    System.out.println(nameRec.getName() + " : " );
                    for (ValueRecommendationType valueRec : nameRec.getValueRecommendation()){
                        System.out.println( " --- : " + valueRec.getValue());
                    }
                }
            }
        } catch (Exception e){
            e.printStackTrace();
        }
    }
    
    @Test
    /* (non-Javadoc)
     */
    public void testGetValidListingDuration(){
        try{
            GetCategoryFeaturesCall categoryFeaturesCall = new GetCategoryFeaturesCall(ebayAppContextService.getApiContext());
            categoryFeaturesCall.setFeatureIDs(new FeatureIDCodeType[] {FeatureIDCodeType.LISTING_DURATIONS});
            categoryFeaturesCall.setDetailLevel(new DetailLevelCodeType[] {DetailLevelCodeType.RETURN_ALL});
            categoryFeaturesCall.setCategoryID("31387");

            categoryFeaturesCall.getCategoryFeatures();
            System.out.println(categoryFeaturesCall.getResponseXml());
        } catch (Exception e){
            e.printStackTrace();
        }
    }
    
    @Test
    public void testGetSellingSumaryService() throws Exception {
        summaryService.sendSellingSummaryByEmail();
    }
    
    @Test
    public void testGetAllActiveListings() throws Exception {
        Set<String> itemIds = apiService.getAllActiveEBayItemIds();
        for (String itemId : itemIds) {
            System.out.println(itemId);
        }
    }
    
    @Test
    public void testWebFlagEbayauction() throws Exception {
        //Read feed from test
        List<FeedItem> feedItems = feedService.loadFromXmlFile("src/test/resources/ebay-feed.xml");
        java.util.Optional<FeedItem> foundFeedItemOpt = feedItems.stream().filter(c -> c.getWebTagNumber().equals("142757")).findAny();
        assertTrue(foundFeedItemOpt.isPresent());
        //List<FeedItem> feedItems = feedService.getItemsFromFeed();
        if (foundFeedItemOpt.isPresent()) {
            //listItemNoDownloadImage(foundFeedItemOpt.get());
            assertTrue(foundFeedItemOpt.get().getWebFlagEbayauction().equals("1"));
        }
    }
    
    @Test
    public void testListEBayauction() throws Exception {
        //Read feed from test
        List<FeedItem> feedItems = feedService.loadFromXmlFile("src/test/resources/ebay-feed.xml");
        java.util.Optional<FeedItem> foundFeedItemOpt = feedItems.stream().filter(c -> c.getWebTagNumber().equals("151196")).findAny();
        assertTrue(foundFeedItemOpt.isPresent());
        //List<FeedItem> feedItems = feedService.getItemsFromFeed();
        if (foundFeedItemOpt.isPresent()) {
        	try {
        		listItemNoDownloadImage(foundFeedItemOpt.get());
        	} catch (ApiException apiE) {
        		//EBay error messages are hidden inside these Error Parameters. 
        		System.out.println(apiE.getLocalizedMessage());
        		for (ErrorType t : apiE.getErrors()) {
        			for (ErrorParameterType ep : t.getErrorParameters()) {
        				System.out.println(ep.getValue());
        			}
        		}
        	}
        }
    }
    
    
    private void listItemNoDownloadImage(FeedItem feedItem) throws ApiException, SdkException, Exception {
        ItemType ebayItem = gwEBayItemfactory.buildItem(feedItem);
        AddItemCall api = new AddItemCall(ebayAppContextService.getApiContext()); 
        api.setItem(ebayItem);
        
        FeesType fees = apiService.callAddItem(api);
        double listingFee = 0.0;
        if (fees != null){
            listingFee = eBayUtil.findFeeByName(fees.getFee(), "ListingFee").getFee().getValue();
        }
        logger.info("Listing fee is: " + new Double(listingFee).toString());
        logger.info("Listed Item ID: " + ebayItem.getItemID());
        logger.info("List ends: " + api.getReturnedEndTime().getTime().toString());
        logger.info("The list was listed successfully!");
    }
}
