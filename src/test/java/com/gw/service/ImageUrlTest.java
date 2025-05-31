package com.gw.service;

import com.gw.services.ImageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.Arrays;

@SpringBootTest
@SpringJUnitConfig
public class ImageUrlTest {

    @Autowired
    private ImageService imageService;

    @Test
    public void testValidImageUrl() throws Exception {
        // Clean up any existing products first
        imageService.removeAllProducts();
        
        // Create a test product
        Product product = new Product();
        product.setTitle("Test Product with Valid Image");
        product.setVendor("Test Vendor");
        product.setProductType("Watches");
        
        // Create product first
        Product createdProduct = imageService.addProduct(product);
        System.out.println("Created product ID: " + createdProduct.getId());
        
        // Create image with the valid URL provided by user
        Image image = new Image();
        image.setSrc("http://ebay.gruenbergwatches.com/gwebaycss/images/watches/199869-9.jpg");
        
        // Add image to product
        imageService.addImagesToProduct(createdProduct.getId(), Arrays.asList(image));
        
        System.out.println("Successfully added image to product!");
        
        // Clean up
        //imageService.deleteProductById(createdProduct.getId());
        //System.out.println("Test completed successfully - image handling works!");
    }
} 