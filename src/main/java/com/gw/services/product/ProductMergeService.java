package com.gw.services.product;

import com.gw.services.shopifyapi.objects.Option;
import com.gw.services.shopifyapi.objects.Product;
import com.gw.services.shopifyapi.objects.Variant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Reusable Product Merge Service
 * 
 * Handles all product merging operations with clean separation of concerns:
 * - Variant merging with ID preservation
 * - Option merging with change detection
 * - Image merging with deduplication
 * - Metafield preservation strategies
 * 
 * Benefits:
 * - Reusable across different sync scenarios
 * - Testable individual merge operations  
 * - Consistent merge logic
 * - Clear change detection and logging
 */
@Service
public class ProductMergeService {
    
    private static final Logger logger = LoggerFactory.getLogger(ProductMergeService.class);
    
    @Autowired
    private VariantService variantService;
    
    @Autowired
    private ProductImageService imageService;
    
    /**
     * Complete product merge operation
     * 
     * @param existing The existing product from Shopify
     * @param updated The updated product data
     * @return MergeResult with details of what changed
     */
    public ProductMergeResult mergeProducts(Product existing, Product updated) {
        logger.info("🔄 Starting product merge for ID: {}", existing.getId());
        
        ProductMergeResult result = new ProductMergeResult();
        
        // Set the existing ID on updated product
        updated.setId(existing.getId());
        
        // Merge each component and track changes
        result.variantChanges = mergeVariants(existing.getVariants(), updated.getVariants());
        result.optionChanges = mergeOptions(existing.getOptions(), updated.getOptions());
        result.imageChanges = mergeImages(existing, updated);
        result.metafieldChanges = mergeMetafields(existing, updated);
        
        logger.info("✅ Product merge completed - {} total changes detected", result.getTotalChanges());
        return result;
    }
    
    /**
     * Merge variants using the specialized service
     */
    private int mergeVariants(List<Variant> existing, List<Variant> updated) {
        if (existing == null || updated == null) {
            return 0;
        }
        
        logger.debug("🔧 Merging {} existing variants with {} updated variants", 
            existing.size(), updated.size());
        
        variantService.mergeVariants(existing, updated);
        
        // Simple change detection - could be enhanced
        return updated.size() > 0 ? 1 : 0;
    }
    
    /**
     * Merge product options with detailed change detection
     */
    private int mergeOptions(List<Option> existing, List<Option> updated) {
        if (existing == null || updated == null) {
            logger.debug("Skipping option merge - null options list");
            return 0;
        }
        
        logger.debug("🎛️ Merging {} existing options with {} updated options", 
            existing.size(), updated.size());
        
        Map<String, Option> existingByName = existing.stream()
            .collect(Collectors.toMap(Option::getName, option -> option));
        
        int changeCount = 0;
        
        for (Option updatedOption : updated) {
            Option existingOption = existingByName.get(updatedOption.getName());
            
            if (existingOption != null) {
                // Preserve existing option ID
                updatedOption.setId(existingOption.getId());
                
                // Check for value changes
                if (hasOptionValuesChanged(existingOption, updatedOption)) {
                    logger.info("📝 Option '{}' values changed: {} → {}", 
                        updatedOption.getName(),
                        existingOption.getValues(),
                        updatedOption.getValues());
                    changeCount++;
                }
            } else {
                logger.info("✨ New option added: {} with values: {}", 
                    updatedOption.getName(), updatedOption.getValues());
                changeCount++;
            }
        }
        
        return changeCount;
    }
    
    /**
     * Merge images using the specialized service
     */
    private int mergeImages(Product existing, Product updated) {
        logger.debug("🖼️ Merging product images");
        
        imageService.mergeImages(
            existing.getId(), 
            existing.getImages(), 
            updated.getImages()
        );
        
        // Return simple change indicator
        return (updated.getImages() != null && !updated.getImages().isEmpty()) ? 1 : 0;
    }
    
    /**
     * Smart metafield merging with preservation strategy
     */
    private int mergeMetafields(Product existing, Product updated) {
        logger.debug("📋 Merging product metafields");
        
        // Strategy: Preserve existing metafields if updated product has none
        // This allows eBay metafields and other metafields to persist during updates
        if (updated.getMetafields() == null || updated.getMetafields().isEmpty()) {
            updated.setMetafields(existing.getMetafields());
            
            int preservedCount = existing.getMetafields() != null ? existing.getMetafields().size() : 0;
            logger.debug("💾 Preserved {} existing metafields during merge", preservedCount);
            return 0; // No changes, just preservation
        } else {
            int newCount = updated.getMetafields().size();
            logger.debug("🆕 Using {} new metafields from updated product", newCount);
            return 1; // Indicate metafields changed
        }
    }
    
    /**
     * Check if option values have changed between existing and updated options
     */
    private boolean hasOptionValuesChanged(Option existing, Option updated) {
        List<String> existingValues = existing.getValues();
        List<String> updatedValues = updated.getValues();
        
        return !OptionComparator.areValuesEqual(existingValues, updatedValues);
    }
    
    /**
     * Helper class for comparing option values
     */
    private static class OptionComparator {
        
        /**
         * Compare two lists of option values for equality
         */
        public static boolean areValuesEqual(List<String> existing, List<String> updated) {
            // Handle null cases
            if (existing == null && updated == null) return true;
            if (existing == null || updated == null) return false;
            
            // Compare sizes
            if (existing.size() != updated.size()) return false;
            
            // Compare individual values
            for (int i = 0; i < existing.size(); i++) {
                if (!areStringValuesEqual(existing.get(i), updated.get(i))) {
                    return false;
                }
            }
            
            return true;
        }
        
        /**
         * Compare two string values with null safety and trimming
         */
        private static boolean areStringValuesEqual(String existing, String updated) {
            if (existing == null && updated == null) return true;
            if (existing == null || updated == null) return false;
            return existing.trim().equals(updated.trim());
        }
    }
    
    /**
     * Result of a product merge operation
     */
    public static class ProductMergeResult {
        private int variantChanges = 0;
        private int optionChanges = 0;
        private int imageChanges = 0;
        private int metafieldChanges = 0;
        
        public int getTotalChanges() {
            return variantChanges + optionChanges + imageChanges + metafieldChanges;
        }
        
        public boolean hasChanges() {
            return getTotalChanges() > 0;
        }
        
        public void logSummary() {
            logger.info("📊 Merge Summary - Variants: {}, Options: {}, Images: {}, Metafields: {} (Total: {})",
                variantChanges, optionChanges, imageChanges, metafieldChanges, getTotalChanges());
        }
        
        // Getters
        public int getVariantChanges() { return variantChanges; }
        public int getOptionChanges() { return optionChanges; }
        public int getImageChanges() { return imageChanges; }
        public int getMetafieldChanges() { return metafieldChanges; }
    }
} 