package com.gw.services.shopifyapi.objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_EMPTY)
public class Metafield {
    
    @JsonProperty("id")
    private String id;

    @JsonProperty("owner_id")
    private String ownerId;
    
    @JsonProperty("owner_resource")
    private String ownerResource;
    
    @JsonProperty("description")
    private String description;
    
    @JsonProperty("namespace")
    private String namespace;
    
    @JsonProperty("key")
    private String key;
    
    @JsonProperty("value")
    private String value;
    
    @JsonProperty("type")
    private String type;
    
    @JsonProperty("update_at")
    private String updateAt;
    
    @JsonProperty("created_at")
    private String createdAt;
    
    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getOwnerResource() {
        return ownerResource;
    }

    public void setOwnerResource(String ownerResource) {
        this.ownerResource = ownerResource;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getUpdateAt() {
        return updateAt;
    }

    public void setUpdateAt(String updateAt) {
        this.updateAt = updateAt;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
    
    
}
