package com.gw.domain;

/**
 * Enum defining all eBay metafield definitions
 * 
 * This enum serves as the single source of truth for eBay metafield definitions,
 * making it easy to add, remove, or modify metafield definitions.
 * 
 * Each enum value defines:
 * - key: The metafield key used in Shopify
 * - name: The display name in Shopify admin
 * - description: The description shown in Shopify admin
 * - type: The Shopify metafield type
 * 
 * Usage:
 * - Use EbayMetafieldDefinition.values() to get all definitions
 * - Use in validation methods to check structure
 * - Use in creation methods to generate metafields
 * 
 * @author jyuan
 */
public enum EbayMetafieldDefinition {
    
    BRAND("brand", "Brand", "Watch brand/manufacturer for eBay listings", "single_line_text_field"),
    MODEL("model", "Model", "Watch model for eBay listings", "single_line_text_field"),
    REFERENCE_NUMBER("reference_number", "Reference Number", "Manufacturer reference number for eBay listings", "single_line_text_field"),
    YEAR("year", "Year", "Year of manufacture for eBay listings", "single_line_text_field"),
    CASE_MATERIAL("case_material", "Case Material", "Case material for eBay listings", "single_line_text_field"),
    MOVEMENT("movement", "Movement", "Movement type for eBay listings", "single_line_text_field"),
    DIAL("dial", "Dial", "Dial information for eBay listings", "single_line_text_field"),
    STRAP("strap", "Strap/Bracelet", "Strap or bracelet information for eBay listings", "single_line_text_field"),
    CONDITION("condition", "Condition", "Watch condition for eBay listings", "single_line_text_field"),
    DIAMETER("diameter", "Case Diameter", "Case diameter for eBay listings", "single_line_text_field"),
    BOX_PAPERS("box_papers", "Box & Papers", "Box and papers information for eBay listings", "single_line_text_field"),
    CATEGORY("category", "Category", "Watch category for eBay listings", "single_line_text_field"),
    STYLE("style", "Style", "Watch style for eBay listings", "single_line_text_field");
    
    private final String key;
    private final String name;
    private final String description;
    private final String type;
    
    /**
     * Constructor for metafield definition
     * 
     * @param key The metafield key used in Shopify (e.g., "brand")
     * @param name The display name in Shopify admin (e.g., "Brand")
     * @param description The description shown in Shopify admin
     * @param type The Shopify metafield type (e.g., "single_line_text_field")
     */
    EbayMetafieldDefinition(String key, String name, String description, String type) {
        this.key = key;
        this.name = name;
        this.description = description;
        this.type = type;
    }
    
    /**
     * Get the metafield key used in Shopify
     * @return The metafield key (e.g., "brand")
     */
    public String getKey() {
        return key;
    }
    
    /**
     * Get the display name shown in Shopify admin
     * @return The display name (e.g., "Brand")
     */
    public String getName() {
        return name;
    }
    
    /**
     * Get the description shown in Shopify admin
     * @return The description
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * Get the Shopify metafield type
     * @return The metafield type (e.g., "single_line_text_field")
     */
    public String getType() {
        return type;
    }
    
    /**
     * Get all metafield keys as a sorted list
     * Useful for validation and comparison
     * @return Sorted list of all metafield keys
     */
    public static java.util.List<String> getAllKeys() {
        return java.util.Arrays.stream(values())
                .map(EbayMetafieldDefinition::getKey)
                .sorted()
                .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * Get the total count of eBay metafield definitions
     * @return The total number of metafield definitions
     */
    public static int getCount() {
        return values().length;
    }
    
    /**
     * Find a metafield definition by key
     * @param key The metafield key to search for
     * @return The metafield definition, or null if not found
     */
    public static EbayMetafieldDefinition findByKey(String key) {
        for (EbayMetafieldDefinition definition : values()) {
            if (definition.getKey().equals(key)) {
                return definition;
            }
        }
        return null;
    }
    
    /**
     * Check if a key exists in the metafield definitions
     * @param key The key to check
     * @return true if the key exists, false otherwise
     */
    public static boolean hasKey(String key) {
        return findByKey(key) != null;
    }
} 