# Production Diagnostics Tests

This directory contains production diagnostic and maintenance tools designed to be safely run against live production data.

## 🚨 CRITICAL SAFETY INFORMATION 🚨

### **PRODUCTION SAFE DESIGN**
- ✅ **Does NOT extend BaseGraphqlTest** - Will NOT delete existing data
- ✅ **Parameter controlled** - No source code edits needed for configuration
- ✅ **Dry run by default** - Safe mode unless explicitly overridden
- ✅ **Comprehensive logging** - Detailed output for audit trails

### **BEFORE RUNNING IN PRODUCTION**
1. **Test on dev/staging first** if possible
2. **Run during low-traffic periods** to minimize impact  
3. **Coordinate with team** to ensure no concurrent operations
4. **Have rollback plan ready**
5. **Monitor system during execution**

## 📁 Available Tools

### `InventoryFixTest.java`
**Comprehensive Inventory Rules Enforcer**

Enforces strict inventory rules across all Shopify products:
- Inventory can NEVER be above 1
- SOLD items → inventory = 0
- All other items → inventory = 1

**Key Methods:**
- `fixInflatedInventoryLevels()` - Main comprehensive fix method
- `scanInventoryIssues()` - Read-only analysis (SAFE)
- `scanSpecificInventoryByWebTagNumber()` - Deep-dive single SKU analysis  
- `showInventoryByLocationOverview()` - Location distribution analysis

### `ReconciliationAnalysisTest.java`
**Read-Only Data Reconciliation Analyzer (SAFE)**

Analyzes discrepancies between database, Shopify, and feed data without making changes:
- Database vs Shopify synchronization status
- Orphaned products and missing items
- Shopify ID mismatches
- Image count discrepancies

**Key Methods:**
- `testReconciliationAnalysis()` - Comprehensive read-only analysis

### `ReconciliationTest.java`
**Data Reconciliation Enforcer (MODIFIES DATA)**

Performs actual reconciliation to synchronize database and Shopify:
- Deletes extra products from Shopify
- Removes orphaned items from database
- Fixes Shopify ID mismatches
- Marks items for image re-sync

**Key Methods:**
- `testReconciliation()` - Full reconciliation with safety checks

### `ForceUpdateTest.java`
**Production Force Update Tool (MODIFIES DATA)**

Comprehensive force update capabilities for production environments:
- Force update all items with fresh feed data
- Force update specific items by web tag number
- Validate and recreate eBay metafields
- Retry failed update items
- Production-safe batch processing

**Key Methods:**
- `forceUpdateAllItems()` - Updates all items with fresh feed data
- `forceUpdateSpecificItem()` - Target specific item by web tag number
- `analyzeForceUpdateImpact()` - Read-only analysis of potential updates
- `validateAndFixEbayMetafields()` - Validate/recreate eBay metafields
- `retryUpdateFailedItems()` - Retry items with STATUS_UPDATE_FAILED

## 🎯 Usage Patterns

### **Scan First (Read-Only - Always Safe)**
```bash
# Inventory analysis
mvn test -Dtest=InventoryFixTest#scanInventoryIssues
mvn test -Dtest=InventoryFixTest#showInventoryByLocationOverview
mvn test -Dtest=InventoryFixTest#scanSpecificInventoryByWebTagNumber -DwebTagNumber=YOUR_SKU

# Data reconciliation analysis (read-only)
mvn test -Dtest=ReconciliationAnalysisTest#testReconciliationAnalysis

# Force update impact analysis (read-only)
mvn test -Dtest=ForceUpdateTest#analyzeForceUpdateImpact
```

### **Dry Run (Simulation - Safe)**
```bash
# All products dry run
mvn test -Dtest=InventoryFixTest#fixInflatedInventoryLevels

# Specific item dry run
mvn test -Dtest=InventoryFixTest#fixInflatedInventoryLevels -DwebTagNumber=YOUR_SKU
```

### **Live Mode (Makes Real Changes)**
```bash
# Inventory fixes
mvn test -Dtest=InventoryFixTest#fixInflatedInventoryLevels -DdryRun=false
mvn test -Dtest=InventoryFixTest#fixInflatedInventoryLevels -DdryRun=false -DwebTagNumber=YOUR_SKU

# Data reconciliation (DANGEROUS - deletes/modifies data)
mvn test -Dtest=ReconciliationTest#testReconciliation

# Force updates (MODIFIES PRODUCTS)
mvn test -Dtest=ForceUpdateTest#forceUpdateAllItems
mvn test -Dtest=ForceUpdateTest#forceUpdateSpecificItem -Dforce.update.web_tag_number=YOUR_SKU
mvn test -Dtest=ForceUpdateTest#validateAndFixEbayMetafields
mvn test -Dtest=ForceUpdateTest#retryUpdateFailedItems
```

## 📊 Parameter Control

All tools support parameter-based control without source code edits:

- `-DdryRun=false` - Enable live mode (default: true)
- `-DwebTagNumber=SKU` - Target specific item
- `-Dspring.profiles.active=keystone-dev` - Environment selection

## 🔧 Best Practices

### **Execution Workflow**
1. **Scan** → Understand scope and issues
2. **Dry run** → Verify fix plan  
3. **Live mode** → Apply actual fixes
4. **Verify** → Confirm results

### **Monitoring**
- Watch logs for error rates
- Monitor success/failure counts
- Check system performance impact
- Save output for audit trails

### **Emergency Stop**
If issues arise during execution:
- Stop the process (Ctrl+C)
- Review logs for errors
- Run scan methods to assess current state
- Coordinate with team before resuming

## 📋 Maintenance Schedule

**Recommended Frequency:**
- **Weekly scan:** Check for inventory issues
- **Monthly overview:** Location distribution analysis  
- **As needed:** Fix when issues > 5% of products
- **After updates:** When sync system changes deployed

## 🚨 Troubleshooting

### Common Issues
- **High failure rate:** Check API rate limits, network connectivity
- **No issues found:** Good! System working correctly
- **Feed item not found:** SKU exists in Shopify but not in database
- **Product not found:** Database has stale Shopify ID

### Error Recovery
- Review detailed logs for specific failures
- Use scan methods to assess current state
- Run targeted fixes for specific SKUs
- Manual review may be needed for persistent failures

## 📞 Support

For issues or questions about production diagnostic tools:
1. Check logs for detailed error information
2. Run read-only scan methods first
3. Review this documentation
4. Coordinate with development team

---

**Remember:** These tools modify production data. Always test thoroughly and use appropriate caution. 