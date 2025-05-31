# Reconciliation System Documentation

## Overview

The reconciliation system has been refactored to provide better control and safety when synchronizing data between Shopify, Database, and Feed sources. The system now operates in two distinct modes:

1. **Analysis Mode** (Read-Only) - Analyzes discrepancies without making any changes
2. **Reconciliation Mode** - Actually performs the reconciliation work

## Key Benefits

- ✅ **Safety First**: Always analyze before making changes
- ✅ **Production Ready**: Safe to run on production environments  
- ✅ **Comprehensive Reporting**: Detailed analysis of what would be changed
- ✅ **Selective Execution**: Only reconcile when needed and safe
- ✅ **Time-Based Optimization**: Expensive operations run once daily only

## Components

### 1. ReconciliationService
- **Location**: `src/main/java/com/gw/services/ReconciliationService.java`
- **Purpose**: Core service containing all reconciliation logic
- **Methods**:
  - `analyzeDiscrepancies()` - Read-only analysis
  - `performReconciliation(boolean force)` - Actual reconciliation work

### 2. Analysis Test
- **Location**: `src/test/java/com/gw/service/ReconciliationAnalysisTest.java`
- **Purpose**: Read-only test for analyzing production data
- **Safety**: Makes NO modifications to any system

### 3. Reconciliation Test  
- **Location**: `src/test/java/com/gw/service/ReconciliationTest.java`
- **Purpose**: Performs actual reconciliation work
- **Warning**: ⚠️ MODIFIES DATA - Use with caution

## Usage

### Step 1: Analyze First (Always)

Run analysis to understand what discrepancies exist:

```bash
# DEVELOPMENT - Analyze discrepancies (Read-Only) - Safe for testing
mvn test -Panalyze-reconciliation

# PRODUCTION - Analyze discrepancies (Read-Only) - Production data
mvn test -Panalyze-reconciliation-prod

# Alternative commands
mvn test -Dtest=ReconciliationAnalysisTest -Panalyze-reconciliation
mvn test -Dtest=ReconciliationAnalysisTest -Panalyze-reconciliation-prod
```

This will provide a comprehensive report showing:
- Total products in Shopify vs Database vs Feed
- Items that exist in Shopify but not in Database (would be deleted)
- Items that exist in Database but not in Shopify (would be removed from DB)
- Shopify ID mismatches (would be updated)
- Image count mismatches (would be marked for update)
- Safety threshold check

### Step 2: Review Analysis Results

The analysis will show:

```
=== RECONCILIATION ANALYSIS REPORT ===
📊 DATA SUMMARY:
  Shopify Products: 1234
  Database Items: 1230
  Feed Items: 1235

🔍 DISCREPANCIES FOUND:
  Extra in Shopify (not in DB): 4 (would be deleted)
  Extra in DB (not in Shopify): 2 (would be removed from DB)
  Shopify ID mismatches: 1 (would be updated)  
  Image count mismatches: 3 (would be marked for update)
  Total discrepancies: 10

📝 NEXT STEPS:
   1. ✅ Review the discrepancies listed above
   2. ✅ If acceptable, run reconciliation:
      mvn test -Pperform-reconciliation (development)
      mvn test -Pperform-reconciliation-prod (production)
```

### Step 3: Reconcile (If Safe)

Only after reviewing analysis results:

```bash
# DEVELOPMENT - Safe for testing
mvn test -Pperform-reconciliation
mvn test -Pforce-reconciliation

# PRODUCTION - Use with extreme caution
mvn test -Pperform-reconciliation-prod  
mvn test -Pforce-reconciliation-prod
```

## Safety Features

### Delete Threshold Protection
- Reconciliation will abort if discrepancies exceed `MAX_TO_DELETE_COUNT` (default: 350)
- This prevents accidental mass deletions due to data issues
- Can be bypassed with force mode only after manual review

### Time-Based Optimization  
- Expensive `getAllProducts()` operations run only once daily (3 AM PST by default)
- Configurable via `reconcile.daily.hour` property
- Regular sync operations continue every minute without expensive reconciliation

### Multiple Safety Checks
- Pre-reconciliation analysis with abort on threshold exceeded
- Post-reconciliation analysis to verify results
- Detailed logging of all changes
- 3-second delay before reconciliation with manual interrupt option

## Configuration

### Properties

```properties
# Daily reconciliation optimization - only run expensive getAllProducts() once per day
reconcile.daily.hour=3  # Hour (0-23) when daily reconciliation should run (PST)

# Safety threshold for reconciliation  
MAX_TO_DELETE_COUNT=350

# Force reconciliation (bypasses safety checks)
reconciliation.force=false
```

### Spring Profiles

Tests now use single, profile-specific configurations instead of mixed profiles:

**Development Profile:**
- `keystone-dev` - Development Shopify API + Development database
- Safe for testing and development work
- Lower safety thresholds and more lenient configurations

**Production Profile:**  
- `keystone-prod` - Production Shopify API + Production database
- Use with extreme caution - modifies live production data
- Higher safety thresholds and strict configurations

## Migration from Old System

The reconciliation logic has been completely extracted from `BaseShopifySyncService` to the dedicated `ReconciliationService`:

### What Changed
- ✅ Reconciliation completely removed from `BaseShopifySyncService`
- ✅ Dedicated `ReconciliationService` handles all reconciliation logic
- ✅ Analysis mode added for safe production inspection  
- ✅ Maven profiles for different reconciliation modes
- ✅ Time-based optimization removed from regular sync (now handled separately)
- ✅ Enhanced safety checks and reporting

### What Stays the Same
- ✅ Regular sync operations (every minute) unchanged
- ✅ All existing sync functionality preserved in `BaseShopifySyncService`
- ✅ Same reconciliation logic (just in dedicated service)
- ✅ All safety thresholds preserved

### Separation of Concerns
- **BaseShopifySyncService**: Handles regular sync operations only
  - Processing feed items
  - Publishing/updating products  
  - Managing collections and inventory
  - No reconciliation logic
  
- **ReconciliationService**: Handles reconciliation only
  - Analysis of discrepancies
  - Reconciliation operations
  - Safety checks and reporting
  - Accessed via dedicated tests/profiles

## Best Practices

### For Production Use

1. **Always Analyze First**
   ```bash
   mvn test -Panalyze-reconciliation
   ```

2. **Review Results Carefully**
   - Check if discrepancies are expected
   - Verify delete operations are acceptable
   - Ensure discrepancy count is reasonable

3. **Reconcile Conservatively**
   ```bash
   # Use normal mode (respects safety limits)
   mvn test -Pperform-reconciliation
   
   # Only use force mode after manual verification
   mvn test -Pforce-reconciliation
   ```

4. **Verify Results**
   - Run analysis again after reconciliation
   - Check that discrepancies were resolved
   - Monitor system behavior

### Scheduling Considerations

- **Analysis**: Can be run anytime safely (read-only)
- **Reconciliation**: Best run during low-traffic periods
- **Daily Reconciliation**: Automatically runs at 3 AM PST via cron job
- **Manual Reconciliation**: Use for immediate fixes or testing

## Troubleshooting

### "Too many items to reconcile" Error
- **Cause**: Discrepancy exceeds `MAX_TO_DELETE_COUNT`
- **Solution**: 
  1. Run analysis to understand scope
  2. Investigate why large discrepancy exists
  3. Fix underlying data issues
  4. Consider using force mode only if discrepancy is expected

### Large Numbers of Discrepancies
- **First**: Run analysis to understand the scope
- **Check**: Has there been a data import/export issue?
- **Verify**: Are feed sources working correctly?
- **Consider**: Running reconciliation in smaller batches

### Reconciliation Not Running Daily
- **Check**: `reconcile.daily.hour` configuration (default: 3)
- **Verify**: Cron schedule is running (`cron.schedule=0 * * * * *`)
- **Logs**: Look for "DAILY RECONCILIATION TIME" messages

## Commands Quick Reference

```bash
# DEVELOPMENT COMMANDS (Safe for testing)

# 1. Analyze discrepancies (SAFE - read-only)
mvn test -Panalyze-reconciliation

# 2. Perform reconciliation (respects safety limits)  
mvn test -Pperform-reconciliation

# 3. Force reconciliation (bypasses safety checks)
mvn test -Pforce-reconciliation

# PRODUCTION COMMANDS (Use with extreme caution)

# 1. Analyze production discrepancies (SAFE - read-only)
mvn test -Panalyze-reconciliation-prod

# 2. Perform production reconciliation (respects safety limits)  
mvn test -Pperform-reconciliation-prod

# 3. Force production reconciliation (bypasses safety checks)
mvn test -Pforce-reconciliation-prod

# DIRECT TEST EXECUTION

# 4. Run specific test directly
mvn test -Dtest=ReconciliationAnalysisTest
mvn test -Dtest=ReconciliationTest

# 5. With custom properties
mvn test -Pperform-reconciliation -Dreconciliation.force=true
mvn test -Pperform-reconciliation-prod -Dreconciliation.force=true
```

## Support

For issues or questions:
1. Check the analysis output for specific discrepancy details
2. Review system logs for error messages  
3. Verify configuration properties are correct
4. Ensure proper Spring profiles are active

Remember: **When in doubt, always run analysis first!** 