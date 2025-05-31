package com.gw.services.constants;

/**
 * Central location for all Shopify-related constants
 * Eliminates magic strings and numbers throughout the codebase
 */
public final class ShopifyConstants {
    
    // Product Constants
    public static final String DEFAULT_VARIANT_TITLE = "Default Title";
    public static final String PUBLISHED_SCOPE_GLOBAL = "global";
    public static final String INVENTORY_MANAGEMENT_SHOPIFY = "shopify";
    public static final String INVENTORY_POLICY_DENY = "deny";
    public static final String TAXABLE_TRUE = "true";
    
    // Variant Option Limits
    public static final int MAX_SHOPIFY_OPTIONS = 3;
    public static final int OPTION_1_INDEX = 1;
    public static final int OPTION_2_INDEX = 2;
    public static final int OPTION_3_INDEX = 3;
    
    // Google Merchant Constants
    public static final String GOOGLE_AGE_GROUP_ADULT = "Adult";
    public static final String GOOGLE_PRODUCT_TYPE_WATCHES = "apparel & accessories > jewelry > watches";
    public static final String GOOGLE_CUSTOM_PRODUCT = "true";
    public static final String GOOGLE_CONDITION_NEW = "New";
    public static final String GOOGLE_CONDITION_USED = "Used";
    public static final String GOOGLE_GENDER_MALE = "Male";
    public static final String GOOGLE_GENDER_FEMALE = "Female";
    public static final String GOOGLE_GENDER_UNISEX = "Unisex";
    
    // Feed Item Status Values
    public static final String FEED_STATUS_SOLD = "SOLD";
    public static final String FEED_STATUS_AVAILABLE = "Available";
    
    // Feed Item Gender Values
    public static final String FEED_GENDER_UNISEX = "Unisex";
    public static final String FEED_GENDER_GENTS = "Gents";
    
    // Feed Item Condition Values
    public static final String FEED_CONDITION_NEW = "New";
    
    // Inventory Values
    public static final String INVENTORY_SOLD = "0";
    public static final String INVENTORY_AVAILABLE = "1";
    
    // Option Names
    public static final String OPTION_COLOR = "Color";
    public static final String OPTION_SIZE = "Size";
    public static final String OPTION_MATERIAL = "Material";
    
    // Private constructor to prevent instantiation
    private ShopifyConstants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
} 