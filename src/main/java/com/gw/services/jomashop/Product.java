package com.gw.services.jomashop;

import java.util.Map;

import com.google.gson.annotations.Expose;

public class Product {
    public String name, category, brand, manufacturer_number, sku, jomashop_sku;
    public Map<String, String> properties;
    public String[] images;
    
    @Expose(serialize = false)
    public boolean hasErrors;
    
    @Expose(serialize = false)
    private String errorMsg;
    
    public void addErrorMsg(String msg) {
        hasErrors = true;
        if (errorMsg == null) {
            errorMsg = msg += "\n";
        } else {
            errorMsg += msg + "\n";
        }
    }
    
    public String getErrorMsg() {
        return errorMsg;
    }
}

