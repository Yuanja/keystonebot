package com.gw.services.product;

import com.gw.domain.FeedItem;
import com.gw.domain.EbayMetafieldDefinition;
import com.gw.services.constants.ShopifyConstants;
import com.gw.services.shopifyapi.objects.GoogleMetafield;
import com.gw.services.shopifyapi.objects.Metafield;
import com.gw.services.shopifyapi.objects.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Service responsible for creating and managing product metadata
 * Handles Google Merchant fields, SEO metadata, and meta descriptions
 */
@Service
public class MetadataService {
    
    private static final Logger logger = LoggerFactory.getLogger(MetadataService.class);
    
    /**
     * Sets all metadata fields for a product based on feed item data
     * 
     * @param product The product to set metadata on
     * @param feedItem The feed item with metadata source data
     */
    public void setProductMetadata(Product product, FeedItem feedItem) {
        logger.debug("Setting metadata for product: {}", feedItem.getWebTagNumber());
        
        setGoogleMerchantMetafields(product, feedItem);
        setEbayMetafields(product, feedItem);
        setSeoMetadata(product, feedItem);
        
        logger.debug("Completed metadata setup for SKU: {}", feedItem.getWebTagNumber());
    }
    
    /**
     * Sets Google Merchant Center metafields required for Google Shopping
     * 
     * @param product The product to add metafields to
     * @param feedItem The feed item with source data
     */
    private void setGoogleMerchantMetafields(Product product, FeedItem feedItem) {
        logger.debug("Setting Google Merchant metafields for SKU: {}", feedItem.getWebTagNumber());
        
        // Required Google Merchant fields
        product.addMetafield(new GoogleMetafield("custom_product", ShopifyConstants.GOOGLE_CUSTOM_PRODUCT));
        product.addMetafield(new GoogleMetafield("age_group", ShopifyConstants.GOOGLE_AGE_GROUP_ADULT));
        product.addMetafield(new GoogleMetafield("google_product_type", ShopifyConstants.GOOGLE_PRODUCT_TYPE_WATCHES));
        
        // Gender mapping
        setGenderMetafield(product, feedItem);
        
        // Condition mapping
        setConditionMetafield(product, feedItem);
        
        // Brand-specific fields
        setBrandMetafields(product, feedItem);
    }
    
    /**
     * Maps feed item gender to Google Merchant gender values
     * 
     * @param product The product to add gender metafield to
     * @param feedItem The feed item with gender data
     */
    private void setGenderMetafield(Product product, FeedItem feedItem) {
        if (feedItem.getWebStyle() == null) {
            logger.debug("No gender/style specified for SKU: {}", feedItem.getWebTagNumber());
            return;
        }
        
        String webStyle = feedItem.getWebStyle().toLowerCase();
        String googleGender;
        
        if (ShopifyConstants.FEED_GENDER_UNISEX.equalsIgnoreCase(feedItem.getWebStyle())) {
            googleGender = ShopifyConstants.GOOGLE_GENDER_UNISEX;
        } else if (webStyle.contains("men") || webStyle.contains("male") || webStyle.contains("gent")) {
            // Handle variations: "Men's", "Men", "Male", "Gents", "Gent", etc.
            googleGender = ShopifyConstants.GOOGLE_GENDER_MALE;
        } else if (webStyle.contains("women") || webStyle.contains("female") || webStyle.contains("ladies")) {
            // Handle variations: "Women's", "Women", "Female", "Ladies", etc.
            googleGender = ShopifyConstants.GOOGLE_GENDER_FEMALE;
        } else {
            // Default fallback for unknown gender styles
            googleGender = ShopifyConstants.GOOGLE_GENDER_UNISEX;
        }
        
        product.addMetafield(new GoogleMetafield("gender", googleGender));
        logger.debug("Set gender: {} -> {} for SKU: {}", 
            feedItem.getWebStyle(), googleGender, feedItem.getWebTagNumber());
    }
    
    /**
     * Maps feed item condition to Google Merchant condition values
     * 
     * @param product The product to add condition metafield to
     * @param feedItem The feed item with condition data
     */
    private void setConditionMetafield(Product product, FeedItem feedItem) {
        String googleCondition = ShopifyConstants.FEED_CONDITION_NEW.equalsIgnoreCase(feedItem.getWebWatchCondition()) 
            ? ShopifyConstants.GOOGLE_CONDITION_NEW 
            : ShopifyConstants.GOOGLE_CONDITION_USED;
        
        product.addMetafield(new GoogleMetafield("condition", googleCondition));
        logger.debug("Set condition: {} -> {} for SKU: {}", 
            feedItem.getWebWatchCondition(), googleCondition, feedItem.getWebTagNumber());
    }
    
    /**
     * Sets brand-specific metafields for advertising
     * 
     * @param product The product to add brand metafields to
     * @param feedItem The feed item with brand data
     */
    private void setBrandMetafields(Product product, FeedItem feedItem) {
        if (feedItem.getWebDesigner() != null) {
            product.addMetafield(new GoogleMetafield("adwords_grouping", feedItem.getWebDesigner()));
            logger.debug("Set adwords_grouping: {} for SKU: {}", 
                feedItem.getWebDesigner(), feedItem.getWebTagNumber());
        }
        
        if (feedItem.getWebWatchModel() != null) {
            product.addMetafield(new GoogleMetafield("adwords_labels", feedItem.getWebWatchModel()));
            logger.debug("Set adwords_labels: {} for SKU: {}", 
                feedItem.getWebWatchModel(), feedItem.getWebTagNumber());
        }
    }
    
    /**
     * Sets SEO metadata including meta title and description
     * 
     * @param product The product to set SEO metadata on
     * @param feedItem The feed item with SEO source data
     */
    private void setSeoMetadata(Product product, FeedItem feedItem) {
        String metaTitle = buildMetaTitle(feedItem);
        String metaDescription = buildMetaDescription(feedItem);
        
        product.setMetafieldsGlobalTitleTag(metaTitle);
        product.setMetafieldsGlobalDescriptionTag(metaDescription);
        
        logger.debug("Set SEO metadata for SKU: {} - Title length: {}, Description length: {}", 
            feedItem.getWebTagNumber(), metaTitle.length(), metaDescription.length());
    }
    
    /**
     * Builds SEO meta title from feed item data
     * Format: Brand Model Reference Material
     * 
     * @param feedItem The feed item with title data
     * @return Formatted meta title
     */
    private String buildMetaTitle(FeedItem feedItem) {
        StringBuilder titleBuilder = new StringBuilder();
        
        appendIfNotNull(feedItem.getWebDesigner(), titleBuilder);
        appendIfNotNull(feedItem.getWebWatchModel(), titleBuilder);
        appendIfNotNull(feedItem.getWebWatchManufacturerReferenceNumber(), titleBuilder);
        appendIfNotNull(feedItem.getWebMetalType(), titleBuilder);
        
        String title = titleBuilder.toString().trim();
        logger.debug("Built meta title: {} for SKU: {}", title, feedItem.getWebTagNumber());
        
        return title;
    }
    
    /**
     * Builds comprehensive meta description from feed item data
     * Includes all relevant product details for SEO
     * 
     * @param feedItem The feed item with description data
     * @return Formatted meta description
     */
    public String buildMetaDescription(FeedItem feedItem) {
        StringBuilder descriptionBuilder = new StringBuilder();
        
        // Start with the meta title
        descriptionBuilder.append(buildMetaTitle(feedItem)).append(" ");
        
        // Add detailed specifications
        appendIfNotNull(feedItem.getWebWatchCondition(), descriptionBuilder);
        appendIfNotNull(feedItem.getWebStyle(), descriptionBuilder);
        appendIfNotNull(feedItem.getWebMetalType(), descriptionBuilder);
        appendIfNotNull(feedItem.getWebWatchDial(), descriptionBuilder);
        appendIfNotNull(feedItem.getWebWatchDiameter(), descriptionBuilder);
        appendIfNotNull(feedItem.getWebWatchMovement(), descriptionBuilder);
        appendIfNotNull(feedItem.getWebWatchYear(), descriptionBuilder);
        appendIfNotNull(feedItem.getWebWatchStrap(), descriptionBuilder);
        appendIfNotNull(feedItem.getWebWatchBoxPapers(), descriptionBuilder);
        
        // Clean up whitespace and line breaks
        String description = descriptionBuilder.toString()
            .replaceAll("[\\t\\n\\r]+", " ")
            .trim();
        
        logger.debug("Built meta description (length: {}) for SKU: {}", 
            description.length(), feedItem.getWebTagNumber());
        
        return description;
    }
    
    /**
     * Sets eBay metafields with namespace "ebay" for all relevant watch fields
     * These metafields are used for eBay listing integration
     * 
     * @param product The product to add eBay metafields to
     * @param feedItem The feed item with eBay metadata source data
     */
    private void setEbayMetafields(Product product, FeedItem feedItem) {
        logger.debug("Setting eBay metafields for SKU: {}", feedItem.getWebTagNumber());
        
        List<Metafield> ebayMetafields = new ArrayList<>();
        
        // Watch Brand/Manufacturer
        if (feedItem.getWebDesigner() != null) {
            ebayMetafields.add(createEbayMetafield(EbayMetafieldDefinition.BRAND, feedItem.getWebDesigner()));
        }
        
        // Watch Model
        if (feedItem.getWebWatchModel() != null) {
            ebayMetafields.add(createEbayMetafield(EbayMetafieldDefinition.MODEL, feedItem.getWebWatchModel()));
        }
        
        // Reference Number
        if (feedItem.getWebWatchManufacturerReferenceNumber() != null) {
            ebayMetafields.add(createEbayMetafield(EbayMetafieldDefinition.REFERENCE_NUMBER, feedItem.getWebWatchManufacturerReferenceNumber()));
        }
        
        // Year of Manufacture
        if (feedItem.getWebWatchYear() != null) {
            ebayMetafields.add(createEbayMetafield(EbayMetafieldDefinition.YEAR, feedItem.getWebWatchYear()));
        }
        
        // Case Material
        if (feedItem.getWebMetalType() != null) {
            ebayMetafields.add(createEbayMetafield(EbayMetafieldDefinition.CASE_MATERIAL, feedItem.getWebMetalType()));
        }
        
        // Movement Type
        if (feedItem.getWebWatchMovement() != null) {
            ebayMetafields.add(createEbayMetafield(EbayMetafieldDefinition.MOVEMENT, feedItem.getWebWatchMovement()));
        }
        
        // Dial Information
        if (feedItem.getWebWatchDial() != null) {
            ebayMetafields.add(createEbayMetafield(EbayMetafieldDefinition.DIAL, feedItem.getWebWatchDial()));
        }
        
        // Strap/Bracelet Information
        if (feedItem.getWebWatchStrap() != null) {
            ebayMetafields.add(createEbayMetafield(EbayMetafieldDefinition.STRAP, feedItem.getWebWatchStrap()));
        }
        
        // Condition
        if (feedItem.getWebWatchCondition() != null) {
            ebayMetafields.add(createEbayMetafield(EbayMetafieldDefinition.CONDITION, feedItem.getWebWatchCondition()));
        }
        
        // Case Diameter
        if (feedItem.getWebWatchDiameter() != null) {
            ebayMetafields.add(createEbayMetafield(EbayMetafieldDefinition.DIAMETER, feedItem.getWebWatchDiameter()));
        }
        
        // Box and Papers
        if (feedItem.getWebWatchBoxPapers() != null) {
            ebayMetafields.add(createEbayMetafield(EbayMetafieldDefinition.BOX_PAPERS, feedItem.getWebWatchBoxPapers()));
        }
        
        // Category
        if (feedItem.getWebCategory() != null) {
            ebayMetafields.add(createEbayMetafield(EbayMetafieldDefinition.CATEGORY, feedItem.getWebCategory()));
        }
        
        // Style
        if (feedItem.getWebStyle() != null) {
            ebayMetafields.add(createEbayMetafield(EbayMetafieldDefinition.STYLE, feedItem.getWebStyle()));
        }
        
        // Add eBay metafields to product
        if (!ebayMetafields.isEmpty()) {
            if (product.getMetafields() == null) {
                product.setMetafields(new ArrayList<>());
            }
            product.getMetafields().addAll(ebayMetafields);
            
            logger.debug("Added {} eBay metafields for SKU: {}", 
                ebayMetafields.size(), feedItem.getWebTagNumber());
        }
    }
    
    /**
     * Create an eBay metafield using the enum definition for consistent types
     * 
     * @param definition The eBay metafield definition enum
     * @param value The metafield value
     * @return Configured eBay metafield
     */
    private Metafield createEbayMetafield(EbayMetafieldDefinition definition, String value) {
        Metafield metafield = new Metafield();
        metafield.setNamespace("ebay");
        metafield.setKey(definition.getKey());
        metafield.setValue(value);
        metafield.setType(definition.getType());
        metafield.setDescription(definition.getDescription());
        return metafield;
    }

    /**
     * Helper method to append non-null strings to a StringBuilder
     * 
     * @param value The string to append if not null
     * @param builder The StringBuilder to append to
     */
    private void appendIfNotNull(String value, StringBuilder builder) {
        if (value != null && !value.trim().isEmpty()) {
            builder.append(value).append(" ");
        }
    }
} 