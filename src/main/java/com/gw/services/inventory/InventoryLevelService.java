package com.gw.services.inventory;

import com.gw.domain.FeedItem;
import com.gw.services.constants.ShopifyConstants;
import com.gw.services.shopifyapi.objects.InventoryLevel;
import com.gw.services.shopifyapi.objects.InventoryLevels;
import com.gw.services.shopifyapi.objects.Location;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service responsible for creating and managing inventory levels
 * Follows Single Responsibility Principle by focusing only on inventory concerns
 */
@Service
public class InventoryLevelService {
    
    private static final Logger logger = LoggerFactory.getLogger(InventoryLevelService.class);
    
    /**
     * Creates inventory levels for a feed item across all locations
     * CRITICAL: Total inventory across ALL locations = exactly 1 (not per location)
     * 
     * @param feedItem The feed item to create inventory for (source of truth)
     * @param locations List of all available locations
     * @return InventoryLevels object with distributed quantities (total=1)
     */
    public InventoryLevels createInventoryLevels(FeedItem feedItem, List<Location> locations) {
        logger.debug("Creating inventory levels for SKU: {}", feedItem.getWebTagNumber());
        
        InventoryLevels inventoryLevels = new InventoryLevels();
        String totalQuantity = determineAvailableQuantity(feedItem);
        boolean hasInventory = !totalQuantity.equals(ShopifyConstants.INVENTORY_SOLD);
        
        logger.debug("📦 FeedItem {} status: {} -> Total inventory: {}", 
            feedItem.getWebTagNumber(), feedItem.getWebStatus(), totalQuantity);
        
        // Distribute inventory across locations (total = exactly 1, not per location)
        for (int i = 0; i < locations.size(); i++) {
            Location location = locations.get(i);
            InventoryLevel inventoryLevel = new InventoryLevel();
            inventoryLevel.setLocationId(location.getId());
            
            // CRITICAL: Only first location gets inventory, others get 0
            // This ensures total inventory = exactly 1 (not multiplied by location count)
            String locationQuantity;
            if (hasInventory && i == 0) {
                locationQuantity = totalQuantity; // First location gets the inventory
            } else {
                locationQuantity = ShopifyConstants.INVENTORY_SOLD; // Others get 0
            }
            
            inventoryLevel.setAvailable(locationQuantity);
            inventoryLevels.addInventoryLevel(inventoryLevel);
            
            logger.debug("📍 Location[{}]: {} -> Quantity: {}", 
                i, location.getId(), locationQuantity);
        }
        
        logger.debug("✅ Created inventory distribution - Total across all locations: {}", totalQuantity);
        
        return inventoryLevels;
    }
    
    /**
     * Determines the available quantity based on the feed item's status
     * 
     * @param feedItem The feed item to check
     * @return "0" if sold, "1" if available
     */
    private String determineAvailableQuantity(FeedItem feedItem) {
        if (feedItem.getWebStatus() == null) {
            logger.warn("Feed item {} has null status, defaulting to available", 
                feedItem.getWebTagNumber());
            return ShopifyConstants.INVENTORY_AVAILABLE;
        }
        
        boolean isSold = feedItem.getWebStatus().equalsIgnoreCase(ShopifyConstants.FEED_STATUS_SOLD);
        String quantity = isSold ? ShopifyConstants.INVENTORY_SOLD : ShopifyConstants.INVENTORY_AVAILABLE;
        
        logger.debug("SKU: {} status: {} -> quantity: {}", 
            feedItem.getWebTagNumber(), feedItem.getWebStatus(), quantity);
        
        return quantity;
    }
    
    /**
     * Merges existing inventory levels with new ones, preserving inventory item IDs
     * 
     * @param existingInventoryLevels Current inventory levels from Shopify
     * @param newInventoryLevels New inventory levels from feed item
     */
    public void mergeInventoryLevels(InventoryLevels existingInventoryLevels, InventoryLevels newInventoryLevels) {
        logger.debug("=== MERGING INVENTORY LEVELS ===");
        
        if (!validateInventoryLevels(existingInventoryLevels, newInventoryLevels)) {
            return;
        }
        
        logger.debug("Existing inventory levels count: {}", existingInventoryLevels.get().size());
        logger.debug("New inventory levels count: {}", newInventoryLevels.get().size());
        
        for (InventoryLevel existingLevel : existingInventoryLevels.get()) {
            logger.debug("Processing existing inventory level - LocationId: {}, InventoryItemId: {}, Available: {}", 
                existingLevel.getLocationId(), existingLevel.getInventoryItemId(), existingLevel.getAvailable());
                
            InventoryLevel matchingNewLevel = newInventoryLevels.getByLocationId(existingLevel.getLocationId());
            if (matchingNewLevel != null) {
                logger.debug("✅ Found matching new inventory level for location: {}", existingLevel.getLocationId());
                matchingNewLevel.setInventoryItemId(existingLevel.getInventoryItemId());
            } else {
                logger.warn("⚠️ No matching new inventory level found for location: {}", existingLevel.getLocationId());
            }
        }
        
        logger.debug("=== END MERGING INVENTORY LEVELS ===");
    }
    
    /**
     * Validates that inventory levels are not null and properly initialized
     * 
     * @param existingInventoryLevels Existing levels to validate
     * @param newInventoryLevels New levels to validate
     * @return true if both are valid, false otherwise
     */
    private boolean validateInventoryLevels(InventoryLevels existingInventoryLevels, InventoryLevels newInventoryLevels) {
        if (existingInventoryLevels == null) {
            logger.error("❌ Existing inventory levels are NULL - this indicates a problem with GraphQL API retrieval");
            return false;
        }
        
        if (existingInventoryLevels.get() == null) {
            logger.error("❌ Existing inventory levels list is NULL - this indicates a REST vs GraphQL compatibility issue");
            return false;
        }
        
        if (newInventoryLevels == null || newInventoryLevels.get() == null) {
            logger.error("❌ New inventory levels are NULL - this indicates a problem with factory creation");
            return false;
        }
        
        return true;
    }
} 