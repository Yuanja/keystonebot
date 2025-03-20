package com.gw.services.shopifyapi.objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_EMPTY)
public class InventoryLevel {
    
    @JsonProperty("available")
    private String available;
    
    @JsonProperty("inventory_item_id")
    private String inventoryItemId;
    
    @JsonProperty("location_id")
    private String locationId;
    
    @JsonProperty("updated_at")
    private String updatedAt;

	public String getAvailable() {
		return available;
	}

	public void setAvailable(String available) {
		this.available = available;
	}

	public String getInventoryItemId() {
		return inventoryItemId;
	}

	public void setInventoryItemId(String inventoryItemId) {
		this.inventoryItemId = inventoryItemId;
	}

	public String getLocationId() {
		return locationId;
	}

	public void setLocationId(String locationId) {
		this.locationId = locationId;
	}

	public String getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(String updatedAt) {
		this.updatedAt = updatedAt;
	}
}
                 