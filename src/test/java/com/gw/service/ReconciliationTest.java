package com.gw.service;

import com.gw.services.ReconciliationService;
import com.gw.services.ReconciliationService.ReconciliationResult;
import com.gw.services.ReconciliationService.ReconciliationAnalysis;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * Actual reconciliation test that MODIFIES data
 * 
 * This test performs actual reconciliation work including:
 * - Deleting extra products from Shopify
 * - Removing orphaned items from database
 * - Fixing Shopify ID mismatches
 * - Marking items with image mismatches for update
 * 
 * ⚠️ WARNING: THIS TEST MODIFIES DATA
 * 
 * Uses keystone-dev profile for safe testing with development environment.
 * For production reconciliation, use the perform-reconciliation-prod Maven profile.
 * 
 * Usage:
 * Development: mvn test -Pperform-reconciliation
 * Production:  mvn test -Pperform-reconciliation-prod
 * Force mode:  mvn test -Pforce-reconciliation[-prod]
 * 
 * @author jyuan
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@ActiveProfiles("keystone-dev")  // Use development profile for safer testing
public class ReconciliationTest {

    private static final Logger logger = LogManager.getLogger(ReconciliationTest.class);

    @Autowired
    private ReconciliationService reconciliationService;
    
    @Value("${reconciliation.force:false}")
    private boolean forceReconciliation;

    @Test
    public void testReconciliation() throws Exception {
        logger.info("=== RECONCILIATION TEST (MODIFIES DATA) ===");
        logger.warn("⚠️ WARNING: THIS TEST WILL MODIFY DATA");
        logger.info("📊 Using keystone-dev profile for safe testing");
        logger.info("💡 For production reconciliation, use: mvn test -Pperform-reconciliation-prod");
        logger.info("Force mode: {}", forceReconciliation);
        logger.info("");
        
        // Add extra safety confirmation
        confirmReconciliationIntent();
        
        try {
            // First run analysis to show what will be done
            logger.info("🔍 Running pre-reconciliation analysis...");
            ReconciliationAnalysis preAnalysis = reconciliationService.analyzeDiscrepancies();
            
            if (!preAnalysis.hasDiscrepancies()) {
                logger.info("✅ No discrepancies found - reconciliation not needed");
                logger.info("🎉 System is already in sync!");
                return;
            }
            
            logPreReconciliationSummary(preAnalysis);
            
            // Perform actual reconciliation
            logger.info("🔧 Starting reconciliation process...");
            ReconciliationResult result = reconciliationService.performReconciliation(forceReconciliation);
            
            // Log results
            logReconciliationResults(result);
            
            // Run post-reconciliation analysis to verify results
            logger.info("🔍 Running post-reconciliation analysis...");
            ReconciliationAnalysis postAnalysis = reconciliationService.analyzeDiscrepancies();
            logPostReconciliationSummary(postAnalysis);
            
        } catch (Exception e) {
            logger.error("❌ Reconciliation failed with exception", e);
            throw e;
        }
    }
    
    private void confirmReconciliationIntent() {
        logger.warn("⚠️ ⚠️ ⚠️  RECONCILIATION CONFIRMATION  ⚠️ ⚠️ ⚠️");
        logger.warn("This operation will modify production data:");
        logger.warn("  - Delete products from Shopify");
        logger.warn("  - Remove items from database");  
        logger.warn("  - Update Shopify IDs in database");
        logger.warn("  - Mark items for image re-sync");
        logger.warn("");
        logger.warn("If this is not intended, stop the test now!");
        logger.warn("Continue only if you've reviewed the analysis first!");
        logger.warn("⚠️ ⚠️ ⚠️ ⚠️ ⚠️ ⚠️ ⚠️ ⚠️ ⚠️ ⚠️ ⚠️ ⚠️ ⚠️ ⚠️ ⚠️");
        logger.info("");
        
        // Add a small delay to allow manual interruption
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Test interrupted - reconciliation cancelled");
        }
    }
    
    private void logPreReconciliationSummary(ReconciliationAnalysis analysis) {
        logger.info("=== PRE-RECONCILIATION ANALYSIS ===");
        logger.info("Found {} total discrepancies to fix:", analysis.getTotalDiscrepancies());
        logger.info("  - Extra in Shopify: {} (will be DELETED)", analysis.getExtraInShopify().size());
        logger.info("  - Extra in DB: {} (will be REMOVED from DB)", analysis.getExtraInDB().size());
        logger.info("  - Shopify ID mismatches: {} (will be UPDATED)", analysis.getMismatchedShopifyIds().size());
        logger.info("  - Image count mismatches: {} (will be MARKED for update)", analysis.getImageCountMismatches().size());
        
        if (analysis.isExceedsDeleteThreshold() && !forceReconciliation) {
            logger.error("❌ SAFETY CHECK FAILED: {}", analysis.getDeleteThresholdMessage());
            logger.error("❌ Reconciliation will be aborted unless force=true");
        }
        logger.info("");
    }
    
    private void logReconciliationResults(ReconciliationResult result) {
        logger.info("=== RECONCILIATION RESULTS ===");
        if (result.isSuccess()) {
            logger.info("✅ Reconciliation completed successfully");
            logger.info("📋 {}", result.getMessage());
        } else {
            logger.error("❌ Reconciliation failed");
            logger.error("💥 {}", result.getMessage());
        }
        logger.info("");
    }
    
    private void logPostReconciliationSummary(ReconciliationAnalysis analysis) {
        logger.info("=== POST-RECONCILIATION ANALYSIS ===");
        logger.info("Remaining discrepancies: {}", analysis.getTotalDiscrepancies());
        
        if (analysis.getTotalDiscrepancies() == 0) {
            logger.info("🎉 PERFECT! All discrepancies have been resolved");
            logger.info("✅ Database and Shopify are now in sync");
        } else {
            logger.warn("⚠️ Some discrepancies remain:");
            logger.warn("  - Extra in Shopify: {}", analysis.getExtraInShopify().size());
            logger.warn("  - Extra in DB: {}", analysis.getExtraInDB().size());
            logger.warn("  - Shopify ID mismatches: {}", analysis.getMismatchedShopifyIds().size());
            logger.warn("  - Image count mismatches: {}", analysis.getImageCountMismatches().size());
            logger.warn("💡 Consider running reconciliation again or investigating remaining issues");
        }
        
        logger.info("");
        logger.info("📊 FINAL STATUS:");
        logger.info("  Shopify Products: {}", analysis.getTotalProductsInShopify());
        logger.info("  Database Items: {}", analysis.getTotalItemsInDB());
        logger.info("  Feed Items: {}", analysis.getTotalItemsInFeed());
        
        logger.info("=== RECONCILIATION COMPLETE ===");
    }
} 