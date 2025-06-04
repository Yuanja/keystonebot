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
                shopifyGraphQLService.addProductAndCollectionsAssociations(collectsToAdd);
                logger.debug("Added product to {} collections", collectsToAdd.size());
            } else {
                logger.debug("No collections found for product SKU: {}", item.getWebTagNumber());
            }
        } catch (Exception e) {
            logger.error("Failed to update collections for SKU: {}", item.getWebTagNumber(), e);
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
                shopifyGraphQLService.addProductAndCollectionsAssociations(collectsToAdd);
                logger.debug("✅ Added product {} to {} collections", productId, collectsToAdd.size());
            } else {
                logger.debug("⚠️ No collections found for product SKU: {}", item.getWebTagNumber());
            }
        } catch (Exception e) {
            logger.error("❌ Failed to update collections for SKU: {} with product ID: {}", item.getWebTagNumber(), productId, e);
            throw e;
        }
    }
} 