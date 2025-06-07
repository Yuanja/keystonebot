package com.gw.services.product;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.gw.domain.FeedItem;
import com.gw.services.FreeMakerService;
import com.gw.services.constants.ShopifyConstants;
import com.gw.services.shopifyapi.objects.Product;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple Product Creation Service
 * 
 * Two clear responsibilities:
 * 1. createProductTemplate() - Build product template with all metadata and variants
 * 2. createProductOnShopify() - Create product on Shopify with options and variants
 * 
 * Benefits:
 * - Simple, focused methods
 * - Easy to understand and use
 * - No complex pipelines or multiple creation paths
 * - Direct usage by publish/update pipelines
 */
@Component
public abstract class ProductCreationService {
    
    private static final Logger logger = LoggerFactory.getLogger(ProductCreationService.class);
    
    @Autowired
    private FreeMakerService freeMakerService;
    
    /**
     * Generate product description using FreeMaker template
     * Used by update pipeline for basic field comparison
     */
    public String generateDescription(FeedItem feedItem) throws Exception {
        return freeMakerService.generateFromTemplate(feedItem);
    }
    
    /**
     * Set basic product information from feed item
     */
    public void setBasicProductInfo(Product product, FeedItem feedItem) {
        try {
            String bodyHtml = freeMakerService.generateFromTemplate(feedItem);
            product.setBodyHtml(bodyHtml);
        } catch (Exception e) {
            logger.warn("⚠️ Failed to generate template for SKU: {} - using empty description", 
                feedItem.getWebTagNumber());
            product.setBodyHtml("");
        }
        
        product.setTitle(feedItem.getWebDescriptionShort());
        product.setVendor(feedItem.getWebDesigner());
        product.setProductType(feedItem.getWebCategory());
        product.setPublishedScope(ShopifyConstants.PUBLISHED_SCOPE_GLOBAL);
        addTags(feedItem, product);
    }

    public abstract void addTags(FeedItem feedItem, Product product);
}
