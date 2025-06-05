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
            
            // Step 1: Get current managed collections for this product
            List<Collect> currentManagedCollections = getCurrentManagedCollections(item.getShopifyItemId(), collectionMappings);
            
            // Step 2: Determine which collections the product should be in
            List<Collect> collectsToAdd = CollectionUtility.getCollectionForProduct(
                item.getShopifyItemId(), item, collectionMappings);
            
            // Step 3: Compare current vs desired collections
            if (collectionsMatch(currentManagedCollections, collectsToAdd, collectionMappings)) {
                logger.debug("✅ Product {} (SKU: {}) collections already match desired state - no changes needed", 
                    item.getShopifyItemId(), item.getWebTagNumber());
                logCurrentCollections(currentManagedCollections, collectionMappings, "Current collections");
                return; // No changes needed
            }
            
            // Step 4: Collections don't match - perform remove and re-add
            logger.debug("🔄 Product {} (SKU: {}) collections differ from desired state - updating", 
                item.getShopifyItemId(), item.getWebTagNumber());
            logCurrentCollections(currentManagedCollections, collectionMappings, "Current collections");
            logDesiredCollections(collectsToAdd, collectionMappings, "Desired collections");
            
            // Remove from ALL managed collections first (clean state)
            removeProductFromManagedCollections(item.getShopifyItemId(), item.getWebTagNumber(), collectionMappings);
            
            // Add to desired collections using bulk API
            if (!collectsToAdd.isEmpty()) {
                logger.debug("🏷️ Adding product {} (SKU: {}) to {} collections using bulk API", 
                    item.getShopifyItemId(), item.getWebTagNumber(), collectsToAdd.size());
                
                try {
                    // Use bulk API for better performance
                    shopifyGraphQLService.addProductAndCollectionsAssociations(collectsToAdd);
                    logger.debug("✅ Successfully added product to {} collections using bulk API", collectsToAdd.size());
                    
                } catch (Exception e) {
                    logger.error("❌ Bulk collection addition failed, falling back to individual operations", e);
                    
                    // Fallback to individual operations if bulk fails
                    addCollectionsIndividually(collectsToAdd, collectionMappings);
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
            
            // Step 1: Get current managed collections for this product
            List<Collect> currentManagedCollections = getCurrentManagedCollections(productId, collectionMappings);
            
            // Step 2: Determine which collections the product should be in
            List<Collect> collectsToAdd = CollectionUtility.getCollectionForProduct(
                productId, item, collectionMappings);
            
            // Step 3: Compare current vs desired collections
            if (collectionsMatch(currentManagedCollections, collectsToAdd, collectionMappings)) {
                logger.debug("✅ Product {} (SKU: {}) collections already match desired state - no changes needed", 
                    productId, item.getWebTagNumber());
                logCurrentCollections(currentManagedCollections, collectionMappings, "Current collections");
                return; // No changes needed
            }
            
            // Step 4: Collections don't match - perform remove and re-add
            logger.debug("🔄 Product {} (SKU: {}) collections differ from desired state - updating", 
                productId, item.getWebTagNumber());
            logCurrentCollections(currentManagedCollections, collectionMappings, "Current collections");
            logDesiredCollections(collectsToAdd, collectionMappings, "Desired collections");
            
            // Remove from ALL managed collections first (clean state)
            removeProductFromManagedCollections(productId, item.getWebTagNumber(), collectionMappings);
            
            // Add to desired collections using bulk API
            if (!collectsToAdd.isEmpty()) {
                logger.debug("🏷️ Adding product {} (SKU: {}) to {} collections using bulk API", 
                    productId, item.getWebTagNumber(), collectsToAdd.size());
                
                try {
                    // Use bulk API for better performance
                    shopifyGraphQLService.addProductAndCollectionsAssociations(collectsToAdd);
                    logger.debug("✅ Successfully added product {} to {} collections using bulk API", productId, collectsToAdd.size());
                    
                } catch (Exception e) {
                    logger.error("❌ Bulk collection addition failed for product {}, falling back to individual operations", productId, e);
                    
                    // Fallback to individual operations if bulk fails
                    addCollectionsIndividually(collectsToAdd, collectionMappings);
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
    
    /**
     * Get current managed collections for a product
     * Returns only collections that are defined in our collection mappings
     */
    private List<Collect> getCurrentManagedCollections(String productId, 
                                                     Map<PredefinedCollection, CustomCollection> collectionMappings) {
        List<Collect> allCurrentCollections = shopifyGraphQLService.getCollectsForProductId(productId);
        Set<String> managedCollectionIds = collectionMappings.values().stream()
            .map(CustomCollection::getId)
            .collect(java.util.stream.Collectors.toSet());
        
        return allCurrentCollections.stream()
            .filter(collect -> managedCollectionIds.contains(collect.getCollectionId()))
            .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * Compare current collections with desired collections
     * Returns true if they match (same collections), false if they differ
     */
    private boolean collectionsMatch(List<Collect> currentCollections, List<Collect> desiredCollections, 
                                   Map<PredefinedCollection, CustomCollection> collectionMappings) {
        // Convert to sets of collection IDs for easy comparison
        Set<String> currentCollectionIds = currentCollections.stream()
            .map(Collect::getCollectionId)
            .collect(java.util.stream.Collectors.toSet());
        
        Set<String> desiredCollectionIds = desiredCollections.stream()
            .map(Collect::getCollectionId)
            .collect(java.util.stream.Collectors.toSet());
        
        return currentCollectionIds.equals(desiredCollectionIds);
    }
    
    /**
     * Log current collections for debugging
     */
    private void logCurrentCollections(List<Collect> collections, 
                                     Map<PredefinedCollection, CustomCollection> collectionMappings, 
                                     String label) {
        if (collections.isEmpty()) {
            logger.debug("📋 {}: None", label);
            return;
        }
        
        logger.debug("📋 {}: {} collections", label, collections.size());
        for (Collect collect : collections) {
            String collectionName = findCollectionNameById(collect.getCollectionId(), collectionMappings);
            logger.debug("  - '{}' (ID: {})", collectionName, collect.getCollectionId());
        }
    }
    
    /**
     * Log desired collections for debugging
     */
    private void logDesiredCollections(List<Collect> collections, 
                                     Map<PredefinedCollection, CustomCollection> collectionMappings, 
                                     String label) {
        if (collections.isEmpty()) {
            logger.debug("📋 {}: None", label);
            return;
        }
        
        logger.debug("📋 {}: {} collections", label, collections.size());
        for (Collect collect : collections) {
            String collectionName = findCollectionNameById(collect.getCollectionId(), collectionMappings);
            logger.debug("  - '{}' (ID: {})", collectionName, collect.getCollectionId());
        }
    }
    
    /**
     * Add collections individually with detailed error handling
     * Used as fallback when bulk API fails
     */
    private void addCollectionsIndividually(List<Collect> collectsToAdd, 
                                          Map<PredefinedCollection, CustomCollection> collectionMappings) throws Exception {
        logger.debug("🔄 Adding {} collections individually", collectsToAdd.size());
        
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
    }
} 