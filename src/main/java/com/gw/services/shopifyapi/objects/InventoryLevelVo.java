package com.gw.services.shopifyapi.objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_EMPTY)
public class InventoryLevelVo {
    
    @JsonProperty("inventory_level")
    private InventoryLevel inventoryLevel;
    
    public InventoryLevelVo(InventoryLevel inventoryLevel) {
        this.inventoryLevel = inventoryLevel;
    }
    
    public InventoryLevelVo() {
    }

    public InventoryLevel get() {
        return this.inventoryLevel;
    }
}
                 