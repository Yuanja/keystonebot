package com.gw.services.shopifyapi.objects;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_EMPTY)
public class Product {
    
    @JsonProperty("body_html")
    private String bodyHtml;
    
    @JsonProperty("created_at")
    private String createdAt;
 
    @JsonProperty("handle")
    private String handle;
    
    @JsonProperty("id")
    private String id;
    
    @JsonProperty("images")
    List<Image> images;
    
    @JsonProperty("product_type")
    private String productType;
    
    @JsonProperty("published_at")
    private String publishedAt;

    @JsonProperty("published_scope")
    private String publishedScope;
    
    @JsonProperty("tags")
    private String tags;
    
    @JsonProperty("title")
    private String title;
    
    @JsonProperty("metafields_global_title_tag")
    private String metafieldsGlobalTitleTag;
    
    @JsonProperty("metafields_global_description_tag")
    private String metafieldsGlobalDescriptionTag;
    
    @JsonProperty("updated_at")
    private String updatedAt;
    
    @JsonProperty("variants")
    private List<Variant> variants;
    
    @JsonProperty("vendor")
    private String vendor;
    
    @JsonProperty("metafields")
    private List<Metafield> metafields;
    
    @JsonProperty("options")
    private List<Option> options;

    public String getBodyHtml() {
        return bodyHtml;
    }

    public void setBodyHtml(String bodyHtml) {
        this.bodyHtml = bodyHtml;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getHandle() {
        return handle;
    }

    public void setHandle(String handle) {
        this.handle = handle;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List<Image> getImages() {
        return images;
    }

    public void setImages(List<Image> images) {
        this.images = images;
    }

    public String getProductType() {
        return productType;
    }

    public void setProductType(String productType) {
        this.productType = productType;
    }

    public String getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(String publishedAt) {
        this.publishedAt = publishedAt;
    }

    public String getPublishedScope() {
        return publishedScope;
    }

    public void setPublishedScope(String publishedScope) {
        this.publishedScope = publishedScope;
    }

    public String getVendor() {
        return vendor;
    }

    public void setVendor(String vendor) {
        this.vendor = vendor;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMetafieldsGlobalTitleTag() {
        return metafieldsGlobalTitleTag;
    }

    public void setMetafieldsGlobalTitleTag(String metafieldsGlobalTitleTag) {
        this.metafieldsGlobalTitleTag = metafieldsGlobalTitleTag;
    }

    public String getMetafieldsGlobalDescriptionTag() {
        return metafieldsGlobalDescriptionTag;
    }

    public void setMetafieldsGlobalDescriptionTag(String metafieldsGlobalDescriptionTag) {
        this.metafieldsGlobalDescriptionTag = metafieldsGlobalDescriptionTag;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<Variant> getVariants() {
        return variants;
    }

    public void setVariants(List<Variant> variants) {
        this.variants = variants;
    }
    
    public void addVariant(Variant variant) {
        if (variants == null)
            variants = new ArrayList<Variant> ();
        
        variants.add(variant);
    }

    public List<Metafield> getMetafields() {
        return metafields;
    }

    public void setMetafields(List<Metafield> metafields) {
        this.metafields = metafields;
    }

    public void addMetafield(Metafield field) {
        if (metafields == null) {
            metafields = new ArrayList<Metafield>();
        }
        metafields.add(field);
    }

    public List<Option> getOptions() {
        return options;
    }

    public void setOptions(List<Option> options) {
        this.options = options;
    }
    
    public void addOption(Option op) {
        if (options == null) {
            options = new ArrayList<Option>();
        }
        
        options.add(op);
    }
    
}
                 