package com.gw.services.product;

import com.gw.domain.FeedItem;
import com.gw.services.constants.ShopifyConstants;
import com.gw.services.inventory.InventoryLevelService;
import com.gw.services.pricing.PricingStrategy;
import com.gw.services.shopifyapi.objects.InventoryLevels;
import com.gw.services.shopifyapi.objects.Location;
import com.gw.services.shopifyapi.objects.Option;
import com.gw.services.shopifyapi.objects.Product;
import com.gw.services.shopifyapi.objects.Variant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service responsible for creating and managing product variants
 * Handles variant creation, options, and merging logic
 */
@Service
public class VariantService {
    
    private static final Logger logger = LoggerFactory.getLogger(VariantService.class);
    
    @Autowired
    private InventoryLevelService inventoryLevelService;
    
    @Autowired
    private PricingStrategy pricingStrategy;
    
    /**
     * Creates a default variant for a product based on feed item data
     * 
     * @param product The product to add the variant to
     * @param feedItem The feed item data
     * @param locations Available locations for inventory
     */
    public void createDefaultVariant(Product product, FeedItem feedItem, List<Location> locations) {
        logger.debug("Creating default variant for SKU: {}", feedItem.getWebTagNumber());
        
        Variant variant = new Variant();
        variant.setTitle(ShopifyConstants.DEFAULT_VARIANT_TITLE);
        variant.setSku(feedItem.getWebTagNumber());
        variant.setPrice(pricingStrategy.getPrice(feedItem));
        variant.setTaxable(ShopifyConstants.TAXABLE_TRUE);
        variant.setInventoryManagement(ShopifyConstants.INVENTORY_MANAGEMENT_SHOPIFY);
        variant.setInventoryPolicy(ShopifyConstants.INVENTORY_POLICY_DENY);
        
        // Create inventory levels for all locations
        InventoryLevels inventoryLevels = inventoryLevelService.createInventoryLevels(feedItem, locations);
        variant.setInventoryLevels(inventoryLevels);
        
        // Set variant options based on feed item data
        setVariantOptions(product, variant, feedItem);
        
        product.addVariant(variant);
        
        logger.debug("Created default variant for SKU: {} with {} options", 
            feedItem.getWebTagNumber(), product.getOptions() != null ? product.getOptions().size() : 0);
    }
    
    /**
     * Sets variant options based on feed item properties
     * Creates up to 3 options: Color, Size, Material
     * 
     * @param product The product to add options to
     * @param variant The variant to set option values on
     * @param feedItem The feed item with option data
     */
    private void setVariantOptions(Product product, Variant variant, FeedItem feedItem) {
        int optionIndex = ShopifyConstants.OPTION_1_INDEX;
        
        // Option 1: Color (from dial color)
        if (feedItem.getWebWatchDial() != null) {
            setOptionValue(product, variant, ShopifyConstants.OPTION_COLOR, 
                feedItem.getWebWatchDial(), optionIndex++);
        }
        
        // Option 2: Size (from diameter)
        if (feedItem.getWebWatchDiameter() != null) {
            setOptionValue(product, variant, ShopifyConstants.OPTION_SIZE, 
                feedItem.getWebWatchDiameter(), optionIndex++);
        }
        
        // Option 3: Material (from metal type)
        if (feedItem.getWebMetalType() != null && optionIndex <= ShopifyConstants.MAX_SHOPIFY_OPTIONS) {
            setOptionValue(product, variant, ShopifyConstants.OPTION_MATERIAL, 
                feedItem.getWebMetalType(), optionIndex);
        }
    }
    
    /**
     * Sets a specific option value for a variant and adds the option to the product
     * 
     * @param product The product to add the option to
     * @param variant The variant to set the option value on
     * @param optionName The name of the option (Color, Size, Material)
     * @param optionValue The value of the option
     * @param optionIndex The index of the option (1, 2, or 3)
     */
    private void setOptionValue(Product product, Variant variant, String optionName, 
                               String optionValue, int optionIndex) {
        if (optionValue == null || optionValue.trim().isEmpty()) {
            logger.warn("Skipping null/empty option value for {}", optionName);
            return;
        }
        
        // Create and add option to product
        Option option = new Option();
        option.setName(optionName);
        option.setPosition(String.valueOf(optionIndex));
        option.setValues(Arrays.asList(optionValue));
        product.addOption(option);
        
        // Set option value on variant
        switch (optionIndex) {
            case ShopifyConstants.OPTION_1_INDEX:
                variant.setOption1(optionValue);
                break;
            case ShopifyConstants.OPTION_2_INDEX:
                variant.setOption2(optionValue);
                break;
            case ShopifyConstants.OPTION_3_INDEX:
                variant.setOption3(optionValue);
                break;
            default:
                logger.warn("Option index {} exceeds maximum allowed options ({})", 
                    optionIndex, ShopifyConstants.MAX_SHOPIFY_OPTIONS);
        }
        
        logger.debug("Set option {} (index {}) = {} for SKU: {}", 
            optionName, optionIndex, optionValue, variant.getSku());
    }
    
    /**
     * Merges existing variants with new variants, preserving IDs and inventory item IDs
     * 
     * @param existingVariants Variants from the existing product
     * @param newVariants Variants from the new product data
     */
    public void mergeVariants(List<Variant> existingVariants, List<Variant> newVariants) {
        logger.debug("Merging {} existing variants with {} new variants", 
            existingVariants.size(), newVariants.size());
        
        Map<String, Variant> existingVariantsBySku = existingVariants.stream()
            .collect(Collectors.toMap(Variant::getSku, variant -> variant));
        
        for (Variant newVariant : newVariants) {
            Variant existingVariant = existingVariantsBySku.get(newVariant.getSku());
            if (existingVariant != null) {
                logger.debug("Merging variant with SKU: {}", newVariant.getSku());
                
                // Preserve important IDs from existing variant
                newVariant.setId(existingVariant.getId());
                newVariant.setInventoryItemId(existingVariant.getInventoryItemId());
                
                // Merge inventory levels
                if (existingVariant.getInventoryLevels() != null && newVariant.getInventoryLevels() != null) {
                    inventoryLevelService.mergeInventoryLevels(
                        existingVariant.getInventoryLevels(), 
                        newVariant.getInventoryLevels()
                    );
                }
            } else {
                logger.debug("New variant with SKU: {} - no existing variant to merge", newVariant.getSku());
            }
        }
    }
} 