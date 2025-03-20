package com.gw.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * POJO representing a row in the BONETA.csv file
 */
public class BonetaCsvEntry {
    // Use JsonProperty to explicitly map the CSV column headers
    private String itemId;
    private String condition;
    private String brand;
    private String item;
    private String modelId;
    private String material;
    private String webDescription;
    private String box;
    private String papers;
    private String listPrice;
    private String priceLevel1;
    private String itemImage;
    private String textJoin;
    private String description;

    @JsonProperty("Item ID")
    public String getItemId() { return itemId; }
    
    @JsonProperty("Item ID")
    public void setItemId(String itemId) { this.itemId = itemId; }
    
    @JsonProperty("Condition")
    public String getCondition() { return condition; }
    
    @JsonProperty("Condition")
    public void setCondition(String condition) { this.condition = condition; }
    
    @JsonProperty("Brand")
    public String getBrand() { return brand; }
    
    @JsonProperty("Brand")
    public void setBrand(String brand) { this.brand = brand; }
    
    @JsonProperty("Item")
    public String getItem() { return item; }
    
    @JsonProperty("Item")
    public void setItem(String item) { this.item = item; }
    
    @JsonProperty("Model ID")
    public String getModelId() { return modelId; }
    
    @JsonProperty("Model ID")
    public void setModelId(String modelId) { this.modelId = modelId; }
    
    @JsonProperty("Material")
    public String getMaterial() { return material; }
    
    @JsonProperty("Material")
    public void setMaterial(String material) { this.material = material; }
    
    @JsonProperty("Web Description")
    public String getWebDescription() { return webDescription; }
    
    @JsonProperty("Web Description")
    public void setWebDescription(String webDescription) { this.webDescription = webDescription; }
    
    @JsonProperty("Box")
    public String getBox() { return box; }
    
    @JsonProperty("Box")
    public void setBox(String box) { this.box = box; }
    
    @JsonProperty("Papers")
    public String getPapers() { return papers; }
    
    @JsonProperty("Papers")
    public void setPapers(String papers) { this.papers = papers; }
    
    @JsonProperty("List Price (NI)")
    public String getListPrice() { return listPrice; }
    
    @JsonProperty("List Price (NI)")
    public void setListPrice(String listPrice) { this.listPrice = listPrice; }
    
    @JsonProperty("Price Level 1")
    public String getPriceLevel1() { return priceLevel1; }
    
    @JsonProperty("Price Level 1")
    public void setPriceLevel1(String priceLevel1) { this.priceLevel1 = priceLevel1; }
    
    @JsonProperty("Item Image")
    public String getItemImage() { return itemImage; }
    
    @JsonProperty("Item Image")
    public void setItemImage(String itemImage) { this.itemImage = itemImage; }
    
    @JsonProperty("TEXT JOIN")
    public String getTextJoin() { return textJoin; }
    
    @JsonProperty("TEXT JOIN")
    public void setTextJoin(String textJoin) { this.textJoin = textJoin; }

    @JsonProperty("Description")
    public String getDescription() { return description; }
    
    @JsonProperty("Description")
    public void setDescription(String fullDescription) { this.description = fullDescription; }
} 