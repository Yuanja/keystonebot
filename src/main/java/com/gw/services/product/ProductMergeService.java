package com.gw.services.product;

import com.gw.services.shopifyapi.objects.Product;
import com.gw.services.shopifyapi.objects.Metafield;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.Objects;

/**
 * Simple Product Merge Service
 * 
 * Focused operations:
 * - Basic field merging (metafields only - other components handled separately)
 * - Metafield preservation with ID handling
 * - Clean product creation for API updates
 * 
 * Benefits:
 * - Simple and focused
 * - Only handles what's actually needed
 * - Easy to understand and maintain
 */
@Service
public class ProductMergeService {
    
    private static final Logger logger = LoggerFactory.getLogger(ProductMergeService.class);
    
    /**
     * Merge basic fields (currently just metafields since other components are handled separately)
     * 
     * @param existing The existing product from Shopify
     * @param updated The updated product data
     * @return Simple result indicating if changes were made
     */
    public boolean mergeBasicFields(Product existing, Product updated) {
        logger.debug("🔄 Merging basic fields for product ID: {}", existing.getId());
        
        // Set the existing ID on updated product
        updated.setId(existing.getId());
        
        // Only merge metafields since variants/options/images are handled separately
        boolean metafieldsChanged = mergeMetafields(existing, updated);
        
        logger.debug("✅ Basic field merge completed - metafields changed: {}", metafieldsChanged);
        return metafieldsChanged;
    }
    
    /**
     * Create a product for basic API updates (excludes variants/options)
     * 
     * @param source The source product with all data
     * @return Product with only basic fields for safe API updates
     */
    public Product createBasicUpdateProduct(Product source) {
        logger.debug("🔧 Creating basic update product for ID: {}", source.getId());
        
        Product basic = new Product();
        
        // Copy basic fields
        basic.setId(source.getId());
        basic.setTitle(source.getTitle());
        basic.setBodyHtml(source.getBodyHtml());
        basic.setVendor(source.getVendor());
        basic.setProductType(source.getProductType());
        basic.setHandle(source.getHandle());
        basic.setTags(source.getTags());
        basic.setStatus(source.getStatus());
        basic.setMetafields(source.getMetafields());
        
        // Explicitly exclude variants and options
        basic.setVariants(null);
        basic.setOptions(null);
        
        logger.debug("✅ Basic update product created");
        return basic;
    }
    
    /**
     * Smart metafield merging with ID preservation
     */
    private boolean mergeMetafields(Product existing, Product updated) {
        logger.debug("📋 Merging metafields");
        
        // If no new metafields, preserve existing ones
        if (updated.getMetafields() == null || updated.getMetafields().isEmpty()) {
            updated.setMetafields(existing.getMetafields());
            logger.debug("💾 Preserved existing metafields");
            return false; // No changes
        }
        
        // If no existing metafields, use new ones
        if (existing.getMetafields() == null || existing.getMetafields().isEmpty()) {
            logger.debug("🆕 Using new metafields");
            return true; // Changes made
        }
        
        // Merge existing and updated metafields
        return mergeMetafieldsWithIdPreservation(existing.getMetafields(), updated.getMetafields(), updated);
    }
    
    /**
     * Merge metafields while preserving existing IDs
     */
    private boolean mergeMetafieldsWithIdPreservation(List<Metafield> existing, List<Metafield> updated, Product updatedProduct) {
        Map<String, Metafield> existingByKey = existing.stream()
            .collect(Collectors.toMap(
                mf -> mf.getNamespace() + "." + mf.getKey(), 
                mf -> mf
            ));
        
        boolean hasChanges = false;
        List<Metafield> mergedMetafields = new ArrayList<>();
        
        // Process updated metafields
        for (Metafield updatedMetafield : updated) {
            String key = updatedMetafield.getNamespace() + "." + updatedMetafield.getKey();
            Metafield existingMetafield = existingByKey.get(key);
            
            if (existingMetafield != null) {
                // Preserve existing ID
                updatedMetafield.setId(existingMetafield.getId());
                
                // Check for changes
                if (!Objects.equals(existingMetafield.getValue(), updatedMetafield.getValue()) ||
                    !Objects.equals(existingMetafield.getType(), updatedMetafield.getType())) {
                    logger.debug("📝 Metafield changed: {}", key);
                    hasChanges = true;
                }
                
                mergedMetafields.add(updatedMetafield);
                existingByKey.remove(key); // Mark as processed
            } else {
                // New metafield
                logger.debug("✨ New metafield: {}", key);
                mergedMetafields.add(updatedMetafield);
                hasChanges = true;
            }
        }
        
        // Add remaining existing metafields
        for (Metafield remaining : existingByKey.values()) {
            logger.debug("💾 Preserving metafield: {}.{}", remaining.getNamespace(), remaining.getKey());
            mergedMetafields.add(remaining);
        }
        
        // Update the product with merged metafields
        updatedProduct.setMetafields(mergedMetafields);
        
        logger.debug("🔄 Metafield merge completed: {} total, changes: {}", mergedMetafields.size(), hasChanges);
        return hasChanges;
    }
} 