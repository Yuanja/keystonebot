package com.gw.services.gwebaybot;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.ebay.soap.eBLBaseComponents.AmountType;
import com.ebay.soap.eBLBaseComponents.BestOfferDetailsType;
import com.ebay.soap.eBLBaseComponents.CategoryType;
import com.ebay.soap.eBLBaseComponents.CountryCodeType;
import com.ebay.soap.eBLBaseComponents.CurrencyCodeType;
import com.ebay.soap.eBLBaseComponents.ItemType;
import com.ebay.soap.eBLBaseComponents.ListingDetailsType;
import com.ebay.soap.eBLBaseComponents.ListingDurationCodeType;
import com.ebay.soap.eBLBaseComponents.ListingTypeCodeType;
import com.ebay.soap.eBLBaseComponents.NameValueListArrayType;
import com.ebay.soap.eBLBaseComponents.NameValueListType;
import com.ebay.soap.eBLBaseComponents.PictureDetailsType;
import com.ebay.soap.eBLBaseComponents.SellerPaymentProfileType;
import com.ebay.soap.eBLBaseComponents.SellerProfilesType;
import com.ebay.soap.eBLBaseComponents.SellerReturnProfileType;
import com.ebay.soap.eBLBaseComponents.SellerShippingProfileType;
import com.gw.domain.FeedItem;
import com.gw.services.BaseEBayItemFactory;

import freemarker.core.ParseException;
import freemarker.template.MalformedTemplateNameException;
import freemarker.template.TemplateException;
import freemarker.template.TemplateNotFoundException;

@Component
public class GWEBayItemFactoryService extends BaseEBayItemFactory {
    
    protected @Value("${css.hosting.url.base}") String cssHostingUrlBase;
    
    /* (non-Javadoc)
     * @see com.gw.components.IEbayItemService#buildItem(com.gw.FeedItem)
     */
    @Override
    public ItemType buildItem(FeedItem feedItem) 
            throws TemplateNotFoundException, MalformedTemplateNameException, ParseException, IOException, TemplateException {

        if (feedItem.getWebFlagEbayauction() != null && 
                feedItem.getWebFlagEbayauction().equals("1")) {
            return buildAuction(feedItem);
        }
        return buildFixedPrice(feedItem);
    }
    
    public ItemType buildFixedPrice(FeedItem feedItem) 
            throws TemplateNotFoundException, MalformedTemplateNameException, ParseException, IOException, TemplateException {
        ItemType item = buildBasicEBayItem(feedItem);
        
        //Fixed price Item GTC
        item.setListingType(ListingTypeCodeType.FIXED_PRICE_ITEM);
        item.setCurrency(CurrencyCodeType.USD);
        AmountType amount = new AmountType();
        amount.setValue(Double.parseDouble(feedItem.getWebPriceEbay()));
        item.setStartPrice(amount);
        item.setConditionID(getItemCondition(feedItem)); 
        item.setListingDuration(ListingDurationCodeType.GTC.value());

        //Best offer
        BestOfferDetailsType bestOfferDetails = new BestOfferDetailsType();
        bestOfferDetails.setBestOfferEnabled(true);
        item.setBestOfferDetails(bestOfferDetails);
        
        //Add minimumBestOffer
        ListingDetailsType ldt = new ListingDetailsType();
        AmountType minBestOfferPrice = new AmountType();
        minBestOfferPrice.setValue(Double.parseDouble(feedItem.getCostInvoiced()));
        ldt.setMinimumBestOfferPrice(minBestOfferPrice);
        item.setListingDetails(ldt);

        return item;
    }
    
    public ItemType buildAuction(FeedItem feedItem) 
            throws TemplateNotFoundException, MalformedTemplateNameException, ParseException, IOException, TemplateException {
        ItemType item = buildBasicEBayItem(feedItem);
        
        item.setListingType(ListingTypeCodeType.CHINESE);
        item.setCurrency(CurrencyCodeType.USD);
        AmountType amount = new AmountType();
        amount.setValue(Double.parseDouble("0.1"));
        item.setStartPrice(amount);
        item.setListingDuration(ListingDurationCodeType.DAYS_7.value());

        return item;
    }
    
    public ItemType buildBasicEBayItem(FeedItem feedItem) 
            throws TemplateNotFoundException, MalformedTemplateNameException, ParseException, IOException, TemplateException {
        ItemType item = new ItemType();
        
        item.setSellerInventoryID(feedItem.getWebTagNumber());
        item.setTitle(feedItem.getWebDescriptionShort());
        // listing category
        // Jewelry & Watches > Watches, Parts & Accessories > Wristwatches
        CategoryType cat = new CategoryType();
        cat.setCategoryID("31387");
        item.setPrimaryCategory(cat);
        feedItem.setCssHostingBaseUrl(cssHostingUrlBase);
        String description = freeMakerService.generateFromTemplate(feedItem);
        item.setDescription(description);
        item.setConditionID(getItemCondition(feedItem)); 
        
        // item location and country
        item.setLocation("Beverly Hills, California");
        item.setCountry(CountryCodeType.US);

        // item quantity
        item.setQuantity(1);

        PictureDetailsType itemPicDetail = new PictureDetailsType();
        itemPicDetail.setExternalPictureURL(
                imageService.getAvailableExternalImagePathByCSS(feedItem));
        item.setPictureDetails(itemPicDetail);
        
        item.setItemSpecifics(buildItemSpecifics(feedItem));
        
        setShipping(item, feedItem);

        return item;
    }
    
    private void setShipping(ItemType item, FeedItem feedItem) {
        /*
         * The Business Policies API and related Trading API fields are
         * available in sandbox. It will be available in production for a
         * limited number of sellers with Version 775. 100 percent of sellers
         * will be ramped up to use Business Polcies in July 2012
         *
         *  Account->my account->Business Policies, the ids are in the url.  for example profileId=5373972000
         */
        // Create Seller Profile container
        SellerProfilesType sellerProfile = new SellerProfilesType();

        // Set Payment ProfileId
        SellerPaymentProfileType sellerPaymentProfile = new SellerPaymentProfileType();
        if (feedItem.getWebFlagEbayauction() != null && !feedItem.getWebFlagEbayauction().equals("1")){
            if(Double.parseDouble(feedItem.getWebPriceEbay()) < 60000.0) {
               sellerPaymentProfile.setPaymentProfileID(Long.valueOf(paymentPolicyId));
           } else {
               sellerPaymentProfile.setPaymentProfileID(Long.valueOf(noImmediatePaymentPolicyId));
           } 
        } else {
            sellerPaymentProfile.setPaymentProfileID(Long.valueOf(noImmediatePaymentPolicyId));
        } 

        sellerProfile.setSellerPaymentProfile(sellerPaymentProfile);

        // Set Shipping ProfileId
        SellerShippingProfileType sellerShippingProfile = new SellerShippingProfileType();
        sellerShippingProfile.setShippingProfileID(Long.valueOf(shippingPolicyId));
        sellerProfile.setSellerShippingProfile(sellerShippingProfile);

        // Set Return Policy ProfileId
        SellerReturnProfileType sellerReturnProfile = new SellerReturnProfileType();
        sellerReturnProfile.setReturnProfileID(Long.valueOf(returnPolicyId));
        sellerProfile.setSellerReturnProfile(sellerReturnProfile);

        // Add Seller Profile to Item
        item.setSellerProfiles(sellerProfile);
    }
    
    // build sample item specifics
    /* (non-Javadoc)
     * @see com.gw.components.IEbayItemService#buildItemSpecifics(com.gw.FeedItem)
     */
    @Override
    public NameValueListArrayType buildItemSpecifics(FeedItem feedItem) {
        List<NameValueListType> itemSpecifics = 
                new ArrayList<NameValueListType>();
        addToSepcificsIfNotNull(itemSpecifics, "Department", feedItem.getWebStyle());
        addToSepcificsIfNotNull(itemSpecifics, "Brand", feedItem.getWebDesigner());
        addToSepcificsIfNotNull(itemSpecifics, "Model", feedItem.getWebWatchModel());
        addToSepcificsIfNotNull(itemSpecifics, "Reference", feedItem.getWebWatchManufacturerReferenceNumber());
        addToSepcificsIfNotNull(itemSpecifics, "Year Manufactured", feedItem.getWebWatchYear());
        addToSepcificsIfNotNull(itemSpecifics, "Case Material", feedItem.getWebMetalType());
        addToSepcificsIfNotNull(itemSpecifics, "Dial Color", feedItem.getWebWatchDial()); // Must have need to throw excepton and abort the listing.
        addToSepcificsIfNotNull(itemSpecifics, "Case Size", feedItem.getWebWatchDiameter());
        addToSepcificsIfNotNull(itemSpecifics, "Movement", feedItem.getMovementCode());
        addToSepcificsIfNotNull(itemSpecifics, "Band Type", feedItem.getWebWatchStrap());
        addToSepcificsIfNotNull(itemSpecifics, "With Original Packaging", feedItem.getWebWatchBoxPapers());
        addToSepcificsIfNotNull(itemSpecifics, "Sku", feedItem.getWebTagNumber()); 
        
        addToSepcificsIfNotNull(itemSpecifics, "Type", "Wristwatch");
        
        NameValueListArrayType nvArray = new NameValueListArrayType();
        nvArray.setNameValueList(itemSpecifics.toArray(new NameValueListType[0]));
        return nvArray;
    }
}
