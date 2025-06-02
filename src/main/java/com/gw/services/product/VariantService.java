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
 * Handles variant creation, options, and merging logic with enhanced change detection
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
        
        logger.info("Created default variant for SKU: {} with {} options [Color: {}, Size: {}, Material: {}]", 
            feedItem.getWebTagNumber(), 
            product.getOptions() != null ? product.getOptions().size() : 0,
            feedItem.getWebWatchDial(),
            feedItem.getWebWatchDiameter(),
            feedItem.getWebMetalType());
    }
    
    /**
     * Sets variant options based on feed item properties
     * Creates up to 3 options: Color, Size, Material using specific feedItem attributes
     * 
     * @param product The product to add options to
     * @param variant The variant to set option values on
     * @param feedItem The feed item with option data
     */
    private void setVariantOptions(Product product, Variant variant, FeedItem feedItem) {
        int optionIndex = ShopifyConstants.OPTION_1_INDEX;
        
        // Option 1: Color (from webWatchDial)
        if (feedItem.getWebWatchDial() != null && !feedItem.getWebWatchDial().trim().isEmpty()) {
            setOptionValue(product, variant, ShopifyConstants.OPTION_COLOR, 
                feedItem.getWebWatchDial(), optionIndex++);
            logger.debug("Set Color option from webWatchDial: {}", feedItem.getWebWatchDial());
        }
        
        // Option 2: Size (from webWatchDiameter)
        if (feedItem.getWebWatchDiameter() != null && !feedItem.getWebWatchDiameter().trim().isEmpty()) {
            setOptionValue(product, variant, ShopifyConstants.OPTION_SIZE, 
                feedItem.getWebWatchDiameter(), optionIndex++);
            logger.debug("Set Size option from webWatchDiameter: {}", feedItem.getWebWatchDiameter());
        }
        
        // Option 3: Material (from webMetalType)
        if (feedItem.getWebMetalType() != null && !feedItem.getWebMetalType().trim().isEmpty() 
            && optionIndex <= ShopifyConstants.MAX_SHOPIFY_OPTIONS) {
            setOptionValue(product, variant, ShopifyConstants.OPTION_MATERIAL, 
                feedItem.getWebMetalType(), optionIndex);
            logger.debug("Set Material option from webMetalType: {}", feedItem.getWebMetalType());
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
                
                // Compare and log variant option changes
                logVariantOptionChanges(existingVariant, newVariant);
                
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
    
    /**
     * Compares and logs changes in variant option values between existing and new variants
     * 
     * @param existingVariant The existing variant from Shopify
     * @param newVariant The new variant from feed item data
     */
    private void logVariantOptionChanges(Variant existingVariant, Variant newVariant) {
        boolean hasChanges = false;
        
        // Compare Option 1 (Color)
        if (!optionValuesEqual(existingVariant.getOption1(), newVariant.getOption1())) {
            logger.info("Color option changed for SKU: {} | '{}' -> '{}'", 
                newVariant.getSku(), existingVariant.getOption1(), newVariant.getOption1());
            hasChanges = true;
        }
        
        // Compare Option 2 (Size)
        if (!optionValuesEqual(existingVariant.getOption2(), newVariant.getOption2())) {
            logger.info("Size option changed for SKU: {} | '{}' -> '{}'", 
                newVariant.getSku(), existingVariant.getOption2(), newVariant.getOption2());
            hasChanges = true;
        }
        
        // Compare Option 3 (Material)
        if (!optionValuesEqual(existingVariant.getOption3(), newVariant.getOption3())) {
            logger.info("Material option changed for SKU: {} | '{}' -> '{}'", 
                newVariant.getSku(), existingVariant.getOption3(), newVariant.getOption3());
            hasChanges = true;
        }
        
        if (!hasChanges) {
            logger.debug("No variant option changes detected for SKU: {}", newVariant.getSku());
        } else {
            logger.info("Variant options updated for SKU: {} [Color: {}, Size: {}, Material: {}]",
                newVariant.getSku(), newVariant.getOption1(), newVariant.getOption2(), newVariant.getOption3());
        }
    }
    
    /**
     * Safely compares two option values, handling nulls appropriately
     * 
     * @param existing The existing option value
     * @param updated The new option value
     * @return true if values are equal (both null or same string value)
     */
    private boolean optionValuesEqual(String existing, String updated) {
        if (existing == null && updated == null) {
            return true;
        }
        if (existing == null || updated == null) {
            return false;
        }
        return existing.equals(updated);
    }
    
    /**
     * Validates that variant options are properly set based on feed item attributes
     * This is useful for testing and debugging variant option setup
     * 
     * @param variant The variant to validate
     * @param feedItem The source feed item
     * @return true if all expected options are properly set
     */
    public boolean validateVariantOptions(Variant variant, FeedItem feedItem) {
        boolean isValid = true;
        
        // Validate Color option (webWatchDial)
        if (feedItem.getWebWatchDial() != null && !feedItem.getWebWatchDial().trim().isEmpty()) {
            if (!feedItem.getWebWatchDial().equals(variant.getOption1())) {
                logger.warn("Color option mismatch for SKU: {} | Expected: '{}', Got: '{}'",
                    variant.getSku(), feedItem.getWebWatchDial(), variant.getOption1());
                isValid = false;
            }
        }
        
        // Validate Size option (webWatchDiameter)
        if (feedItem.getWebWatchDiameter() != null && !feedItem.getWebWatchDiameter().trim().isEmpty()) {
            if (!feedItem.getWebWatchDiameter().equals(variant.getOption2())) {
                logger.warn("Size option mismatch for SKU: {} | Expected: '{}', Got: '{}'",
                    variant.getSku(), feedItem.getWebWatchDiameter(), variant.getOption2());
                isValid = false;
            }
        }
        
        // Validate Material option (webMetalType)
        if (feedItem.getWebMetalType() != null && !feedItem.getWebMetalType().trim().isEmpty()) {
            if (!feedItem.getWebMetalType().equals(variant.getOption3())) {
                logger.warn("Material option mismatch for SKU: {} | Expected: '{}', Got: '{}'",
                    variant.getSku(), feedItem.getWebMetalType(), variant.getOption3());
                isValid = false;
            }
        }
        
        if (isValid) {
            logger.debug("Variant options validation passed for SKU: {}", variant.getSku());
        }
        
        return isValid;
    }
} 