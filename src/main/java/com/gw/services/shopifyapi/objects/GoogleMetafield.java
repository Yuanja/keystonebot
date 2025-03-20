package com.gw.services.shopifyapi.objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_EMPTY)
public class GoogleMetafield extends Metafield {

    public GoogleMetafield(String key, String value) {
        super();
        setNamespace("google");
        setKey(key);
        setValue(value);
        setType("single_line_text_field");
    }
}
