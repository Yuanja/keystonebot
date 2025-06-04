package com.gw.service;

import com.gw.services.ReconciliationService;
import com.gw.services.ReconciliationService.ReconciliationAnalysis;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * Read-only reconciliation analysis test - Profile Aware
 * 
 * This test analyzes discrepancies between:
 * - Database (FeedItems)
 * - Shopify Products  
 * - Feed data
 * - Image counts
 * 
 * NO MODIFICATIONS ARE MADE - This is purely analytical
 * 
 * PROFILE AWARE: Uses the profile specified by the Maven profile:
 * - Development: keystone-dev (safe for testing)
 * - Production: keystone-prod (live production data)
 * 
 * Usage:
 * Development: mvn test -Panalyze-reconciliation (uses keystone-dev)
 * Production:  mvn test -Panalyze-reconciliation-prod (uses keystone-prod)
 * 
 * @author jyuan
 */
@SpringJUnitConfig
@SpringBootTest
@TestPropertySource(properties = {
    "cron.schedule=0 0 0 31 2 ?",  // Never runs (Feb 31st doesn't exist)
    "spring.task.scheduling.enabled=false"  // Disable scheduling entirely during tests
})
public class ReconciliationAnalysisTest {

    private static final Logger logger = LogManager.getLogger(ReconciliationAnalysisTest.class);

    @Autowired
    private ReconciliationService reconciliationService;

    @Test
    public void testReconciliationAnalysis() throws Exception {
        // Detect active profile from system properties (set by Maven profiles)
        String activeProfile = System.getProperty("spring.profiles.active", "keystone-dev");
        boolean isProduction = activeProfile.contains("keystone-prod");
        
        logger.info("=== RECONCILIATION ANALYSIS TEST (READ-ONLY) ===");
        logger.info("⚠️ ANALYSIS MODE - NO MODIFICATIONS WILL BE MADE");
        logger.info("📊 Active Profile: {}", activeProfile);
        
        if (isProduction) {
            logger.warn("🚨 PRODUCTION MODE - Analyzing live production data");
            logger.warn("🚨 Use extreme caution when reviewing results");
        } else {
            logger.info("🛡️ DEVELOPMENT MODE - Safe for testing");
        }
        
        logger.info("💡 Profile Usage:");
        logger.info("  Development: mvn test -Panalyze-reconciliation");
        logger.info("  Production:  mvn test -Panalyze-reconciliation-prod");
        logger.info("");
        
        try {
            // Perform comprehensive analysis
            ReconciliationAnalysis analysis = reconciliationService.analyzeDiscrepancies();
            
            // Log summary results for easy review
            logAnalysisSummary(analysis);
            
            // Generate recommendations
            generateRecommendations(analysis);
            
        } catch (Exception e) {
            logger.error("❌ Analysis failed with exception", e);
            throw e;
        }
    }
    
    private void logAnalysisSummary(ReconciliationAnalysis analysis) {
        logger.info("=== ANALYSIS SUMMARY ===");
        logger.info("Analysis Time: {}", analysis.getAnalysisTime());
        logger.info("");
        
        logger.info("📊 SYSTEM STATUS:");
        logger.info("  Shopify Products: {}", analysis.getTotalProductsInShopify());
        logger.info("  Database Items: {}", analysis.getTotalItemsInDB());
        logger.info("  Feed Items: {}", analysis.getTotalItemsInFeed());
        logger.info("");
        
        if (analysis.isExceedsDeleteThreshold()) {
            logger.warn("🚨 SAFETY WARNING: {}", analysis.getDeleteThresholdMessage());
            logger.warn("🚨 Manual review required before reconciliation");
        } else {
            logger.info("✅ Within safety threshold for reconciliation");
        }
        logger.info("");
        
        logger.info("🔍 DISCREPANCY SUMMARY:");
        logger.info("  Total Discrepancies: {}", analysis.getTotalDiscrepancies());
        logger.info("  - Extra in Shopify: {} (would be deleted)", analysis.getExtraInShopify().size());
        logger.info("  - Extra in DB: {} (would be removed from DB)", analysis.getExtraInDB().size());
        logger.info("  - Shopify ID mismatches: {} (would be updated)", analysis.getMismatchedShopifyIds().size());
        logger.info("  - Image count mismatches: {} (would be marked for update)", analysis.getImageCountMismatches().size());
        logger.info("");
        
        if (!analysis.getErrors().isEmpty()) {
            logger.error("❌ ERRORS FOUND:");
            analysis.getErrors().forEach(error -> logger.error("  {}", error));
            logger.info("");
        }
    }
    
    private void generateRecommendations(ReconciliationAnalysis analysis) {
        logger.info("=== RECOMMENDATIONS ===");
        
        if (!analysis.hasDiscrepancies() && analysis.getErrors().isEmpty()) {
            logger.info("🎉 EXCELLENT! No action needed - system is perfectly synchronized");
            logger.info("✅ Database, Shopify, and Feed are all in sync");
            return;
        }
        
        if (analysis.isExceedsDeleteThreshold()) {
            logger.warn("🚨 IMMEDIATE ACTION REQUIRED:");
            logger.warn("   1. Investigate why there's a large discrepancy");
            logger.warn("   2. DO NOT run reconciliation until manually reviewed");
            logger.warn("   3. Consider running with smaller batches or fixing data issues first");
            logger.info("");
        }
        
        if (analysis.hasDiscrepancies()) {
            logger.info("📋 RECONCILIATION RECOMMENDATIONS:");
            
            if (!analysis.getExtraInShopify().isEmpty()) {
                logger.info("  🔸 {} items exist in Shopify but not in DB", analysis.getExtraInShopify().size());
                logger.info("     → These would be DELETED from Shopify during reconciliation");
                logger.info("     → Review list above to ensure these deletions are acceptable");
            }
            
            if (!analysis.getExtraInDB().isEmpty()) {
                logger.info("  🔸 {} items exist in DB but not in Shopify", analysis.getExtraInDB().size());
                logger.info("     → These would be REMOVED from database during reconciliation");
                logger.info("     → Check if these items should be republished instead");
            }
            
            if (!analysis.getMismatchedShopifyIds().isEmpty()) {
                logger.info("  🔸 {} items have Shopify ID mismatches", analysis.getMismatchedShopifyIds().size());
                logger.info("     → Database would be updated with correct Shopify IDs");
                logger.info("     → This is generally safe to fix");
            }
            
            if (!analysis.getImageCountMismatches().isEmpty()) {
                logger.info("  🔸 {} items have image count mismatches", analysis.getImageCountMismatches().size());
                logger.info("     → These would be marked for update to fix image synchronization");
                logger.info("     → Images would be re-uploaded to match database expectations");
            }
            
            logger.info("");
            logger.info("📝 NEXT STEPS:");
            if (analysis.isExceedsDeleteThreshold()) {
                logger.warn("   1. ⚠️  DO NOT run reconciliation yet");
                logger.warn("   2. 🔍 Investigate the large discrepancy");
                logger.warn("   3. 🛠️  Fix underlying data issues");
                logger.warn("   4. 🔄 Re-run analysis to confirm fixes");
                logger.warn("   5. 💡 Consider using force mode only if discrepancies are expected");
            } else {
                logger.info("   1. ✅ Review the discrepancies listed above");
                logger.info("   2. ✅ If acceptable, run reconciliation:");
                logger.info("      mvn test -Dtest=ReconciliationTest -Pperform-reconciliation");
                logger.info("   3. ✅ Monitor the reconciliation process carefully");
                logger.info("   4. ✅ Re-run analysis after reconciliation to confirm fixes");
            }
        }
        
        if (!analysis.getErrors().isEmpty()) {
            logger.error("❌ ERRORS NEED ATTENTION:");
            logger.error("   Some errors occurred during analysis");
            logger.error("   Fix these issues before running reconciliation");
        }
        
        logger.info("");
        logger.info("💡 TIP: Always run analysis before reconciliation in production!");
        logger.info("=== END RECOMMENDATIONS ===");
    }
} 