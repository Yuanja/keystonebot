package com.gw.services;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.gw.domain.FeedItem;
import com.gw.services.constants.ShopifyConstants;
import com.gw.services.inventory.InventoryLevelService;
import com.gw.services.product.MetadataService;
import com.gw.services.product.ProductImageService;
import com.gw.services.product.VariantService;
import com.gw.services.shopifyapi.ShopifyGraphQLService;
import com.gw.services.shopifyapi.objects.InventoryLevels;
import com.gw.services.shopifyapi.objects.Location;
import com.gw.services.shopifyapi.objects.Option;
import com.gw.services.shopifyapi.objects.Product;
import com.gw.services.shopifyapi.objects.Variant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Refactored Product Factory following SOLID principles
 * 
 * This class now delegates to specialized services:
 * - VariantService: Handles variant creation and options
 * - InventoryLevelService: Manages inventory levels
 * - MetadataService: Handles SEO and Google Merchant metadata
 * - ProductImageService: Processes and manages images
 * 
 * Benefits of refactoring:
 * - Single Responsibility: Each service has one clear purpose
 * - Easier testing: Smaller, focused classes
 * - Better maintainability: Changes are isolated to specific concerns
 * - Improved readability: Less complex, self-documenting code
 */
public class BaseShopifyProductFactory implements IShopifyProductFactory {
    
    private static final Logger logger = LoggerFactory.getLogger(BaseShopifyProductFactory.class);
    
    @Autowired
    private FreeMakerService freeMakerService;
    
    @Autowired 
    private ShopifyGraphQLService shopifyApiService;
    
    @Autowired
    private VariantService variantService;
    
    @Autowired
    private InventoryLevelService inventoryLevelService;
    
    @Autowired
    private MetadataService metadataService;
    
    @Autowired
    private ProductImageService productImageService;
    
    private List<Location> locations;
    
    @Override
    public List<Location> getLocations() {
        if (locations == null) {
            locations = shopifyApiService.getAllLocations();
            logger.debug("Loaded {} locations from Shopify", locations.size());
        }
        return locations;
    }

    @Override
    public Product createProduct(FeedItem feedItem) throws Exception {
        logger.debug("Creating product for SKU: {}", feedItem.getWebTagNumber());
        
        Product product = new Product();
        
        // Set basic product information
        setBasicProductInfo(product, feedItem);
        
        // Use specialized services for complex operations
        productImageService.setProductImages(product, feedItem);
        variantService.createDefaultVariant(product, feedItem, getLocations());
        metadataService.setProductMetadata(product, feedItem);
        
        logger.debug("Created product for SKU: {} with {} variants and {} images", 
            feedItem.getWebTagNumber(), 
            product.getVariants() != null ? product.getVariants().size() : 0,
            product.getImages() != null ? product.getImages().size() : 0);
        
        return product;
    }
    
    /**
     * Sets basic product information from feed item
     * 
     * @param product The product to configure
     * @param feedItem The feed item with source data
     */
    private void setBasicProductInfo(Product product, FeedItem feedItem) {
        try {
            product.setBodyHtml(freeMakerService.generateFromTemplate(feedItem));
        } catch (Exception e) {
            logger.error("Failed to generate template for SKU: {} - {}", 
                feedItem.getWebTagNumber(), e.getMessage());
            product.setBodyHtml(""); // Set empty body as fallback
        }
        
        product.setTitle(feedItem.getWebDescriptionShort());
        product.setVendor(feedItem.getWebDesigner());
        product.setProductType(feedItem.getWebCategory());
        product.setPublishedScope(ShopifyConstants.PUBLISHED_SCOPE_GLOBAL);
        
        logger.debug("Set basic info for SKU: {} - Title: {}, Vendor: {}", 
            feedItem.getWebTagNumber(), feedItem.getWebDescriptionShort(), feedItem.getWebDesigner());
    }
    
    @Override
    public void mergeProduct(Product existing, Product toBeUpdatedProduct) {
        logger.debug("Merging product with ID: {}", existing.getId());
        
        toBeUpdatedProduct.setId(existing.getId());
        
        // Use specialized services for merging
        variantService.mergeVariants(existing.getVariants(), toBeUpdatedProduct.getVariants());
        mergeOptions(existing.getOptions(), toBeUpdatedProduct.getOptions());
        productImageService.mergeImages(existing.getId(), existing.getImages(), toBeUpdatedProduct.getImages());
        
        // Can't update metafields for the time being
        toBeUpdatedProduct.setMetafields(null);
        
        logger.debug("Completed product merge for ID: {}", existing.getId());
    }
    
    @Override
    public void mergeExistingDescription(String existingDescriptionHtml, String toBeUpdatedDescriptionHtml) {
        // Do blanket override with new description
        logger.debug("Merging descriptions - using new description");
    }
    
    @Override
    public void mergeInventoryLevels(InventoryLevels existingInventoryLevels, InventoryLevels newInventoryLevels) {
        // Delegate to specialized service
        inventoryLevelService.mergeInventoryLevels(existingInventoryLevels, newInventoryLevels);
    }
    
    /**
     * Merges existing options with new options, preserving option IDs
     * 
     * @param existingOptions Options from the existing product
     * @param newOptions Options from the new product data
     */
    private void mergeOptions(List<Option> existingOptions, List<Option> newOptions) {
        if (existingOptions == null || newOptions == null) {
            logger.debug("Skipping option merge - null options list");
            return;
        }
        
        logger.debug("Merging {} existing options with {} new options", 
            existingOptions.size(), newOptions.size());
        
        Map<String, Option> existingOptionsByName = existingOptions.stream()
            .collect(Collectors.toMap(Option::getName, option -> option));
        
        for (Option newOption : newOptions) {
            Option existingOption = existingOptionsByName.get(newOption.getName());
            if (existingOption != null) {
                logger.debug("Preserving option ID {} for option: {}", 
                    existingOption.getId(), newOption.getName());
                newOption.setId(existingOption.getId());
            } else {
                logger.debug("New option: {} - no existing option to merge", newOption.getName());
            }
        }
    }
}
