package com.gw.services.shopifyapi.objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_EMPTY)
public class Collect {
    
    @JsonProperty("collection_id")
    private String collectionId;
    
    @JsonProperty("created_at")
    private String createdAt;
 
    @JsonProperty("id")
    private String id;
    
    @JsonProperty("product_id")
    private String productId;
    
    @JsonProperty("sort_value")
    private String sortValue;

    @JsonProperty("position")
    private String position;
    
    @JsonProperty("updated_at")
    private String updatedAt;

    public String getCollectionId() {
        return collectionId;
    }

    public void setCollectionId(String collectionId) {
        this.collectionId = collectionId;
    }

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

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getSortValue() {
        return sortValue;
    }

    public void setSortValue(String sortValue) {
        this.sortValue = sortValue;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((collectionId == null) ? 0 : collectionId.hashCode());
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        result = prime * result + ((productId == null) ? 0 : productId.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Collect other = (Collect)obj;
        if (collectionId == null) {
            if (other.collectionId != null)
                return false;
        }
        else if (!collectionId.equals(other.collectionId))
            return false;
        if (id == null) {
            if (other.id != null)
                return false;
        }
        else if (!id.equals(other.id))
            return false;
        if (productId == null) {
            if (other.productId != null)
                return false;
        }
        else if (!productId.equals(other.productId))
            return false;
        return true;
    }
}
                 