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
import java.util.Set;

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
            
            // Step 1: Remove product from ALL managed collections first (clean state)
            removeProductFromManagedCollections(item.getShopifyItemId(), item.getWebTagNumber(), collectionMappings);
            
            // Step 2: Determine which collections the product should be in
            List<Collect> collectsToAdd = CollectionUtility.getCollectionForProduct(
                item.getShopifyItemId(), item, collectionMappings);
            
            if (!collectsToAdd.isEmpty()) {
                // Enhanced logging: Show which collections we're trying to add
                logger.debug("🏷️ Step 2: Adding product {} (SKU: {}) to {} collections", 
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
                logger.debug("⚠️ No collections found for product SKU: {} (product will remain in no managed collections)", item.getWebTagNumber());
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
            
            // Step 1: Remove product from ALL managed collections first (clean state)
            removeProductFromManagedCollections(productId, item.getWebTagNumber(), collectionMappings);
            
            // Step 2: Determine which collections the product should be in
            List<Collect> collectsToAdd = CollectionUtility.getCollectionForProduct(
                productId, item, collectionMappings);
            
            if (!collectsToAdd.isEmpty()) {
                // Enhanced logging: Show which collections we're trying to add
                logger.debug("🏷️ Step 2: Adding product {} (SKU: {}) to {} collections", 
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
                logger.debug("⚠️ No collections found for product SKU: {} (product will remain in no managed collections)", item.getWebTagNumber());
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
    
    /**
     * Remove product from all managed collections with detailed logging
     * This provides better debugging information than the base ShopifyGraphQLService method
     */
    private void removeProductFromManagedCollections(String productId, String sku, 
                                                   Map<PredefinedCollection, CustomCollection> collectionMappings) {
        if (collectionMappings == null || collectionMappings.isEmpty()) {
            logger.debug("🔍 No managed collections defined, skipping removal for product: {} (SKU: {})", productId, sku);
            return;
        }
        
        // Get current collections for this product
        List<Collect> currentCollections = shopifyGraphQLService.getCollectsForProductId(productId);
        
        // Create a map of managed collection IDs to their titles for easy lookup
        Map<String, String> managedCollectionTitles = collectionMappings.values().stream()
            .collect(java.util.stream.Collectors.toMap(CustomCollection::getId, CustomCollection::getTitle));
        
        Set<String> managedCollectionIds = managedCollectionTitles.keySet();
        
        // Filter to only managed collections that the product is currently in
        List<Collect> managedCollectionsToRemove = currentCollections.stream()
            .filter(collect -> managedCollectionIds.contains(collect.getCollectionId()))
            .collect(java.util.stream.Collectors.toList());
        
        if (managedCollectionsToRemove.isEmpty()) {
            logger.debug("🔍 Product {} (SKU: {}) is not in any managed collections, no removal needed", productId, sku);
            return;
        }
        
        logger.debug("🧹 Removing product {} (SKU: {}) from {} managed collections", 
            productId, sku, managedCollectionsToRemove.size());
        
        // Remove from each managed collection individually
        int removalSuccessCount = 0;
        int removalFailCount = 0;
        StringBuilder failedRemovals = new StringBuilder();
        
        for (Collect collect : managedCollectionsToRemove) {
            String collectionId = collect.getCollectionId();
            String collectionTitle = managedCollectionTitles.get(collectionId);
            
            try {
                logger.debug("  🗑️ Removing from collection: '{}' (ID: {})", collectionTitle, collectionId);
                shopifyGraphQLService.deleteCollectByProductAndCollection(productId, collectionId);
                logger.debug("  ✅ Successfully removed from collection: '{}'", collectionTitle);
                removalSuccessCount++;
                
            } catch (Exception e) {
                logger.warn("  ❌ Failed to remove from collection: '{}' (ID: {}) - {}", 
                    collectionTitle, collectionId, e.getMessage());
                removalFailCount++;
                
                if (failedRemovals.length() > 0) {
                    failedRemovals.append(", ");
                }
                failedRemovals.append("'").append(collectionTitle).append("'");
                
                // Continue with other collections instead of failing completely
            }
        }
        
        // Summary logging for removals
        if (removalSuccessCount > 0) {
            logger.debug("✅ Successfully removed product from {}/{} managed collections", 
                removalSuccessCount, managedCollectionsToRemove.size());
        }
        
        if (removalFailCount > 0) {
            logger.warn("⚠️ Failed to remove product from {}/{} managed collections: {}", 
                removalFailCount, managedCollectionsToRemove.size(), failedRemovals.toString());
        }
        
        // Log info about non-managed collections we're leaving untouched
        long untouchedCount = currentCollections.stream()
            .filter(collect -> !managedCollectionIds.contains(collect.getCollectionId()))
            .count();
        
        if (untouchedCount > 0) {
            logger.debug("ℹ️ Left product in {} non-managed collections (not removed)", untouchedCount);
        }
    }
} 