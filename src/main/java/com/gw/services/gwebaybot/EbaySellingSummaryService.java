package com.gw.services.gwebaybot;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ebay.sdk.ApiException;
import com.ebay.sdk.SdkException;
import com.ebay.sdk.call.GetMyeBaySellingCall;
import com.ebay.soap.eBLBaseComponents.GetMyeBaySellingResponseType;
import com.ebay.soap.eBLBaseComponents.ItemListCustomizationType;
import com.ebay.soap.eBLBaseComponents.MyeBaySellingSummaryType;
import com.gw.services.EmailService;
import com.gw.services.ebayapi.EBayAppContextService;

/**
 * @author jyuan
 *
 */
@Component
public class EbaySellingSummaryService {
    
    private static Logger logger = LogManager.getLogger(EbaySellingSummaryService.class);
    
    @Autowired
    private EmailService emailService;
    
    @Autowired
    private EBayAppContextService ebayAppContextService;
    
    public MyeBaySellingSummaryType getListingLimitSummary() throws Exception {
        GetMyeBaySellingCall api = null;
        logger.info("Getting ebay summary for selling limit.");
        try {
            api = new GetMyeBaySellingCall(ebayAppContextService.getApiContext());
            ItemListCustomizationType activeListCust = new ItemListCustomizationType();
            activeListCust.setInclude(Boolean.TRUE);
            api.setActiveList(activeListCust);
            
            api = new GetMyeBaySellingCall(ebayAppContextService.getApiContext());
            ItemListCustomizationType itemSumCust = new ItemListCustomizationType();
            itemSumCust.setInclude(Boolean.TRUE);
            api.setSellingSummary(itemSumCust);
            //Invoke API
            api.getMyeBaySelling();
            
            GetMyeBaySellingResponseType response = api.getReturnedMyeBaySellingResponse();
            MyeBaySellingSummaryType s = response.getSummary();
            
            return s;
           
        } catch (ApiException apie) {
           logger.error(apie);
        } catch (SdkException sdke) {
           logger.error(sdke);
        }
        return null;
    }
    
    //@Scheduled(cron="1 1 11 * * *")
    public void sendSellingSummaryByEmail() {
        logger.info("Getting ebay summary.");
        try {
            MyeBaySellingSummaryType s = getListingLimitSummary();
            StringBuffer summaryEmailTxt = new StringBuffer();
            summaryEmailTxt.append("Remaining listing value limit: $" + s.getAmountLimitRemaining().getValue() + "\n");
            summaryEmailTxt.append("Remaining listing quantity limit: " + s.getQuantityLimitRemaining() + "\n");
            summaryEmailTxt.append("Sold listing count: " + s.getTotalSoldCount() + "\n");
            summaryEmailTxt.append("Sold listing value: $" + s.getTotalSoldValue().getValue() + "\n");
            
            emailService.sendMessage("Daily EBay Limit Report", summaryEmailTxt.toString());
            logger.info(summaryEmailTxt.toString());
            
        } catch (Exception e) {
            logger.error(e);
        }
    }
}