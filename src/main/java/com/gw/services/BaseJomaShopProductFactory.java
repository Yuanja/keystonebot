package com.gw.services;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import com.google.gson.Gson;
import com.gw.domain.FeedItem;
import com.gw.services.jomashop.Category;
import com.gw.services.jomashop.CategoryProperties;
import com.gw.services.jomashop.Inventory;
import com.gw.services.jomashop.Product;
import com.gw.services.jomashop.SingleProductRequest;
import com.gw.services.jomashop.InventoryUpdateRequest.Status;

@Component
public class BaseJomaShopProductFactory {
    
    private static Logger logger = LogManager.getLogger(BaseJomaShopProductFactory.class);
    
    @Value("classpath:PreOwnedWatchCategory.json")
    private Resource usedWatchCategoryJsonFileResourceFile;
    
    @Value("classpath:NewWatchCategory.json")
    private Resource newWatchCategoryJsonFileResourceFile;
    
    @Autowired
    private ImageService imageService;

    private static Category usedWatchCategory;
    
    private static Category newWatchCategory;
    
    private static Class feedItemClass;

    private void init() throws FileNotFoundException, IOException, ClassNotFoundException {
        if (usedWatchCategory == null) {
            //initialize
            BufferedReader br = new BufferedReader(
                    new FileReader(usedWatchCategoryJsonFileResourceFile.getFile()));
            Gson gson = new Gson();
            usedWatchCategory = gson.fromJson(br, Category.class);
        }
        
        if (newWatchCategory == null) {
            //initialize
            BufferedReader br = new BufferedReader(
                    new FileReader(newWatchCategoryJsonFileResourceFile.getFile()));
            Gson gson = new Gson();
            newWatchCategory = gson.fromJson(br, Category.class);
        }
        
        if (feedItemClass == null)
            feedItemClass = Class.forName("com.gw.domain.FeedItem");
    }
    
    public SingleProductRequest makeProduct(FeedItem item) throws Exception{
        init();
        
        SingleProductRequest sp = new SingleProductRequest();
        Product p = new Product();
        sp.product = p;
        p.sku = item.getWebTagNumber();
        
        logger.debug("Making product for sku: " + item.getWebTagNumber());
        
        if (item.getWebDesigner() != null)
            p.brand = getBrand(item);
        else
            p.addErrorMsg("WebDesigner is null!");
            
            
        if (item.getWebDescriptionShort() != null)
            p.name = item.getWebDescriptionShort();
        else
            p.addErrorMsg("WebDescriptionShort is null!");
            
        if (item.getWebWatchManufacturerReferenceNumber() != null)
            p.manufacturer_number = item.getWebWatchManufacturerReferenceNumber();
        else 
            p.addErrorMsg("WebWatchManufacturerReferenceNumber is null!");
        
        Inventory inv = new Inventory();
        sp.inventory = inv;
        inv.quantity = 1;
        inv.status = Status.Active.getStringVal();
        
        //web_price_wholesale = price
        if (item.getWebPriceWholesale() != null) {
            inv.price = Double.parseDouble(item.getWebPriceWholesale());
            if (! (Math.abs(inv.price - 0) > 0.1)) {
                p.addErrorMsg("WebPriceWholesale must be greater than 0!");
            }
        } else {
            p.addErrorMsg("WebPriceWholesale is null!");
        }
        
        //getWebWatchCondition can't be null
        if (item.getWebWatchCondition() == null) {
            p.addErrorMsg("WebWatchCondition is null!");
            //can't continue if we don't know what condition it is.
            return sp;
        }
        
        String[] imagesArray = imageService.getAvailableDefaultImageUrl(item);
        if (imagesArray != null && imagesArray.length>0) {
            p.images = imagesArray;
        } else {
            p.addErrorMsg("No images!");
        }
        
        Category categoryToUse = usedWatchCategory;
        p.category = "Pre-Owned Watches";
        if ("New".equalsIgnoreCase(item.getWebWatchCondition()) ||
                "Unworn".equalsIgnoreCase(item.getWebWatchCondition()) ||
                item.getWebWatchCondition().contains("Unworn")) {
            categoryToUse = newWatchCategory;
            p.category = "Watches";
        }
        
        Map<String, String> properties = new HashMap<String, String>();
        for (CategoryProperties catProp : categoryToUse.properties ) {
            String feedItemFieldName = catProp.feedItemField;
            String gwValue = catProp.gwValue;
            
            if (feedItemFieldName!=null) {
                Method m = feedItemClass.getMethod("get"+feedItemFieldName.substring(0,1).toUpperCase() + feedItemFieldName.substring(1) );
                String feedValue = (String) m.invoke(item, null);
                String pvalue = null;
                
                feedValue = doExtraFeedValueOverride(feedItemFieldName, feedValue);
                
                if (catProp.kind.equalsIgnoreCase("enumerable")) {
                    if (catProp.gwValueMap != null) {
                        pvalue=catProp.gwValueMap.get(feedValue);
                        if (pvalue == null) {
                            if (catProp.gwDefaultValue != null) {
                                //No matching map value, defaulting
                                pvalue = catProp.gwDefaultValue;
                            } else {
                                p.addErrorMsg("Incompatible value for " +catProp.key+ ", "+ feedItemFieldName +" value: '" + feedValue +"'.");
                            }
                        } 
                    } else {
                        //Get the value and validate against expected.
                        boolean found = false;
                        for (String expectedValue : catProp.data.values) {
                            if (expectedValue.equalsIgnoreCase(feedValue)) {
                                found = true;
                                break;
                            }
                        }
                        if (found)
                            pvalue = feedValue;
                        else if (catProp.gwDefaultValue != null) {
                            //No matching map value, defaulting
                            pvalue = catProp.gwDefaultValue;
                        } else {
                            p.addErrorMsg("Incompatible value for " +catProp.key+ ", "+ feedItemFieldName +" value: '" + feedValue +"'.");
                        }
                    }
                } else {
                    pvalue=feedValue;
                }
                    
                if (pvalue != null) {
                    logger.debug("For Used Watch cateogory property: " + catProp.key 
                            + " feedItem: "+ item.getWebTagNumber() 
                            + " : " +feedItemFieldName+ " : " + feedValue + " maps to : " + pvalue);
                    properties.put(catProp.key, pvalue);
                } 
            } else if (gwValue != null){
                logger.debug("For Used Watch cateogory property: " + catProp.key 
                        + " feedItem: "+ item.getWebTagNumber() 
                        + " : " + gwValue );
                properties.put(catProp.key, gwValue);
            }
        }
        
        if (properties.size() > 0 )
            p.properties = properties;
        
        return sp;
    }
    
    private String doExtraFeedValueOverride(String feedItemFieldName, String feedValue) {
        if (feedValue == null)
            return null;
        
        if (feedItemFieldName.equalsIgnoreCase("webWatchDiameter")) {
            return feedValue.substring(0, feedValue.length()-2 ); //Take out the 'mm' unit
        }
        
        return feedValue;
    }
    
    private String getBrand(FeedItem feedItem) {
        String feedValue = feedItem.getWebDesigner();
        
        if (feedValue.equalsIgnoreCase("TAG Heuer"))
            return "Tag Heuer";
        if (feedValue.equalsIgnoreCase("Bulgari"))
            return "Bvlgari";
        if (feedValue.equalsIgnoreCase("Van Cleef & Arpels"))
            return "Van Cleef";
        
        return feedValue;
    }
}
