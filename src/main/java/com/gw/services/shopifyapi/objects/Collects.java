package com.gw.services.shopifyapi.objects;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_EMPTY)
public class Collects {
    
    @JsonProperty("collects")
    private List<Collect> collects;
 
    public List<Collect> getList(){
        return collects;
    }
}
                 