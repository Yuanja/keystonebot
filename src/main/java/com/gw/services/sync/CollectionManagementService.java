package com.gw.services.sync;

import com.gw.domain.FeedItem;
import com.gw.domain.PredefinedCollection;
import com.gw.services.CollectionUtility;
import com.gw.services.shopifyapi.ShopifyGraphQLService;
import com.gw.services.shopifyapi.objects.Collect;
import com.gw.services.shopifyapi.objects.CustomCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Reusable Collection Management Service
 * 
 * Handles all collection-related operations:
 * - Collection mapping management
 * - Product-collection associations
 * - Collection updates and validation
 * 
 * Benefits:
 * - Centralized collection logic
 * - Reusable across sync operations
 * - Clean error handling
 */
@Service
public class CollectionManagementService {
    
    private static final Logger logger = LoggerFactory.getLogger(CollectionManagementService.class);
    
    @Autowired
    private ShopifyGraphQLService shopifyGraphQLService;
    
    @Autowired
    private SyncConfigurationService syncConfigurationService;
    
    /**
     * Update product collection associations
     */
    public void updateProductCollections(FeedItem item) throws Exception {
        try {
            Map<PredefinedCollection, CustomCollection> collectionMappings = 
                syncConfigurationService.getCollectionMappings();
            
            List<Collect> collectsToAdd = CollectionUtility.getCollectionForProduct(
                item.getShopifyItemId(), item, collectionMappings);
            
            if (!collectsToAdd.isEmpty()) {
                // Enhanced logging: Show which collections we're trying to add
                logger.debug("🏷️ Attempting to add product {} (SKU: {}) to {} collections", 
                    item.getShopifyItemId(), item.getWebTagNumber(), collectsToAdd.size());
                
                // Process each collection individually for better error handling
                int successCount = 0;
                int failCount = 0;
                StringBuilder failedCollections = new StringBuilder();
                
                for (Collect collect : collectsToAdd) {
                    String collectionId = collect.getCollectionId();
                    String collectionName = findCollectionNameById(collectionId, collectionMappings);
                    
                    try {
                        logger.debug("  🔗 Adding to collection: '{}' (ID: {})", collectionName, collectionId);
                        
                        // Perform individual ShopifyQL operation for this collection
                        shopifyGraphQLService.addProductToCollection(collect.getProductId(), collectionId);
                        
                        logger.debug("  ✅ Successfully added to collection: '{}'", collectionName);
                        successCount++;
                        
                    } catch (Exception e) {
                        logger.warn("  ❌ Failed to add to collection: '{}' (ID: {}) - {}", 
                            collectionName, collectionId, e.getMessage());
                        failCount++;
                        
                        if (failedCollections.length() > 0) {
                            failedCollections.append(", ");
                        }
                        failedCollections.append("'").append(collectionName).append("'");
                        
                        // Continue with other collections instead of failing completely
                    }
                }
                
                // Summary logging
                if (successCount > 0) {
                    logger.debug("✅ Successfully added product to {}/{} collections", successCount, collectsToAdd.size());
                }
                
                if (failCount > 0) {
                    logger.warn("⚠️ Failed to add product to {}/{} collections: {}", 
                        failCount, collectsToAdd.size(), failedCollections.toString());
                    
                    // Only throw exception if ALL collections failed
                    if (successCount == 0) {
                        throw new Exception("Failed to add product to any collections. Failed collections: " + failedCollections.toString());
                    }
                }
                
            } else {
                logger.debug("⚠️ No collections found for product SKU: {}", item.getWebTagNumber());
            }
        } catch (Exception e) {
            logger.error("❌ Failed to update collections for SKU: {}", item.getWebTagNumber(), e);
            throw e;
        }
    }
    
    /**
     * Update product collection associations with explicit product ID
     * This is useful during publish pipeline when FeedItem doesn't have Shopify ID set yet
     */
    public void updateProductCollections(String productId, FeedItem item) throws Exception {
        try {
            Map<PredefinedCollection, CustomCollection> collectionMappings = 
                syncConfigurationService.getCollectionMappings();
            
            List<Collect> collectsToAdd = CollectionUtility.getCollectionForProduct(
                productId, item, collectionMappings);
            
            if (!collectsToAdd.isEmpty()) {
                // Enhanced logging: Show which collections we're trying to add
                logger.debug("🏷️ Attempting to add product {} (SKU: {}) to {} collections", 
                    productId, item.getWebTagNumber(), collectsToAdd.size());
                
                // Process each collection individually for better error handling
                int successCount = 0;
                int failCount = 0;
                StringBuilder failedCollections = new StringBuilder();
                
                for (Collect collect : collectsToAdd) {
                    String collectionId = collect.getCollectionId();
                    String collectionName = findCollectionNameById(collectionId, collectionMappings);
                    
                    try {
                        logger.debug("  🔗 Adding to collection: '{}' (ID: {})", collectionName, collectionId);
                        
                        // Perform individual ShopifyQL operation for this collection
                        shopifyGraphQLService.addProductToCollection(productId, collectionId);
                        
                        logger.debug("  ✅ Successfully added to collection: '{}'", collectionName);
                        successCount++;
                        
                    } catch (Exception e) {
                        logger.warn("  ❌ Failed to add to collection: '{}' (ID: {}) - {}", 
                            collectionName, collectionId, e.getMessage());
                        failCount++;
                        
                        if (failedCollections.length() > 0) {
                            failedCollections.append(", ");
                        }
                        failedCollections.append("'").append(collectionName).append("'");
                        
                        // Continue with other collections instead of failing completely
                    }
                }
                
                // Summary logging
                if (successCount > 0) {
                    logger.debug("✅ Successfully added product {} to {}/{} collections", productId, successCount, collectsToAdd.size());
                }
                
                if (failCount > 0) {
                    logger.warn("⚠️ Failed to add product {} to {}/{} collections: {}", 
                        productId, failCount, collectsToAdd.size(), failedCollections.toString());
                    
                    // Only throw exception if ALL collections failed
                    if (successCount == 0) {
                        throw new Exception("Failed to add product to any collections. Failed collections: " + failedCollections.toString());
                    }
                }
                
            } else {
                logger.debug("⚠️ No collections found for product SKU: {}", item.getWebTagNumber());
            }
        } catch (Exception e) {
            logger.error("❌ Failed to update collections for SKU: {} with product ID: {}", item.getWebTagNumber(), productId, e);
            throw e;
        }
    }
    
    /**
     * Helper method to find collection name by ID from the mappings
     */
    private String findCollectionNameById(String collectionId, Map<PredefinedCollection, CustomCollection> collectionMappings) {
        return collectionMappings.values().stream()
            .filter(collection -> collectionId.equals(collection.getId()))
            .map(CustomCollection::getTitle)
            .findFirst()
            .orElse("Unknown Collection (ID: " + collectionId + ")");
    }
} 