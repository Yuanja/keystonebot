package com.gw.service;

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
 * Test to create a simple product with options for analysis
 * This will create a real product in Shopify that we can then examine
 */
@SpringBootTest
@ActiveProfiles("keystone-dev")
public class CreateTestProductWithOptionsTest {

    protected Logger logger = LogManager.getLogger(this.getClass());

    @Autowired
    private ShopifyGraphQLService shopifyApiService;

    @Test
    public void createSimpleProductWithOptions() throws Exception {
        logger.info("=== CREATING TEST PRODUCT WITH OPTIONS ===");
        logger.info("📝 This will create a real product in Shopify for analysis");
        
        // Create a simple product with 3 options and 1 variant
        Product product = new Product();
        product.setTitle("Test Watch with Options");
        product.setBodyHtml("A test watch product to examine how options work in Shopify");
        product.setVendor("Test Vendor");
        product.setProductType("Watch");
        product.setHandle("test-watch-with-options");
        
        // Define 3 options
        List<Option> options = new ArrayList<>();
        
        // Option 1: Color
        Option colorOption = new Option();
        colorOption.setName("Color");
        colorOption.setPosition("1");
        List<String> colorValues = new ArrayList<>();
        colorValues.add("Silver");
        colorOption.setValues(colorValues);
        options.add(colorOption);
        
        // Option 2: Size
        Option sizeOption = new Option();
        sizeOption.setName("Size");
        sizeOption.setPosition("2");
        List<String> sizeValues = new ArrayList<>();
        sizeValues.add("40mm");
        sizeOption.setValues(sizeValues);
        options.add(sizeOption);
        
        // Option 3: Material
        Option materialOption = new Option();
        materialOption.setName("Material");
        materialOption.setPosition("3");
        List<String> materialValues = new ArrayList<>();
        materialValues.add("Steel");
        materialOption.setValues(materialValues);
        options.add(materialOption);
        
        product.setOptions(options);
        
        // Create 1 variant that uses all 3 option values
        List<Variant> variants = new ArrayList<>();
        Variant variant = new Variant();
        variant.setTitle("Silver / 40mm / Steel");
        variant.setSku("TEST-WATCH-001");
        variant.setPrice("299.99");
        variant.setOption1("Silver");    // Must match Color option value
        variant.setOption2("40mm");      // Must match Size option value
        variant.setOption3("Steel");     // Must match Material option value
        variant.setInventoryManagement("shopify");
        variant.setInventoryPolicy("deny");
        variant.setTaxable("true");
        variant.setRequiresShipping("true");
        variant.setWeight("0.5");
        variant.setWeightUnit("KILOGRAMS");
        variants.add(variant);
        
        product.setVariants(variants);
        
        logger.info("🔧 Product structure to create:");
        logger.info("  Title: {}", product.getTitle());
        logger.info("  Options: {}", options.size());
        for (Option option : options) {
            logger.info("    {}: {}", option.getName(), option.getValues());
        }
        logger.info("  Variants: {}", variants.size());
        logger.info("    Variant: SKU={}, Options=[{}, {}, {}]", 
            variant.getSku(), variant.getOption1(), variant.getOption2(), variant.getOption3());
        
        try {
            // Attempt to create the product
            logger.info("🚀 Creating product in Shopify...");
            Product createdProduct = shopifyApiService.addProduct(product);
            
            if (createdProduct != null) {
                logger.info("✅ SUCCESS! Product created with ID: {}", createdProduct.getId());
                logger.info("📊 Created product details:");
                logger.info("  ID: {}", createdProduct.getId());
                logger.info("  Title: {}", createdProduct.getTitle());
                logger.info("  Handle: {}", createdProduct.getHandle());
                
                if (createdProduct.getOptions() != null) {
                    logger.info("  Options: {}", createdProduct.getOptions().size());
                    for (Option option : createdProduct.getOptions()) {
                        logger.info("    {}: {}", option.getName(), option.getValues());
                    }
                }
                
                if (createdProduct.getVariants() != null) {
                    logger.info("  Variants: {}", createdProduct.getVariants().size());
                    for (Variant v : createdProduct.getVariants()) {
                        logger.info("    ID={}, SKU={}, Options=[{}, {}, {}]", 
                            v.getId(), v.getSku(), v.getOption1(), v.getOption2(), v.getOption3());
                    }
                }
                
                logger.info("🎉 You can now run ExamineProductOptionsTest to analyze this product!");
                
            } else {
                logger.error("❌ Failed to create product - returned null");
            }
            
        } catch (Exception e) {
            logger.error("❌ Failed to create product with options: {}", e.getMessage());
            logger.error("Stack trace:", e);
            
            // This will help us understand exactly what went wrong
            if (e.getMessage().contains("GraphQL")) {
                logger.error("🔍 This appears to be a GraphQL API issue");
                logger.error("🔍 Check if the variant option values match the product option values exactly");
            }
        }
    }
    
    @Test
    public void createProductWithoutOptions() throws Exception {
        logger.info("=== CREATING SIMPLE PRODUCT WITHOUT OPTIONS (FOR COMPARISON) ===");
        
        // Create a simple product without options
        Product product = new Product();
        product.setTitle("Simple Test Watch");
        product.setBodyHtml("A simple watch product without options");
        product.setVendor("Test Vendor");
        product.setProductType("Watch");
        product.setHandle("simple-test-watch");
        
        // Create 1 variant with no option values
        List<Variant> variants = new ArrayList<>();
        Variant variant = new Variant();
        variant.setTitle("Default Title");
        variant.setSku("SIMPLE-WATCH-001");
        variant.setPrice("199.99");
        // No option1, option2, option3 values
        variant.setInventoryManagement("shopify");
        variant.setInventoryPolicy("deny");
        variant.setTaxable("true");
        variant.setRequiresShipping("true");
        variants.add(variant);
        
        product.setVariants(variants);
        
        try {
            logger.info("🚀 Creating simple product in Shopify...");
            Product createdProduct = shopifyApiService.addProduct(product);
            
            if (createdProduct != null) {
                logger.info("✅ SUCCESS! Simple product created with ID: {}", createdProduct.getId());
                logger.info("📊 This product has NO options and serves as a baseline comparison");
            } else {
                logger.error("❌ Failed to create simple product");
            }
            
        } catch (Exception e) {
            logger.error("❌ Failed to create simple product: {}", e.getMessage());
        }
    }
} 