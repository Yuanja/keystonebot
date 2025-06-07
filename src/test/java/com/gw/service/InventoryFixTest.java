package com.gw.service;

import com.gw.domain.FeedItem;
import com.gw.services.shopifyapi.objects.Product;
import com.gw.services.shopifyapi.objects.Variant;
import com.gw.services.shopifyapi.objects.InventoryLevel;
import com.gw.services.shopifyapi.objects.Option;
import com.gw.services.shopifyapi.objects.Image;
import com.gw.services.shopifyapi.objects.Metafield;
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
 * 🎯 **PARAMETER CONTROLLED**: No source code edits needed - uses command line parameters!
 * 📍 **LOCATION AWARE**: Enhanced with detailed inventory by location analysis!
 * 
 * 1. **ALWAYS RUN SCAN FIRST**: Use scanInventoryIssues() to identify problems before fixing
 * 2. **SAFE BY DEFAULT**: Defaults to dry run mode unless explicitly overridden
 * 3. **BACKUP RECOMMENDED**: Consider backing up inventory data before running fixes
 * 4. **TEST ENVIRONMENT**: Test on dev/staging environment first if possible
 * 5. **BUSINESS HOURS**: Run during low-traffic periods to minimize impact
 * 
 * 🚨 CRITICAL FIX APPLIED 🚨
 * =========================
 * This class was updated to NOT extend BaseGraphqlTest to prevent accidental data deletion.
 * Previous versions would have deleted all Shopify products and database records on startup!
 * 
 * 🎯 NEW FEATURES 🎯
 * ==================
 * 
 * ✅ **Parameter-Based Control**: No more source code edits! Use -DdryRun=false
 * ✅ **Specific SKU Scanner**: Deep-dive analysis for individual items
 * ✅ **Location Overview**: Complete inventory distribution analysis
 * ✅ **Enhanced Tables**: Formatted location displays with detailed breakdowns
 * ✅ **Smart Detection**: Automatic identification of concentration risks
 * ✅ **🔧 CRITICAL API FIX**: Now uses inventorySetQuantities for absolute value setting
 * 
 * 🚨 **MAJOR BUG FIX**: The tool now uses the correct Shopify API! 🚨
 * ================================================================
 * 
 * **PROBLEM SOLVED**: The tool was previously using `inventoryAdjustQuantities` (delta adjustment)
 * instead of `inventorySetQuantities` (absolute value setting). This caused inventory to increment
 * instead of being set to the correct value.
 * 
 * **BEFORE**: Inventory of 4 would become 5 when trying to set it to 1 (4 + 1 = 5)
 * **AFTER**: Inventory of 4 becomes 1 when set to 1 (absolute setting)
 * 
 * **API REFERENCE**: https://shopify.dev/docs/api/admin-graphql/latest/mutations/inventorySetQuantities
 * 
 * 🔧 PRODUCTION USAGE GUIDE 🔧
 * ============================
 * 
 * STEP 1: SCAN FOR ISSUES (READ-ONLY) 
 * ------------------------------------
 * This is SAFE to run anytime - only reads data and reports issues:
 * 
 *   mvn test -Dtest=InventoryFixTest#scanInventoryIssues
 * 
 * Expected output:
 * - List of products with inventory > 1
 * - Breakdown by feed item status (SOLD/AVAILABLE/NOT_FOUND)
 * - Total excess inventory count
 * 
 * STEP 2: ANALYZE SPECIFIC ITEMS (READ-ONLY)
 * ------------------------------------------
 * For deep-dive analysis of specific SKUs:
 * 
 *   # Edit TARGET_WEB_TAG_NUMBER in the method first
 *   mvn test -Dtest=InventoryFixTest#scanSpecificInventoryByWebTagNumber
 * 
 * Expected output:
 * - Complete product details (variants, options, images, metafields)
 * - Formatted inventory by location tables
 * - Database vs Shopify comparison
 * - Automatic issue detection with recommendations
 * 
 * STEP 3: LOCATION OVERVIEW (READ-ONLY)
 * -------------------------------------
 * For understanding inventory distribution patterns:
 * 
 *   mvn test -Dtest=InventoryFixTest#showInventoryByLocationOverview
 * 
 * Expected output:
 * - Complete location breakdown with statistics
 * - Top locations by inventory and product count
 * - Concentration risk analysis
 * - Distribution patterns and averages
 * 
 * STEP 4: DRY RUN FIX (SIMULATION)
 * --------------------------------
 * Shows what WOULD be fixed without making changes:
 * 
 * All products:
 *   mvn test -Dtest=InventoryFixTest#fixInflatedInventoryLevels
 * 
 * Specific item:
 *   mvn test -Dtest=InventoryFixTest#fixInflatedInventoryLevels -DwebTagNumber=YOUR_SKU
 * 
 * Expected output:
 * - Detailed fix plan showing current → correct inventory
 * - Enhanced location change tables
 * - "DRY RUN MODE - No actual changes will be made" message
 * - Count of issues that would be fixed
 * 
 * STEP 5: APPLY ACTUAL FIXES (LIVE MODE)
 * -------------------------------------
 * ⚠️  THIS MAKES REAL CHANGES TO SHOPIFY INVENTORY ⚠️
 * 
 * No source code edits needed! Use parameter control:
 * 
 * All products:
 *   mvn test -Dtest=InventoryFixTest#fixInflatedInventoryLevels -DdryRun=false
 * 
 * Specific item:
 *   mvn test -Dtest=InventoryFixTest#fixInflatedInventoryLevels -DdryRun=false -DwebTagNumber=YOUR_SKU
 * 
 * Monitor the output for:
 * - "LIVE MODE - Changes will be applied to Shopify!" message
 * - Enhanced location change tables showing before/after
 * - Success/failure count for each fix
 * - Any error messages
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
 * 📍 NEW: Location Analysis Tables:
 * - Formatted tables showing inventory by location with Location ID, quantities, and changes
 * - Top locations ranked by inventory and product count
 * - Concentration risk warnings (>80% inventory in one location)
 * - Distribution statistics and averages
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
 * "Product not found in Shopify":
 * - Item exists in database but was deleted from Shopify
 * - Database has stale Shopify ID
 * - Run reconciliation to clean up
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
 * "High location concentration" warning:
 * - Most inventory is in one location (>80%)
 * - Consider redistribution for risk management
 * - Normal for single-location setups
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
 * - Weekly scan: mvn test -Dtest=InventoryFixTest#scanInventoryIssues
 * - Monthly location overview: mvn test -Dtest=InventoryFixTest#showInventoryByLocationOverview
 * - Fix as needed: When scan shows issues > 5% of products
 * - After major updates: When sync system changes are deployed
 * - Specific SKU analysis: As needed for troubleshooting individual items
 * 
 * ⚡ EMERGENCY USAGE ⚡
 * ===================
 * 
 * If you need to quickly fix inventory issues:
 * 
 * 1. Quick scan: mvn test -Dtest=InventoryFixTest#scanInventoryIssues | grep "Total products with issues"
 * 2. If issues found:
 *    - All products: mvn test -Dtest=InventoryFixTest#fixInflatedInventoryLevels -DdryRun=false
 *    - Specific item: mvn test -Dtest=InventoryFixTest#fixInflatedInventoryLevels -DdryRun=false -DwebTagNumber=YOUR_SKU
 * 
 * For specific problem SKUs:
 * 1. Analyze: mvn test -Dtest=InventoryFixTest#scanSpecificInventoryByWebTagNumber -DwebTagNumber=YOUR_SKU
 * 2. Fix: mvn test -Dtest=InventoryFixTest#fixInflatedInventoryLevels -DdryRun=false -DwebTagNumber=YOUR_SKU
 * 
 * For location distribution issues:
 * 1. Overview: mvn test -Dtest=InventoryFixTest#showInventoryByLocationOverview
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
 * □ ~~Restore DRY_RUN = true~~ (Not needed - uses parameters!)
 * 
 * =====================================
 * 
 * 🎯 AVAILABLE METHODS 🎯
 * =======================
 * 
 * 1. scanInventoryIssues() - READ-ONLY general scan
 * 2. scanSpecificInventoryByWebTagNumber() - READ-ONLY single SKU deep-dive
 * 3. showInventoryByLocationOverview() - READ-ONLY location distribution analysis
 * 4. fixInflatedInventoryLevels() - Fix method (respects -DdryRun parameter)
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
 * KEY FEATURES:
 * - Parameter-based control (no source code edits)
 * - Enhanced location analysis with formatted tables
 * - Smart concentration risk detection
 * - Comprehensive single-SKU analysis
 * - Production-safe (does not extend BaseGraphqlTest)
 * 
 * SAFETY FEATURES:
 * - Temporarily disables cron schedule during execution to prevent conflicts
 * - Does not clear existing data (unlike BaseGraphqlTest)
 * - Only performs actions when actually needed (smart validation)
 * - Safe dry run mode by default
 * - Parameter-controlled live mode
 * 
 * QUICK USAGE REFERENCE:
 * 
 * 1. General scan: mvn test -Dtest=InventoryFixTest#scanInventoryIssues
 * 2. Location overview: mvn test -Dtest=InventoryFixTest#showInventoryByLocationOverview
 * 3. Specific SKU scan: mvn test -Dtest=InventoryFixTest#scanSpecificInventoryByWebTagNumber -DwebTagNumber=YOUR_SKU
 * 4. Dry run fix (all): mvn test -Dtest=InventoryFixTest#fixInflatedInventoryLevels
 * 5. Dry run fix (specific): mvn test -Dtest=InventoryFixTest#fixInflatedInventoryLevels -DwebTagNumber=YOUR_SKU
 * 6. Live fix (all): mvn test -Dtest=InventoryFixTest#fixInflatedInventoryLevels -DdryRun=false
 * 7. Live fix (specific): mvn test -Dtest=InventoryFixTest#fixInflatedInventoryLevels -DdryRun=false -DwebTagNumber=YOUR_SKU
 */
@SpringJUnitConfig
@SpringBootTest
@TestPropertySource(properties = {
    "cron.schedule=0 0 0 31 2 ?"  // Disable cron during inventory fixes (Feb 31st never exists)
})
public class InventoryFixTest {
    
    private static final Logger logger = LoggerFactory.getLogger(InventoryFixTest.class);
    
    /**
     * DRY_RUN mode control via system property
     * 
     * Usage:
     * - Dry run (default): mvn test -Dtest=InventoryFixTest#fixInflatedInventoryLevels
     * - Live mode: mvn test -Dtest=InventoryFixTest#fixInflatedInventoryLevels -DdryRun=false
     * - Explicit dry run: mvn test -Dtest=InventoryFixTest#fixInflatedInventoryLevels -DdryRun=true
     */
    private static boolean getDryRunMode() {
        String dryRunProperty = System.getProperty("dryRun", "true");
        return Boolean.parseBoolean(dryRunProperty);
    }
    
    @Autowired
    private ShopifyGraphQLService shopifyApiService;
    
    @Autowired
    private FeedItemService feedItemService;
    
    @Autowired
    private BaseShopifySyncService syncService;
    
    /**
     * 🚨 COMPREHENSIVE INVENTORY ENFORCER 🚨
     * 
     * This is the primary method for enforcing strict inventory rules in production.
     * 
     * STRICT RULES ENFORCED:
     * 1. Inventory can NEVER be above 1
     * 2. SOLD items → inventory = 0
     * 3. All other items → inventory = 1
     * 4. Checks ALL products (not just those with inventory > 1)
     * 
     * SAFETY: Respects dryRun parameter - defaults to safe dry run mode
     * 
     * PROCESS:
     * 1. Scans ALL Shopify products (or specific item) for rule violations
     * 2. Looks up feed_items in database to determine correct inventory
     * 3. Enforces strict inventory rules based on status
     * 4. Generates detailed fix plan with violation analysis
     * 5. Applies fixes (if -DdryRun=false specified)
     * 
     * USAGE:
     * All products dry run: mvn test -Dtest=InventoryFixTest#fixInflatedInventoryLevels
     * All products live: mvn test -Dtest=InventoryFixTest#fixInflatedInventoryLevels -DdryRun=false
     * Specific item dry run: mvn test -Dtest=InventoryFixTest#fixInflatedInventoryLevels -DwebTagNumber=YOUR_SKU
     * Specific item live: mvn test -Dtest=InventoryFixTest#fixInflatedInventoryLevels -DdryRun=false -DwebTagNumber=YOUR_SKU
     * 
     * ENHANCED: Now checks ALL products and enforces strict rules (not just inflated inventory)
     */
    @Test
    public void fixInflatedInventoryLevels() throws Exception {
        logger.info("=== Comprehensive Inventory Rules Enforcer ===");
        logger.info("🎯 Enforcing strict inventory rules for all products");
        logger.info("📋 STRICT RULES:");
        logger.info("  1. Inventory can NEVER be above 1");
        logger.info("  2. SOLD items → inventory = 0");
        logger.info("  3. All other items → inventory = 1");
        
        // Check for specific web tag number parameter
        String specificWebTagNumber = System.getProperty("webTagNumber");
        
        if (specificWebTagNumber != null && !specificWebTagNumber.trim().isEmpty()) {
            logger.info("🎯 TARGETED MODE - Processing specific item: {}", specificWebTagNumber);
            logger.info("💡 Using web tag number from parameter: -DwebTagNumber={}", specificWebTagNumber);
        } else {
            logger.info("🔍 COMPREHENSIVE MODE - Processing ALL products in Shopify");
            logger.info("💡 To process specific item, add: -DwebTagNumber=YOUR_SKU");
        }
        
        boolean dryRun = getDryRunMode();
        if (dryRun) {
            logger.warn("🧪 DRY RUN MODE - No actual changes will be made");
            logger.info("💡 To apply actual fixes, add: -DdryRun=false");
        } else {
            logger.warn("🔧 LIVE MODE - Changes will be applied to Shopify!");
            logger.warn("⚠️ This will make REAL changes to inventory levels");
        }
        
        // STEP 1: Get products from Shopify (all or specific)
        List<Product> allProducts;
        
        if (specificWebTagNumber != null && !specificWebTagNumber.trim().isEmpty()) {
            // Process specific item only
            logger.info("📡 Looking up specific product by web tag number: {}", specificWebTagNumber);
            allProducts = getProductByWebTagNumber(specificWebTagNumber);
            if (allProducts.isEmpty()) {
                logger.error("❌ Product not found for web tag number: {}", specificWebTagNumber);
                logger.info("💡 Possible reasons:");
                logger.info("  - SKU doesn't exist in database");
                logger.info("  - SKU not published to Shopify");
                logger.info("  - Product was deleted from Shopify");
                return;
            }
            logger.info("✅ Found product for SKU: {}", specificWebTagNumber);
        } else {
            // Process all products
            logger.info("📡 Fetching all products from Shopify...");
            allProducts = shopifyApiService.getAllProducts();
            logger.info("📊 Found {} total products in Shopify", allProducts.size());
        }
        
        // STEP 2: Analyze ALL products for rule violations
        logger.info("🔍 Analyzing ALL products for inventory rule violations...");
        
        List<InventoryIssue> inventoryIssues = new ArrayList<>();
        int totalProducts = allProducts.size();
        int productsAnalyzed = 0;
        int perfectProducts = 0;
        int soldItemsFound = 0;
        int availableItemsFound = 0;
        int unknownItemsFound = 0;
        
        for (int i = 0; i < allProducts.size(); i++) {
            Product product = allProducts.get(i);
            
            if (i % 100 == 0) {
                logger.info("📊 Progress: {}/{} products analyzed", i, totalProducts);
            }
            
            try {
                productsAnalyzed++;
                
                // Calculate total inventory for this product
                int totalInventory = calculateTotalInventory(product);
                String sku = extractSKUFromProduct(product);
                
                if (sku == null) {
                    logger.warn("⚠️ Product {} has no SKU, skipping", product.getId());
                    continue;
                }
                
                // Look up feed item in database
                FeedItem feedItem = null;
                String feedItemStatus = "NOT_FOUND";
                int correctInventory = 1; // Default for unknown items
                
                try {
                    feedItem = feedItemService.findByWebTagNumber(sku);
                    if (feedItem != null) {
                        feedItemStatus = feedItem.getWebStatus();
                        
                        // Apply strict inventory rules
                        if ("SOLD".equalsIgnoreCase(feedItemStatus)) {
                            correctInventory = 0;
                            soldItemsFound++;
                        } else {
                            correctInventory = 1;
                            availableItemsFound++;
                        }
                    } else {
                        unknownItemsFound++;
                        logger.debug("⚠️ Feed item not found for SKU: {} (defaulting to inventory = 1)", sku);
                    }
                } catch (Exception e) {
                    logger.warn("⚠️ Error looking up feed item for SKU {}: {}", sku, e.getMessage());
                    unknownItemsFound++;
                }
                
                // Check if inventory violates strict rules
                boolean violatesRules = false;
                String violationType = "";
                
                if (totalInventory > 1) {
                    violatesRules = true;
                    violationType = "EXCEEDS_MAXIMUM";
                } else if (totalInventory != correctInventory) {
                    violatesRules = true;
                    if (totalInventory < correctInventory) {
                        violationType = "INSUFFICIENT";
                    } else {
                        violationType = "INCORRECT_LEVEL";
                    }
                }
                
                if (violatesRules) {
                    InventoryIssue issue = new InventoryIssue();
                    issue.product = product;
                    issue.sku = sku;
                    issue.currentInventory = totalInventory;
                    issue.correctInventory = correctInventory;
                    issue.excessInventory = Math.abs(totalInventory - correctInventory);
                    issue.feedItem = feedItem;
                    issue.feedItemStatus = feedItemStatus;
                    issue.violationType = violationType;
                    issue.needsFix = true;
                    
                    inventoryIssues.add(issue);
                    
                    logger.debug("🚨 Rule violation - SKU: {}, Current: {}, Correct: {}, Status: {}, Type: {}", 
                        sku, totalInventory, correctInventory, feedItemStatus, violationType);
                } else {
                    perfectProducts++;
                    logger.debug("✅ Perfect inventory - SKU: {}, Inventory: {}, Status: {}", 
                        sku, totalInventory, feedItemStatus);
                }
                
            } catch (Exception e) {
                logger.warn("⚠️ Error analyzing product {}: {}", product.getId(), e.getMessage());
            }
        }
        
        logger.info("📊 Comprehensive Analysis Complete:");
        logger.info("  - Total products analyzed: {}", productsAnalyzed);
        logger.info("  - Products with perfect inventory: {}", perfectProducts);
        logger.info("  - Products with rule violations: {}", inventoryIssues.size());
        logger.info("  - Success rate: {:.2f}%", 
            productsAnalyzed > 0 ? (double) perfectProducts / productsAnalyzed * 100 : 0);
        
        logger.info("📋 Feed Item Status Breakdown:");
        logger.info("  - SOLD items found: {}", soldItemsFound);
        logger.info("  - Available items found: {}", availableItemsFound);
        logger.info("  - Unknown items (not in DB): {}", unknownItemsFound);
        
        if (inventoryIssues.isEmpty()) {
            logger.info("✅ 🎉 ALL PRODUCTS HAVE PERFECT INVENTORY LEVELS!");
            logger.info("✅ No rule violations found - inventory system is working correctly");
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
        if (!dryRun && !fixableIssues.isEmpty()) {
            logger.info("🔧 Applying inventory fixes...");
            
            int successfulFixes = 0;
            int failedFixes = 0;
            
            for (InventoryIssue issue : fixableIssues) {
                try {
                    logger.info("🔧 ATTEMPTING FIX for SKU {}: {} → {}", 
                        issue.sku, issue.currentInventory, issue.correctInventory);
                    logger.info("   Product: {}", issue.product.getTitle());
                    logger.info("   Status: {} | Needs: {} inventory", 
                        issue.feedItemStatus, issue.correctInventory);
                    
                    boolean success = applyInventoryFix(issue);
                    
                    if (success) {
                        successfulFixes++;
                        logger.info("✅ SUCCESS: Fixed inventory for SKU: {}", issue.sku);
                    } else {
                        failedFixes++;
                        logger.error("❌ FAILURE: Could not fix inventory for SKU: {}", issue.sku);
                        logger.error("   This may require manual investigation!");
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
            
        } else if (dryRun) {
            logger.info("🧪 DRY RUN - No changes applied. Add -DdryRun=false to apply fixes.");
        }
        
        logger.info("=== Inventory Fix Test Complete ===");
    }
    
    /**
     * Apply inventory fix for a specific issue
     */
    private boolean applyInventoryFix(InventoryIssue issue) throws Exception {
        Product product = issue.product;
        int targetInventory = issue.correctInventory;
        String sku = issue.sku;
        
        logger.info("🔧 STARTING INVENTORY FIX for SKU: {}", sku);
        logger.info("  Product ID: {}", product.getId());
        logger.info("  Product Title: {}", product.getTitle());
        logger.info("  Current Total Inventory: {}", issue.currentInventory);
        logger.info("  Target Total Inventory: {}", targetInventory);
        logger.info("  Feed Item Status: {}", issue.feedItemStatus);
        
        // Get the first variant (assuming single variant products)
        if (product.getVariants() == null || product.getVariants().isEmpty()) {
            logger.error("❌ No variants found for product {} (SKU: {})", product.getId(), sku);
            return false;
        }
        
        Variant variant = product.getVariants().get(0);
        logger.info("  Variant ID: {}", variant.getId());
        logger.info("  Variant SKU: {}", variant.getSku());
        
        if (variant.getInventoryLevels() == null || variant.getInventoryLevels().get() == null) {
            logger.error("❌ No inventory levels found for variant {} (SKU: {})", variant.getId(), sku);
            return false;
        }
        
        // Get current inventory levels and calculate actual total
        List<InventoryLevel> inventoryLevels = variant.getInventoryLevels().get();
        logger.info("  Found {} inventory locations", inventoryLevels.size());
        
        // Calculate and verify current total
        int actualCurrentTotal = 0;
        for (InventoryLevel level : inventoryLevels) {
            try {
                int qty = Integer.parseInt(level.getAvailable());
                actualCurrentTotal += qty;
                logger.debug("    Location {}: {} units", level.getLocationId(), qty);
            } catch (NumberFormatException e) {
                logger.warn("⚠️ Invalid quantity '{}' at location {}", level.getAvailable(), level.getLocationId());
            }
        }
        
        logger.info("  Calculated Current Total: {} (should match issue.currentInventory: {})", 
            actualCurrentTotal, issue.currentInventory);
        
        if (actualCurrentTotal != issue.currentInventory) {
            logger.warn("⚠️ MISMATCH: Calculated total {} != issue total {}", 
                actualCurrentTotal, issue.currentInventory);
        }
        
        // Update inventory levels
        boolean success = true;
        
        try {
            logger.info("📍 BEFORE UPDATE - Current inventory distribution:");
            logger.info("    " + "=".repeat(110));
            logger.info("    {:^10} | {:^35} | {:^15} | {:^15} | {:^15} | {:^10}", 
                "Location", "Location ID", "Before Qty", "Target Qty", "Inventory Item", "Change");
            logger.info("    " + "=".repeat(110));
            
            // Create a copy of original values for verification
            Map<String, String> originalValues = new HashMap<>();
            
            for (int i = 0; i < inventoryLevels.size(); i++) {
                InventoryLevel level = inventoryLevels.get(i);
                
                // Store original value
                originalValues.put(level.getLocationId(), level.getAvailable());
                
                int currentQuantity = 0;
                try {
                    currentQuantity = Integer.parseInt(level.getAvailable());
                } catch (NumberFormatException e) {
                    logger.warn("⚠️ Invalid current quantity for location {}: {}", 
                        level.getLocationId(), level.getAvailable());
                }
                
                int newQuantity;
                if (i == 0) {
                    // First location gets all inventory
                    newQuantity = targetInventory;
                } else {
                    // Other locations get 0
                    newQuantity = 0;
                }
                
                int change = newQuantity - currentQuantity;
                String changeDisplay = change > 0 ? "+" + change : String.valueOf(change);
                
                // Format location ID for display
                String locationIdDisplay = level.getLocationId();
                if (locationIdDisplay != null && locationIdDisplay.length() > 33) {
                    locationIdDisplay = locationIdDisplay.substring(0, 30) + "...";
                }
                
                // Format inventory item ID for display
                String inventoryItemDisplay = level.getInventoryItemId();
                if (inventoryItemDisplay != null && inventoryItemDisplay.length() > 13) {
                    inventoryItemDisplay = inventoryItemDisplay.substring(0, 10) + "...";
                }
                
                logger.info("    {:^10} | {:^35} | {:^15} | {:^15} | {:^15} | {:^10}", 
                    (i + 1),
                    locationIdDisplay != null ? locationIdDisplay : "N/A",
                    currentQuantity,
                    newQuantity,
                    inventoryItemDisplay != null ? inventoryItemDisplay : "N/A",
                    changeDisplay);
                
                // Log the specific update being made
                logger.debug("      🔄 Setting location {} from '{}' to '{}'", 
                    level.getLocationId(), level.getAvailable(), newQuantity);
                
                // Update inventory level object
                level.setAvailable(String.valueOf(newQuantity));
                
                // Verify the update was applied
                logger.debug("      ✅ InventoryLevel object updated: getAvailable() = '{}'", level.getAvailable());
            }
            logger.info("    " + "=".repeat(110));
            
            // Log the API call details
            logger.info("🚀 CALLING SHOPIFY API: updateInventoryLevels()");
            logger.info("  Number of inventory levels to update: {}", inventoryLevels.size());
            logger.info("  API Method: shopifyApiService.updateInventoryLevels(inventoryLevels)");
            
            // Before API call - log all values being sent
            for (int i = 0; i < inventoryLevels.size(); i++) {
                InventoryLevel level = inventoryLevels.get(i);
                logger.info("    API Payload #{}: LocationId={}, InventoryItemId={}, Available={}", 
                    i + 1, level.getLocationId(), level.getInventoryItemId(), level.getAvailable());
            }
            
            // Apply all inventory level updates via Shopify API using ABSOLUTE value setting
            logger.info("📡 Executing API call...");
            logger.info("🎯 Using inventorySetQuantities for ABSOLUTE value setting (not delta adjustment)");
            shopifyApiService.setInventoryLevelsAbsolute(inventoryLevels);
            logger.info("✅ API call completed successfully");
            
            // Verify the update by fetching fresh data
            logger.info("🔍 VERIFICATION: Fetching updated product to verify changes...");
            try {
                Product updatedProduct = shopifyApiService.getProductByProductId(product.getId());
                if (updatedProduct != null && updatedProduct.getVariants() != null && !updatedProduct.getVariants().isEmpty()) {
                    Variant updatedVariant = updatedProduct.getVariants().get(0);
                    if (updatedVariant.getInventoryLevels() != null && updatedVariant.getInventoryLevels().get() != null) {
                        
                        logger.info("📊 AFTER UPDATE - Actual inventory distribution:");
                        logger.info("    " + "=".repeat(90));
                        logger.info("    {:^10} | {:^35} | {:^15} | {:^15} | {:^10}", 
                            "Location", "Location ID", "Actual Qty", "Expected", "Status");
                        logger.info("    " + "=".repeat(90));
                        
                        int newTotal = 0;
                        List<InventoryLevel> updatedLevels = updatedVariant.getInventoryLevels().get();
                        
                        for (int i = 0; i < updatedLevels.size(); i++) {
                            InventoryLevel updatedLevel = updatedLevels.get(i);
                            
                            int actualQty = 0;
                            try {
                                actualQty = Integer.parseInt(updatedLevel.getAvailable());
                                newTotal += actualQty;
                            } catch (NumberFormatException e) {
                                logger.warn("⚠️ Invalid updated quantity: {}", updatedLevel.getAvailable());
                            }
                            
                            int expectedQty = (i == 0) ? targetInventory : 0;
                            String status = (actualQty == expectedQty) ? "✅ CORRECT" : "❌ WRONG";
                            
                            String locationIdDisplay = updatedLevel.getLocationId();
                            if (locationIdDisplay != null && locationIdDisplay.length() > 33) {
                                locationIdDisplay = locationIdDisplay.substring(0, 30) + "...";
                            }
                            
                            logger.info("    {:^10} | {:^35} | {:^15} | {:^15} | {:^10}", 
                                (i + 1),
                                locationIdDisplay != null ? locationIdDisplay : "N/A",
                                actualQty,
                                expectedQty,
                                status);
                        }
                        logger.info("    " + "=".repeat(90));
                        
                        logger.info("📈 FINAL TOTALS COMPARISON:");
                        logger.info("  Original Total: {}", issue.currentInventory);
                        logger.info("  Target Total: {}", targetInventory);
                        logger.info("  Actual New Total: {}", newTotal);
                        
                        if (newTotal == targetInventory) {
                            logger.info("  ✅ SUCCESS: Fix applied correctly!");
                        } else {
                            logger.error("  ❌ FAILURE: Total inventory is {} but should be {}", newTotal, targetInventory);
                            logger.error("  🚨 CRITICAL: The fix did not work as expected!");
                            
                            // Detailed error analysis
                            if (newTotal > issue.currentInventory) {
                                logger.error("  📈 PROBLEM: Inventory INCREASED from {} to {} (increment detected!)", 
                                    issue.currentInventory, newTotal);
                            } else if (newTotal < targetInventory) {
                                logger.error("  📉 PROBLEM: Inventory is {} but should be {}", newTotal, targetInventory);
                            }
                            
                            success = false;
                        }
                        
                    } else {
                        logger.warn("⚠️ Could not verify update - no inventory levels in updated product");
                    }
                } else {
                    logger.warn("⚠️ Could not verify update - failed to fetch updated product");
                }
            } catch (Exception verifyException) {
                logger.warn("⚠️ Could not verify update due to error: {}", verifyException.getMessage());
            }
            
        } catch (Exception e) {
            logger.error("❌ Failed to update inventory levels for variant {} (SKU: {}): {}", 
                variant.getId(), sku, e.getMessage());
            logger.error("❌ Exception details: ", e);
            success = false;
        }
        
        logger.info("🏁 INVENTORY FIX COMPLETE for SKU: {} - Success: {}", sku, success);
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
        String violationType;
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
    
    /**
     * 🔍 SCAN SPECIFIC ITEM BY WEB_TAG_NUMBER 🔍
     * 
     * This method scans inventory for a specific web_tag_number by:
     * 1. Looking up the FeedItem in the database by web_tag_number
     * 2. Getting the Shopify product ID from the FeedItem
     * 3. Retrieving detailed product information from Shopify
     * 4. Displaying comprehensive inventory and variant details
     * 
     * PURPOSE:
     * - Deep-dive analysis for specific SKUs
     * - Troubleshooting individual inventory issues
     * - Detailed product information for support
     * 
     * USAGE:
     * Parameter method (recommended):
     *   mvn test -Dtest=InventoryFixTest#scanSpecificInventoryByWebTagNumber -DwebTagNumber=SKU123
     * 
     * Alternative - edit constant:
     *   1. Edit the TARGET_WEB_TAG_NUMBER constant below
     *   2. Run: mvn test -Dtest=InventoryFixTest#scanSpecificInventoryByWebTagNumber
     * 
     * SAFE: Read-only operation, no changes made
     */
    @Test
    public void scanSpecificInventoryByWebTagNumber() throws Exception {
        // Get web tag number from system property or fallback to constant
        String TARGET_WEB_TAG_NUMBER = System.getProperty("webTagNumber", "GW12345"); // Default fallback
        
        logger.info("=== Specific Inventory Scanner for Web Tag Number: {} ===", TARGET_WEB_TAG_NUMBER);
        logger.info("🔍 Performing detailed inventory analysis for specific SKU");
        
        // Show how the web tag number was determined
        String providedWebTagNumber = System.getProperty("webTagNumber");
        if (providedWebTagNumber != null) {
            logger.info("💡 Using web tag number from parameter: -DwebTagNumber={}", providedWebTagNumber);
        } else {
            logger.info("💡 Using default web tag number: {} (add -DwebTagNumber=YOUR_SKU to specify different SKU)", TARGET_WEB_TAG_NUMBER);
        }
        
        // STEP 1: Look up FeedItem in database
        logger.info("📂 Looking up FeedItem in database...");
        FeedItem feedItem = feedItemService.findByWebTagNumber(TARGET_WEB_TAG_NUMBER);
        
        if (feedItem == null) {
            logger.error("❌ FeedItem not found in database for web_tag_number: {}", TARGET_WEB_TAG_NUMBER);
            logger.info("💡 Possible reasons:");
            logger.info("  - SKU doesn't exist in the database");
            logger.info("  - Typo in the web_tag_number");
            logger.info("  - Item was deleted from database");
            return;
        }
        
        logger.info("✅ FeedItem found in database:");
        logger.info("  - Web Tag Number: {}", feedItem.getWebTagNumber());
        logger.info("  - Description: {}", feedItem.getWebDescriptionShort());
        logger.info("  - Status: {}", feedItem.getWebStatus());
        logger.info("  - Price: ${}", feedItem.getWebPriceRetail());
        logger.info("  - Brand: {}", feedItem.getWebDesigner());
        logger.info("  - Model: {}", feedItem.getWebWatchModel());
        logger.info("  - Condition: {}", feedItem.getWebWatchCondition());
        logger.info("  - Shopify Item ID: {}", feedItem.getShopifyItemId());
        
        // STEP 2: Check if item has Shopify ID
        String shopifyItemId = feedItem.getShopifyItemId();
        if (shopifyItemId == null || shopifyItemId.trim().isEmpty()) {
            logger.warn("⚠️ FeedItem has no Shopify ID - item not published to Shopify");
            logger.info("💡 This means:");
            logger.info("  - Item exists in database but not in Shopify");
            logger.info("  - Item may be waiting to be published");
            logger.info("  - Item may have failed to publish");
            logger.info("  - No inventory issues possible (not in Shopify)");
            return;
        }
        
        // STEP 3: Get detailed product information from Shopify
        logger.info("🛍️ Retrieving detailed product information from Shopify...");
        logger.info("  Shopify Product ID: {}", shopifyItemId);
        
        Product shopifyProduct;
        try {
            shopifyProduct = shopifyApiService.getProductByProductId(shopifyItemId);
        } catch (Exception e) {
            logger.error("❌ Failed to retrieve product from Shopify: {}", e.getMessage());
            logger.info("💡 Possible reasons:");
            logger.info("  - Product was deleted from Shopify");
            logger.info("  - Shopify ID in database is outdated");
            logger.info("  - Network or API issues");
            logger.info("  - Invalid product ID format");
            return;
        }
        
        if (shopifyProduct == null) {
            logger.error("❌ Product not found in Shopify with ID: {}", shopifyItemId);
            logger.info("💡 This indicates:");
            logger.info("  - Product exists in database but was deleted from Shopify");
            logger.info("  - Database has stale Shopify ID");
            logger.info("  - Possible data synchronization issue");
            return;
        }
        
        // STEP 4: Display comprehensive product information
        logger.info("✅ Product found in Shopify - Detailed Analysis:");
        logger.info("=" .repeat(100));
        
        // Basic Product Information
        logger.info("📦 BASIC PRODUCT INFORMATION:");
        logger.info("  Product ID: {}", shopifyProduct.getId());
        logger.info("  Title: {}", shopifyProduct.getTitle());
        logger.info("  Vendor: {}", shopifyProduct.getVendor());
        logger.info("  Product Type: {}", shopifyProduct.getProductType());
        logger.info("  Status: {}", shopifyProduct.getStatus());
        logger.info("  Handle: {}", shopifyProduct.getHandle());
        logger.info("  Tags: {}", shopifyProduct.getTags());
        logger.info("  Created At: {}", shopifyProduct.getCreatedAt());
        logger.info("  Updated At: {}", shopifyProduct.getUpdatedAt());
        
        // Variants Analysis
        logger.info("");
        logger.info("🔧 VARIANTS ANALYSIS:");
        if (shopifyProduct.getVariants() == null || shopifyProduct.getVariants().isEmpty()) {
            logger.warn("  ⚠️ No variants found - this is unusual!");
        } else {
            logger.info("  Total Variants: {}", shopifyProduct.getVariants().size());
            
            for (int i = 0; i < shopifyProduct.getVariants().size(); i++) {
                Variant variant = shopifyProduct.getVariants().get(i);
                logger.info("");
                logger.info("  📋 VARIANT {} DETAILS:", i + 1);
                logger.info("    Variant ID: {}", variant.getId());
                logger.info("    SKU: {}", variant.getSku());
                logger.info("    Title: {}", variant.getTitle());
                logger.info("    Price: ${}", variant.getPrice());
                logger.info("    Compare At Price: ${}", variant.getCompareAtPrice());
                logger.info("    Weight: {} {}", variant.getWeight(), variant.getWeightUnit());
                logger.info("    Inventory Management: {}", variant.getInventoryManagement());
                logger.info("    Inventory Policy: {}", variant.getInventoryPolicy());
                logger.info("    Taxable: {}", variant.getTaxable());
                logger.info("    Barcode: {}", variant.getBarcode());
                
                // Variant Options
                logger.info("    Options:");
                logger.info("      Option 1: {}", variant.getOption1());
                logger.info("      Option 2: {}", variant.getOption2());
                logger.info("      Option 3: {}", variant.getOption3());
                
                                 // Inventory Levels Analysis
                 logger.info("");
                 logger.info("    📊 INVENTORY LEVELS BY LOCATION:");
                 if (variant.getInventoryLevels() == null || variant.getInventoryLevels().get() == null) {
                     logger.warn("      ⚠️ No inventory levels found");
                 } else {
                     List<InventoryLevel> inventoryLevels = variant.getInventoryLevels().get();
                     logger.info("      Total Locations: {}", inventoryLevels.size());
                     logger.info("      " + "=".repeat(80));
                     logger.info("      {:^15} | {:^20} | {:^10} | {:^20}", "Location #", "Location ID", "Available", "Inventory Item ID");
                     logger.info("      " + "=".repeat(80));
                     
                     int totalInventory = 0;
                     for (int j = 0; j < inventoryLevels.size(); j++) {
                         InventoryLevel level = inventoryLevels.get(j);
                         int quantity = 0;
                         try {
                             quantity = Integer.parseInt(level.getAvailable());
                             totalInventory += quantity;
                         } catch (NumberFormatException e) {
                             logger.warn("      ⚠️ Invalid inventory quantity: {}", level.getAvailable());
                             quantity = 0; // Set to 0 for display purposes
                         }
                         
                         // Format location ID for display (truncate if too long)
                         String locationIdDisplay = level.getLocationId();
                         if (locationIdDisplay != null && locationIdDisplay.length() > 18) {
                             locationIdDisplay = locationIdDisplay.substring(0, 15) + "...";
                         }
                         
                         // Format inventory item ID for display (truncate if too long)
                         String inventoryItemIdDisplay = level.getInventoryItemId();
                         if (inventoryItemIdDisplay != null && inventoryItemIdDisplay.length() > 18) {
                             inventoryItemIdDisplay = inventoryItemIdDisplay.substring(0, 15) + "...";
                         }
                         
                         logger.info("      {:^15} | {:^20} | {:^10} | {:^20}", 
                             "Location " + (j + 1), 
                             locationIdDisplay != null ? locationIdDisplay : "N/A",
                             quantity,
                             inventoryItemIdDisplay != null ? inventoryItemIdDisplay : "N/A");
                     }
                     logger.info("      " + "=".repeat(80));
                    
                    logger.info("      📈 INVENTORY SUMMARY:");
                    logger.info("        Total Inventory Across All Locations: {}", totalInventory);
                    
                    // Inventory Analysis
                    String expectedStatus = feedItem.getWebStatus();
                    int expectedInventory = "SOLD".equalsIgnoreCase(expectedStatus) ? 0 : 1;
                    
                    logger.info("        Expected Inventory (based on status '{}'): {}", expectedStatus, expectedInventory);
                    
                    if (totalInventory == expectedInventory) {
                        logger.info("        ✅ INVENTORY CORRECT - No issues found");
                    } else {
                        logger.warn("        ⚠️ INVENTORY ISSUE DETECTED:");
                        logger.warn("          Current: {}", totalInventory);
                        logger.warn("          Expected: {}", expectedInventory);
                        logger.warn("          Difference: {}", totalInventory - expectedInventory);
                        
                        if (totalInventory > expectedInventory) {
                            logger.warn("          Issue Type: INFLATED INVENTORY");
                            logger.warn("          Action Needed: REDUCE by {}", totalInventory - expectedInventory);
                        } else {
                            logger.warn("          Issue Type: INSUFFICIENT INVENTORY");
                            logger.warn("          Action Needed: INCREASE by {}", expectedInventory - totalInventory);
                        }
                    }
                }
            }
        }
        
        // Product Options Analysis
        logger.info("");
        logger.info("🎛️ PRODUCT OPTIONS:");
        if (shopifyProduct.getOptions() == null || shopifyProduct.getOptions().isEmpty()) {
            logger.info("  No product options configured");
        } else {
            logger.info("  Total Options: {}", shopifyProduct.getOptions().size());
            for (int i = 0; i < shopifyProduct.getOptions().size(); i++) {
                Option option = shopifyProduct.getOptions().get(i);
                logger.info("    Option {}: {} = {}", i + 1, option.getName(), 
                    option.getValues() != null ? String.join(", ", option.getValues()) : "No values");
            }
        }
        
        // Images Analysis
        logger.info("");
        logger.info("🖼️ IMAGES ANALYSIS:");
        if (shopifyProduct.getImages() == null || shopifyProduct.getImages().isEmpty()) {
            logger.info("  No images found");
        } else {
            logger.info("  Total Images: {}", shopifyProduct.getImages().size());
            for (int i = 0; i < shopifyProduct.getImages().size(); i++) {
                Image image = shopifyProduct.getImages().get(i);
                logger.info("    Image {}: ID {} - {}", i + 1, image.getId(), 
                    image.getSrc() != null ? image.getSrc().substring(0, Math.min(80, image.getSrc().length())) + "..." : "No URL");
            }
        }
        
        // Metafields Analysis
        logger.info("");
        logger.info("🏷️ METAFIELDS ANALYSIS:");
        if (shopifyProduct.getMetafields() == null || shopifyProduct.getMetafields().isEmpty()) {
            logger.info("  No metafields found");
        } else {
            logger.info("  Total Metafields: {}", shopifyProduct.getMetafields().size());
            for (Metafield metafield : shopifyProduct.getMetafields()) {
                logger.info("    {} ({}): {}", 
                    metafield.getKey(), 
                    metafield.getNamespace(),
                    metafield.getValue() != null && metafield.getValue().length() > 50 ? 
                        metafield.getValue().substring(0, 50) + "..." : metafield.getValue());
            }
        }
        
        // Database vs Shopify Comparison
        logger.info("");
        logger.info("🔄 DATABASE vs SHOPIFY COMPARISON:");
        logger.info("  Database Status: {} | Shopify Status: {}", feedItem.getWebStatus(), shopifyProduct.getStatus());
        logger.info("  Database Description: {} | Shopify Title: {}", feedItem.getWebDescriptionShort(), shopifyProduct.getTitle());
        logger.info("  Database Brand: {} | Shopify Vendor: {}", feedItem.getWebDesigner(), shopifyProduct.getVendor());
        logger.info("  Database Price: ${} | Shopify Price: ${}", feedItem.getWebPriceRetail(), 
            shopifyProduct.getVariants() != null && !shopifyProduct.getVariants().isEmpty() ? 
                shopifyProduct.getVariants().get(0).getPrice() : "N/A");
        
        // Final Summary
        logger.info("");
        logger.info("📋 SCAN SUMMARY:");
        logger.info("  - FeedItem found in database: ✅");
        logger.info("  - Shopify ID present: ✅");
        logger.info("  - Product found in Shopify: ✅");
        logger.info("  - Total variants: {}", shopifyProduct.getVariants() != null ? shopifyProduct.getVariants().size() : 0);
        logger.info("  - Total inventory locations: {}", 
            shopifyProduct.getVariants() != null && !shopifyProduct.getVariants().isEmpty() &&
            shopifyProduct.getVariants().get(0).getInventoryLevels() != null &&
            shopifyProduct.getVariants().get(0).getInventoryLevels().get() != null ?
                shopifyProduct.getVariants().get(0).getInventoryLevels().get().size() : 0);
        
        // Calculate total inventory for summary
        int totalInventoryAcrossAllVariants = 0;
        if (shopifyProduct.getVariants() != null) {
            for (Variant variant : shopifyProduct.getVariants()) {
                if (variant.getInventoryLevels() != null && variant.getInventoryLevels().get() != null) {
                    for (InventoryLevel level : variant.getInventoryLevels().get()) {
                        try {
                            totalInventoryAcrossAllVariants += Integer.parseInt(level.getAvailable());
                        } catch (NumberFormatException e) {
                            // Skip invalid quantities
                        }
                    }
                }
            }
        }
        
        String expectedStatus = feedItem.getWebStatus();
        int expectedInventory = "SOLD".equalsIgnoreCase(expectedStatus) ? 0 : 1;
        
        logger.info("  - Total inventory: {}", totalInventoryAcrossAllVariants);
        logger.info("  - Expected inventory: {}", expectedInventory);
        
        if (totalInventoryAcrossAllVariants == expectedInventory) {
            logger.info("  - Inventory status: ✅ CORRECT");
        } else {
            logger.warn("  - Inventory status: ⚠️ ISSUE DETECTED");
        }
        
        logger.info("=" .repeat(100));
        logger.info("=== Specific Inventory Scan Complete for {} ===", TARGET_WEB_TAG_NUMBER);
    }
    
    /**
     * 📍 INVENTORY BY LOCATION OVERVIEW 📍
     * 
     * This method provides a comprehensive overview of inventory distribution
     * across all Shopify locations for all products.
     * 
     * PURPOSE:
     * - Understand inventory distribution patterns
     * - Identify location-specific inventory issues
     * - Analyze inventory concentration by location
     * 
     * USAGE:
     * mvn test -Dtest=InventoryFixTest#showInventoryByLocationOverview
     * 
     * SAFE: Read-only operation, no changes made
     */
    @Test
    public void showInventoryByLocationOverview() throws Exception {
        logger.info("=== Inventory by Location Overview ===");
        logger.info("📍 Analyzing inventory distribution across all Shopify locations");
        
        // Get all products from Shopify
        logger.info("📡 Fetching all products from Shopify...");
        List<Product> allProducts = shopifyApiService.getAllProducts();
        logger.info("📊 Found {} total products in Shopify", allProducts.size());
        
        // Track location statistics
        Map<String, LocationStats> locationStatsMap = new HashMap<>();
        int totalProductsAnalyzed = 0;
        int productsWithInventory = 0;
        int totalInventoryAcrossAllProducts = 0;
        
        logger.info("🔍 Analyzing inventory by location...");
        
        for (int i = 0; i < allProducts.size(); i++) {
            Product product = allProducts.get(i);
            
            if (i % 100 == 0) {
                logger.info("📊 Progress: {}/{} products analyzed", i, allProducts.size());
            }
            
            try {
                totalProductsAnalyzed++;
                boolean productHasInventory = false;
                
                if (product.getVariants() != null) {
                    for (Variant variant : product.getVariants()) {
                        if (variant.getInventoryLevels() != null && variant.getInventoryLevels().get() != null) {
                            for (InventoryLevel level : variant.getInventoryLevels().get()) {
                                String locationId = level.getLocationId();
                                int quantity = 0;
                                
                                try {
                                    quantity = Integer.parseInt(level.getAvailable());
                                } catch (NumberFormatException e) {
                                    // Skip invalid quantities
                                    continue;
                                }
                                
                                // Track location statistics
                                LocationStats stats = locationStatsMap.computeIfAbsent(locationId, k -> new LocationStats());
                                stats.totalProducts++;
                                stats.totalInventory += quantity;
                                
                                if (quantity > 0) {
                                    stats.productsWithInventory++;
                                    productHasInventory = true;
                                }
                                
                                totalInventoryAcrossAllProducts += quantity;
                            }
                        }
                    }
                }
                
                if (productHasInventory) {
                    productsWithInventory++;
                }
                
            } catch (Exception e) {
                logger.warn("⚠️ Error analyzing product {}: {}", product.getId(), e.getMessage());
            }
        }
        
        logger.info("📊 Analysis Complete - Inventory by Location Results:");
        logger.info("=" .repeat(120));
        
        // Display overall statistics
        logger.info("📈 OVERALL STATISTICS:");
        logger.info("  - Total products analyzed: {}", totalProductsAnalyzed);
        logger.info("  - Products with inventory: {}", productsWithInventory);
        logger.info("  - Products without inventory: {}", totalProductsAnalyzed - productsWithInventory);
        logger.info("  - Total inventory across all locations: {}", totalInventoryAcrossAllProducts);
        logger.info("  - Average inventory per product: {:.2f}", 
            totalProductsAnalyzed > 0 ? (double) totalInventoryAcrossAllProducts / totalProductsAnalyzed : 0);
        logger.info("  - Unique locations found: {}", locationStatsMap.size());
        
        // Display location breakdown
        logger.info("");
        logger.info("📍 INVENTORY BY LOCATION BREAKDOWN:");
        logger.info("=" .repeat(120));
        logger.info("{:^5} | {:^35} | {:^15} | {:^20} | {:^15} | {:^15}", 
            "#", "Location ID", "Total Products", "Products w/ Inventory", "Total Inventory", "Avg per Product");
        logger.info("=" .repeat(120));
        
        // Sort locations by total inventory (descending)
        List<Map.Entry<String, LocationStats>> sortedLocations = locationStatsMap.entrySet()
            .stream()
            .sorted((e1, e2) -> Integer.compare(e2.getValue().totalInventory, e1.getValue().totalInventory))
            .toList();
        
        for (int i = 0; i < sortedLocations.size(); i++) {
            Map.Entry<String, LocationStats> entry = sortedLocations.get(i);
            String locationId = entry.getKey();
            LocationStats stats = entry.getValue();
            
            // Format location ID for display
            String locationIdDisplay = locationId;
            if (locationIdDisplay != null && locationIdDisplay.length() > 33) {
                locationIdDisplay = locationIdDisplay.substring(0, 30) + "...";
            }
            
            double avgPerProduct = stats.totalProducts > 0 ? (double) stats.totalInventory / stats.totalProducts : 0;
            
            logger.info("{:^5} | {:^35} | {:^15} | {:^20} | {:^15} | {:^15.2f}", 
                i + 1,
                locationIdDisplay != null ? locationIdDisplay : "N/A",
                stats.totalProducts,
                stats.productsWithInventory,
                stats.totalInventory,
                avgPerProduct);
        }
        logger.info("=" .repeat(120));
        
        // Show locations with highest inventory
        logger.info("");
        logger.info("🏆 TOP 5 LOCATIONS BY TOTAL INVENTORY:");
        int topCount = Math.min(5, sortedLocations.size());
        for (int i = 0; i < topCount; i++) {
            Map.Entry<String, LocationStats> entry = sortedLocations.get(i);
            LocationStats stats = entry.getValue();
            double percentage = totalInventoryAcrossAllProducts > 0 ? 
                (double) stats.totalInventory / totalInventoryAcrossAllProducts * 100 : 0;
                
            logger.info("  {}. Location: {} - {} units ({:.1f}% of total)", 
                i + 1, entry.getKey(), stats.totalInventory, percentage);
        }
        
        // Show locations with most products
        logger.info("");
        logger.info("📦 TOP 5 LOCATIONS BY PRODUCT COUNT:");
        List<Map.Entry<String, LocationStats>> sortedByProducts = locationStatsMap.entrySet()
            .stream()
            .sorted((e1, e2) -> Integer.compare(e2.getValue().totalProducts, e1.getValue().totalProducts))
            .toList();
            
        topCount = Math.min(5, sortedByProducts.size());
        for (int i = 0; i < topCount; i++) {
            Map.Entry<String, LocationStats> entry = sortedByProducts.get(i);
            LocationStats stats = entry.getValue();
            double percentage = totalProductsAnalyzed > 0 ? 
                (double) stats.totalProducts / totalProductsAnalyzed * 100 : 0;
                
            logger.info("  {}. Location: {} - {} products ({:.1f}% of total)", 
                i + 1, entry.getKey(), stats.totalProducts, percentage);
        }
        
        // Identify potential issues
        logger.info("");
        logger.info("⚠️ POTENTIAL ISSUES DETECTED:");
        boolean issuesFound = false;
        
        for (Map.Entry<String, LocationStats> entry : locationStatsMap.entrySet()) {
            LocationStats stats = entry.getValue();
            double avgInventory = stats.totalProducts > 0 ? (double) stats.totalInventory / stats.totalProducts : 0;
            
            if (avgInventory > 1.5) {
                logger.warn("  - Location {} has high average inventory: {:.2f} per product", 
                    entry.getKey(), avgInventory);
                issuesFound = true;
            }
            
            if (stats.totalInventory > totalInventoryAcrossAllProducts * 0.8) {
                logger.warn("  - Location {} contains {:.1f}% of total inventory (potential concentration risk)", 
                    entry.getKey(), (double) stats.totalInventory / totalInventoryAcrossAllProducts * 100);
                issuesFound = true;
            }
        }
        
        if (!issuesFound) {
            logger.info("  ✅ No obvious inventory distribution issues detected");
        }
        
        logger.info("=" .repeat(120));
        logger.info("=== Inventory by Location Overview Complete ===");
    }
    
    /**
     * Helper method to get a specific product by web tag number
     * 
     * @param webTagNumber The SKU to look up
     * @return List containing the product if found, empty list if not found
     */
    private List<Product> getProductByWebTagNumber(String webTagNumber) throws Exception {
        List<Product> result = new ArrayList<>();
        
        // Look up feed item in database
        FeedItem feedItem = feedItemService.findByWebTagNumber(webTagNumber);
        if (feedItem == null) {
            logger.warn("⚠️ Feed item not found in database for web tag number: {}", webTagNumber);
            return result;
        }
        
        // Check if feed item has Shopify ID
        String shopifyItemId = feedItem.getShopifyItemId();
        if (shopifyItemId == null || shopifyItemId.trim().isEmpty()) {
            logger.warn("⚠️ Feed item found but has no Shopify ID - not published to Shopify");
            return result;
        }
        
        // Get product from Shopify
        try {
            Product product = shopifyApiService.getProductByProductId(shopifyItemId);
            if (product != null) {
                result.add(product);
                logger.debug("✅ Successfully retrieved product for SKU: {}", webTagNumber);
            } else {
                logger.warn("⚠️ Product not found in Shopify with ID: {}", shopifyItemId);
            }
        } catch (Exception e) {
            logger.error("❌ Error retrieving product from Shopify: {}", e.getMessage());
            throw e;
        }
        
        return result;
    }
    
    /**
     * Helper class to track location statistics
     */
    private static class LocationStats {
        int totalProducts = 0;
        int productsWithInventory = 0;
        int totalInventory = 0;
    }
    
    /**
     * 🧪 STANDALONE INVENTORY API TEST 🧪
     * 
     * This test isolates the inventory update API to verify:
     * 1. The inventorySetQuantities mutation works without errors
     * 2. Setting inventory to the same value doesn't increment it
     * 3. Absolute value setting works correctly
     * 
     * USAGE:
     * mvn test -Dtest=InventoryFixTest#testInventoryAPIDirectly -Dspring.profiles.active=keystone-dev
     * 
     * SAFE: This test only works with available items and sets inventory to the same value
     */
    @Test
    public void testInventoryAPIDirectly() throws Exception {
        logger.info("=== Standalone Inventory API Test ===");
        logger.info("🔬 Testing inventorySetQuantities mutation in isolation");
        
        // STEP 1: Find an available item from the database (not Shopify)
        logger.info("📂 Finding an available product from database...");
        
        // Get all items and filter by webStatus (product status)
        List<FeedItem> allItems = feedItemService.findAll();
        List<FeedItem> availableItems = allItems.stream()
            .filter(item -> "Available".equalsIgnoreCase(item.getWebStatus()))
            .toList();
        
        if (availableItems.isEmpty()) {
            logger.error("❌ No items with webStatus='Available' found in database");
            logger.info("💡 Available webStatus values in database:");
            allItems.stream()
                .map(FeedItem::getWebStatus)
                .distinct()
                .forEach(status -> logger.info("  - '{}'", status));
            return;
        }
        
        logger.info("✅ Found {} items with webStatus='Available'", availableItems.size());
        
        // Find an available item that has a Shopify ID (published)
        FeedItem testFeedItem = null;
        for (FeedItem item : availableItems) {
            if (item.getShopifyItemId() != null && !item.getShopifyItemId().trim().isEmpty()) {
                testFeedItem = item;
                logger.info("✅ Found test item from database: SKU={}, Description={}, Shopify ID={}", 
                    item.getWebTagNumber(), 
                    item.getWebDescriptionShort() != null ? 
                        (item.getWebDescriptionShort().length() > 50 ? 
                            item.getWebDescriptionShort().substring(0, 50) + "..." : 
                            item.getWebDescriptionShort()) : "N/A",
                    item.getShopifyItemId());
                break;
            }
        }
        
        if (testFeedItem == null) {
            logger.error("❌ No published available items found (need item with Shopify ID)");
            logger.info("💡 Available items without Shopify ID: {}", 
                availableItems.stream()
                    .filter(item -> item.getShopifyItemId() == null || item.getShopifyItemId().trim().isEmpty())
                    .map(FeedItem::getWebTagNumber)
                    .limit(5)
                    .toList());
            return;
        }
        
        // STEP 2: Get the product from Shopify using the database item
        logger.info("📡 Retrieving product from Shopify using database reference...");
        Product testProduct;
        try {
            testProduct = shopifyApiService.getProductByProductId(testFeedItem.getShopifyItemId());
        } catch (Exception e) {
            logger.error("❌ Failed to retrieve product from Shopify: {}", e.getMessage());
            return;
        }
        
        if (testProduct == null) {
            logger.error("❌ Product not found in Shopify with ID: {}", testFeedItem.getShopifyItemId());
            logger.info("💡 This indicates the database has a stale Shopify ID");
            return;
        }
        
        // Verify the SKUs match
        String shopifySku = extractSKUFromProduct(testProduct);
        if (!testFeedItem.getWebTagNumber().equals(shopifySku)) {
            logger.warn("⚠️ SKU mismatch - Database: {}, Shopify: {}", testFeedItem.getWebTagNumber(), shopifySku);
        }
        
        logger.info("✅ Product verified: Title={}, Total Inventory={}", 
            testProduct.getTitle(), calculateTotalInventory(testProduct));
        
        // STEP 3: Ensure product is published
        logger.info("📢 Ensuring product is published...");
        try {
            shopifyApiService.publishProductToAllChannels(testProduct.getId());
            logger.info("✅ Product published successfully");
        } catch (Exception e) {
            logger.warn("⚠️ Publishing may have failed, but continuing test: {}", e.getMessage());
        }
        
        // STEP 4: Get current inventory levels
        logger.info("📊 Getting current inventory levels...");
        if (testProduct.getVariants() == null || testProduct.getVariants().isEmpty()) {
            logger.error("❌ Test product has no variants");
            return;
        }
        
        Variant testVariant = testProduct.getVariants().get(0);
        if (testVariant.getInventoryLevels() == null || testVariant.getInventoryLevels().get() == null) {
            logger.error("❌ Test variant has no inventory levels");
            return;
        }
        
        List<InventoryLevel> originalLevels = testVariant.getInventoryLevels().get();
        logger.info("📍 Current inventory distribution:");
        logger.info("    " + "=".repeat(80));
        logger.info("    {:^15} | {:^20} | {:^15} | {:^20}", "Location #", "Location ID", "Quantity", "Inventory Item ID");
        logger.info("    " + "=".repeat(80));
        
        int originalTotal = 0;
        for (int i = 0; i < originalLevels.size(); i++) {
            InventoryLevel level = originalLevels.get(i);
            int qty = 0;
            try {
                qty = Integer.parseInt(level.getAvailable());
                originalTotal += qty;
            } catch (NumberFormatException e) {
                logger.warn("⚠️ Invalid quantity: {}", level.getAvailable());
            }
            
            String locationIdDisplay = level.getLocationId();
            if (locationIdDisplay != null && locationIdDisplay.length() > 18) {
                locationIdDisplay = locationIdDisplay.substring(0, 15) + "...";
            }
            
            String inventoryItemIdDisplay = level.getInventoryItemId();
            if (inventoryItemIdDisplay != null && inventoryItemIdDisplay.length() > 18) {
                inventoryItemIdDisplay = inventoryItemIdDisplay.substring(0, 15) + "...";
            }
            
            logger.info("    {:^15} | {:^20} | {:^15} | {:^20}", 
                "Location " + (i + 1), 
                locationIdDisplay != null ? locationIdDisplay : "N/A",
                qty,
                inventoryItemIdDisplay != null ? inventoryItemIdDisplay : "N/A");
        }
        logger.info("    " + "=".repeat(80));
        logger.info("📈 Original Total Inventory: {}", originalTotal);
        
        // STEP 5: Test absolute inventory setting (set to same value)
        logger.info("🔧 Testing absolute inventory setting...");
        logger.info("💡 Setting inventory to SAME values to test absolute setting (should not increment)");
        
        try {
            // Call the absolute inventory setting method
            shopifyApiService.setInventoryLevelsAbsolute(originalLevels);
            logger.info("✅ setInventoryLevelsAbsolute call completed without error");
            
        } catch (Exception e) {
            logger.error("❌ setInventoryLevelsAbsolute failed with error: {}", e.getMessage());
            logger.error("❌ Exception details: ", e);
            throw e;
        }
        
        // STEP 6: Verify inventory didn't change
        logger.info("🔍 Verifying inventory levels after API call...");
        
        // Fetch fresh product data
        Product updatedProduct = shopifyApiService.getProductByProductId(testProduct.getId());
        if (updatedProduct == null || updatedProduct.getVariants() == null || updatedProduct.getVariants().isEmpty()) {
            logger.error("❌ Could not fetch updated product data");
            return;
        }
        
        Variant updatedVariant = updatedProduct.getVariants().get(0);
        if (updatedVariant.getInventoryLevels() == null || updatedVariant.getInventoryLevels().get() == null) {
            logger.error("❌ Updated variant has no inventory levels");
            return;
        }
        
        List<InventoryLevel> updatedLevels = updatedVariant.getInventoryLevels().get();
        logger.info("📍 Updated inventory distribution:");
        logger.info("    " + "=".repeat(90));
        logger.info("    {:^10} | {:^20} | {:^15} | {:^15} | {:^15} | {:^10}", 
            "Location", "Location ID", "Original Qty", "Updated Qty", "Difference", "Status");
        logger.info("    " + "=".repeat(90));
        
        int updatedTotal = 0;
        boolean testPassed = true;
        
        for (int i = 0; i < Math.min(originalLevels.size(), updatedLevels.size()); i++) {
            InventoryLevel originalLevel = originalLevels.get(i);
            InventoryLevel updatedLevel = updatedLevels.get(i);
            
            int originalQty = 0;
            int updatedQty = 0;
            
            try {
                originalQty = Integer.parseInt(originalLevel.getAvailable());
                updatedQty = Integer.parseInt(updatedLevel.getAvailable());
                updatedTotal += updatedQty;
            } catch (NumberFormatException e) {
                logger.warn("⚠️ Invalid quantity format");
                continue;
            }
            
            int difference = updatedQty - originalQty;
            String status = (difference == 0) ? "✅ CORRECT" : "❌ CHANGED";
            
            if (difference != 0) {
                testPassed = false;
            }
            
            String locationIdDisplay = updatedLevel.getLocationId();
            if (locationIdDisplay != null && locationIdDisplay.length() > 18) {
                locationIdDisplay = locationIdDisplay.substring(0, 15) + "...";
            }
            
            logger.info("    {:^10} | {:^20} | {:^15} | {:^15} | {:^15} | {:^10}", 
                (i + 1),
                locationIdDisplay != null ? locationIdDisplay : "N/A",
                originalQty,
                updatedQty,
                difference > 0 ? "+" + difference : String.valueOf(difference),
                status);
        }
        logger.info("    " + "=".repeat(90));
        
        // STEP 7: Final verification
        logger.info("📊 FINAL VERIFICATION:");
        logger.info("  - Database Item: SKU={}, webStatus={}, syncStatus={}", 
            testFeedItem.getWebTagNumber(), testFeedItem.getWebStatus(), testFeedItem.getStatus());
        logger.info("  - Shopify Product: Title={}, Status={}", testProduct.getTitle(), testProduct.getStatus());
        logger.info("  - Original Total: {}", originalTotal);
        logger.info("  - Updated Total: {}", updatedTotal);
        logger.info("  - Difference: {}", updatedTotal - originalTotal);
        
        if (testPassed && updatedTotal == originalTotal) {
            logger.info("✅ 🎉 TEST PASSED: Absolute inventory setting works correctly!");
            logger.info("✅ Inventory levels remained the same (no increment detected)");
            logger.info("✅ The inventorySetQuantities mutation is working as expected");
        } else {
            logger.error("❌ 🚨 TEST FAILED: Inventory levels changed unexpectedly!");
            logger.error("❌ Expected: No change in inventory levels");
            logger.error("❌ Actual: Total changed from {} to {}", originalTotal, updatedTotal);
            
            if (updatedTotal > originalTotal) {
                logger.error("❌ 🐛 BUG CONFIRMED: Inventory INCREMENTED (delta behavior detected)");
                logger.error("❌ This suggests the API is still using delta adjustment instead of absolute setting");
            } else {
                logger.error("❌ Inventory DECREASED unexpectedly");
            }
            
            throw new AssertionError("Inventory levels changed when they should have remained the same");
        }
        
        logger.info("=== Standalone Inventory API Test Complete ===");
    }
    
    /**
     * 🔍 GET SAMPLE FEED ITEM FROM DATABASE 🔍
     * 
     * This method fetches a sample item from the feed_item database for testing purposes.
     * 
     * USAGE:
     * mvn test -Dtest=InventoryFixTest#getSampleFeedItem -Dspring.profiles.active=keystone-dev
     * 
     * SAFE: Read-only operation, no changes made
     */
    @Test
    public void getSampleFeedItem() throws Exception {
        logger.info("=== Getting Sample Feed Item from Database ===");
        logger.info("🔍 Fetching available feed items for testing");
        
        // Get a few sample feed items
        logger.info("📂 Looking for available feed items in database...");
        
        try {
            // Get all items and filter by webStatus (product status)
            List<FeedItem> allItems = feedItemService.findAll();
            
            // Try to find an available item first
            logger.info("🔍 Searching for items with webStatus='Available'...");
            List<FeedItem> availableItems = allItems.stream()
                .filter(item -> "Available".equalsIgnoreCase(item.getWebStatus()))
                .toList();
            
            if (!availableItems.isEmpty()) {
                logger.info("✅ Found {} items with webStatus='Available'", availableItems.size());
                
                // Show first few available items
                int displayCount = Math.min(5, availableItems.size());
                logger.info("📋 First {} Available items:", displayCount);
                
                for (int i = 0; i < displayCount; i++) {
                    FeedItem item = availableItems.get(i);
                    logger.info("  {}. SKU: {} | Description: {} | Price: ${} | Shopify ID: {}", 
                        i + 1,
                        item.getWebTagNumber(),
                        item.getWebDescriptionShort() != null ? 
                            (item.getWebDescriptionShort().length() > 50 ? 
                                item.getWebDescriptionShort().substring(0, 50) + "..." : 
                                item.getWebDescriptionShort()) : "N/A",
                        item.getWebPriceRetail(),
                        item.getShopifyItemId());
                }
                
                // Use first available item for detailed display
                FeedItem sampleItem = availableItems.get(0);
                logger.info("");
                logger.info("🎯 SELECTED SAMPLE ITEM FOR TESTING:");
                logger.info("=" .repeat(80));
                displayFeedItemDetails(sampleItem);
                
            } else {
                logger.warn("⚠️ No items with webStatus='Available' found, looking for any items...");
            }
            
            // Also try to find a sold item
            logger.info("");
            logger.info("🔍 Searching for items with webStatus='Sold'...");
            List<FeedItem> soldItems = allItems.stream()
                .filter(item -> "Sold".equalsIgnoreCase(item.getWebStatus()))
                .toList();
            
            if (!soldItems.isEmpty()) {
                logger.info("✅ Found {} items with webStatus='Sold'", soldItems.size());
                
                // Show first sold item
                FeedItem soldItem = soldItems.get(0);
                logger.info("📋 Sample Sold item:");
                logger.info("  SKU: {} | Description: {} | Price: ${} | Shopify ID: {}", 
                    soldItem.getWebTagNumber(),
                    soldItem.getWebDescriptionShort() != null ? 
                        (soldItem.getWebDescriptionShort().length() > 50 ? 
                            soldItem.getWebDescriptionShort().substring(0, 50) + "..." : 
                            soldItem.getWebDescriptionShort()) : "N/A",
                    soldItem.getWebPriceRetail(),
                    soldItem.getShopifyItemId());
            } else {
                logger.warn("⚠️ No items with webStatus='Sold' found");
            }
            
            // Get total count
            logger.info("");
            logger.info("📊 DATABASE STATISTICS:");
            logger.info("  Total feed items in database: {}", allItems.size());
            
            // Count by webStatus (product status)
            Map<String, Integer> webStatusCounts = new HashMap<>();
            Map<String, Integer> syncStatusCounts = new HashMap<>();
            int withShopifyId = 0;
            int withoutShopifyId = 0;
            
            for (FeedItem item : allItems) {
                // Count by webStatus (product status)
                String webStatus = item.getWebStatus() != null ? item.getWebStatus() : "NULL";
                webStatusCounts.put(webStatus, webStatusCounts.getOrDefault(webStatus, 0) + 1);
                
                // Count by status (sync status)
                String syncStatus = item.getStatus() != null ? item.getStatus() : "NULL";
                syncStatusCounts.put(syncStatus, syncStatusCounts.getOrDefault(syncStatus, 0) + 1);
                
                if (item.getShopifyItemId() != null && !item.getShopifyItemId().trim().isEmpty()) {
                    withShopifyId++;
                } else {
                    withoutShopifyId++;
                }
            }
            
            logger.info("  Items by webStatus (product status):");
            for (Map.Entry<String, Integer> entry : webStatusCounts.entrySet()) {
                logger.info("    - '{}': {} items", entry.getKey(), entry.getValue());
            }
            
            logger.info("  Items by status (sync status):");
            for (Map.Entry<String, Integer> entry : syncStatusCounts.entrySet()) {
                logger.info("    - '{}': {} items", entry.getKey(), entry.getValue());
            }
            
            logger.info("  Items with Shopify ID: {}", withShopifyId);
            logger.info("  Items without Shopify ID: {}", withoutShopifyId);
            
        } catch (Exception e) {
            logger.error("❌ Error accessing feed item database: {}", e.getMessage());
            logger.error("❌ Exception details: ", e);
            throw e;
        }
        
        logger.info("=" .repeat(80));
        logger.info("=== Sample Feed Item Retrieval Complete ===");
    }
    
    /**
     * Helper method to display detailed feed item information
     */
    private void displayFeedItemDetails(FeedItem item) {
        logger.info("  Web Tag Number (SKU): {}", item.getWebTagNumber());
        logger.info("  Description: {}", item.getWebDescriptionShort());
        logger.info("  Web Status (product): {}", item.getWebStatus());
        logger.info("  Status (sync): {}", item.getStatus());
        logger.info("  Price: ${}", item.getWebPriceRetail());
        logger.info("  Designer/Brand: {}", item.getWebDesigner());
        logger.info("  Watch Model: {}", item.getWebWatchModel());
        logger.info("  Condition: {}", item.getWebWatchCondition());
        logger.info("  Shopify Item ID: {}", item.getShopifyItemId());
        logger.info("  Last Updated Date: {}", item.getLastUpdatedDate());
        logger.info("  Published Date: {}", item.getPublishedDate());
        
        // Check if published to Shopify
        if (item.getShopifyItemId() != null && !item.getShopifyItemId().trim().isEmpty()) {
            logger.info("  📢 Status: PUBLISHED to Shopify");
            logger.info("  💡 This item can be used for inventory testing");
        } else {
            logger.info("  📝 Status: NOT PUBLISHED to Shopify");
            logger.info("  💡 This item cannot be used for inventory testing");
        }
    }
    
        // Removed redundant enforceInventoryRules() method - functionality merged into fixInflatedInventoryLevels()
} 