package com.gw.services.sync;

import com.gw.domain.FeedItem;
import com.gw.services.shopifyapi.ShopifyGraphQLService;
import com.gw.services.shopifyapi.objects.InventoryLevel;
import com.gw.services.shopifyapi.objects.InventoryLevels;
import com.gw.services.shopifyapi.objects.Product;
import com.gw.services.shopifyapi.objects.Variant;
import com.gw.services.inventory.InventoryLevelService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.ArrayList;

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
    private InventoryLevelService inventoryLevelService;
    
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
            inventoryLevelService.mergeInventoryLevels(levels, 
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

    /**
     * Handle inventory updates based on status changes (SOLD vs Available)
     * This is the main entry point for inventory management during updates
     * 
     * @param item The feed item with potentially changed status
     * @param existingProduct The current product in Shopify
     * @throws Exception if inventory update fails
     */
    public void handleInventoryStatusChange(FeedItem item, Product existingProduct) throws Exception {
        logger.debug("🔍 Checking inventory status change for SKU: {}", item.getWebTagNumber());
        
        try {
            if (!hasInventoryStatusChanged(item, existingProduct)) {
                logger.debug("No inventory status change detected for SKU: {}", item.getWebTagNumber());
                return;
            }
            
            logger.info("📦 Inventory status change detected for SKU: {} -> {}", 
                item.getWebTagNumber(), item.getWebStatus());
            
            // Update inventory based on new status
            updateInventoryForStatusChange(item, existingProduct);
            
        } catch (Exception e) {
            logger.error("❌ Failed to handle inventory status change for SKU: {}", item.getWebTagNumber(), e);
            throw e;
        }
    }

    /**
     * Determine if the feed item's status represents a change in inventory levels
     * 
     * @param item The feed item with current status
     * @param existingProduct The existing product to compare against
     * @return true if inventory should be updated based on status change
     */
    private boolean hasInventoryStatusChanged(FeedItem item, Product existingProduct) {
        if (existingProduct.getVariants() == null || existingProduct.getVariants().isEmpty()) {
            logger.warn("No variants found in existing product for status comparison");
            return false;
        }
        
        // Get current inventory level
        String currentInventory = getCurrentInventoryLevel(existingProduct.getVariants().get(0));
        
        // Determine what inventory should be based on feed status
        String expectedInventory = determineInventoryFromStatus(item.getWebStatus());
        
        boolean hasChanged = !expectedInventory.equals(currentInventory);
        
        if (hasChanged) {
            logger.info("📊 Status change detected: Current inventory={}, Expected inventory={} for status='{}'", 
                currentInventory, expectedInventory, item.getWebStatus());
        }
        
        return hasChanged;
    }

    /**
     * Get the current inventory level from a variant
     */
    private String getCurrentInventoryLevel(Variant variant) {
        if (variant.getInventoryLevels() == null || variant.getInventoryLevels().get() == null) {
            logger.debug("No inventory levels found, assuming 0");
            return "0";
        }
        
        // Sum all inventory across locations
        int totalInventory = 0;
        for (InventoryLevel level : variant.getInventoryLevels().get()) {
            if (level.getAvailable() != null) {
                try {
                    totalInventory += Integer.parseInt(level.getAvailable());
                } catch (NumberFormatException e) {
                    logger.warn("Could not parse inventory level: {}", level.getAvailable());
                }
            }
        }
        
        return String.valueOf(totalInventory);
    }

    /**
     * Determine what inventory level should be based on feed status
     */
    private String determineInventoryFromStatus(String webStatus) {
        if (webStatus == null) {
            logger.debug("Null status, defaulting to available inventory");
            return "1";
        }
        
        boolean isSold = webStatus.equalsIgnoreCase("SOLD");
        String inventory = isSold ? "0" : "1";
        
        logger.debug("Status '{}' -> inventory '{}'", webStatus, inventory);
        return inventory;
    }

    /**
     * Update inventory for a status change (e.g., Available -> SOLD)
     */
    private void updateInventoryForStatusChange(FeedItem item, Product existingProduct) throws Exception {
        if (existingProduct.getVariants() == null || existingProduct.getVariants().isEmpty()) {
            logger.warn("No variants found for inventory status update");
            return;
        }
        
        Variant variant = existingProduct.getVariants().get(0);
        String newQuantity = determineInventoryFromStatus(item.getWebStatus());
        
        logger.info("🔄 Updating inventory for SKU: {} to quantity: {} (status: {})", 
            item.getWebTagNumber(), newQuantity, item.getWebStatus());
        
        updateVariantInventoryQuantity(variant, newQuantity);
    }

    /**
     * Update the inventory quantity for a specific variant
     */
    private void updateVariantInventoryQuantity(Variant variant, String newQuantity) throws Exception {
        if (variant.getInventoryLevels() == null || variant.getInventoryLevels().get() == null) {
            logger.warn("No inventory levels found for quantity update");
            return;
        }
        
        // Update all inventory levels to the new quantity
        List<InventoryLevel> levelsToUpdate = new ArrayList<>();
        for (InventoryLevel level : variant.getInventoryLevels().get()) {
            level.setAvailable(newQuantity);
            levelsToUpdate.add(level);
        }
        
        // Update on Shopify
        if (validateInventoryLevels(levelsToUpdate)) {
            shopifyGraphQLService.updateInventoryLevels(levelsToUpdate);
            logger.info("✅ Updated inventory levels to {} for {} locations", 
                newQuantity, levelsToUpdate.size());
        } else {
            logger.warn("⚠️ Skipping inventory update due to invalid inventory level data");
        }
    }
} 