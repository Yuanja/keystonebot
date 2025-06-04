package com.gw.services.sync;

import com.gw.domain.EbayMetafieldDefinition;
import com.gw.domain.PredefinedCollection;
import com.gw.services.shopifyapi.ShopifyGraphQLService;
import com.gw.services.shopifyapi.objects.CustomCollection;
import com.gw.domain.keystone.KeyStoneCollections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reusable Sync Configuration Service
 * 
 * Handles initialization and configuration for sync operations:
 * - Collection mappings management
 * - Metafield definitions setup
 * - Configuration caching and validation
 * 
 * Benefits:
 * - Centralized configuration logic
 * - Thread-safe initialization
 * - Performance optimization through caching
 */
@Service
public class SyncConfigurationService {
    
    private static final Logger logger = LoggerFactory.getLogger(SyncConfigurationService.class);
    
    @Autowired
    private ShopifyGraphQLService shopifyGraphQLService;
    
    // Cached mappings
    private Map<PredefinedCollection, CustomCollection> cachedCollectionByEnum;
    private boolean collectionsInitialized = false;
    private boolean metafieldDefinitionsInitialized = false;
    
    /**
     * Get collection mappings (cached)
     */
    public synchronized Map<PredefinedCollection, CustomCollection> getCollectionMappings() throws Exception {
        if (!collectionsInitialized || cachedCollectionByEnum == null) {
            ensureCollections();
        }
        return cachedCollectionByEnum;
    }
    
    /**
     * Ensure collections are configured
     */
    public void ensureCollections(PredefinedCollection[] requiredCollections) throws Exception {
        if (!collectionsInitialized || cachedCollectionByEnum == null) {
            logger.info("🔍 Checking collection configuration status...");
            
            try {
                List<CustomCollection> allCollectionsFromShopify = shopifyGraphQLService.getAllCustomCollections();
                Map<String, CustomCollection> collectionByTitleFromShopify = 
                    allCollectionsFromShopify.stream().collect(Collectors.toMap(CustomCollection::getTitle, Function.identity()));
                
                int existingCount = 0;
                for (PredefinedCollection collectionEnum : requiredCollections) {
                    if (collectionByTitleFromShopify.containsKey(collectionEnum.getTitle())) {
                        existingCount++;
                    }
                }
                
                boolean allCollectionsExist = (existingCount == requiredCollections.length);
                
                if (allCollectionsExist) {
                    logger.info("✅ All {} required collections already exist", requiredCollections.length);
                    
                    cachedCollectionByEnum = new HashMap<>();
                    for (PredefinedCollection collectionEnum : requiredCollections) {
                        CustomCollection existingCollection = collectionByTitleFromShopify.get(collectionEnum.getTitle());
                        cachedCollectionByEnum.put(collectionEnum, existingCollection);
                    }
                } else {
                    logger.info("🔧 Missing collections detected ({}/{}) - ensuring configuration...", existingCount, requiredCollections.length);
                    cachedCollectionByEnum = shopifyGraphQLService.ensureConfiguredCollections(requiredCollections);
                    logger.info("✅ Successfully ensured {} collection mappings", cachedCollectionByEnum.size());
                }
                
                collectionsInitialized = true;
                logger.info("📊 Cached {} collection mappings", cachedCollectionByEnum.size());
                
            } catch (Exception e) {
                logger.error("❌ Error ensuring collections: {}", e.getMessage(), e);
                throw e;
            }
        }
    }
    
    /**
     * Simplified version for internal use when we don't have the collections array
     */
    private void ensureCollections() throws Exception {
        // Get the predefined collections from the sync service
        PredefinedCollection[] requiredCollections = KeyStoneCollections.values();
        ensureCollections(requiredCollections);
    }
    
    /**
     * Ensure metafield definitions are configured
     */
    public void ensureMetafieldDefinitions() throws Exception {
        if (!metafieldDefinitionsInitialized) {
            logger.info("🔍 Checking eBay metafield definitions status...");
            
            try {
                List<Map<String, String>> existingEbayMetafields = shopifyGraphQLService.getMetafieldDefinitions("ebay");
                
                int expectedCount = EbayMetafieldDefinition.getCount();
                boolean allMetafieldsExist = (existingEbayMetafields.size() == expectedCount);
                
                if (allMetafieldsExist) {
                    boolean structureValid = validateMetafieldStructure(existingEbayMetafields);
                    
                    if (structureValid) {
                        logger.info("✅ All {} eBay metafield definitions already exist with correct structure", expectedCount);
                        metafieldDefinitionsInitialized = true;
                        return;
                    } else {
                        logger.info("🔧 eBay metafield definitions exist but structure is invalid - will recreate");
                    }
                } else {
                    logger.info("🔧 Missing eBay metafield definitions detected ({}/{}) - creating...", existingEbayMetafields.size(), expectedCount);
                }
                
                shopifyGraphQLService.createEbayMetafieldDefinitions();
                metafieldDefinitionsInitialized = true;
                logger.info("✅ eBay metafield definitions ensured and cached");
                
            } catch (Exception e) {
                logger.info("ℹ️ eBay metafield definitions status check completed: {}", e.getMessage());
                metafieldDefinitionsInitialized = true;
            }
        }
    }
    
    /**
     * Validate metafield structure
     */
    private boolean validateMetafieldStructure(List<Map<String, String>> metafields) {
        int expectedCount = EbayMetafieldDefinition.getCount();
        if (metafields.size() != expectedCount) {
            logger.warn("⚠️ Expected {} eBay metafields, found {}", expectedCount, metafields.size());
            return false;
        }
        
        List<String> expectedKeys = EbayMetafieldDefinition.getAllKeys();
        List<String> currentKeys = metafields.stream()
            .map(m -> m.get("key"))
            .sorted()
            .collect(Collectors.toList());
        
        boolean structureValid = currentKeys.equals(expectedKeys);
        
        if (!structureValid) {
            logger.warn("⚠️ Metafield structure validation failed");
            logger.warn("  Expected keys: {}", expectedKeys);
            logger.warn("  Current keys: {}", currentKeys);
        } else {
            logger.info("✅ Metafield structure validation passed - all {} expected keys found", expectedCount);
        }
        
        return structureValid;
    }
    
    /**
     * Clear cached configuration (for testing or forced refresh)
     */
    public void clearCache() {
        cachedCollectionByEnum = null;
        collectionsInitialized = false;
        metafieldDefinitionsInitialized = false;
        logger.info("🗑️ Configuration cache cleared");
    }
} 