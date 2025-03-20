package com.gw.services.shopifyapi.objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_EMPTY)
public class CollectVo {
    
    @JsonProperty("collect")
    private Collect collect;
    
    public CollectVo(Collect collect) {
        this.collect = collect;
    }
    
    public CollectVo() {
    }

    public Collect get() {
        return this.collect;
    }
}
                 