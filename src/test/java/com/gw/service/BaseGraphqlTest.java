package com.gw.service;

import com.gw.Config;
import com.gw.domain.FeedItem;
import com.gw.domain.PredefinedCollection;
import com.gw.domain.keystone.KeyStoneCollections;
import com.gw.services.FeedItemService;
import com.gw.services.keystone.KeyStoneFeedService;
import com.gw.services.keystone.KeystoneShopifySyncService;
import com.gw.services.EmailService;
import com.gw.services.shopifyapi.ShopifyGraphQLService;
import com.gw.services.shopifyapi.objects.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@SpringJUnitConfig
@SpringBootTest
@ActiveProfiles("keystone-dev")
public abstract class BaseGraphqlTest {
    protected static Logger logger = LogManager.getLogger(BaseGraphqlTest.class);

    @Autowired
    protected EmailService emailService;
    
    @Autowired
    protected ShopifyGraphQLService shopifyApiService;
    
    @Autowired
    protected KeystoneShopifySyncService syncService;

    @Autowired
    protected FeedItemService feedItemService;
    
    @Autowired
    protected KeyStoneFeedService keyStoneFeedService;

    /**
     * Setup method that runs before each test to ensure clean state
     * Removes all products from Shopify and all feed items from database
     */
    @BeforeEach
    public void setUp() throws Exception {
        logger.info("=== TEST SETUP: Cleaning Shopify and Database ===");
        
        // Remove all products from Shopify
        logger.info("Removing all products from Shopify...");
        shopifyApiService.removeAllProducts();
        
        // Remove all feed items from database  
        logger.info("Removing all feed items from database...");
        feedItemService.deleteAllAutonomous();
        
        removeAllCollections();
        
        // Remove eBay metafield definitions for clean testing state
        removeAllEbayMetafieldDefinitions();

        // Verify clean state
        List<Product> allProducts = shopifyApiService.getAllProducts();
        Assertions.assertTrue(allProducts.isEmpty(), "Shopify should be empty after setup");
        
        logger.info("✅ Setup complete - Shopify and Database are clean");
        logger.info("=== END TEST SETUP ===");
    }
    
    protected void removeAllCollections() throws Exception{
        shopifyApiService.removeAllCollections();
        
        List<CustomCollection> allCollections = shopifyApiService.getAllCustomCollections();
        Assertions.assertTrue(allCollections.isEmpty());
    }
    
    protected void removeAllEbayMetafieldDefinitions() {
        try {
            logger.info("Removing all eBay metafield definitions...");
            shopifyApiService.removeEbayMetafieldDefinitions();
            logger.info("✅ eBay metafield definitions cleaned");
        } catch (Exception e) {
            logger.info("ℹ️ eBay metafield cleanup: " + e.getMessage());
        }
    }
    
    protected List<FeedItem> getTopFeedItems(int count) throws Exception{
        // Load all items from the live feed
        List<FeedItem> allFeedItems = keyStoneFeedService.getItemsFromFeed();
        logger.info("Loaded " + allFeedItems.size() + " total items from live feed");
        
        // Sort by webTagNumber in descending order and take the highest requested count
        List<FeedItem> topFeedItems = allFeedItems.stream()
            .filter(item -> item.getWebTagNumber() != null && !item.getWebTagNumber().trim().isEmpty())
            .sorted((a, b) -> {
                try {
                    // Parse as integers for proper numeric sorting
                    Integer aNum = Integer.parseInt(a.getWebTagNumber());
                    Integer bNum = Integer.parseInt(b.getWebTagNumber());
                    return bNum.compareTo(aNum); // Descending order (highest first)
                } catch (NumberFormatException e) {
                    // Fallback to string comparison if not numeric
                    return b.getWebTagNumber().compareTo(a.getWebTagNumber());
                }
            })
            .limit(count)
            .collect(Collectors.toList());
        return topFeedItems;
    }

    protected Document getDocument(String filePath) throws ParserConfigurationException, SAXException, IOException {
        // Copy the same method from BaseFeedService to parse XML files
        java.io.File xmlFeedFile = new java.io.File(filePath);
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        dbFactory.setValidating(false);
        dbFactory.setNamespaceAware(true);
        dbFactory.setFeature("http://xml.org/sax/features/namespaces", false);
        dbFactory.setFeature("http://xml.org/sax/features/validation", false);
        dbFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
        dbFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        dbFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);

        javax.xml.parsers.DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(xmlFeedFile);
        return doc;
    }
} 