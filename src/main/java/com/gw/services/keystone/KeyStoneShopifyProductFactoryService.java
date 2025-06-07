package com.gw.services.keystone;

import com.gw.domain.FeedItem;
import com.gw.services.product.ProductCreationService;
import com.gw.services.shopifyapi.objects.Product;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@Profile({"keystone-prod", "keystone-dev"})
public class KeyStoneShopifyProductFactoryService extends ProductCreationService {

    @Override
    public void addTags(FeedItem feedItem, Product product) {
        StringBuffer tagString = new StringBuffer();
        
        if (feedItem.getWebDesigner() != null) {
            tagString.append(feedItem.getWebDesigner()).append(",");
        }
        
        if (feedItem.getWebWatchModel() != null) {
            tagString.append(feedItem.getWebWatchModel()).append(",");
        }
        
        if (feedItem.getWebPriceKeystone() != null) {
            double price = Double.parseDouble(feedItem.getWebPriceKeystone());
            if (price < 5000.0 ) {
                tagString.append("Under $5000").append(",");
            } else if (price >= 5000.0 && price <10000.0) {
                tagString.append("$5,000 to $10,000").append(",");
            } else if (price >= 10000.0 && price <30000.0) {
                tagString.append("$10,000 to $30,000").append(",");
            } else if (price >= 30000.0) {
                tagString.append("$30,000 and above").append(",");
            }
        }
        
        if (feedItem.getWebMetalType() != null) {
            Map<String, String> materialMap = getMaterialMap();
            if (materialMap.containsKey(feedItem.getWebMetalType())) {
                tagString.append(materialMap.get(feedItem.getWebMetalType())).append(",");
            }
        }

        if (feedItem.getWebCategory() != null){
            tagString.append(feedItem.getWebCategory()).append(",");
        }
        
        product.setTags(tagString.toString());
    }
    

  
    
    private Map<String, String> getMaterialMap(){
        Map<String, String> materialMap = new HashMap<String, String>();
        materialMap.put("PVD Steel/TItanium","Black");
        materialMap.put("Tantalum/RG","Black");
        materialMap.put("Lucite/18K YG","Black");
        materialMap.put("PVD Steel","Black");
        materialMap.put("SS/PLAT","Platinum");
        materialMap.put("Enamel/Plat","Platinum");
        materialMap.put("PLT/Ceramic","Platinum");
        materialMap.put("Plt/14K WG","Platinum");
        materialMap.put("PT950","Platinum");
        materialMap.put("PT950/18K YG","Platinum");
        materialMap.put("Palladium","Platinum");
        materialMap.put("PT900","Platinum");
        materialMap.put("PT850","Platinum");
        materialMap.put("Platinum","Platinum");
        materialMap.put("9K RG","Rose Gold");
        materialMap.put("RG Vermeil","Rose Gold");
        materialMap.put("14K RG","Rose Gold");
        materialMap.put("18k RG","Rose Gold");
        materialMap.put("Steel","Steel");
        materialMap.put("Two-Tone","Two-Tone");
        materialMap.put("SS/RG","Two-Tone");
        materialMap.put("SS/YG","Two-Tone");
        materialMap.put("YG/RG","Two-Tone");
        materialMap.put("WG/YG","Two-Tone");
        materialMap.put("Rose Gold/Titanium","Two-Tone");
        materialMap.put("PVD Steel/RG","Two-Tone");
        materialMap.put("Ceramic/RG","Two-Tone");
        materialMap.put("Ceramic/WG","Two-Tone");
        materialMap.put("18K RG/WG/YG","Two-Tone");
        materialMap.put("Platinum/RG","Two-Tone");
        materialMap.put("18K YG/Plt","Two-Tone");
        materialMap.put("18K YG/18K WG","Two-Tone");
        materialMap.put("Plt/18K WG","Two-Tone");
        materialMap.put("Plt/18K YG","Two-Tone");
        materialMap.put("Plt/18K RG","Two-Tone");
        materialMap.put("WG/RG","Two-Tone");
        materialMap.put("14K WG/14K RG","Two-Tone");
        materialMap.put("14K YG/14K RG","Two-Tone");
        materialMap.put("18K WG/18K YG/Plt","Two-Tone");
        materialMap.put("14K YG/14K WG","Two-Tone");
        materialMap.put("18K RG/18K YG","Two-Tone");
        materialMap.put("18K RG/PLT","Two-Tone");
        materialMap.put("18K RG/18K WG","Two-Tone");
        materialMap.put("14K YG/18K YG","Two-Tone");
        materialMap.put("Plt/14K WG","Two-Tone");
        materialMap.put("18K YG/ Silv","Two-Tone");
        materialMap.put("PLT/14K YG","Two-Tone");
        materialMap.put("Steel/RG","Two-Tone");
        materialMap.put("14K WG/14K RG","Two-Tone");
        materialMap.put("Plt/20K YG","Two-Tone");
        materialMap.put("18K WG/14K WG","Two-Tone");
        materialMap.put("SS/18K WG","Two-Tone");
        materialMap.put("14K WG/18K YG","Two-Tone");
        materialMap.put("18K YG/Silver","Two-Tone");
        materialMap.put("14K YG/ Silver","Two-Tone");
        materialMap.put("Silver/Gold","Two-Tone");
        materialMap.put("14K YG/14K RG","Two-Tone");
        materialMap.put("Platinum/RG","Two-Tone");
        materialMap.put("Tricolor","Two-Tone");
        materialMap.put("PT900/18K YG","Two-Tone");
        materialMap.put("White/Rose Gold","Two-Tone");
        materialMap.put("18k WG","White Gold");
        materialMap.put("14K WG","White Gold");
        materialMap.put("10K WG","White Gold");
        materialMap.put("14k YG","Yellow Gold");
        materialMap.put("18k YG","Yellow Gold");
        materialMap.put("Gold Vermeil","Yellow Gold");
        materialMap.put("9K YG","Yellow Gold");
        materialMap.put("14K Gold Filled","Yellow Gold");
        materialMap.put("YG/Enamel","Yellow Gold");
        materialMap.put("12K Gold Filled","Yellow Gold");
        materialMap.put("Honey Gold","Yellow Gold");
        materialMap.put("10K YG","Yellow Gold");
        materialMap.put("Gold Plated","Yellow Gold");
        materialMap.put("9k Gold","Yellow Gold");
        materialMap.put("12K YG","Yellow Gold");
        materialMap.put("22K YG","Yellow Gold");
        materialMap.put("24K YG","Yellow Gold");
        materialMap.put("Gold Filled","Yellow Gold");
        materialMap.put("20K YG","Yellow Gold");
        materialMap.put("21K YG","Yellow Gold");
        materialMap.put("21.6K YG","Yellow Gold");
        materialMap.put("Ceramic","Other");
        materialMap.put("Aluminium","Other");
        materialMap.put("Tantalum","Other");
        materialMap.put("Silver","Other");
        materialMap.put("Plastic","Other");
        materialMap.put("Carbon Fiber","Other");
        materialMap.put("Bronze","Other");
        materialMap.put("Yttrium/Plt","Other");
        materialMap.put("Sterling Silver/18K YG","Other");
        materialMap.put("-HK","Other");
        materialMap.put("-KA","Other");
        materialMap.put("-KH","Other");
        materialMap.put("-KB","Other");
        materialMap.put("Zirconium","Other");
        materialMap.put("Zalium","Other");
        materialMap.put("Titanium","Other");
        return materialMap;
    }
}
