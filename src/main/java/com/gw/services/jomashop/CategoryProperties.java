package com.gw.services.jomashop;

import java.util.Map;

import com.google.gson.annotations.Expose;

public class CategoryProperties {
    public String key, designation, name, instructions, kind;
    
    @Expose(serialize = false)
    public String feedItemField;
    
    @Expose(serialize = false)
    public String gwValue;
    
    public CategoryPropertyData data;
    
    @Expose(serialize = false)
    public Map<String, String> gwValueMap;
    
    @Expose(serialize = false)
    public String gwDefaultValue;
    
    @Override
    public String toString() {
        return "CategoryProperties [key=" + key + ", designation=" + designation + ", name=" + name + ", instructions="
                + instructions + ", kind=" + kind + ", data=" + data + "]";
    }
    
}
