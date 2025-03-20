package com.gw.services.shopifyapi.objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_EMPTY)
public class ProductVo {
    
    @JsonProperty("product")
    private Product product;
    
    public ProductVo(Product prod) {
        this.product = prod;
    }
    
    public ProductVo() {
    }

    public Product get() {
        return this.product;
    }
}
                 