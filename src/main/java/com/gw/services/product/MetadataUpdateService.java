package com.gw.services.product;

import com.gw.domain.FeedItem;
import com.gw.services.shopifyapi.ShopifyGraphQLService;
import com.gw.services.shopifyapi.objects.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Objects;

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
     * Uses simple size-based comparison for now - can be enhanced for detailed comparison
     */
    public boolean areRegularMetafieldsChanged(Product existing, Product updated) {
        int existingSize = existing.getMetafields() != null ? existing.getMetafields().size() : 0;
        int updatedSize = updated.getMetafields() != null ? updated.getMetafields().size() : 0;
        
        boolean changed = existingSize != updatedSize;
        
        if (changed) {
            logger.debug("📊 Regular metafields size changed: {} → {}", existingSize, updatedSize);
        }
        
        return changed;
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
        
        // Check regular metafields
        int metafieldCount = product.getMetafields() != null ? product.getMetafields().size() : 0;
        logger.debug("📋 Regular metafields created: {} fields", metafieldCount);
    }
} 