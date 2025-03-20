package com.gw.services.shopifyapi.objects;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_EMPTY)
public class Image {
    
    @JsonProperty("created_at")
    private String createdAt;
    
    @JsonProperty("id")
    private String id;
    
    @JsonProperty("position")
    private String position;
    
    @JsonProperty("product_id")
    private String productId;
    
    @JsonProperty("variant_ids")
    private String[] variantIds;
    
    @JsonProperty("src")
    private String src;
    
    @JsonProperty("updated_at")
    private String updatedAt;
    
    @JsonProperty("metafields")
    private List<Metafield>metafields;

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String[] getVariantIds() {
        return variantIds;
    }

    public void setVariantIds(String[] variantIds) {
        this.variantIds = variantIds;
    }

    public String getSrc() {
        return src;
    }

    public void setSrc(String src) {
        this.src = src;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    public List<Metafield> getMetafields() {
        return metafields;
    }

    public void setMetafields(List<Metafield> metafields) {
        this.metafields = metafields;
    }

    /**
     * helper method to set alt tags
     * 
     */
    public void addAltTag(String altTextString) {
        Metafield altTag = new Metafield();
        altTag.setNamespace("tags");
        altTag.setKey("alt");
        altTag.setValue(altTextString);
        altTag.setType("single_line_text_field");
        
        if (metafields == null )
            metafields = new ArrayList<Metafield>();
        
        metafields.add(altTag);
    }
}
                 