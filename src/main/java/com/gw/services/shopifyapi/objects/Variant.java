package com.gw.services.shopifyapi.objects;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_EMPTY)
public class Variant {
    
    @JsonProperty("barcode")
    private String barcode;
    
    @JsonProperty("compare_at_price")
    private String compareAtPrice;
    
    @JsonProperty("created_at")
    private String createdAt;
    
    @JsonProperty("fulfillment_service")
    private String fulfillmentService;

    @JsonProperty("grams")
    private String grams;
    
    @JsonProperty("id")
    private String id;
    
    @JsonProperty("image_id")
    private String imageId;
    
    @JsonProperty("inventory_management")
    private String inventoryManagement;
    
    @JsonProperty("inventory_policy")
    private String inventoryPolicy;
    
    @JsonProperty("inventory_item_id")
    private String inventoryItemId;

    @JsonProperty("metafield")
    private Map<String, String> metafield;
    
    @JsonProperty("position")
    private String position;
    
    @JsonProperty("price")
    private String price;
    
    @JsonProperty("product_id")
    private String productId;
    
    @JsonProperty("requires_shipping")
    private String requiresShipping;
    
    @JsonProperty("sku")
    private String sku;
    
    @JsonProperty("taxable")
    private String taxable;
    
    @JsonProperty("title")
    private String title;
    
    @JsonProperty("updated_at")
    private String updatedAt;
    
    @JsonProperty("weight")
    private String weight;
    
    @JsonProperty("weight_unit")
    private String weightUnit;

    @JsonProperty("option1")
    private String option1;
    
    @JsonProperty("option2")
    private String option2;
    
    @JsonProperty("option3")
    private String option3;

    private InventoryLevels inventoryLevels;
    
    public String getBarcode() {
        return barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    public String getCompareAtPrice() {
        return compareAtPrice;
    }

    public void setCompareAtPrice(String compareAtPrice) {
        this.compareAtPrice = compareAtPrice;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getFulfillmentService() {
        return fulfillmentService;
    }

    public void setFulfillmentService(String fulfillmentService) {
        this.fulfillmentService = fulfillmentService;
    }

    public String getGrams() {
        return grams;
    }

    public void setGrams(String grams) {
        this.grams = grams;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getImageId() {
        return imageId;
    }

    public void setImageId(String imageId) {
        this.imageId = imageId;
    }

    public String getInventoryManagement() {
        return inventoryManagement;
    }

    public void setInventoryManagement(String inventoryManagement) {
        this.inventoryManagement = inventoryManagement;
    }

    public String getInventoryPolicy() {
        return inventoryPolicy;
    }

    public void setInventoryPolicy(String inventoryPolicy) {
        this.inventoryPolicy = inventoryPolicy;
    }
    

    public String getInventoryItemId() {
		return inventoryItemId;
	}

	public void setInventoryItemId(String inventoryItemId) {
		this.inventoryItemId = inventoryItemId;
	}

	public Map<String, String> getMetafield() {
        return metafield;
    }

    public void setMetafield(Map<String, String> metafield) {
        this.metafield = metafield;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public String getPrice() {
        return price;
    }

    public void setPrice(String price) {
        this.price = price;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getRequiresShipping() {
        return requiresShipping;
    }

    public void setRequiresShipping(String requiresShipping) {
        this.requiresShipping = requiresShipping;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getTaxable() {
        return taxable;
    }

    public void setTaxable(String taxable) {
        this.taxable = taxable;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getWeight() {
        return weight;
    }

    public void setWeight(String weight) {
        this.weight = weight;
    }

    public String getWeightUnit() {
        return weightUnit;
    }

    public void setWeightUnit(String weightUnit) {
        this.weightUnit = weightUnit;
    }

    public String getOption1() {
        return option1;
    }

    public void setOption1(String option1) {
        this.option1 = option1;
    }

    public String getOption2() {
        return option2;
    }

    public void setOption2(String option2) {
        this.option2 = option2;
    }

    public String getOption3() {
        return option3;
    }

    public void setOption3(String option3) {
        this.option3 = option3;
    }

	public InventoryLevels getInventoryLevels() {
		return inventoryLevels;
	}

	public void setInventoryLevels(InventoryLevels inventoryLevels) {
		this.inventoryLevels = inventoryLevels;
	}
    
}
                 