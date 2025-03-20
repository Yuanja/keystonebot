package com.gw.services.shopifyapi.objects;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_EMPTY)
public class Products {
    
    @JsonProperty("products")
    private List<Product> products;
    
    public List<Product> get() {
        return this.products;
    }
}
                 