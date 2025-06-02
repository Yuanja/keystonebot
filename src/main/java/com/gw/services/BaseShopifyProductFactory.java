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
import com.gw.services.shopifyapi.objects.Metafield;
import com.gw.services.shopifyapi.objects.Image;

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
    private ShopifyGraphQLService shopifyGraphQLService;
    
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
            locations = shopifyGraphQLService.getAllLocations();
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
        
        logger.debug("Created product object for SKU: {} with {} variants and {} metafields", 
            feedItem.getWebTagNumber(), 
            product.getVariants() != null ? product.getVariants().size() : 0,
            product.getMetafields() != null ? product.getMetafields().size() : 0);
        
        return product;
    }
    
    /**
     * Creates a product using the correct two-step approach for variant options
     * Step 1: Create basic product without options using addProduct
     * Step 2: Add options using productOptionsCreate mutation
     * Step 3: Add images separately using productImageCreate mutations
     * 
     * @param feedItem The feed item with source data
     * @return The created product with both product ID and variant ID
     * @throws Exception if product creation fails
     */
    public Product createProductWithOptions(FeedItem feedItem) throws Exception {
        logger.debug("Creating product with options using three-step approach for SKU: {}", feedItem.getWebTagNumber());
        
        // STEP 1: Create basic product structure (this will call overridden createProduct in child classes)
        // This ensures eBay metafields and other child-specific logic is executed
        Product product = this.createProduct(feedItem);
        logger.info("✅ STEP 1: Created product structure for SKU: {} with {} metafields", 
            feedItem.getWebTagNumber(), 
            product.getMetafields() != null ? product.getMetafields().size() : 0);
        
        // Important: The createProduct call above only builds the Product object
        // but doesn't send it to Shopify yet. We need to modify it for the three-step approach.
        
        // Store images for later addition (Step 3)
        List<Image> originalImages = product.getImages();
        
        // Remove images and options from the product before creation to ensure clean creation
        product.setImages(null);  // Images must be added separately via GraphQL
        product.setOptions(null); // Options must be added separately via GraphQL
        
        // Also ensure variants don't have option values during creation
        if (product.getVariants() != null) {
            for (Variant variant : product.getVariants()) {
                variant.setOption1(null);
                variant.setOption2(null);
                variant.setOption3(null);
            }
        }
        
        // Now send the product to Shopify using the GraphQL service
        Product createdProduct = shopifyGraphQLService.addProduct(product);
        logger.info("✅ STEP 1: Created basic product for SKU: {} - Product ID: {}", 
            feedItem.getWebTagNumber(), createdProduct.getId());
        
        // STEP 2: Add options using productOptionsCreate mutation
        boolean optionsCreated = shopifyGraphQLService.createProductOptions(createdProduct.getId(), feedItem);
        if (optionsCreated) {
            logger.info("✅ STEP 2: Successfully added variant options to product ID: {}", createdProduct.getId());
        } else {
            logger.warn("⚠️ STEP 2: Failed to add variant options to product ID: {} (product created but no options)", 
                createdProduct.getId());
        }
        
        // STEP 3: Add images using productImageCreate mutations
        if (originalImages != null && !originalImages.isEmpty()) {
            try {
                logger.info("🖼️ STEP 3: Adding {} images to product ID: {}", originalImages.size(), createdProduct.getId());
                shopifyGraphQLService.addImagesToProduct(createdProduct.getId(), originalImages);
                logger.info("✅ STEP 3: Successfully added {} images to product ID: {}", originalImages.size(), createdProduct.getId());
            } catch (Exception e) {
                logger.error("❌ STEP 3: Failed to add images to product ID: {} - {}", createdProduct.getId(), e.getMessage());
                logger.warn("⚠️ Product created successfully but without images");
            }
        } else {
            logger.debug("ℹ️ STEP 3: No images to add for product ID: {}", createdProduct.getId());
        }
        
        // Re-fetch the product to get the complete structure with options and images
        Product finalProduct = shopifyGraphQLService.getProductByProductId(createdProduct.getId());
        if (finalProduct != null) {
            logger.info("✅ Final product verification - ID: {}, Options: {}, Variants: {}, Metafields: {}, Images: {}", 
                finalProduct.getId(),
                finalProduct.getOptions() != null ? finalProduct.getOptions().size() : 0,
                finalProduct.getVariants() != null ? finalProduct.getVariants().size() : 0,
                finalProduct.getMetafields() != null ? finalProduct.getMetafields().size() : 0,
                finalProduct.getImages() != null ? finalProduct.getImages().size() : 0);
            return finalProduct;
        } else {
            logger.warn("Failed to re-fetch created product, returning original created product");
            return createdProduct;
        }
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
        
        // Preserve existing metafields if the new product doesn't have any
        // This allows eBay metafields and other metafields to persist during updates
        if (toBeUpdatedProduct.getMetafields() == null || toBeUpdatedProduct.getMetafields().isEmpty()) {
            toBeUpdatedProduct.setMetafields(existing.getMetafields());
            logger.debug("Preserved {} existing metafields during merge", 
                existing.getMetafields() != null ? existing.getMetafields().size() : 0);
        } else {
            logger.debug("Using {} new metafields from updated product", 
                toBeUpdatedProduct.getMetafields().size());
        }
        
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
     * Merges existing options with new options, preserving option IDs and detecting changes
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
                // Compare option values for changes
                boolean optionChanged = compareOptionValues(existingOption, newOption);
                
                if (optionChanged) {
                    logger.info("Option '{}' values changed | Old: {} -> New: {}", 
                        newOption.getName(), 
                        existingOption.getValues(), 
                        newOption.getValues());
                } else {
                    logger.debug("Option '{}' values unchanged: {}", 
                        newOption.getName(), newOption.getValues());
                }
                
                logger.debug("Preserving option ID {} for option: {}", 
                    existingOption.getId(), newOption.getName());
                newOption.setId(existingOption.getId());
            } else {
                logger.debug("New option: {} with values: {} - no existing option to merge", 
                    newOption.getName(), newOption.getValues());
            }
        }
        
        // Log summary of option changes
        logOptionMergeSummary(existingOptions, newOptions);
    }
    
    /**
     * Compares option values between existing and new options
     * 
     * @param existingOption The existing option
     * @param newOption The new option
     * @return true if option values have changed
     */
    private boolean compareOptionValues(Option existingOption, Option newOption) {
        List<String> existingValues = existingOption.getValues();
        List<String> newValues = newOption.getValues();
        
        // Handle null lists
        if (existingValues == null && newValues == null) {
            return false;
        }
        if (existingValues == null || newValues == null) {
            return true;
        }
        
        // Compare sizes first
        if (existingValues.size() != newValues.size()) {
            return true;
        }
        
        // Compare individual values
        for (int i = 0; i < existingValues.size(); i++) {
            String existingValue = existingValues.get(i);
            String newValue = newValues.get(i);
            
            if (!optionValueEquals(existingValue, newValue)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Safely compares two option values, handling nulls appropriately
     * 
     * @param existing The existing option value
     * @param updated The new option value
     * @return true if values are equal (both null or same string value)
     */
    private boolean optionValueEquals(String existing, String updated) {
        if (existing == null && updated == null) {
            return true;
        }
        if (existing == null || updated == null) {
            return false;
        }
        return existing.trim().equals(updated.trim());
    }
    
    /**
     * Logs a summary of option merge operations
     * 
     * @param existingOptions The existing options
     * @param newOptions The new options
     */
    private void logOptionMergeSummary(List<Option> existingOptions, List<Option> newOptions) {
        Map<String, Option> existingByName = existingOptions.stream()
            .collect(Collectors.toMap(Option::getName, option -> option));
        
        int preservedCount = 0;
        int newCount = 0;
        int changedCount = 0;
        
        for (Option newOption : newOptions) {
            Option existing = existingByName.get(newOption.getName());
            if (existing != null) {
                preservedCount++;
                if (compareOptionValues(existing, newOption)) {
                    changedCount++;
                }
            } else {
                newCount++;
            }
        }
        
        logger.info("Option merge summary: {} preserved ({} changed), {} new options | Product options: Color (webWatchDial), Size (webWatchDiameter), Material (webMetalType)", 
            preservedCount, changedCount, newCount);
    }
}
