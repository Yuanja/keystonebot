package com.gw.services.product;

import com.gw.domain.FeedItem;
import com.gw.services.shopifyapi.ShopifyGraphQLService;
import com.gw.services.shopifyapi.objects.Metafield;
import com.gw.services.shopifyapi.objects.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Consolidated Metadata Update Service
 * 
 * Handles all metadata updates for both publish and update pipelines:
 * - SEO metadata (metafieldsGlobalTitleTag, metafieldsGlobalDescriptionTag)
 * - Google metadata (metafields for Google Merchant)
 * - eBay metadata (regular metafields)
 * 
 * Benefits:
 * - Single source of truth for metadata comparison logic
 * - Eliminates duplication between publish and update pipelines
 * - Consistent metadata handling across all operations
 * - Centralized logging and error handling
 */
@Service
public class MetadataUpdateService {
    
    private static final Logger logger = LoggerFactory.getLogger(MetadataUpdateService.class);
    
    @Autowired
    private ShopifyGraphQLService shopifyGraphQLService;
    
    @Autowired
    private MetadataService metadataService;
    
    /**
     * Update all metadata (SEO + regular metafields) if changed
     * Used by ProductUpdatePipeline for efficient updates
     * 
     * @param existing Current product from Shopify
     * @param feedItem Updated feed item with new metadata
     * @throws Exception if update fails
     */
    public void updateMetadataIfChanged(Product existing, FeedItem feedItem) throws Exception {
        logger.debug("📋 Checking all metadata for changes (SKU: {})", feedItem.getWebTagNumber());
        
        // Build updated metadata from feedItem
        Product updatedMetadata = buildMetadataProduct(feedItem);
        
        boolean seoChanged = isSeoMetadataChanged(existing, updatedMetadata);
        boolean regularMetafieldsChanged = areRegularMetafieldsChanged(existing, updatedMetadata);
        
        if (seoChanged || regularMetafieldsChanged) {
            logger.info("🔄 Updating metadata for SKU: {} (SEO: {}, Metafields: {})", 
                feedItem.getWebTagNumber(), seoChanged, regularMetafieldsChanged);
            
            // Create update product with only changed metadata
            Product updateProduct = new Product();
            updateProduct.setId(existing.getId());
            
            if (seoChanged) {
                updateProduct.setMetafieldsGlobalTitleTag(updatedMetadata.getMetafieldsGlobalTitleTag());
                updateProduct.setMetafieldsGlobalDescriptionTag(updatedMetadata.getMetafieldsGlobalDescriptionTag());
                logger.debug("🔍 Including SEO metadata in update");
            }
            
            if (regularMetafieldsChanged) {
                updateProduct.setMetafields(updatedMetadata.getMetafields());
                logger.debug("📋 Including regular metafields in update");
            }
            
            shopifyGraphQLService.updateProduct(updateProduct);
            logger.debug("✅ Metadata updated successfully");
        } else {
            logger.debug("⏭️ All metadata unchanged - skipping");
        }
    }
    
    /**
     * Check if SEO metadata has changed
     */
    public boolean isSeoMetadataChanged(Product existing, Product updated) {
        String existingTitle = existing.getMetafieldsGlobalTitleTag();
        String updatedTitle = updated.getMetafieldsGlobalTitleTag();
        String existingDescription = existing.getMetafieldsGlobalDescriptionTag();
        String updatedDescription = updated.getMetafieldsGlobalDescriptionTag();
        
        boolean titleChanged = !Objects.equals(existingTitle, updatedTitle);
        boolean descriptionChanged = !Objects.equals(existingDescription, updatedDescription);
        
        if (titleChanged || descriptionChanged) {
            logger.debug("📊 SEO metadata changes detected:");
            if (titleChanged) {
                logger.debug("  - Title: '{}' → '{}'", existingTitle, updatedTitle);
            }
            if (descriptionChanged) {
                logger.debug("  - Description: '{}' → '{}'", 
                    truncateForLog(existingDescription), truncateForLog(updatedDescription));
            }
        }
        
        return titleChanged || descriptionChanged;
    }
    
    /**
     * Check if regular metafields have changed
     * Performs detailed comparison of Google and eBay metafields
     */
    public boolean areRegularMetafieldsChanged(Product existing, Product updated) {
        Map<String, String> existingMetafields = extractMetafieldsAsMap(existing.getMetafields());
        Map<String, String> updatedMetafields = extractMetafieldsAsMap(updated.getMetafields());
        
        boolean googleChanged = areGoogleMetafieldsChanged(existingMetafields, updatedMetafields);
        boolean ebayChanged = areEbayMetafieldsChanged(existingMetafields, updatedMetafields);
        
        if (googleChanged || ebayChanged) {
            logger.debug("📊 Metafield changes detected - Google: {}, eBay: {}", googleChanged, ebayChanged);
            return true;
        }
        
        return false;
    }
    
    /**
     * Check if Google metafields have changed
     */
    private boolean areGoogleMetafieldsChanged(Map<String, String> existing, Map<String, String> updated) {
        List<String> changedFields = new ArrayList<>();
        
        // Check each Google metafield
        String[] googleKeys = {"google.custom_product", "google.age_group", "google.google_product_type", 
                              "google.gender", "google.condition", "google.adwords_grouping", "google.adwords_labels"};
        
        for (String key : googleKeys) {
            String existingValue = existing.get(key);
            String updatedValue = updated.get(key);
            
            if (!Objects.equals(existingValue, updatedValue)) {
                changedFields.add(key);
                logger.debug("  - Google {}: '{}' → '{}'", 
                    key.substring(7), existingValue, updatedValue); // Remove "google." prefix
            }
        }
        
        return !changedFields.isEmpty();
    }
    
    /**
     * Check if eBay metafields have changed
     */
    private boolean areEbayMetafieldsChanged(Map<String, String> existing, Map<String, String> updated) {
        List<String> changedFields = new ArrayList<>();
        
        // Get all eBay metafield keys from both existing and updated
        Set<String> allEbayKeys = new HashSet<>();
        allEbayKeys.addAll(getEbayKeys(existing));
        allEbayKeys.addAll(getEbayKeys(updated));
        
        for (String key : allEbayKeys) {
            String existingValue = existing.get(key);
            String updatedValue = updated.get(key);
            
            if (!Objects.equals(existingValue, updatedValue)) {
                changedFields.add(key);
                logger.debug("  - eBay {}: '{}' → '{}'", 
                    key.substring(5), existingValue, updatedValue); // Remove "ebay." prefix
            }
        }
        
        if (!changedFields.isEmpty()) {
            logger.debug("📦 eBay metafield changes: {} fields changed", changedFields.size());
        }
        
        return !changedFields.isEmpty();
    }
    
    /**
     * Extract metafields as a map for easier comparison
     * Key format: "namespace.key" -> value
     */
    private Map<String, String> extractMetafieldsAsMap(List<Metafield> metafields) {
        if (metafields == null) {
            return new HashMap<>();
        }
        
        return metafields.stream()
            .filter(m -> m.getNamespace() != null && m.getKey() != null)
            .collect(Collectors.toMap(
                m -> m.getNamespace() + "." + m.getKey(),
                m -> m.getValue() != null ? m.getValue() : "",
                (existing, replacement) -> replacement // Handle duplicates by keeping the new value
            ));
    }
    
    /**
     * Get all eBay metafield keys from a metafield map
     */
    private Set<String> getEbayKeys(Map<String, String> metafieldMap) {
        return metafieldMap.keySet().stream()
            .filter(key -> key.startsWith("ebay."))
            .collect(Collectors.toSet());
    }
    
    /**
     * Build a product with updated metadata from feedItem
     * This creates a fresh metadata state for comparison
     */
    private Product buildMetadataProduct(FeedItem feedItem) throws Exception {
        Product product = new Product();
        
        // Build fresh metadata using the centralized service
        metadataService.setProductMetadata(product, feedItem);
        
        return product;
    }
    
    /**
     * Truncate long strings for logging
     */
    private String truncateForLog(String text) {
        if (text == null) return "null";
        return text.length() > 50 ? text.substring(0, 50) + "..." : text;
    }
    
    /**
     * Validate that metadata creation succeeded
     * Used for debugging metadata creation issues
     */
    public void validateMetadataCreation(Product product, String sku) {
        logger.debug("🔍 Validating metadata creation for SKU: {}", sku);
        
        // Check SEO metadata
        if (product.getMetafieldsGlobalTitleTag() != null) {
            logger.debug("✅ SEO title created: '{}'", product.getMetafieldsGlobalTitleTag());
        } else {
            logger.warn("⚠️ SEO title is null for SKU: {}", sku);
        }
        
        if (product.getMetafieldsGlobalDescriptionTag() != null) {
            logger.debug("✅ SEO description created (length: {})", 
                product.getMetafieldsGlobalDescriptionTag().length());
        } else {
            logger.warn("⚠️ SEO description is null for SKU: {}", sku);
        }
        
        // Check metafields breakdown
        Map<String, String> metafieldMap = extractMetafieldsAsMap(product.getMetafields());
        long googleCount = metafieldMap.keySet().stream().filter(k -> k.startsWith("google.")).count();
        long ebayCount = metafieldMap.keySet().stream().filter(k -> k.startsWith("ebay.")).count();
        
        logger.debug("📋 Metafields created - Google: {}, eBay: {}, Total: {}", 
            googleCount, ebayCount, metafieldMap.size());
        
        if (googleCount == 0) {
            logger.warn("⚠️ No Google metafields created for SKU: {}", sku);
        }
        
        if (ebayCount == 0) {
            logger.warn("⚠️ No eBay metafields created for SKU: {}", sku);
        }
    }
} 