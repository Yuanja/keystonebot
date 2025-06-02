package com.gw.services;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.gw.domain.FeedItem;
import com.gw.services.shopifyapi.ShopifyGraphQLService;
import com.gw.services.shopifyapi.objects.Metafield;
import com.gw.services.shopifyapi.objects.Product;
import com.gw.services.shopifyapi.objects.Variant;

/**
 * Service for backfilling eBay metafields on existing Shopify products
 * 
 * This service will:
 * 1. Ensure eBay metafield definitions exist in Shopify
 * 2. Retrieve all existing products from Shopify
 * 3. Find corresponding FeedItem records in the database by SKU
 * 4. Create/update eBay metafields using the same logic as normal product publishing
 * 
 * This approach ensures consistency with the normal publishing process and uses
 * the authoritative FeedItem data from the database instead of parsing Shopify content.
 * 
 * @author Assistant
 */
@Service
public class EbayMetafieldBackfillService {
    
    private static final Logger logger = LogManager.getLogger(EbayMetafieldBackfillService.class);
    
    @Autowired
    private ShopifyGraphQLService shopifyService;
    
    @Autowired
    private FeedItemService feedItemService;
    
    /**
     * Execute the complete backfill process
     */
    public BackfillResult executeBackfill() throws Exception {
        logger.info("🚀 Starting eBay metafield backfill process...");
        
        BackfillResult result = new BackfillResult();
        
        try {
            // Step 1: Ensure eBay metafield definitions exist
            logger.info("📋 Step 1: Ensuring eBay metafield definitions exist...");
            shopifyService.createEbayMetafieldDefinitions();
            logger.info("✅ eBay metafield definitions ensured");
            
            // Step 2: Get all products from Shopify
            logger.info("📦 Step 2: Retrieving all products from Shopify...");
            List<Product> allProducts = shopifyService.getAllProducts();
            logger.info("📊 Found " + allProducts.size() + " total products in Shopify");
            
            // Step 3: Get feed items map for lookup
            logger.info("🗄️ Step 3: Loading feed items from database...");
            Map<String, FeedItem> feedItemsBySku = feedItemService.getFeedItemBySkuMap();
            logger.info("📊 Found " + feedItemsBySku.size() + " feed items in database");
            
            // Step 4: Filter for watch products that have corresponding feed items
            List<ProductFeedItemPair> watchProductPairs = matchProductsWithFeedItems(allProducts, feedItemsBySku);
            logger.info("⌚ Found " + watchProductPairs.size() + " watch products with matching feed items to process");
            result.setTotalProducts(watchProductPairs.size());
            
            // Step 5: Process each watch product
            logger.info("🔄 Step 5: Processing watch products...");
            int processed = 0;
            int updated = 0;
            int skipped = 0;
            int errors = 0;
            
            for (ProductFeedItemPair pair : watchProductPairs) {
                try {
                    processed++;
                    logger.info("Processing product " + processed + "/" + watchProductPairs.size() + 
                               ": " + pair.product.getTitle() + " (SKU: " + pair.feedItem.getWebTagNumber() + ")");
                    
                    boolean wasUpdated = processProductFeedItemPair(pair);
                    if (wasUpdated) {
                        updated++;
                        logger.info("✅ Updated eBay metafields for: " + pair.product.getTitle());
                    } else {
                        skipped++;
                        logger.info("⏭️ Skipped (already has eBay metafields): " + pair.product.getTitle());
                    }
                    
                } catch (Exception e) {
                    errors++;
                    logger.error("❌ Error processing product: " + pair.product.getTitle(), e);
                    result.addError(pair.product.getId(), pair.product.getTitle(), e.getMessage());
                }
            }
            
            result.setProcessed(processed);
            result.setUpdated(updated);
            result.setSkipped(skipped);
            result.setErrors(errors);
            
            logger.info("🎉 Backfill process completed!");
            logger.info("📊 Summary: " + processed + " processed, " + updated + " updated, " + skipped + " skipped, " + errors + " errors");
            
        } catch (Exception e) {
            logger.error("💥 Backfill process failed", e);
            throw e;
        }
        
        return result;
    }
    
    /**
     * Match Shopify products with their corresponding FeedItem records
     */
    private List<ProductFeedItemPair> matchProductsWithFeedItems(List<Product> allProducts, Map<String, FeedItem> feedItemsBySku) {
        List<ProductFeedItemPair> pairs = new ArrayList<>();
        
        for (Product product : allProducts) {
            // Skip if not a watch product
            if (!isWatchProduct(product)) {
                continue;
            }
            
            // Find corresponding feed item by SKU from product variant
            String sku = getProductSku(product);
            if (sku != null) {
                FeedItem feedItem = feedItemsBySku.get(sku);
                if (feedItem != null) {
                    pairs.add(new ProductFeedItemPair(product, feedItem));
                    logger.debug("✅ Matched product '{}' with feed item SKU '{}'", product.getTitle(), sku);
                } else {
                    logger.debug("⚠️ No feed item found for product '{}' with SKU '{}'", product.getTitle(), sku);
                }
            } else {
                logger.debug("⚠️ No SKU found for product '{}'", product.getTitle());
            }
        }
        
        return pairs;
    }
    
    /**
     * Get the SKU from the first variant of a product
     */
    private String getProductSku(Product product) {
        if (product.getVariants() != null && !product.getVariants().isEmpty()) {
            Variant firstVariant = product.getVariants().get(0);
            return firstVariant.getSku();
        }
        return null;
    }
    
    /**
     * Determine if a product is a watch
     */
    private boolean isWatchProduct(Product product) {
        // Check product type
        if (product.getProductType() != null && "Watches".equalsIgnoreCase(product.getProductType())) {
            return true;
        }
        
        // Check tags
        if (product.getTags() != null && product.getTags().toLowerCase().contains("watch")) {
            return true;
        }
        
        // Check title
        if (product.getTitle() != null && product.getTitle().toLowerCase().contains("watch")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Process a single product-feeditem pair to add/update eBay metafields
     * @return true if the product was updated, false if skipped
     */
    private boolean processProductFeedItemPair(ProductFeedItemPair pair) throws Exception {
        Product product = pair.product;
        FeedItem feedItem = pair.feedItem;
        
        // Check if product already has eBay metafields
        if (hasEbayMetafields(product)) {
            logger.debug("Product already has eBay metafields, skipping: " + product.getTitle());
            return false;
        }
        
        // Create eBay metafields from feed item using the same logic as normal publishing
        List<Metafield> ebayMetafields = createEbayMetafieldsFromFeedItem(feedItem);
        
        if (ebayMetafields.isEmpty()) {
            logger.debug("No eBay metafield data could be created from feed item: " + feedItem.getWebTagNumber());
            return false;
        }
        
        // Add metafields to existing product metafields
        if (product.getMetafields() == null) {
            product.setMetafields(new ArrayList<>());
        }
        product.getMetafields().addAll(ebayMetafields);
        
        // Update the product
        shopifyService.updateProduct(product);
        
        logger.info("✅ Added " + ebayMetafields.size() + " eBay metafields to product: " + product.getTitle());
        return true;
    }
    
    /**
     * Create eBay metafields from FeedItem data using the same logic as KeyStoneShopifyProductFactoryService
     * This ensures consistency with the normal product publishing process
     */
    private List<Metafield> createEbayMetafieldsFromFeedItem(FeedItem feedItem) {
        List<Metafield> ebayMetafields = new ArrayList<>();
        
        // Watch Brand/Manufacturer
        if (feedItem.getWebDesigner() != null) {
            ebayMetafields.add(createEbayMetafield("brand", feedItem.getWebDesigner(), "single_line_text_field", "Watch brand/manufacturer"));
        }
        
        // Watch Model
        if (feedItem.getWebWatchModel() != null) {
            ebayMetafields.add(createEbayMetafield("model", feedItem.getWebWatchModel(), "single_line_text_field", "Watch model"));
        }
        
        // Reference Number
        if (feedItem.getWebWatchManufacturerReferenceNumber() != null) {
            ebayMetafields.add(createEbayMetafield("reference_number", feedItem.getWebWatchManufacturerReferenceNumber(), "single_line_text_field", "Manufacturer reference number"));
        }
        
        // Year of Manufacture
        if (feedItem.getWebWatchYear() != null) {
            ebayMetafields.add(createEbayMetafield("year", feedItem.getWebWatchYear(), "single_line_text_field", "Year of manufacture"));
        }
        
        // Case Material
        if (feedItem.getWebMetalType() != null) {
            ebayMetafields.add(createEbayMetafield("case_material", feedItem.getWebMetalType(), "single_line_text_field", "Case material"));
        }
        
        // Movement Type
        if (feedItem.getWebWatchMovement() != null) {
            ebayMetafields.add(createEbayMetafield("movement", feedItem.getWebWatchMovement(), "single_line_text_field", "Movement type"));
        }
        
        // Dial Information
        if (feedItem.getWebWatchDial() != null) {
            ebayMetafields.add(createEbayMetafield("dial", feedItem.getWebWatchDial(), "multi_line_text_field", "Dial information"));
        }
        
        // Strap/Bracelet Information
        if (feedItem.getWebWatchStrap() != null) {
            ebayMetafields.add(createEbayMetafield("strap", feedItem.getWebWatchStrap(), "multi_line_text_field", "Strap/bracelet information"));
        }
        
        // Condition
        if (feedItem.getWebWatchCondition() != null) {
            ebayMetafields.add(createEbayMetafield("condition", feedItem.getWebWatchCondition(), "single_line_text_field", "Watch condition"));
        }
        
        // Case Diameter
        if (feedItem.getWebWatchDiameter() != null) {
            ebayMetafields.add(createEbayMetafield("diameter", feedItem.getWebWatchDiameter(), "single_line_text_field", "Case diameter"));
        }
        
        // Box and Papers
        if (feedItem.getWebWatchBoxPapers() != null) {
            ebayMetafields.add(createEbayMetafield("box_papers", feedItem.getWebWatchBoxPapers(), "single_line_text_field", "Box and papers information"));
        }
        
        // Category
        if (feedItem.getWebCategory() != null) {
            ebayMetafields.add(createEbayMetafield("category", feedItem.getWebCategory(), "single_line_text_field", "Watch category"));
        }
        
        // Style
        if (feedItem.getWebStyle() != null) {
            ebayMetafields.add(createEbayMetafield("style", feedItem.getWebStyle(), "single_line_text_field", "Watch style"));
        }
        
        return ebayMetafields;
    }
    
    /**
     * Create an eBay metafield with the specified parameters
     * This uses the same signature as KeyStoneShopifyProductFactoryService
     */
    private Metafield createEbayMetafield(String key, String value, String type, String description) {
        Metafield metafield = new Metafield();
        metafield.setNamespace("ebay");
        metafield.setKey(key);
        metafield.setValue(value);
        metafield.setType(type);
        metafield.setDescription(description);
        return metafield;
    }
    
    /**
     * Check if product already has eBay metafields
     */
    private boolean hasEbayMetafields(Product product) {
        if (product.getMetafields() == null) {
            return false;
        }
        
        return product.getMetafields().stream()
                .anyMatch(mf -> "ebay".equals(mf.getNamespace()));
    }
    
    /**
     * Helper class to pair a Product with its corresponding FeedItem
     */
    private static class ProductFeedItemPair {
        final Product product;
        final FeedItem feedItem;
        
        ProductFeedItemPair(Product product, FeedItem feedItem) {
            this.product = product;
            this.feedItem = feedItem;
        }
    }
    
    /**
     * Result object for backfill operation
     */
    public static class BackfillResult {
        private int totalProducts;
        private int processed;
        private int updated;
        private int skipped;
        private int errors;
        private List<BackfillError> errorList = new ArrayList<>();
        
        public void addError(String productId, String productTitle, String errorMessage) {
            errorList.add(new BackfillError(productId, productTitle, errorMessage));
        }
        
        // Getters and setters
        public int getTotalProducts() { return totalProducts; }
        public void setTotalProducts(int totalProducts) { this.totalProducts = totalProducts; }
        
        public int getProcessed() { return processed; }
        public void setProcessed(int processed) { this.processed = processed; }
        
        public int getUpdated() { return updated; }
        public void setUpdated(int updated) { this.updated = updated; }
        
        public int getSkipped() { return skipped; }
        public void setSkipped(int skipped) { this.skipped = skipped; }
        
        public int getErrors() { return errors; }
        public void setErrors(int errors) { this.errors = errors; }
        
        public List<BackfillError> getErrorList() { return errorList; }
        
        @Override
        public String toString() {
            return String.format("BackfillResult{total=%d, processed=%d, updated=%d, skipped=%d, errors=%d}", 
                totalProducts, processed, updated, skipped, errors);
        }
    }
    
    public static class BackfillError {
        private String productId;
        private String productTitle;
        private String errorMessage;
        
        public BackfillError(String productId, String productTitle, String errorMessage) {
            this.productId = productId;
            this.productTitle = productTitle;
            this.errorMessage = errorMessage;
        }
        
        // Getters
        public String getProductId() { return productId; }
        public String getProductTitle() { return productTitle; }
        public String getErrorMessage() { return errorMessage; }
        
        @Override
        public String toString() {
            return String.format("Error on %s (%s): %s", productTitle, productId, errorMessage);
        }
    }
} 