package com.gw.services;

import java.io.IOException;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.ebay.soap.eBLBaseComponents.ItemType;
import com.ebay.soap.eBLBaseComponents.NameValueListArrayType;
import com.ebay.soap.eBLBaseComponents.NameValueListType;
import com.gw.domain.FeedItem;

import freemarker.core.ParseException;
import freemarker.template.MalformedTemplateNameException;
import freemarker.template.TemplateException;
import freemarker.template.TemplateNotFoundException;

public class BaseEBayItemFactory {
    
    protected @Value("${PAYMENT_POLICY_ID}") String paymentPolicyId;
    protected @Value("${PAYMENT_POLICY_ID_NO_IMMEDIATE}") String noImmediatePaymentPolicyId;
    protected @Value("${SHIPPING_POLICY_ID}") String shippingPolicyId;
    protected @Value("${RETURN_POLICY_ID}") String returnPolicyId;
    
    @Autowired
    protected FreeMakerService freeMakerService;
    
    @Autowired
    protected ImageService imageService;

    /* (non-Javadoc)
     * @see com.gw.components.IEbayItemService#buildItem(com.gw.FeedItem)
     */
    public ItemType buildItem(FeedItem feedItem) 
            throws TemplateNotFoundException, MalformedTemplateNameException, ParseException, IOException, TemplateException {
        
        return null;
    }
    
    protected int getItemCondition(FeedItem feedItem) {
        //check with Justin
        //New item https://developer.ebay.com/devzone/finding/callref/Enums/conditionIdList.html
        //https://developer.ebay.com/devzone/finding/callref/Enums/conditionIdList.html#ConditionIDValues
        
        String feedCondition = feedItem.getWebWatchCondition();
        if (feedCondition.equalsIgnoreCase("New")) {
            return 1000;
        } else {
            return 3000;
        }
    }
    // build sample item specifics
    /* (non-Javadoc)
     * @see com.gw.components.IEbayItemService#buildItemSpecifics(com.gw.FeedItem)
     */
    public NameValueListArrayType buildItemSpecifics(FeedItem feedItem) {
        return null;
    }
    
    protected void addToSepcificsIfNotNull(
            List<NameValueListType>itemSpecifics, 
            String specification, String value){
        if (value != null){
            itemSpecifics.add(getNameValueListType(specification, value));
        }
    }
    
    protected NameValueListType getNameValueListType(String name, String value){
        NameValueListType nv1 = new NameValueListType();
        nv1.setName(name);
        nv1.setValue(new String[] {value});
        return nv1;
    }
    
}
