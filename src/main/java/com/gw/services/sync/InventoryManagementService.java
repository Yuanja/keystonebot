package com.gw.services.sync;

import com.gw.domain.FeedItem;
import com.gw.services.IShopifyProductFactory;
import com.gw.services.shopifyapi.ShopifyGraphQLService;
import com.gw.services.shopifyapi.objects.InventoryLevel;
import com.gw.services.shopifyapi.objects.InventoryLevels;
import com.gw.services.shopifyapi.objects.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Reusable Inventory Management Service
 * 
 * Handles all inventory-related operations:
 * - Inventory level updates
 * - Validation and error handling
 * - Integration with Shopify GraphQL API
 * 
 * Benefits:
 * - Centralized inventory logic
 * - Reusable across sync operations
 * - Clean error handling with validation
 */
@Service
public class InventoryManagementService {
    
    private static final Logger logger = LoggerFactory.getLogger(InventoryManagementService.class);
    
    @Autowired
    private ShopifyGraphQLService shopifyGraphQLService;
    
    @Autowired
    private IShopifyProductFactory shopifyProductFactoryService;
    
    /**
     * Update inventory after product update
     */
    public void updateInventoryAfterProductUpdate(FeedItem item, Product updatedProduct) throws Exception {
        try {
            // Get refreshed product data from Shopify
            Product refreshedProduct = shopifyGraphQLService.getProductByProductId(updatedProduct.getId());
            if (refreshedProduct == null) {
                logger.warn("Could not fetch refreshed product data for inventory update: {}", updatedProduct.getId());
                return;
            }
            
            updateInventoryWithRefreshedData(refreshedProduct, updatedProduct);
            
        } catch (Exception e) {
            logger.error("Failed to update inventory for SKU: {}", item.getWebTagNumber(), e);
            throw e;
        }
    }
    
    /**
     * Update inventory with refreshed product data
     */
    private void updateInventoryWithRefreshedData(Product refreshedProduct, Product updatedProduct) throws Exception {
        if (refreshedProduct.getVariants() == null || refreshedProduct.getVariants().isEmpty()) {
            logger.warn("No variants found in refreshed product data for inventory update");
            return;
        }
        
        String inventoryItemId = refreshedProduct.getVariants().get(0).getInventoryItemId();
        if (inventoryItemId == null) {
            logger.warn("No inventory item ID found for inventory update");
            return;
        }
        
        // Get current inventory levels from Shopify
        List<InventoryLevel> levelsList = shopifyGraphQLService.getInventoryLevelByInventoryItemId(inventoryItemId);
        
        // Convert to wrapper for compatibility
        InventoryLevels levels = new InventoryLevels();
        for (InventoryLevel level : levelsList) {
            levels.addInventoryLevel(level);
        }
        
        // Merge with updated product inventory data
        if (updatedProduct.getVariants() != null && !updatedProduct.getVariants().isEmpty()) {
            shopifyProductFactoryService.mergeInventoryLevels(levels, 
                updatedProduct.getVariants().get(0).getInventoryLevels());
            
            // Update inventory levels on Shopify
            List<InventoryLevel> inventoryLevelsToUpdate = updatedProduct.getVariants().get(0).getInventoryLevels().get();
            if (validateInventoryLevels(inventoryLevelsToUpdate)) {
                shopifyGraphQLService.updateInventoryLevels(inventoryLevelsToUpdate);
                logger.debug("Successfully updated inventory levels for {} locations", inventoryLevelsToUpdate.size());
            } else {
                logger.warn("Skipping inventory update due to invalid inventory level data");
            }
        }
    }
    
    /**
     * Validate inventory levels before update
     */
    private boolean validateInventoryLevels(List<InventoryLevel> inventoryLevels) {
        if (inventoryLevels == null || inventoryLevels.isEmpty()) {
            logger.warn("No inventory levels found to update");
            return false;
        }
        
        boolean allValid = true;
        for (InventoryLevel level : inventoryLevels) {
            if (level.getInventoryItemId() == null || level.getLocationId() == null || level.getAvailable() == null) {
                logger.error("Invalid inventory level detected - InventoryItemId: {}, LocationId: {}, Available: {}", 
                    level.getInventoryItemId(), level.getLocationId(), level.getAvailable());
                allValid = false;
            }
        }
        
        if (!allValid) {
            logger.error("Some inventory levels have invalid data - skipping update");
        }
        
        return allValid;
    }
} 