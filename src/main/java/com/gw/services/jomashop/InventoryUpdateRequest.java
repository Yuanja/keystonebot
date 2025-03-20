package com.gw.services.jomashop;


public class InventoryUpdateRequest {
    
    public Inventory inventory;
    
    public enum Status {
        
        Active("active"),
        Sold("out_of_stock"),
        Inactive("inactive");
        
        private String statusStringVal;
        
        public String getStringVal(){
            return this.statusStringVal;
        }
        
        Status(String status) {
            this.statusStringVal = status;
        }
    }
    
    public InventoryUpdateRequest(String inventoryStatus, String sku, int quantity, double price) {
        inventory = new Inventory();
        inventory.sku = sku;
        inventory.quantity = quantity;
        inventory.status = inventoryStatus;
        inventory.price = price;
    }
    
    public InventoryUpdateRequest(Status inventoryStatus, String sku, int quantity, double price) {
        inventory = new Inventory();
        inventory.sku = sku;
        inventory.quantity = quantity;
        inventory.status = inventoryStatus.statusStringVal;
        inventory.price = price;
    }

}