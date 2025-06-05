package com.gw.service;

import com.gw.domain.FeedItem;
import com.gw.services.shopifyapi.objects.Product;
import com.gw.services.shopifyapi.objects.Variant;
import com.gw.services.shopifyapi.objects.InventoryLevel;
import com.gw.services.shopifyapi.ShopifyGraphQLService;
import com.gw.services.BaseShopifySyncService;
import com.gw.services.FeedItemService;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

/**
 * 🚨 PRODUCTION INVENTORY FIX TOOL 🚨
 * ===================================
 * 
 * This tool identifies and fixes products with inflated inventory levels in production.
 * 
 * ⚠️  CRITICAL SAFETY INFORMATION ⚠️
 * ===================================
 * 
 * 🛡️ **PRODUCTION SAFE**: Does NOT extend BaseGraphqlTest - will NOT delete existing data!
 * 
 * 1. **ALWAYS RUN SCAN FIRST**: Use scanInventoryIssues() to identify problems before fixing
 * 2. **DRY RUN BY DEFAULT**: The tool defaults to DRY_RUN = true for safety
 * 3. **BACKUP RECOMMENDED**: Consider backing up inventory data before running fixes
 * 4. **TEST ENVIRONMENT**: Test on dev/staging environment first if possible
 * 5. **BUSINESS HOURS**: Run during low-traffic periods to minimize impact
 * 
 * 🚨 CRITICAL FIX APPLIED 🚨
 * =========================
 * This class was updated to NOT extend BaseGraphqlTest to prevent accidental data deletion.
 * Previous versions would have deleted all Shopify products and database records on startup!
 * 
 * 🔧 PRODUCTION USAGE GUIDE 🔧
 * ============================
 * 
 * STEP 1: SCAN FOR ISSUES (READ-ONLY)
 * ------------------------------------
 * This is SAFE to run anytime - it only reads data and reports issues:
 * 
 *   mvn test -Dtest=InventoryFixTest#scanInventoryIssues -Dspring.profiles.active=keystone-prod
 * 
 * Expected output:
 * - List of products with inventory > 1
 * - Breakdown by feed item status (SOLD/AVAILABLE/NOT_FOUND)
 * - Total excess inventory count
 * 
 * STEP 2: REVIEW SCAN RESULTS
 * ---------------------------
 * Look for:
 * - How many products are affected
 * - Whether the issues make sense (SOLD items should have 0, AVAILABLE should have 1)
 * - Any products with "NOT_FOUND" status (these need manual review)
 * 
 * STEP 3: RUN DRY RUN FIX (SIMULATION)
 * -----------------------------------
 * This shows what WOULD be fixed without making changes:
 * 
 *   mvn test -Dtest=InventoryFixTest#fixInflatedInventoryLevels -Dspring.profiles.active=keystone-prod
 * 
 * Expected output:
 * - Detailed fix plan showing current → correct inventory
 * - "DRY RUN MODE - No actual changes will be made" message
 * - Count of issues that would be fixed
 * 
 * STEP 4: APPLY ACTUAL FIXES (LIVE MODE)
 * -------------------------------------
 * ⚠️  THIS MAKES REAL CHANGES TO SHOPIFY INVENTORY ⚠️
 * 
 * 1. First, change DRY_RUN setting:
 *    Edit this file and change: private static boolean DRY_RUN = false;
 * 
 * 2. Run the fix:
 *    mvn test -Dtest=InventoryFixTest#fixInflatedInventoryLevels -Dspring.profiles.active=keystone-prod
 * 
 * 3. Monitor the output for:
 *    - "LIVE MODE - Changes will be applied to Shopify" message
 *    - Success/failure count for each fix
 *    - Any error messages
 * 
 * 4. Restore DRY_RUN setting:
 *    Change back to: private static boolean DRY_RUN = true;
 * 
 * 📊 UNDERSTANDING THE OUTPUT 📊
 * ==============================
 * 
 * Inventory Analysis:
 * - "Products with inventory issues" = products with total inventory > 1
 * - "Total excess inventory" = how much extra inventory exists
 * - "Percentage affected" = what % of products have issues
 * 
 * Feed Item Status Meanings:
 * - SOLD: Product is sold, should have 0 inventory
 * - AVAILABLE: Product is available, should have 1 inventory  
 * - NOT_FOUND: SKU not found in database (needs manual review)
 * 
 * Fix Plan Table:
 * - Current: Current total inventory across all locations
 * - Correct: What inventory should be based on feed item status
 * - Diff: How much inventory needs to change
 * - Action: REDUCE (lower inventory) or INCREASE (raise inventory)
 * 
 * 🚨 TROUBLESHOOTING 🚨
 * ====================
 * 
 * "No inventory issues found":
 * - This is good! All inventory levels are correct
 * - May indicate previous fixes were successful
 * 
 * "Feed item not found for SKU":
 * - SKU exists in Shopify but not in database
 * - May be test data or manually created products
 * - Defaults to treating as AVAILABLE (inventory = 1)
 * 
 * "Failed to fix inventory for SKU":
 * - API call failed for that specific product
 * - Check Shopify API rate limits
 * - Verify product/variant still exists
 * - May need manual review
 * 
 * High failure rate:
 * - Check network connectivity
 * - Verify Shopify API credentials
 * - May be hitting rate limits (tool includes delays)
 * 
 * 💡 BEST PRACTICES 💡
 * ===================
 * 
 * 1. **Schedule during off-peak hours**: Minimize customer impact
 * 2. **Run scan regularly**: Catch inventory inflation early
 * 3. **Monitor after fixes**: Verify inventory levels are correct
 * 4. **Keep logs**: Save output for audit purposes
 * 5. **Coordinate with team**: Ensure no concurrent inventory operations
 * 
 * 🔄 REGULAR MAINTENANCE 🔄
 * ========================
 * 
 * Recommended frequency:
 * - Weekly scan: mvn test -Dtest=InventoryFixTest#scanInventoryIssues -Dspring.profiles.active=keystone-prod
 * - Fix as needed: When scan shows issues > 5% of products
 * - After major updates: When sync system changes are deployed
 * 
 * ⚡ EMERGENCY USAGE ⚡
 * ===================
 * 
 * If you need to quickly fix inventory issues:
 * 
 * 1. Quick scan: mvn test -Dtest=InventoryFixTest#scanInventoryIssues -Dspring.profiles.active=keystone-prod | grep "Total products with issues"
 * 2. If issues found, set DRY_RUN = false and run: mvn test -Dtest=InventoryFixTest#fixInflatedInventoryLevels -Dspring.profiles.active=keystone-prod
 * 3. Restore DRY_RUN = true immediately after
 * 
 * 📋 PRODUCTION CHECKLIST 📋
 * ==========================
 * 
 * Before running in production:
 * □ Tested on dev/staging environment
 * □ Reviewed scan results
 * □ Coordinated with team
 * □ Scheduled during low-traffic period
 * □ Have rollback plan ready
 * □ Monitoring systems in place
 * 
 * During execution:
 * □ Monitor output for errors
 * □ Watch success/failure rates
 * □ Be ready to stop if issues arise
 * 
 * After execution:
 * □ Verify inventory levels are correct
 * □ Check for customer impact
 * □ Save logs for audit
 * □ Restore DRY_RUN = true
 * 
 * =====================================
 * 
 * PURPOSE: Identify and fix products with inflated inventory levels
 * 
 * PROCESS:
 * 1. Scan all products in Shopify for total inventory > 1
 * 2. Look up corresponding feed_item in database
 * 3. Fix inventory based on feed_item status:
 *    - SOLD status → Set inventory to 0
 *    - Available status → Set inventory to 1
 * 
 * METHODS:
 * - scanInventoryIssues(): READ-ONLY scan and report
 * - fixInflatedInventoryLevels(): Main fix method (respects DRY_RUN flag)
 * 
 * IMPORTANT: This test does NOT extend BaseGraphqlTest to avoid the destructive
 * setUp() method that would delete all products and data!
 * 
 * SAFETY FEATURES:
 * - Temporarily disables cron schedule during execution to prevent conflicts
 * - Does not clear existing data (unlike BaseGraphqlTest)
 * - Only performs actions when actually needed (smart validation)
 * - DRY_RUN mode for safe testing
 * 
 * Usage:
 * 
 * 1. Scan for inventory issues (read-only):
 *    mvn test -Dtest=InventoryFixTest#scanInventoryIssues -Dspring.profiles.active=keystone-prod
 * 
 * 2. Fix inventory issues (dry run):
 *    mvn test -Dtest=InventoryFixTest#fixInflatedInventoryLevels -Dspring.profiles.active=keystone-prod
 * 
 * 3. Fix inventory issues (live mode - edit DRY_RUN = false first):
 *    mvn test -Dtest=InventoryFixTest#fixInflatedInventoryLevels -Dspring.profiles.active=keystone-prod
 */
@SpringJUnitConfig
@SpringBootTest
@TestPropertySource(properties = {
    "cron.schedule=0 0 0 31 2 ?"  // Disable cron during inventory fixes (Feb 31st never exists)
})
public class InventoryFixTest {
    
    private static final Logger logger = LoggerFactory.getLogger(InventoryFixTest.class);
    private static boolean DRY_RUN = true; // Set to false to actually apply fixes
    
    @Autowired
    private ShopifyGraphQLService shopifyApiService;
    
    @Autowired
    private FeedItemService feedItemService;
    
    @Autowired
    private BaseShopifySyncService syncService;
    
    /**
     * 🚨 MAIN PRODUCTION FIX METHOD 🚨
     * 
     * This is the primary method for fixing inventory issues in production.
     * 
     * SAFETY: Respects DRY_RUN flag - set to false only when ready to apply real changes
     * 
     * PROCESS:
     * 1. Scans all Shopify products for inventory > 1
     * 2. Looks up feed_items in database
     * 3. Determines correct inventory based on status
     * 4. Generates detailed fix plan
     * 5. Applies fixes (if DRY_RUN = false)
     * 
     * USAGE:
     * mvn test -Dtest=InventoryFixTest#fixInflatedInventoryLevels
     * 
     * IMPORTANT: Always run scanInventoryIssues() first to understand scope!
     */
    @Test
    public void fixInflatedInventoryLevels() throws Exception {
        logger.info("=== Starting Inventory Fix Test - Production Data Cleanup ===");
        logger.info("🔍 Scanning for products with inflated inventory levels (total > 1)");
        
        if (DRY_RUN) {
            logger.warn("🧪 DRY RUN MODE - No actual changes will be made");
        } else {
            logger.info("🔧 LIVE MODE - Changes will be applied to Shopify");
        }
        
        // STEP 1: Get all products from Shopify
        logger.info("📡 Fetching all products from Shopify...");
        List<Product> allProducts = shopifyApiService.getAllProducts();
        logger.info("📊 Found {} total products in Shopify", allProducts.size());
        
        // STEP 2: Analyze inventory levels
        logger.info("🔍 Analyzing inventory levels for all products...");
        
        List<InventoryIssue> inventoryIssues = new ArrayList<>();
        int totalProducts = allProducts.size();
        int productsWithIssues = 0;
        int totalExcessInventory = 0;
        
        for (int i = 0; i < allProducts.size(); i++) {
            Product product = allProducts.get(i);
            
            if (i % 100 == 0) {
                logger.info("📊 Progress: {}/{} products analyzed", i, totalProducts);
            }
            
            try {
                // Calculate total inventory for this product
                int totalInventory = calculateTotalInventory(product);
                
                if (totalInventory > 1) {
                    // This product has inflated inventory
                    String sku = extractSKUFromProduct(product);
                    if (sku != null) {
                        InventoryIssue issue = new InventoryIssue();
                        issue.product = product;
                        issue.sku = sku;
                        issue.currentInventory = totalInventory;
                        issue.excessInventory = totalInventory - 1; // Assuming max should be 1
                        
                        inventoryIssues.add(issue);
                        productsWithIssues++;
                        totalExcessInventory += issue.excessInventory;
                        
                        logger.debug("🐛 Found inventory issue - SKU: {}, Current: {}, Excess: {}", 
                            sku, totalInventory, issue.excessInventory);
                    }
                }
            } catch (Exception e) {
                logger.warn("⚠️ Error analyzing product {}: {}", product.getId(), e.getMessage());
            }
        }
        
        logger.info("📊 Inventory Analysis Complete:");
        logger.info("  - Total products scanned: {}", totalProducts);
        logger.info("  - Products with inventory issues: {}", productsWithIssues);
        logger.info("  - Total excess inventory: {}", totalExcessInventory);
        logger.info("  - Percentage affected: {:.2f}%", 
            totalProducts > 0 ? (double) productsWithIssues / totalProducts * 100 : 0);
        
        if (inventoryIssues.isEmpty()) {
            logger.info("✅ No inventory issues found - all products have correct inventory levels");
            return;
        }
        
        // STEP 3: Look up feed items and determine correct inventory
        logger.info("🔍 Looking up feed items to determine correct inventory levels...");
        
        int feedItemsFound = 0;
        int feedItemsNotFound = 0;
        int soldItems = 0;
        int availableItems = 0;
        
        for (InventoryIssue issue : inventoryIssues) {
            try {
                FeedItem feedItem = feedItemService.findByWebTagNumber(issue.sku);
                
                if (feedItem != null) {
                    feedItemsFound++;
                    issue.feedItem = feedItem;
                    issue.feedItemStatus = feedItem.getWebStatus();
                    
                    // Determine correct inventory based on status
                    if ("SOLD".equalsIgnoreCase(feedItem.getWebStatus())) {
                        issue.correctInventory = 0;
                        soldItems++;
                    } else {
                        issue.correctInventory = 1;
                        availableItems++;
                    }
                    
                    issue.needsFix = (issue.currentInventory != issue.correctInventory);
                    
                } else {
                    feedItemsNotFound++;
                    issue.feedItem = null;
                    issue.feedItemStatus = "NOT_FOUND";
                    issue.correctInventory = 1; // Default to available if not found
                    issue.needsFix = true;
                    
                    logger.warn("⚠️ Feed item not found for SKU: {}", issue.sku);
                }
                
            } catch (Exception e) {
                logger.warn("⚠️ Error looking up feed item for SKU {}: {}", issue.sku, e.getMessage());
                feedItemsNotFound++;
            }
        }
        
        logger.info("📊 Feed Item Lookup Results:");
        logger.info("  - Feed items found: {}", feedItemsFound);
        logger.info("  - Feed items not found: {}", feedItemsNotFound);
        logger.info("  - SOLD items (should have 0 inventory): {}", soldItems);
        logger.info("  - Available items (should have 1 inventory): {}", availableItems);
        
        // STEP 4: Generate fix plan
        logger.info("🔧 Generating inventory fix plan...");
        
        List<InventoryIssue> fixableIssues = new ArrayList<>();
        int totalInventoryToReduce = 0;
        int totalInventoryToIncrease = 0;
        
        for (InventoryIssue issue : inventoryIssues) {
            if (issue.needsFix) {
                fixableIssues.add(issue);
                
                int difference = issue.correctInventory - issue.currentInventory;
                if (difference < 0) {
                    totalInventoryToReduce += Math.abs(difference);
                } else {
                    totalInventoryToIncrease += difference;
                }
            }
        }
        
        logger.info("📋 Inventory Fix Plan:");
        logger.info("  - Issues that need fixing: {}", fixableIssues.size());
        logger.info("  - Total inventory to reduce: {}", totalInventoryToReduce);
        logger.info("  - Total inventory to increase: {}", totalInventoryToIncrease);
        
        // STEP 5: Display detailed fix plan
        logger.info("📝 Detailed Fix Plan:");
        logger.info("=" .repeat(120));
        logger.info(String.format("%-15s %-50s %-10s %-10s %-10s %-10s %-15s", 
            "SKU", "Product Title", "Current", "Correct", "Diff", "Status", "Action"));
        logger.info("=" .repeat(120));
        
        for (InventoryIssue issue : fixableIssues) {
            String title = issue.product.getTitle();
            if (title.length() > 47) {
                title = title.substring(0, 47) + "...";
            }
            
            int difference = issue.correctInventory - issue.currentInventory;
            String action = difference < 0 ? "REDUCE" : "INCREASE";
            
            logger.info(String.format("%-15s %-50s %-10d %-10d %-10d %-10s %-15s",
                issue.sku,
                title,
                issue.currentInventory,
                issue.correctInventory,
                Math.abs(difference),
                issue.feedItemStatus,
                action));
        }
        logger.info("=" .repeat(120));
        
        // STEP 6: Apply fixes if not in dry run mode
        if (!DRY_RUN && !fixableIssues.isEmpty()) {
            logger.info("🔧 Applying inventory fixes...");
            
            int successfulFixes = 0;
            int failedFixes = 0;
            
            for (InventoryIssue issue : fixableIssues) {
                try {
                    logger.info("🔧 Fixing SKU {}: {} → {}", 
                        issue.sku, issue.currentInventory, issue.correctInventory);
                    
                    boolean success = applyInventoryFix(issue);
                    
                    if (success) {
                        successfulFixes++;
                        logger.info("✅ Successfully fixed inventory for SKU: {}", issue.sku);
                    } else {
                        failedFixes++;
                        logger.error("❌ Failed to fix inventory for SKU: {}", issue.sku);
                    }
                    
                    // Small delay to avoid rate limiting
                    Thread.sleep(100);
                    
                } catch (Exception e) {
                    failedFixes++;
                    logger.error("❌ Error fixing inventory for SKU {}: {}", issue.sku, e.getMessage());
                }
            }
            
            logger.info("📊 Inventory Fix Results:");
            logger.info("  - Successful fixes: {}", successfulFixes);
            logger.info("  - Failed fixes: {}", failedFixes);
            logger.info("  - Success rate: {:.2f}%", 
                fixableIssues.size() > 0 ? (double) successfulFixes / fixableIssues.size() * 100 : 0);
            
        } else if (DRY_RUN) {
            logger.info("🧪 DRY RUN - No changes applied. Set DRY_RUN = false to apply fixes.");
        }
        
        logger.info("=== Inventory Fix Test Complete ===");
    }
    
    /**
     * Apply inventory fix for a specific issue
     */
    private boolean applyInventoryFix(InventoryIssue issue) throws Exception {
        Product product = issue.product;
        int targetInventory = issue.correctInventory;
        
        // Get the first variant (assuming single variant products)
        if (product.getVariants() == null || product.getVariants().isEmpty()) {
            logger.warn("⚠️ No variants found for product {}", product.getId());
            return false;
        }
        
        Variant variant = product.getVariants().get(0);
        
        if (variant.getInventoryLevels() == null || variant.getInventoryLevels().get() == null) {
            logger.warn("⚠️ No inventory levels found for variant {}", variant.getId());
            return false;
        }
        
        // Update inventory levels
        boolean success = true;
        List<InventoryLevel> inventoryLevels = variant.getInventoryLevels().get();
        
        try {
            for (int i = 0; i < inventoryLevels.size(); i++) {
                InventoryLevel level = inventoryLevels.get(i);
                
                int newQuantity;
                if (i == 0) {
                    // First location gets all inventory
                    newQuantity = targetInventory;
                } else {
                    // Other locations get 0
                    newQuantity = 0;
                }
                
                // Update inventory level
                level.setAvailable(String.valueOf(newQuantity));
                
                logger.debug("✅ Prepared inventory for location {}: {}", 
                    level.getLocationId(), newQuantity);
            }
            
            // Apply all inventory level updates via Shopify API
            shopifyApiService.updateInventoryLevels(inventoryLevels);
            logger.info("✅ Successfully updated inventory levels for variant: {}", variant.getId());
            
        } catch (Exception e) {
            logger.error("❌ Failed to update inventory levels for variant {}: {}", 
                variant.getId(), e.getMessage());
            success = false;
        }
        
        return success;
    }
    
    /**
     * Calculate total inventory across all variants and locations
     */
    private int calculateTotalInventory(Product product) {
        int total = 0;
        
        if (product.getVariants() != null) {
            for (Variant variant : product.getVariants()) {
                if (variant.getInventoryLevels() != null && variant.getInventoryLevels().get() != null) {
                    for (InventoryLevel level : variant.getInventoryLevels().get()) {
                        try {
                            total += Integer.parseInt(level.getAvailable());
                        } catch (NumberFormatException e) {
                            logger.warn("Invalid inventory quantity: {}", level.getAvailable());
                        }
                    }
                }
            }
        }
        
        return total;
    }
    
    /**
     * Extract SKU from product (assuming SKU is in the first variant)
     */
    private String extractSKUFromProduct(Product product) {
        if (product.getVariants() != null && !product.getVariants().isEmpty()) {
            Variant firstVariant = product.getVariants().get(0);
            return firstVariant.getSku();
        }
        return null;
    }
    
    /**
     * Data class to hold inventory issue information
     */
    private static class InventoryIssue {
        Product product;
        String sku;
        int currentInventory;
        int correctInventory;
        int excessInventory;
        FeedItem feedItem;
        String feedItemStatus;
        boolean needsFix;
    }
    
    /**
     * 🔍 SAFE SCAN METHOD - READ ONLY 🔍
     * 
     * This method is SAFE to run anytime in production - it only reads data.
     * 
     * PURPOSE:
     * - Identifies products with inventory issues
     * - Reports statistics and breakdowns
     * - Shows what needs fixing WITHOUT making changes
     * 
     * WHAT IT DOES:
     * 1. Scans all Shopify products
     * 2. Finds products with total inventory > 1
     * 3. Looks up feed_item status for each
     * 4. Reports issues by status (SOLD/AVAILABLE/NOT_FOUND)
     * 
     * USAGE:
     * mvn test -Dtest=InventoryFixTest#scanInventoryIssues
     * 
     * SAFE: No changes made to Shopify or database
     * RECOMMENDED: Run this first before any fixes
     */
    @Test
    public void scanInventoryIssues() throws Exception {
        logger.info("=== Inventory Issue Scanner - Report Only ===");
        logger.info("🔍 Scanning for inventory issues without applying fixes");
        
        // Get all products
        List<Product> allProducts = shopifyApiService.getAllProducts();
        logger.info("📊 Scanning {} products for inventory issues", allProducts.size());
        
        Map<String, Integer> statusCounts = new HashMap<>();
        int totalIssues = 0;
        int totalExcessInventory = 0;
        
        for (Product product : allProducts) {
            int totalInventory = calculateTotalInventory(product);
            
            if (totalInventory > 1) {
                String sku = extractSKUFromProduct(product);
                if (sku != null) {
                    FeedItem feedItem = feedItemService.findByWebTagNumber(sku);
                    String status = feedItem != null ? feedItem.getWebStatus() : "NOT_FOUND";
                    
                    statusCounts.put(status, statusCounts.getOrDefault(status, 0) + 1);
                    totalIssues++;
                    totalExcessInventory += (totalInventory - 1);
                    
                    logger.info("🐛 Inventory Issue - SKU: {}, Status: {}, Current: {}, Excess: {}", 
                        sku, status, totalInventory, totalInventory - 1);
                }
            }
        }
        
        logger.info("📊 Inventory Issues Summary:");
        logger.info("  - Total products with issues: {}", totalIssues);
        logger.info("  - Total excess inventory: {}", totalExcessInventory);
        logger.info("  - Issues by status:");
        
        for (Map.Entry<String, Integer> entry : statusCounts.entrySet()) {
            logger.info("    - {}: {} products", entry.getKey(), entry.getValue());
        }
        
        if (totalIssues == 0) {
            logger.info("✅ No inventory issues found!");
        }
    }
    
    // NOTE: Demo test methods removed to prevent accidental data creation in production
    // This is a PRODUCTION TOOL - only scan and fix methods should be available
} 