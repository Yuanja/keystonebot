package com.gw.services.shopifyapi.objects;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_EMPTY)
public class InventoryLevels {
    
    @JsonProperty("inventory_levels")
    private List<InventoryLevel> inventoryLevels;
    
    public List<InventoryLevel> get() {
        return this.inventoryLevels;
    }
    
    public InventoryLevel getByLocationId(String locationId) {
    	if (inventoryLevels == null)
    		return null;
    	
    	if (locationId == null)
    		return null;
    	
    	for (InventoryLevel inv : inventoryLevels) {
    		if (locationId.contentEquals(inv.getLocationId()))
    			return inv;
    	}
    	return null;
    }
    
    public void addInventoryLevel(InventoryLevel invlevel) {
    	if (inventoryLevels==null){
    		inventoryLevels = new ArrayList<InventoryLevel>();
    	}
    	inventoryLevels.add(invlevel);
    }
}
                 