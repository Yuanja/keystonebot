package com.gw.domain;

import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Lob;
import jakarta.persistence.MappedSuperclass;

@MappedSuperclass
public class BaseListedItem {
    
    public static final String STATUS_NEW_WAITING_PUBLISH = "NEW_WAITING_PUBLISH";
    public static final String STATUS_CHANGED_WAITING_UPDATE = "CHANGED_WAITING_UPDATE";
    public static final String STATUS_PUBLISHED= "PUBLISHED";
    public static final String STATUS_PUBLISHED_FAILED = "PUBLISH_FAILED";
    public static final String STATUS_UPDATED = "UPDATED";
    public static final String STATUS_UPDATE_FAILED = "UPDATE_FAILED";
    public static final String STATUS_DELETED_FROM_FEED = "DELETED";
    
    public static final String EBAY_STATUS_END_ITEM_FAILED = "END_ITEM_FAILED";
    public static final String EBAY_STATUS_ITEM_ENDED= "ITEM_ENDED";
    
    private String cssHostingBaseUrl;
    
    @Column
    private Date lastUpdatedDate;
    @Column
    private String status;
    @Column
    private String ebayItemId;
    @Column 
    private Date ebayItemEndDate;
    @Column
    private Double ebayFees;
    
    @Column
    private String shopifyItemId;
    
    @Column 
    private Date publishedDate;
    
    @Column
    @Lob
    private String systemMessages;
    
    public String getCssHostingBaseUrl() {
        return cssHostingBaseUrl;
    }
    public void setCssHostingBaseUrl(String cssHostingBaseUrl) {
        this.cssHostingBaseUrl = cssHostingBaseUrl;
    }
    public Date getLastUpdatedDate() {
        return lastUpdatedDate;
    }
    public void setLastUpdatedDate(Date lastUpdatedDate) {
        this.lastUpdatedDate = lastUpdatedDate;
    }
    public String getStatus() {
        return status;
    }
    public void setStatus(String status) {
        this.status = status;
    }
    public String getShopifyItemId() {
        return shopifyItemId;
    }
    public void setShopifyItemId(String shopifyItemId) {
        this.shopifyItemId = shopifyItemId;
    }
    public Date getPublishedDate() {
        return publishedDate;
    }
    public void setPublishedDate(Date publishedDate) {
        this.publishedDate = publishedDate;
    }
    public String getSystemMessages() {
        return systemMessages;
    }
    public void setSystemMessages(String systemMessages) {
        this.systemMessages = systemMessages;
    }
    public String getEbayItemId() {
        return ebayItemId;
    }
    public void setEbayItemId(String ebayItemId) {
        this.ebayItemId = ebayItemId;
    }
    public Date getEbayItemEndDate() {
        return ebayItemEndDate;
    }
    public void setEbayItemEndDate(Date ebayItemEndDate) {
        this.ebayItemEndDate = ebayItemEndDate;
    }
    public Double getEbayFees() {
        return ebayFees;
    }
    public void setEbayFees(Double ebayFees) {
        this.ebayFees = ebayFees;
    }
}
