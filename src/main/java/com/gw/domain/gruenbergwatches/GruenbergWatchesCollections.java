package com.gw.domain.gruenbergwatches;

import org.apache.commons.lang3.StringUtils;

import com.gw.domain.FeedItem;
import com.gw.domain.PredefinedCollection;
import com.gw.domain.keystone.KeyStoneCollections;

public enum GruenbergWatchesCollections implements PredefinedCollection {
    
    MENS("Men's", false),
    WOMENS("Women's", false),
    UNDER_5000("Under $5,000", false),
    PATEK_PHILIPPE("Patek Philippe", true),
    VINTAGE_WATCHES("Vintage Watches", false),
    DIAMOND_WATCHES("Diamond Watches", false),
    ROLEX("Rolex", true),
    Audemars_Piguet("Audemars Piguet", true),
    Piaget("Piaget", true),
    Cartier("Cartier", true),
    Hublot("Hublot", true),
    Paneri("Panerai", true),
    FPJourne("F.P. Journe", true),
    VintageJewelry("Vintage Jewelry", false),
    OtherBrand("Other Brand", false)
    ;
    
    private String title;
    private boolean isBrand;
    
    GruenbergWatchesCollections(String title, boolean isBrand){
        this.title = title;
        this.isBrand = isBrand;
    }
    
    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public boolean isBrand() {
        return isBrand;
    }
    
    @Override
    public boolean accepts(FeedItem feedItem) {
        switch(this)
        {
            case MENS:
                return isMens(feedItem);
            case DIAMOND_WATCHES:
                return isDiamond(feedItem);           
            case UNDER_5000:
                return isUnder5000(feedItem);
            case VINTAGE_WATCHES:
                return isVintage(feedItem);
            case WOMENS:
                return isWomens(feedItem);
            case PATEK_PHILIPPE:
                return isPatek(feedItem);
            case ROLEX:
                return isRolex(feedItem);
            case Audemars_Piguet:
                return isAudemarsPiguet(feedItem);
            case Piaget:
                return isPiaget(feedItem);
            case Cartier:
                return isCartier(feedItem);
            case Hublot:
                return isHublot(feedItem);
            case Paneri:
                return isPanerai(feedItem);
            case FPJourne:
                return isFPJourne(feedItem);
            case VintageJewelry:
                return isVintageJewelry(feedItem);
            case OtherBrand:
                return isOtherBrand(feedItem);
            default:
                return false;
            
        }
    }
    
    private boolean isDiamond(FeedItem feedItem) {
        if (StringUtils.containsIgnoreCase(feedItem.getWebCategory(), "watches" ) && 
                StringUtils.containsIgnoreCase(feedItem.getWebDescriptionShort(), "diamond" )) {
            return true;
        }
        return false;
    }
    
    private boolean isVintage(FeedItem feedItem) {
        if (StringUtils.containsIgnoreCase(feedItem.getWebCategory(), "watches" ) && 
                StringUtils.containsIgnoreCase(feedItem.getWebDescriptionShort(), "vintage" )) {
            return true;
        }
        return false;
    }
    
    private boolean isMens(FeedItem feedItem) {
        if (StringUtils.containsIgnoreCase(feedItem.getWebCategory(), "watches" ) && 
                feedItem.getWebStyle() != null && 
                (feedItem.getWebStyle().equals("Gents") || feedItem.getWebStyle().equals("Unisex"))
            )
            return true;
        return false;
    }
    
    private boolean isWomens(FeedItem feedItem) {
        if (StringUtils.containsIgnoreCase(feedItem.getWebCategory(), "watches" ) && 
                feedItem.getWebStyle() != null && 
                (feedItem.getWebStyle().equals("Ladies") || feedItem.getWebStyle().equals("Unisex"))
            )
            return true;
        return false;
    }
    
    private boolean isPatek(FeedItem feedItem) {
        if (StringUtils.containsIgnoreCase(feedItem.getWebCategory(), "watches" ) && 
                feedItem.getWebDesigner().equals("Patek Philippe"))
            return true;
        return false;
    }
    
    private boolean isRolex(FeedItem feedItem) {
        if (StringUtils.containsIgnoreCase(feedItem.getWebCategory(), "watches" ) && 
                feedItem.getWebDesigner().equals("Rolex"))
            return true;
        return false;
    }
    
    private boolean isAudemarsPiguet(FeedItem feedItem) {
        if (StringUtils.containsIgnoreCase(feedItem.getWebCategory(), "watches" ) && 
                feedItem.getWebDesigner().equals("Audemars Piguet"))
            return true;
        return false;
    }
    
    private boolean isPiaget(FeedItem feedItem) {
        if (StringUtils.containsIgnoreCase(feedItem.getWebCategory(), "watches" ) && 
                feedItem.getWebDesigner().equals("Piaget"))
            return true;
        return false;
    }
    
    private boolean isCartier(FeedItem feedItem) {
        if (StringUtils.containsIgnoreCase(feedItem.getWebCategory(), "watches" ) && 
                feedItem.getWebDesigner().equals("Cartier"))
            return true;
        return false;
    }    
    
    private boolean isHublot(FeedItem feedItem) {
        if (StringUtils.containsIgnoreCase(feedItem.getWebCategory(), "watches" ) && 
                feedItem.getWebDesigner().equals("Hublot"))
            return true;
        return false;
    }
    
    private boolean isPanerai(FeedItem feedItem) {
        if (StringUtils.containsIgnoreCase(feedItem.getWebCategory(), "watches" ) && 
                feedItem.getWebDesigner().equals("Panerai"))
            return true;
        return false;
    }
    
    private boolean isFPJourne(FeedItem feedItem) {
        if (StringUtils.containsIgnoreCase(feedItem.getWebCategory(), "watches" ) && 
                feedItem.getWebDesigner().equals("FP Journe"))
            return true;
        return false;
    }

    private boolean isOtherBrand(FeedItem feedItem) {
        for (KeyStoneCollections collectEnum : KeyStoneCollections.values()) {
            if (collectEnum.isBrand() && collectEnum.accepts(feedItem))
                return false;
        }
        return true;
    }
    
    private boolean isUnder5000(FeedItem feedItem) {
        if (StringUtils.containsIgnoreCase(feedItem.getWebCategory(), "watches" ) && 
                feedItem.getWebPriceRetail() != null) {
            double priceSale = Double.parseDouble(feedItem.getWebPriceEbay());
            if (priceSale < 5000) {
                return true;
            }
        }
        return false;
    }
    
    private boolean isVintageJewelry(FeedItem feedItem) {
        if (!StringUtils.containsIgnoreCase(feedItem.getWebCategory(), "watches" )) {
                return true;
        }
        return false;
    }
}