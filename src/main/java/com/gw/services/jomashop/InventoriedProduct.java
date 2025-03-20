package com.gw.services.jomashop;

public class InventoriedProduct {
    private Inventory inventory;
    private SingleProductResponse product;
    
    public InventoriedProduct (Inventory inv, SingleProductResponse spr) {
        this.inventory = inv;
        this.product = spr;
    }
    
    public String getSku() {
        return this.inventory.sku;
    }
    
    public Inventory getInventory() {
        return inventory;
    }
    
    public SingleProductResponse getProduct() {
        return product;
    }
}
