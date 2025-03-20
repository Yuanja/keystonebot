package com.gw.services.jomashop;

import java.util.Arrays;

public class CategoryPropertyData {
    public String greater_than, max_length, less_than;
    public Boolean multiple;
    public String[] values;
    
    @Override
    public String toString() {
        return "CategoryPropertyData [greater_than=" + greater_than + ", max_length=" + max_length + ", less_than="
                + less_than +", multiple=" + multiple + ", values=" + Arrays.toString(values) + "]";
    }
}
