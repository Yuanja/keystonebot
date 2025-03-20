package com.gw.services.shopifyapi.objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_EMPTY)
public class CustomCollectionVo {
    
    @JsonProperty("custom_collection")
    private CustomCollection customCollection;
 
    public CustomCollectionVo() {};
    
    public CustomCollectionVo(CustomCollection inCustomCollection) {
        this.customCollection = inCustomCollection;
    }
    
    public CustomCollection get(){
        return customCollection;
    }
}
                 