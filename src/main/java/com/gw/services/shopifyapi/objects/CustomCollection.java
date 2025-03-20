package com.gw.services.shopifyapi.objects;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_EMPTY)
public class CustomCollection {
    
    @JsonProperty("body_html")
    private String bodyHtml;
    
    @JsonProperty("handle")
    private String handle;
 
    @JsonProperty("image")
    private Image image;
    
    @JsonProperty("id")
    private String id;
    
    @JsonProperty("published")
    private String published;
    
    @JsonProperty("published_at")
    private String publishedAt;
    
    @JsonProperty("published_scope")
    private String publishedScope;

    @JsonProperty("sort_order")
    private String sortOrder;
    
    @JsonProperty("template_suffix")
    private String templateSuffix;
    
    @JsonProperty("title")
    private String title;
    
    @JsonProperty("updated_at")
    private String updatedAt;
    
    @JsonProperty("metafields")
    private List<Metafield> metafields;
    
    public String getBodyHtml() {
        return bodyHtml;
    }

    public void setBodyHtml(String bodyHtml) {
        this.bodyHtml = bodyHtml;
    }

    public String getHandle() {
        return handle;
    }

    public void setHandle(String handle) {
        this.handle = handle;
    }

    public Image getImage() {
        return image;
    }

    public void setImage(Image image) {
        this.image = image;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPublished() {
        return published;
    }

    public void setPublished(String published) {
        this.published = published;
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

    public String getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(String sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getTemplateSuffix() {
        return templateSuffix;
    }

    public void setTemplateSuffix(String templateSuffix) {
        this.templateSuffix = templateSuffix;
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

    public List<Metafield> getMetafields() {
        return metafields;
    }

    public void setMetafields(List<Metafield> metafields) {
        this.metafields = metafields;
    }
    
}
                 