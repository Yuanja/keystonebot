package com.gw.services;

import com.gw.domain.FeedItem;
import com.gw.domain.FeedItemChange;
import com.gw.services.shopifyapi.ShopifyGraphQLService;
import com.gw.services.shopifyapi.objects.Product;
import com.gw.services.shopifyapi.objects.Variant;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Reconciliation Service for analyzing and reconciling discrepancies between:
 * - Database (FeedItems)
 * - Shopify Products
 * - Feed data
 * - Image counts
 * 
 * Provides two modes:
 * 1. ANALYSIS: Read-only analysis that reports differences without making changes
 * 2. RECONCILIATION: Actually performs the reconciliation work
 * 
 * @author jyuan
 */
@Service
public class ReconciliationService {
    
    private static final Logger logger = LogManager.getLogger(ReconciliationService.class);
    
    @Value("${MAX_TO_DELETE_COUNT}")
    private int maxToDeleteCount;
    
    @Autowired
    private ShopifyGraphQLService shopifyApiService;
    
    @Autowired
    private FeedItemService feedItemService;
    
    @Autowired(required = false)
    private IFeedService feedService;
    
    /**
     * Analysis result containing all discrepancies found
     */
    public static class ReconciliationAnalysis {
        private final LocalDateTime analysisTime;
        private final int totalProductsInShopify;
        private final int totalItemsInDB;
        private final int totalItemsInFeed;
        
        private final List<ProductDiscrepancy> extraInShopify;
        private final List<ProductDiscrepancy> extraInDB;
        private final List<ProductDiscrepancy> mismatchedShopifyIds;
        private final List<ProductDiscrepancy> imageCountMismatches;
        private final List<String> errors;
        
        private boolean exceedsDeleteThreshold;
        private String deleteThresholdMessage;
        
        public ReconciliationAnalysis() {
            this.analysisTime = LocalDateTime.now();
            this.extraInShopify = new ArrayList<>();
            this.extraInDB = new ArrayList<>();
            this.mismatchedShopifyIds = new ArrayList<>();
            this.imageCountMismatches = new ArrayList<>();
            this.errors = new ArrayList<>();
            this.totalProductsInShopify = 0;
            this.totalItemsInDB = 0;
            this.totalItemsInFeed = 0;
        }
        
        public ReconciliationAnalysis(int shopifyCount, int dbCount, int feedCount) {
            this.analysisTime = LocalDateTime.now();
            this.extraInShopify = new ArrayList<>();
            this.extraInDB = new ArrayList<>();
            this.mismatchedShopifyIds = new ArrayList<>();
            this.imageCountMismatches = new ArrayList<>();
            this.errors = new ArrayList<>();
            this.totalProductsInShopify = shopifyCount;
            this.totalItemsInDB = dbCount;
            this.totalItemsInFeed = feedCount;
        }
        
        // Getters
        public LocalDateTime getAnalysisTime() { return analysisTime; }
        public int getTotalProductsInShopify() { return totalProductsInShopify; }
        public int getTotalItemsInDB() { return totalItemsInDB; }
        public int getTotalItemsInFeed() { return totalItemsInFeed; }
        public List<ProductDiscrepancy> getExtraInShopify() { return extraInShopify; }
        public List<ProductDiscrepancy> getExtraInDB() { return extraInDB; }
        public List<ProductDiscrepancy> getMismatchedShopifyIds() { return mismatchedShopifyIds; }
        public List<ProductDiscrepancy> getImageCountMismatches() { return imageCountMismatches; }
        public List<String> getErrors() { return errors; }
        public boolean isExceedsDeleteThreshold() { return exceedsDeleteThreshold; }
        public String getDeleteThresholdMessage() { return deleteThresholdMessage; }
        
        public void setExceedsDeleteThreshold(boolean exceeds, String message) {
            this.exceedsDeleteThreshold = exceeds;
            this.deleteThresholdMessage = message;
        }
        
        public boolean hasDiscrepancies() {
            return !extraInShopify.isEmpty() || !extraInDB.isEmpty() || 
                   !mismatchedShopifyIds.isEmpty() || !imageCountMismatches.isEmpty();
        }
        
        public int getTotalDiscrepancies() {
            return extraInShopify.size() + extraInDB.size() + 
                   mismatchedShopifyIds.size() + imageCountMismatches.size();
        }
    }
    
    /**
     * Represents a discrepancy found during analysis
     */
    public static class ProductDiscrepancy {
        private final String sku;
        private final String shopifyId;
        private final String dbShopifyId;
        private final String discrepancyType;
        private final String description;
        private final Map<String, Object> details;
        
        public ProductDiscrepancy(String sku, String shopifyId, String discrepancyType, String description) {
            this.sku = sku;
            this.shopifyId = shopifyId;
            this.dbShopifyId = null;
            this.discrepancyType = discrepancyType;
            this.description = description;
            this.details = new HashMap<>();
        }
        
        public ProductDiscrepancy(String sku, String shopifyId, String dbShopifyId, String discrepancyType, String description) {
            this.sku = sku;
            this.shopifyId = shopifyId;
            this.dbShopifyId = dbShopifyId;
            this.discrepancyType = discrepancyType;
            this.description = description;
            this.details = new HashMap<>();
        }
        
        public ProductDiscrepancy withDetail(String key, Object value) {
            this.details.put(key, value);
            return this;
        }
        
        // Getters
        public String getSku() { return sku; }
        public String getShopifyId() { return shopifyId; }
        public String getDbShopifyId() { return dbShopifyId; }
        public String getDiscrepancyType() { return discrepancyType; }
        public String getDescription() { return description; }
        public Map<String, Object> getDetails() { return details; }
        
        @Override
        public String toString() {
            return String.format("[%s] SKU: %s, Shopify ID: %s, DB ID: %s - %s", 
                discrepancyType, sku, shopifyId, dbShopifyId, description);
        }
    }
    
    /**
     * Performs comprehensive analysis of discrepancies between DB, Shopify, and Feed
     * This is READ-ONLY and makes no changes to any system
     */
    public ReconciliationAnalysis analyzeDiscrepancies() {
        logger.info("=== STARTING RECONCILIATION ANALYSIS (READ-ONLY) ===");
        logger.info("Analysis started at: {}", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        
        try {
            // Get all data sources
            logger.info("Fetching data from all sources...");
            Map<String, Product> allProductBySku = shopifyApiService.unlistDupeListings();
            Map<String, FeedItem> allItemsInDBBySku = feedItemService.getFeedItemBySkuMap();
            
            // Get feed data if available
            List<FeedItem> feedItems = null;
            try {
                if (feedService != null) {
                    feedItems = feedService.getItemsFromFeed();
                    logger.info("Retrieved {} items from feed", feedItems != null ? feedItems.size() : 0);
                } else {
                    logger.warn("Feed service not available - skipping feed analysis");
                }
            } catch (Exception e) {
                logger.warn("Failed to retrieve feed data: {}", e.getMessage());
            }
            
            ReconciliationAnalysis analysis = new ReconciliationAnalysis(
                allProductBySku.size(), 
                allItemsInDBBySku.size(), 
                feedItems != null ? feedItems.size() : 0
            );
            
            logger.info("Data retrieved:");
            logger.info("  - Shopify products: {}", analysis.getTotalProductsInShopify());
            logger.info("  - DB items: {}", analysis.getTotalItemsInDB());
            logger.info("  - Feed items: {}", analysis.getTotalItemsInFeed());
            
            // Check delete threshold
            checkDeleteThreshold(analysis, allProductBySku, allItemsInDBBySku);
            
            // Analyze discrepancies
            analyzeExtraShopifyListings(analysis, allProductBySku, allItemsInDBBySku);
            analyzeExtraDBItems(analysis, allProductBySku, allItemsInDBBySku);
            analyzeImageCountMismatches(analysis, allProductBySku, allItemsInDBBySku);
            
            // Generate comprehensive report
            generateAnalysisReport(analysis);
            
            return analysis;
            
        } catch (Exception e) {
            logger.error("❌ Analysis failed with exception", e);
            ReconciliationAnalysis errorAnalysis = new ReconciliationAnalysis();
            errorAnalysis.getErrors().add("Analysis failed: " + e.getMessage());
            return errorAnalysis;
        }
    }
    
    /**
     * Performs actual reconciliation work based on analysis results
     * This MODIFIES data and should only be run after analysis confirms safety
     */
    public ReconciliationResult performReconciliation(boolean force) {
        logger.info("=== STARTING RECONCILIATION (MODIFIES DATA) ===");
        logger.info("Force mode: {}", force);
        
        // First run analysis to determine what needs to be done
        ReconciliationAnalysis analysis = analyzeDiscrepancies();
        
        if (!force && analysis.isExceedsDeleteThreshold()) {
            logger.error("❌ Reconciliation aborted: {}", analysis.getDeleteThresholdMessage());
            return new ReconciliationResult(false, "Reconciliation aborted: " + analysis.getDeleteThresholdMessage(), analysis);
        }
        
        if (!analysis.hasDiscrepancies()) {
            logger.info("✅ No discrepancies found - reconciliation not needed");
            return new ReconciliationResult(true, "No discrepancies found - system is in sync", analysis);
        }
        
        logger.info("🔧 Performing reconciliation for {} discrepancies...", analysis.getTotalDiscrepancies());
        
        try {
            Map<String, Product> allProductBySku = shopifyApiService.unlistDupeListings();
            Map<String, FeedItem> allItemsInDBBySku = feedItemService.getFeedItemBySkuMap();
            
            // Perform actual reconciliation work
            removeExtraListingsNotInDB(allProductBySku, allItemsInDBBySku);
            removeExtraItemsNotListedInShopify(allProductBySku, allItemsInDBBySku);
            
            logger.info("✅ Reconciliation completed successfully");
            return new ReconciliationResult(true, 
                String.format("Reconciliation completed. Fixed %d discrepancies.", analysis.getTotalDiscrepancies()), 
                analysis);
                
        } catch (Exception e) {
            logger.error("❌ Reconciliation failed", e);
            return new ReconciliationResult(false, "Reconciliation failed: " + e.getMessage(), analysis);
        }
    }
    
    /**
     * Result of reconciliation operation
     */
    public static class ReconciliationResult {
        private final boolean success;
        private final String message;
        private final ReconciliationAnalysis analysis;
        
        public ReconciliationResult(boolean success, String message, ReconciliationAnalysis analysis) {
            this.success = success;
            this.message = message;
            this.analysis = analysis;
        }
        
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public ReconciliationAnalysis getAnalysis() { return analysis; }
    }
    
    // Private helper methods (extracted from BaseShopifySyncService)
    
    private void checkDeleteThreshold(ReconciliationAnalysis analysis, 
                                    Map<String, Product> allProductBySku, 
                                    Map<String, FeedItem> allItemsInDBBySku) {
        int difference = Math.abs(allProductBySku.size() - allItemsInDBBySku.size());
        if (difference > maxToDeleteCount) {
            String message = String.format("Difference of %d items exceeds threshold of %d", 
                difference, maxToDeleteCount);
            analysis.setExceedsDeleteThreshold(true, message);
            logger.warn("⚠️ {}", message);
        } else {
            analysis.setExceedsDeleteThreshold(false, "Within acceptable threshold");
            logger.info("✅ Difference of {} items is within threshold of {}", difference, maxToDeleteCount);
        }
    }
    
    private void analyzeExtraShopifyListings(ReconciliationAnalysis analysis,
                                           Map<String, Product> allProductBySku,
                                           Map<String, FeedItem> allItemsInDBBySku) {
        logger.info("Analyzing extra Shopify listings...");
        
        for (Product currentProduct : allProductBySku.values()) {
            List<Variant> variants = currentProduct.getVariants();
            if (variants == null || variants.isEmpty()) {
                analysis.getErrors().add("Product " + currentProduct.getId() + " has no variants");
                continue;
            }
            
            String currentProductSku = variants.get(0).getSku();
            FeedItem feedItemFromDb = allItemsInDBBySku.get(currentProductSku);
            
            if (feedItemFromDb == null) {
                // Product exists in Shopify but not in DB
                analysis.getExtraInShopify().add(new ProductDiscrepancy(
                    currentProductSku,
                    currentProduct.getId(),
                    "EXTRA_IN_SHOPIFY",
                    "Product exists in Shopify but not tracked in DB"
                ).withDetail("title", currentProduct.getTitle()));
            } else {
                // Check if Shopify ID matches between DB and Shopify
                if (feedItemFromDb.getShopifyItemId() == null || 
                    !feedItemFromDb.getShopifyItemId().equals(currentProduct.getId())) {
                    analysis.getMismatchedShopifyIds().add(new ProductDiscrepancy(
                        currentProductSku,
                        currentProduct.getId(),
                        feedItemFromDb.getShopifyItemId(),
                        "MISMATCHED_SHOPIFY_ID",
                        "Shopify ID mismatch between DB and Shopify"
                    ));
                }
            }
        }
        
        logger.info("Found {} extra Shopify listings", analysis.getExtraInShopify().size());
        logger.info("Found {} Shopify ID mismatches", analysis.getMismatchedShopifyIds().size());
    }
    
    private void analyzeExtraDBItems(ReconciliationAnalysis analysis,
                                   Map<String, Product> allProductBySku,
                                   Map<String, FeedItem> allItemsInDBBySku) {
        logger.info("Analyzing extra DB items...");
        
        for (FeedItem itemFromDb : allItemsInDBBySku.values()) {
            Product productFromShopify = allProductBySku.get(itemFromDb.getWebTagNumber());
            if (productFromShopify == null) {
                analysis.getExtraInDB().add(new ProductDiscrepancy(
                    itemFromDb.getWebTagNumber(),
                    itemFromDb.getShopifyItemId(),
                    "EXTRA_IN_DB",
                    "Item exists in DB but not in Shopify"
                ).withDetail("title", itemFromDb.getWebDescriptionShort())
                 .withDetail("status", itemFromDb.getStatus()));
            }
        }
        
        logger.info("Found {} extra DB items", analysis.getExtraInDB().size());
    }
    
    private void analyzeImageCountMismatches(ReconciliationAnalysis analysis,
                                           Map<String, Product> allProductBySku,
                                           Map<String, FeedItem> allItemsInDBBySku) {
        logger.info("Analyzing image count mismatches...");
        
        for (Product currentProduct : allProductBySku.values()) {
            if (currentProduct.getVariants() == null || currentProduct.getVariants().isEmpty()) {
                continue;
            }
            
            String currentProductSku = currentProduct.getVariants().get(0).getSku();
            FeedItem feedItemFromDb = allItemsInDBBySku.get(currentProductSku);
            
            if (feedItemFromDb != null) {
                int currentImageCount = currentProduct.getImages() == null ? 0 : currentProduct.getImages().size();
                int dbImageCount = feedItemFromDb.getImageCount();
                
                if (currentImageCount != dbImageCount) {
                    analysis.getImageCountMismatches().add(new ProductDiscrepancy(
                        currentProductSku,
                        currentProduct.getId(),
                        "IMAGE_COUNT_MISMATCH",
                        String.format("Shopify has %d images but DB expects %d", currentImageCount, dbImageCount)
                    ).withDetail("shopifyImageCount", currentImageCount)
                     .withDetail("dbImageCount", dbImageCount));
                }
            }
        }
        
        logger.info("Found {} image count mismatches", analysis.getImageCountMismatches().size());
    }
    
    private void generateAnalysisReport(ReconciliationAnalysis analysis) {
        logger.info("=== RECONCILIATION ANALYSIS REPORT ===");
        logger.info("Analysis completed at: {}", analysis.getAnalysisTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        logger.info("");
        
        logger.info("📊 DATA SUMMARY:");
        logger.info("  Shopify Products: {}", analysis.getTotalProductsInShopify());
        logger.info("  Database Items: {}", analysis.getTotalItemsInDB());
        logger.info("  Feed Items: {}", analysis.getTotalItemsInFeed());
        logger.info("");
        
        if (analysis.isExceedsDeleteThreshold()) {
            logger.warn("⚠️  SAFETY CHECK: {}", analysis.getDeleteThresholdMessage());
            logger.warn("⚠️  Reconciliation should not proceed without manual review");
            logger.info("");
        }
        
        logger.info("🔍 DISCREPANCIES FOUND:");
        logger.info("  Extra in Shopify (not in DB): {}", analysis.getExtraInShopify().size());
        logger.info("  Extra in DB (not in Shopify): {}", analysis.getExtraInDB().size());
        logger.info("  Shopify ID mismatches: {}", analysis.getMismatchedShopifyIds().size());
        logger.info("  Image count mismatches: {}", analysis.getImageCountMismatches().size());
        logger.info("  Total discrepancies: {}", analysis.getTotalDiscrepancies());
        logger.info("");
        
        // Detail each type of discrepancy
        if (!analysis.getExtraInShopify().isEmpty()) {
            logger.info("🔸 EXTRA IN SHOPIFY (would be deleted):");
            analysis.getExtraInShopify().stream()
                .limit(10)
                .forEach(d -> logger.info("    {}", d));
            if (analysis.getExtraInShopify().size() > 10) {
                logger.info("    ... and {} more", analysis.getExtraInShopify().size() - 10);
            }
            logger.info("");
        }
        
        if (!analysis.getExtraInDB().isEmpty()) {
            logger.info("🔸 EXTRA IN DB (would be removed from DB):");
            analysis.getExtraInDB().stream()
                .limit(10)
                .forEach(d -> logger.info("    {}", d));
            if (analysis.getExtraInDB().size() > 10) {
                logger.info("    ... and {} more", analysis.getExtraInDB().size() - 10);
            }
            logger.info("");
        }
        
        if (!analysis.getMismatchedShopifyIds().isEmpty()) {
            logger.info("🔸 SHOPIFY ID MISMATCHES (would be updated in DB):");
            analysis.getMismatchedShopifyIds().stream()
                .limit(10)
                .forEach(d -> logger.info("    {}", d));
            if (analysis.getMismatchedShopifyIds().size() > 10) {
                logger.info("    ... and {} more", analysis.getMismatchedShopifyIds().size() - 10);
            }
            logger.info("");
        }
        
        if (!analysis.getImageCountMismatches().isEmpty()) {
            logger.info("🔸 IMAGE COUNT MISMATCHES (would be marked for update):");
            analysis.getImageCountMismatches().stream()
                .limit(10)
                .forEach(d -> logger.info("    {}", d));
            if (analysis.getImageCountMismatches().size() > 10) {
                logger.info("    ... and {} more", analysis.getImageCountMismatches().size() - 10);
            }
            logger.info("");
        }
        
        if (!analysis.getErrors().isEmpty()) {
            logger.error("❌ ERRORS ENCOUNTERED:");
            analysis.getErrors().forEach(error -> logger.error("    {}", error));
            logger.info("");
        }
        
        if (analysis.hasDiscrepancies()) {
            logger.warn("⚠️  RECOMMENDATION: Review discrepancies before running reconciliation");
            logger.info("💡 Run with reconciliation mode to fix these issues");
        } else {
            logger.info("✅ NO DISCREPANCIES FOUND - System is in sync!");
        }
        
        logger.info("=== END ANALYSIS REPORT ===");
    }
    
    // Actual reconciliation methods (extracted from BaseShopifySyncService)
    
    private void removeExtraListingsNotInDB(Map<String, Product> allProductBySku,
                                          Map<String, FeedItem> allItemsInDBBySku) {
        logger.info("Removing extra Shopify listings that are not in the DB...");
        
        for (Product currentProduct : allProductBySku.values()) {
            List<Variant> variants = currentProduct.getVariants();
            if (variants == null || variants.isEmpty()) {
                logger.error("Product {} has no variants - skipping", currentProduct.getId());
                continue;
            }
            
            String currentProductSku = variants.get(0).getSku();
            FeedItem feedItemFromDb = allItemsInDBBySku.get(currentProductSku);
            
            if (feedItemFromDb == null) {
                logger.info("Removing SKU {} (Product ID: {}) from Shopify - not tracked in DB", 
                    currentProductSku, currentProduct.getId());
                shopifyApiService.deleteProductByIdOrLogFailure(currentProduct.getId());
            } else {
                // Check if the item in DB has matching Shopify ID
                if (feedItemFromDb.getShopifyItemId() == null ||
                    !feedItemFromDb.getShopifyItemId().equals(currentProduct.getId())) {
                    logger.info("Updating DB Shopify ID for SKU {} from {} to {}", 
                        currentProductSku, feedItemFromDb.getShopifyItemId(), currentProduct.getId());
                    feedItemFromDb.setShopifyItemId(currentProduct.getId());
                    feedItemService.updateAutonomous(feedItemFromDb);
                }
            }
        }
    }
    
    private void removeExtraItemsNotListedInShopify(Map<String, Product> allProductBySku,
                                                  Map<String, FeedItem> allItemsInDBBySku) {
        logger.info("Removing items in DB that don't exist on Shopify...");
        
        List<FeedItem> allFeedItems = allItemsInDBBySku.values().stream().collect(Collectors.toList());
        
        for (FeedItem itemFromDb : allFeedItems) {
            Product productFromShopify = allProductBySku.get(itemFromDb.getWebTagNumber());
            if (productFromShopify == null) {
                logger.info("Removing SKU {} from DB - not listed in Shopify", itemFromDb.getWebTagNumber());
                feedItemService.deleteAutonomous(itemFromDb);
            }
        }
    }
    
    private List<FeedItemChange> getShopifyItemsToUpdateDueToImageCountMismatch(
            Map<String, Product> allProductBySku,
            Map<String, FeedItem> allItemsInDBBySku) {
        List<FeedItemChange> feedItemsToUpdate = new ArrayList<>();
        logger.info("Checking for image count mismatches...");
        
        for (Product currentProduct : allProductBySku.values()) {
            List<Variant> variants = currentProduct.getVariants();
            if (variants == null || variants.isEmpty()) {
                continue;
            }
            
            String currentProductSku = variants.get(0).getSku();
            FeedItem feedItemFromDb = allItemsInDBBySku.get(currentProductSku);
            
            if (feedItemFromDb != null) {
                int currentImageCount = currentProduct.getImages() == null ? 0 : currentProduct.getImages().size();
                if (feedItemFromDb.getImageCount() != currentImageCount) {
                    logger.info("Image count mismatch for SKU {}: Shopify has {}, DB expects {} - marking for update",
                        currentProductSku, currentImageCount, feedItemFromDb.getImageCount());
                    feedItemsToUpdate.add(new FeedItemChange(feedItemFromDb, feedItemFromDb));
                }
            }
        }
        
        return feedItemsToUpdate;
    }
} 