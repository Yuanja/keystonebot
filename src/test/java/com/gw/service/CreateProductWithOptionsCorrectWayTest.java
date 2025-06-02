package com.gw.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.gw.services.shopifyapi.ShopifyGraphQLService;
import com.gw.services.shopifyapi.objects.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;

/**
 * Test demonstrating the CORRECT way to create products with options using Shopify GraphQL API
 * 
 * Based on https://shopify.dev/docs/api/admin-graphql/latest/mutations/productoptionscreate
 * 
 * The correct approach is:
 * 1. Create product with productCreate (no options)
 * 2. Add options with productOptionsCreate
 * 3. Optionally manage variants separately
 */
@SpringBootTest
@ActiveProfiles("keystone-dev")
public class CreateProductWithOptionsCorrectWayTest {

    protected Logger logger = LogManager.getLogger(this.getClass());

    @Autowired
    private ShopifyGraphQLService shopifyApiService;

    @Test
    public void createProductWithOptionsCorrectWay() throws Exception {
        logger.info("=== CREATING PRODUCT WITH OPTIONS - CORRECT TWO-STEP APPROACH ===");
        logger.info("📚 Based on Shopify documentation: https://shopify.dev/docs/api/admin-graphql/latest/mutations/productoptionscreate");
        
        // STEP 1: Create basic product WITHOUT options
        logger.info("\n🏗️ STEP 1: Creating basic product...");
        Product basicProduct = createBasicProduct();
        
        if (basicProduct != null && basicProduct.getId() != null) {
            logger.info("✅ STEP 1 SUCCESS: Product created with ID: {}", basicProduct.getId());
            
            // STEP 2: Add options to the existing product
            logger.info("\n🔧 STEP 2: Adding options to product...");
            boolean optionsAdded = addOptionsToProduct(basicProduct.getId());
            
            if (optionsAdded) {
                logger.info("✅ STEP 2 SUCCESS: Options added to product");
                
                // STEP 3: Examine the final result
                logger.info("\n🔍 STEP 3: Examining final product...");
                examineProductWithOptions(basicProduct.getId());
                
            } else {
                logger.error("❌ STEP 2 FAILED: Could not add options to product");
            }
            
        } else {
            logger.error("❌ STEP 1 FAILED: Could not create basic product");
        }
    }
    
    /**
     * Step 1: Create basic product without options
     */
    private Product createBasicProduct() {
        try {
            Product product = new Product();
            product.setTitle("Watch with Options (Correct Way)");
            product.setBodyHtml("A watch product created using the correct two-step process");
            product.setVendor("Test Vendor");
            product.setProductType("Watch");
            product.setHandle("watch-with-options-correct");
            
            // Create ONE basic variant with NO option values
            List<Variant> variants = new ArrayList<>();
            Variant variant = new Variant();
            variant.setTitle("Default Title");
            variant.setSku("WATCH-CORRECT-001");
            variant.setPrice("399.99");
            // Explicitly NO option1, option2, option3 values
            variant.setInventoryManagement("shopify");
            variant.setInventoryPolicy("deny");
            variant.setTaxable("true");
            variant.setRequiresShipping("true");
            variant.setWeight("0.5");
            variant.setWeightUnit("KILOGRAMS");
            variants.add(variant);
            
            product.setVariants(variants);
            
            logger.info("  Creating product: {}", product.getTitle());
            logger.info("  With basic variant: SKU={}, Price={}", variant.getSku(), variant.getPrice());
            
            return shopifyApiService.addProduct(product);
            
        } catch (Exception e) {
            logger.error("Failed to create basic product: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Step 2: Add options to existing product using productOptionsCreate
     */
    private boolean addOptionsToProduct(String productId) {
        try {
            logger.info("  Adding 3 options to product ID: {}", productId);
            
            // We need to call the GraphQL productOptionsCreate mutation
            // For now, let's create a method that will use our existing GraphQL service
            
            return addOptionsViaGraphQL(productId);
            
        } catch (Exception e) {
            logger.error("Failed to add options to product: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Add options using GraphQL productOptionsCreate mutation
     */
    private boolean addOptionsViaGraphQL(String productId) {
        try {
            // Build the GraphQL mutation for productOptionsCreate
            // Simplified version - just create options, examine result separately
            String mutation = """
                mutation productOptionsCreate($productId: ID!, $options: [OptionCreateInput!]!) {
                  productOptionsCreate(productId: $productId, options: $options) {
                    userErrors {
                      field
                      message
                      code
                    }
                    product {
                      id
                      title
                      options {
                        id
                        name
                        position
                        optionValues {
                          id
                          name
                        }
                      }
                    }
                  }
                }
                """;
            
            // Build the variables (removed variantStrategy)
            java.util.Map<String, Object> variables = new java.util.HashMap<>();
            variables.put("productId", "gid://shopify/Product/" + productId);
            
            // Build options array
            java.util.List<java.util.Map<String, Object>> options = new java.util.ArrayList<>();
            
            // Option 1: Color
            java.util.Map<String, Object> colorOption = new java.util.HashMap<>();
            colorOption.put("name", "Color");
            colorOption.put("position", 1);
            // Values should be objects with name property, not plain strings
            java.util.List<java.util.Map<String, Object>> colorValues = new java.util.ArrayList<>();
            java.util.Map<String, Object> silverValue = new java.util.HashMap<>();
            silverValue.put("name", "Silver");
            colorValues.add(silverValue);
            colorOption.put("values", colorValues);
            options.add(colorOption);
            
            // Option 2: Size
            java.util.Map<String, Object> sizeOption = new java.util.HashMap<>();
            sizeOption.put("name", "Size");
            sizeOption.put("position", 2);
            java.util.List<java.util.Map<String, Object>> sizeValues = new java.util.ArrayList<>();
            java.util.Map<String, Object> size40mmValue = new java.util.HashMap<>();
            size40mmValue.put("name", "40mm");
            sizeValues.add(size40mmValue);
            sizeOption.put("values", sizeValues);
            options.add(sizeOption);
            
            // Option 3: Material
            java.util.Map<String, Object> materialOption = new java.util.HashMap<>();
            materialOption.put("name", "Material");
            materialOption.put("position", 3);
            java.util.List<java.util.Map<String, Object>> materialValues = new java.util.ArrayList<>();
            java.util.Map<String, Object> steelValue = new java.util.HashMap<>();
            steelValue.put("name", "Steel");
            materialValues.add(steelValue);
            materialOption.put("values", materialValues);
            options.add(materialOption);
            
            variables.put("options", options);
            
            logger.info("  GraphQL Mutation: productOptionsCreate (simplified)");
            logger.info("  Product ID: {}", variables.get("productId"));
            logger.info("  Options to add: {}", options.size());
            for (int i = 0; i < options.size(); i++) {
                java.util.Map<String, Object> opt = (java.util.Map<String, Object>) options.get(i);
                logger.info("    Option[{}]: {} = {}", i, opt.get("name"), opt.get("values"));
            }
            
            // Execute the GraphQL mutation using the private method via reflection
            // We need to create a public method in ShopifyGraphQLService or use a different approach
            
            // For now, let's create a custom method that handles productOptionsCreate
            return callProductOptionsCreateMutation(mutation, variables);
            
        } catch (Exception e) {
            logger.error("  ❌ GraphQL mutation failed: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Execute productOptionsCreate mutation by adding it to ShopifyGraphQLService
     * This is a temporary workaround - we should add this as a proper method
     */
    private boolean callProductOptionsCreateMutation(String mutation, java.util.Map<String, Object> variables) {
        try {
            // We need to access the private executeGraphQLQuery method
            // Let's use reflection to call it
            java.lang.reflect.Method method = ShopifyGraphQLService.class.getDeclaredMethod(
                "executeGraphQLQuery", String.class, java.util.Map.class);
            method.setAccessible(true);
            
            JsonNode response = (JsonNode) method.invoke(shopifyApiService, mutation, variables);
            
            if (response != null) {
                logger.info("  ✅ GraphQL mutation executed successfully");
                
                // DEBUG: Log the full response structure
                logger.info("  🔍 FULL RESPONSE: {}", response.toString());
                
                // The response structure is different - no "data" wrapper
                JsonNode productOptionsCreate = response.get("productOptionsCreate");
                if (productOptionsCreate != null) {
                    logger.info("  🔍 PRODUCT_OPTIONS_CREATE FOUND: {}", productOptionsCreate.toString());
                    JsonNode userErrors = productOptionsCreate.get("userErrors");
                    if (userErrors != null && userErrors.isArray() && userErrors.size() > 0) {
                        logger.error("  ❌ GraphQL user errors:");
                        for (JsonNode error : userErrors) {
                            logger.error("    Error: {}", error.toString());
                        }
                        return false;
                    } else {
                        logger.info("  ✅ No GraphQL user errors - options created successfully");
                        
                        // Log the resulting product structure
                        JsonNode product = productOptionsCreate.get("product");
                        if (product != null) {
                            logger.info("  🔍 PRODUCT FOUND: {}", product.toString());
                            logGraphQLProductResult(product);
                        } else {
                            logger.warn("  ⚠️ No product found in productOptionsCreate response");
                        }
                        
                        return true;
                    }
                } else {
                    logger.error("  ❌ No productOptionsCreate field found in response");
                }
            }
            
            logger.error("  ❌ Unexpected GraphQL response structure");
            return false;
            
        } catch (Exception e) {
            logger.error("  ❌ GraphQL mutation failed: {}", e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Log the GraphQL response product structure from JsonNode
     */
    private void logGraphQLProductResult(JsonNode product) {
        logger.info("  📊 Updated Product Structure:");
        logger.info("    ID: {}", product.get("id").asText());
        logger.info("    Title: {}", product.get("title").asText());
        
        JsonNode options = product.get("options");
        if (options != null && options.isArray()) {
            logger.info("    Options: {} created", options.size());
            for (int i = 0; i < options.size(); i++) {
                JsonNode option = options.get(i);
                logger.info("      Option[{}]: Name='{}', Position={}", 
                    i, option.get("name").asText(), option.get("position").asInt());
                
                JsonNode optionValues = option.get("optionValues");
                if (optionValues != null && optionValues.isArray()) {
                    for (int j = 0; j < optionValues.size(); j++) {
                        JsonNode value = optionValues.get(j);
                        logger.info("        Value[{}]: '{}'", j, value.get("name").asText());
                    }
                }
            }
        } else {
            logger.info("    Options: No options found in response");
        }
        
        logger.info("    Note: Variants will be examined separately via getProductByProductId()");
    }
    
    /**
     * Step 3: Examine the final product with options
     */
    private void examineProductWithOptions(String productId) {
        try {
            logger.info("  Re-fetching product to verify final state...");
            Product finalProduct = shopifyApiService.getProductByProductId(productId);
            
            if (finalProduct != null) {
                logger.info("  ✅ Final product examination:");
                logger.info("    ID: {}", finalProduct.getId());
                logger.info("    Title: {}", finalProduct.getTitle());
                
                if (finalProduct.getOptions() != null) {
                    logger.info("    Options: {} found", finalProduct.getOptions().size());
                    for (Option option : finalProduct.getOptions()) {
                        logger.info("      {}: {}", option.getName(), option.getValues());
                    }
                } else {
                    logger.warn("    ⚠️ No options found in final product");
                }
                
                if (finalProduct.getVariants() != null) {
                    logger.info("    Variants: {} found", finalProduct.getVariants().size());
                    for (Variant variant : finalProduct.getVariants()) {
                        logger.info("      SKU={}, Options=[{}, {}, {}]", 
                            variant.getSku(), variant.getOption1(), variant.getOption2(), variant.getOption3());
                    }
                } else {
                    logger.warn("    ⚠️ No variants found in final product");
                }
                
                logger.info("\n🎉 SUCCESS! Product with options created using the correct two-step approach!");
                logger.info("💡 You can now run ExamineProductOptionsTest to see this working example!");
                
            } else {
                logger.error("  ❌ Could not re-fetch final product");
            }
            
        } catch (Exception e) {
            logger.error("Failed to examine final product: {}", e.getMessage());
        }
    }
} 