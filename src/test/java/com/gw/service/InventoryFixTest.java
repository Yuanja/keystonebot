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
 *   mvn test -Dtest=InventoryFixTest#fixInflatedInventoryLevels
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
 *   mvn test -Dtest=InventoryFixTest#fixInflatedInventoryLevels -DdryRun=false
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
 * 2. If issues found: mvn test -Dtest=InventoryFixTest#fixInflatedInventoryLevels -DdryRun=false
 * 
 * For specific problem SKUs:
 * mvn test -Dtest=InventoryFixTest#scanSpecificInventoryByWebTagNumber -DwebTagNumber=YOUR_SKU
 * 
 * For location distribution issues:
 * 1. Run: mvn test -Dtest=InventoryFixTest#showInventoryByLocationOverview
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
 * 3. Specific SKU: mvn test -Dtest=InventoryFixTest#scanSpecificInventoryByWebTagNumber -DwebTagNumber=YOUR_SKU
 * 4. Dry run fix: mvn test -Dtest=InventoryFixTest#fixInflatedInventoryLevels
 * 5. Live fix: mvn test -Dtest=InventoryFixTest#fixInflatedInventoryLevels -DdryRun=false
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
     * 🚨 MAIN PRODUCTION FIX METHOD 🚨
     * 
     * This is the primary method for fixing inventory issues in production.
     * 
     * SAFETY: Respects dryRun parameter - defaults to safe dry run mode
     * 
     * PROCESS:
     * 1. Scans all Shopify products for inventory > 1
     * 2. Looks up feed_items in database
     * 3. Determines correct inventory based on status
     * 4. Generates detailed fix plan
     * 5. Applies fixes (if -DdryRun=false specified)
     * 
     * USAGE:
     * Dry run (safe): mvn test -Dtest=InventoryFixTest#fixInflatedInventoryLevels
     * Live mode: mvn test -Dtest=InventoryFixTest#fixInflatedInventoryLevels -DdryRun=false
     * 
     * IMPORTANT: Always run scanInventoryIssues() first to understand scope!
     */
    @Test
    public void fixInflatedInventoryLevels() throws Exception {
        logger.info("=== Starting Inventory Fix Test - Production Data Cleanup ===");
        logger.info("🔍 Scanning for products with inflated inventory levels (total > 1)");
        
        boolean dryRun = getDryRunMode();
        if (dryRun) {
            logger.warn("🧪 DRY RUN MODE - No actual changes will be made");
            logger.info("💡 To apply actual fixes, add: -DdryRun=false");
        } else {
            logger.warn("🔧 LIVE MODE - Changes will be applied to Shopify!");
            logger.warn("⚠️ This will make REAL changes to inventory levels");
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
        if (!dryRun && !fixableIssues.isEmpty()) {
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
            logger.info("📍 Updating inventory across {} locations:", inventoryLevels.size());
            logger.info("    " + "=".repeat(90));
            logger.info("    {:^10} | {:^25} | {:^15} | {:^15} | {:^10}", 
                "Location", "Location ID", "Current Qty", "New Qty", "Change");
            logger.info("    " + "=".repeat(90));
            
            for (int i = 0; i < inventoryLevels.size(); i++) {
                InventoryLevel level = inventoryLevels.get(i);
                
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
                if (locationIdDisplay != null && locationIdDisplay.length() > 23) {
                    locationIdDisplay = locationIdDisplay.substring(0, 20) + "...";
                }
                
                logger.info("    {:^10} | {:^25} | {:^15} | {:^15} | {:^10}", 
                    (i + 1),
                    locationIdDisplay != null ? locationIdDisplay : "N/A",
                    currentQuantity,
                    newQuantity,
                    changeDisplay);
                
                // Update inventory level
                level.setAvailable(String.valueOf(newQuantity));
            }
            logger.info("    " + "=".repeat(90));
            
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
     * Helper class to track location statistics
     */
    private static class LocationStats {
        int totalProducts = 0;
        int productsWithInventory = 0;
        int totalInventory = 0;
    }
} 