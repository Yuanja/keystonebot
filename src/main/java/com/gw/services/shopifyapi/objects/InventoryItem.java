package com.gw.services.shopifyapi.objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_EMPTY)
public class InventoryItem {
    
    @JsonProperty("cost")
    private String cost;
    
    @JsonProperty("country_code_of_origin")
    private String countryCodeOfOrigin;
    
    @JsonProperty("country_harmonized_system_codes")
    private String countryHarmonizedSystemCodes;
    
    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("harmonized_system_code")
    private String harmonizedSystemCode;
    
    @JsonProperty("id")
    private String id;
    
    @JsonProperty("province_code_of_origin")
    private String provinceCodeOfOrigin;
    
    @JsonProperty("sku")
    private String sku;
    
    @JsonProperty("tracked")
    private String tracked;
    
    @JsonProperty("updated_at")
    private String updatedAt;
    
    @JsonProperty("requires_shipping")
    private String requiresShipping;

	public String getCost() {
		return cost;
	}

	public void setCost(String cost) {
		this.cost = cost;
	}

	public String getCountryCodeOfOrigin() {
		return countryCodeOfOrigin;
	}

	public void setCountryCodeOfOrigin(String countryCodeOfOrigin) {
		this.countryCodeOfOrigin = countryCodeOfOrigin;
	}

	public String getCountryHarmonizedSystemCodes() {
		return countryHarmonizedSystemCodes;
	}

	public void setCountryHarmonizedSystemCodes(String countryHarmonizedSystemCodes) {
		this.countryHarmonizedSystemCodes = countryHarmonizedSystemCodes;
	}

	public String getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(String createdAt) {
		this.createdAt = createdAt;
	}

	public String getHarmonizedSystemCode() {
		return harmonizedSystemCode;
	}

	public void setHarmonizedSystemCode(String harmonizedSystemCode) {
		this.harmonizedSystemCode = harmonizedSystemCode;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getProvinceCodeOfOrigin() {
		return provinceCodeOfOrigin;
	}

	public void setProvinceCodeOfOrigin(String provinceCodeOfOrigin) {
		this.provinceCodeOfOrigin = provinceCodeOfOrigin;
	}

	public String getSku() {
		return sku;
	}

	public void setSku(String sku) {
		this.sku = sku;
	}

	public String getTracked() {
		return tracked;
	}

	public void setTracked(String tracked) {
		this.tracked = tracked;
	}

	public String getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(String updatedAt) {
		this.updatedAt = updatedAt;
	}

	public String getRequiresShipping() {
		return requiresShipping;
	}

	public void setRequiresShipping(String requiresShipping) {
		this.requiresShipping = requiresShipping;
	}

}
                 