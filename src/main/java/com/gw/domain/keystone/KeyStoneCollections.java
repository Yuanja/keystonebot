package com.gw.domain.keystone;

import org.apache.commons.lang3.StringUtils;

import com.gw.domain.FeedItem;
import com.gw.domain.PredefinedCollection;

public enum KeyStoneCollections implements PredefinedCollection {
    
    UNDER_5000("Watches Under $5,000", false),
    ROLEX("Rolex", true),
    PATEK_PHILIPPE("Patek Philippe", true),
    Audemars_Piguet("Audemars Piguet", true),
    Vacheron_Constantin("Vacheron Constantin", true),
    HEUER("Heuer", true),
    OMEGA("Omega", true),
    OtherBrand("Other Brand", false),
    ROLEX_SPORT_WATCHES("Rolex Sport Watches", false),    
    SPORT_WATCHES("Sport Watches", false),
    VINTAGE_WATCHES("Vintage Watches", false),
    MODERN_WATCHES("Modern Watches", false),
    CHRONOGRAPHS("Chronograph", false),
    MENS("Men's", false),
    WOMENS("Women's", false),
    JEWELRY("Jewelry", false)
    ;

    private final String title;
    private final boolean isBrand;
    
    KeyStoneCollections(String title, boolean isBrand){
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
            case UNDER_5000:
                return isUnder5000(feedItem);
            case VINTAGE_WATCHES:
                return isVintage(feedItem);
            case PATEK_PHILIPPE:
                return isPatek(feedItem);
            case ROLEX:
                return isRolex(feedItem);
            case Audemars_Piguet:
                return isAudemarsPiguet(feedItem);
            case OtherBrand:
                return isOtherBrand(feedItem);
            case CHRONOGRAPHS:
                return isChronograph(feedItem);
            case HEUER:
                return isHeuer(feedItem);
            case MODERN_WATCHES:
                return isModernWatch(feedItem);
            case OMEGA:
                return isOmega(feedItem);
            case ROLEX_SPORT_WATCHES:
                return isRolexSportWatch(feedItem);
            case SPORT_WATCHES:
                return isSportWatch(feedItem);
            case Vacheron_Constantin:
                return isVacheronConstantin(feedItem);
            case MENS:
                return isMens(feedItem);
            case WOMENS:
                return isWomens(feedItem);
            case JEWELRY:
                return isJewelry(feedItem);
            default:
                break;
        }
        return false;
    }

    private boolean isJewelry(FeedItem feedItem){
        if (StringUtils.containsIgnoreCase(feedItem.getWebCategory(), "jewelry" )) {
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
    
    private boolean isSportWatch(FeedItem feedItem) {
        if (isRolex(feedItem)) {
            return true;
        }
        
        if (StringUtils.containsIgnoreCase(feedItem.getWebWatchModel(), "Nautilus" )) {
            return true;
        } else if (StringUtils.containsIgnoreCase(feedItem.getWebWatchModel(), "Royal Oak" )) {
            return true;
        } else return StringUtils.containsIgnoreCase(feedItem.getWebWatchModel(), "Speedmaster");
    }
    
    private boolean isRolexSportWatch(FeedItem feedItem) {
        if (isRolex(feedItem)) {
            if (StringUtils.containsIgnoreCase(feedItem.getWebWatchModel(), "Submariner" )) {
                return true;
            } else if (StringUtils.containsIgnoreCase(feedItem.getWebWatchModel(), "Explorer" )) {
                return true;
            } else if (StringUtils.containsIgnoreCase(feedItem.getWebWatchModel(), "GMT" )) {
                return true;
            } else if (StringUtils.containsIgnoreCase(feedItem.getWebWatchModel(), "Daytona" )) {
                return true;
            } else if (StringUtils.containsIgnoreCase(feedItem.getWebWatchModel(), "Milgauss" )) {
                return true;
            } else if (StringUtils.containsIgnoreCase(feedItem.getWebWatchModel(), "Cosmograph" )) {
                return true;
            } else if (StringUtils.containsIgnoreCase(feedItem.getWebWatchModel(), "Sea-Dweller" )) {
                return true;
            }
        } 
        return false;
    }
    
    private boolean isChronograph(FeedItem feedItem) {
        if (StringUtils.containsIgnoreCase(feedItem.getWebDescriptionShort(), "Chronograph" )) {
            return true;
        }
        return false;
    }
    
    private boolean isVintage(FeedItem feedItem) {
        if (StringUtils.containsIgnoreCase(feedItem.getWebDescriptionShort(), "vintage" )) {
            return true;
        } else if (feedItem.getWebWatchYear() != null){
            if (StringUtils.containsIgnoreCase(feedItem.getWebWatchYear(), "Current")) {
                return false;
            } else {
                try {
                    int year = Integer.parseInt(feedItem.getWebWatchYear());
                    if (year < 2000) {
                        return true;
                    }
                } catch(Exception e) {
                }
                return false;
            }
        }
        return false;
    }
    
    private boolean isVacheronConstantin(FeedItem feedItem) {
        if (feedItem.getWebDesigner().equals("Vacheron Constantin"))
            return true;
        return false;
    }
    
    private boolean isHeuer(FeedItem feedItem) {
        if (feedItem.getWebDesigner().equals("Heuer"))
            return true;
        return false;
    }
    
    private boolean isOmega(FeedItem feedItem) {
        if (feedItem.getWebDesigner().equals("Omega"))
            return true;
        return false;
    }
    
    private boolean isModernWatch(FeedItem feedItem) {
        if (feedItem.getWebWatchYear() != null) {
            if (StringUtils.containsIgnoreCase(feedItem.getWebWatchYear(), "Current")) {
                return true;
            } else {
                try {
                    int year = Integer.parseInt(feedItem.getWebWatchYear());
                    if (year >= 2000) {
                        return true;
                    }
                } catch(Exception e) {
                }
                return false;
            }
        }
        return false;
    }
    
    private boolean isPatek(FeedItem feedItem) {
        if (feedItem.getWebDesigner().equals("Patek Philippe"))
            return true;
        return false;
    }
    
    private boolean isRolex(FeedItem feedItem) {
        if (feedItem.getWebDesigner().equals("Rolex"))
            return true;
        return false;
    }
    
    private boolean isAudemarsPiguet(FeedItem feedItem) {
        if (feedItem.getWebDesigner().equals("Audemars Piguet"))
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
        if (feedItem.getWebPriceRetail() != null) {
            double priceSale = Double.parseDouble(feedItem.getWebPriceKeystone());
            if (priceSale < 5000) {
                return true;
            }
        }
        return false;
    }
}