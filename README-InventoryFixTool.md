# 🚨 Production Inventory Fix Tool - Quick Reference

## 🛡️ CRITICAL SAFETY UPDATE 🛡️

**IMPORTANT**: This tool has been fixed to be production-safe! 
- ✅ **NO LONGER** extends BaseGraphqlTest (which would delete all data)
- ✅ **SAFE** to run in production environments
- ✅ **PRESERVES** existing Shopify products and database records

## 🚨 **MAJOR BUG FIX - API CORRECTION** 🚨

**CRITICAL FIX APPLIED**: The inventory fix tool now uses the correct Shopify API!

### **Problem Solved:**
- **BEFORE**: Tool used `inventoryAdjustQuantities` (delta/increment API)
- **ISSUE**: Inventory of 4 would become 5 when trying to set it to 1 (4 + 1 = 5) 
- **AFTER**: Tool uses `inventorySetQuantities` (absolute value API)
- **RESULT**: Inventory of 4 becomes 1 when set to 1 (correct absolute setting)

### **API Details:**
- **New API**: `inventorySetQuantities` - Sets exact inventory quantities
- **Reference**: https://shopify.dev/docs/api/admin-graphql/latest/mutations/inventorySetQuantities
- **Benefits**: Absolute value setting, no more increment issues

## 🔧 Production Profile Configuration

⚠️ **CRITICAL FOR PRODUCTION USE** ⚠️

**For production environments, you MUST specify the `keystone-prod` Spring profile:**

- **ALL production commands** must include: `-Dspring.profiles.active=keystone-prod`
- **Without this profile**, the tool may connect to the wrong database/environment
- **Development commands** can run without profile (uses default configuration)

**Quick Examples:**
```bash
# ❌ Development (may not connect to prod database)
mvn test -Dtest=InventoryFixTest#scanInventoryIssues

# ✅ Production (connects to production database)  
mvn test -Dtest=InventoryFixTest#scanInventoryIssues -Dspring.profiles.active=keystone-prod
```

**Safety Note**: Always verify you're connected to the correct environment by checking the scan results against expected product counts.

## ⚡ Quick Start Commands

### 🔧 Development/Testing Environment
For testing or development environments:

```bash
# 1. Scan for Issues (Safe - Read Only)
mvn test -Dtest=InventoryFixTest#scanInventoryIssues

# 2. Dry Run Fix (Safe - Shows What Would Change)
mvn test -Dtest=InventoryFixTest#fixInflatedInventoryLevels

# 3. Live Fix - All Products (⚠️ Makes Real Changes)
mvn test -Dtest=InventoryFixTest#fixInflatedInventoryLevels -DdryRun=false

# 4. 🆕 Live Fix - Specific Item (⚠️ Makes Real Changes)
mvn test -Dtest=InventoryFixTest#fixInflatedInventoryLevels -DdryRun=false -DwebTagNumber=YOUR_SKU

# 5. Scan Specific Item by SKU (Safe - Read Only)
mvn test -Dtest=InventoryFixTest#scanSpecificInventoryByWebTagNumber -DwebTagNumber=YOUR_SKU

# 6. Inventory by Location Overview (Safe - Read Only)
mvn test -Dtest=InventoryFixTest#showInventoryByLocationOverview
```

### 🚨 Production Environment (keystone-prod profile)
For production usage with keystone-prod active profile:

```bash
# 1. Scan for Issues (Safe - Read Only)
mvn test -Dtest=InventoryFixTest#scanInventoryIssues -Dspring.profiles.active=keystone-prod

# 2. Dry Run Fix (Safe - Shows What Would Change) 
mvn test -Dtest=InventoryFixTest#fixInflatedInventoryLevels -Dspring.profiles.active=keystone-prod

# 3. Live Fix - All Products (⚠️ Makes Real Changes to Production!)
mvn test -Dtest=InventoryFixTest#fixInflatedInventoryLevels -DdryRun=false -Dspring.profiles.active=keystone-prod

# 4. 🆕 Live Fix - Specific Item (⚠️ Makes Real Changes to Production!)
mvn test -Dtest=InventoryFixTest#fixInflatedInventoryLevels -DdryRun=false -DwebTagNumber=YOUR_SKU -Dspring.profiles.active=keystone-prod

# 5. Scan Specific Item by SKU (Safe - Read Only)
mvn test -Dtest=InventoryFixTest#scanSpecificInventoryByWebTagNumber -DwebTagNumber=YOUR_SKU -Dspring.profiles.active=keystone-prod

# 6. Inventory by Location Overview (Safe - Read Only)
mvn test -Dtest=InventoryFixTest#showInventoryByLocationOverview -Dspring.profiles.active=keystone-prod
```

---

## 🎯 What This Tool Does

**Problem**: Products in Shopify have inflated inventory (total > 1) due to bugs in sync process

**Solution**: 
- Scans all products for inventory issues
- Checks feed_item database for correct status
- Fixes inventory based on status:
  - `SOLD` items → 0 inventory
  - `AVAILABLE` items → 1 inventory

---

## 📍 NEW: Inventory by Location Features

### 🔍 Specific Item Scanner
**Purpose**: Deep-dive analysis for individual SKUs
- Complete product information (variants, options, images, metafields)
- **Enhanced location display** with formatted tables
- Database vs Shopify comparison
- Automatic inventory issue detection

**Usage**: 
```bash
# Parameter method (recommended - no source edits needed)
mvn test -Dtest=InventoryFixTest#scanSpecificInventoryByWebTagNumber -DwebTagNumber=YOUR_SKU

# Alternative: Edit TARGET_WEB_TAG_NUMBER in the method, then run
mvn test -Dtest=InventoryFixTest#scanSpecificInventoryByWebTagNumber
```

### 📊 Location Overview Scanner  
**Purpose**: Understand inventory distribution across all locations
- Complete analysis of all products and locations
- Location rankings by inventory and product count
- Automatic detection of concentration risks
- Statistical analysis and averages

**Sample Output**:
```
📍 INVENTORY BY LOCATION BREAKDOWN:
========================================================
  #  |          Location ID           | Total Products | Products w/ Inventory | Total Inventory | Avg per Product
========================================================
  1  |   gid://shopify/Location/123   |      1,245     |         623          |      1,456      |      1.17
  2  |   gid://shopify/Location/456   |      1,245     |         234          |        289      |      0.23
========================================================

🏆 TOP 5 LOCATIONS BY TOTAL INVENTORY:
  1. Location: gid://shopify/Location/123 - 1,456 units (83.4% of total)
  2. Location: gid://shopify/Location/456 - 289 units (16.6% of total)

⚠️ POTENTIAL ISSUES DETECTED:
  - Location gid://shopify/Location/123 contains 83.4% of total inventory (potential concentration risk)
```

### 🔧 Enhanced Fix Display
All inventory fix operations now show **detailed location information**:
```
📍 Updating inventory across 2 locations:
==========================================================================================
 Location  |       Location ID       |  Current Qty  |   New Qty   |  Change  
==========================================================================================
    1      |   gid://shopify/...     |       3       |      1      |    -2    
    2      |   gid://shopify/...     |       1       |      0      |    -1    
==========================================================================================
```

---

## 📊 Understanding Output

### Scan Results
```
📊 Inventory Issues Summary:
  - Total products with issues: 25
  - Total excess inventory: 47
  - Issues by status:
    - SOLD: 8 products        (should be 0 inventory)
    - AVAILABLE: 15 products  (should be 1 inventory)  
    - NOT_FOUND: 2 products   (need manual review)
```

### Fix Plan
```
SKU             Product Title                Current  Correct  Diff  Status     Action
WATCH-001       Rolex Submariner            3        1        2     AVAILABLE  REDUCE
WATCH-002       Patek Philippe             4        0        4     SOLD       REDUCE
```

### 🆕 Enhanced Location Tables
All methods now show inventory with **formatted location tables**:
```
📊 INVENTORY LEVELS BY LOCATION:
================================================================================
   Location #    |     Location ID      | Available  |  Inventory Item ID   
================================================================================
   Location 1    |   gid://shopify/...  |     1      |   gid://shopify/...
   Location 2    |   gid://shopify/...  |     0      |   gid://shopify/...
================================================================================
```

### Fix Results
```
📊 Inventory Fix Results:
  - Successful fixes: 23
  - Failed fixes: 2
  - Success rate: 92.00%
```

### 🆕 Parameter Control
**No more editing source code!** Control via command line parameters:

**Dry Run Control:**
```bash
# Safe dry run (default)
mvn test -Dtest=InventoryFixTest#fixInflatedInventoryLevels

# Live mode with actual changes
mvn test -Dtest=InventoryFixTest#fixInflatedInventoryLevels -DdryRun=false

# Explicit dry run 
mvn test -Dtest=InventoryFixTest#fixInflatedInventoryLevels -DdryRun=true
```

**🆕 Targeted Fix Control:**
```bash
# Fix all products with inventory issues
mvn test -Dtest=InventoryFixTest#fixInflatedInventoryLevels -DdryRun=false

# Fix only a specific SKU (much faster!)
mvn test -Dtest=InventoryFixTest#fixInflatedInventoryLevels -DdryRun=false -DwebTagNumber=WATCH-001

# Dry run for specific SKU
mvn test -Dtest=InventoryFixTest#fixInflatedInventoryLevels -DwebTagNumber=WATCH-001

# Multiple examples
mvn test -Dtest=InventoryFixTest#fixInflatedInventoryLevels -DwebTagNumber=GW12345 -DdryRun=false
mvn test -Dtest=InventoryFixTest#fixInflatedInventoryLevels -DwebTagNumber=ROL2023-001
```

**Specific SKU Analysis:**
```bash
# Analyze a specific SKU (recommended method)
mvn test -Dtest=InventoryFixTest#scanSpecificInventoryByWebTagNumber -DwebTagNumber=WATCH-001

# Multiple examples
mvn test -Dtest=InventoryFixTest#scanSpecificInventoryByWebTagNumber -DwebTagNumber=GW12345
mvn test -Dtest=InventoryFixTest#scanSpecificInventoryByWebTagNumber -DwebTagNumber=ROL2023-001

# Alternative: edit constant in source code (not recommended)
mvn test -Dtest=InventoryFixTest#scanSpecificInventoryByWebTagNumber
```

---

## 🚨 Safety Checklist

### Before Running
- [ ] Test on dev/staging first
- [ ] Run scan to understand scope
- [ ] Coordinate with team
- [ ] Schedule during low-traffic hours
- [ ] Backup recommended

### During Execution
- [ ] Monitor for errors
- [ ] Watch success/failure rates  
- [ ] Ready to stop if needed

### After Execution
- [ ] Verify inventory corrections
- [ ] Check for customer impact
- [ ] Save logs for audit
- [ ] ~~Restore `DRY_RUN = true`~~ (No longer needed - uses parameters!)

---

## 🔧 Troubleshooting

| Issue | Meaning | Action |
|-------|---------|--------|
| "No inventory issues found" | ✅ All good! | Normal - no action needed |
| "Feed item not found" | SKU in Shopify but not in DB | Review manually, may be test data |
| "Failed to fix inventory" | API call failed | Check network/API limits |
| High failure rate | System issues | Stop and investigate |
| **High location concentration** | 🆕 Most inventory in one location | Consider redistribution |
| **Location not found** | 🆕 Invalid location ID | Check Shopify location setup |

---

## 📅 Recommended Schedule

- **Weekly**: Run scan to monitor issues
- **Monthly**: Run location overview to check distribution patterns  
- **As needed**: Fix when >5% of products affected
- **After deployments**: Scan after sync system changes
- **Emergency**: Use quick commands above

---

## ⚡ Emergency Quick Fix

### 🔧 Development/Testing Emergency
If inventory issues are urgent in development:

```bash
# 1. Quick check
mvn test -Dtest=InventoryFixTest#scanInventoryIssues | grep "Total products with issues"

# 2. If issues found, apply fixes immediately:
# All products:
mvn test -Dtest=InventoryFixTest#fixInflatedInventoryLevels -DdryRun=false
# Specific item:
mvn test -Dtest=InventoryFixTest#fixInflatedInventoryLevels -DdryRun=false -DwebTagNumber=YOUR_SKU
```

### 🚨 Production Emergency (keystone-prod)
If inventory issues are urgent in production:

```bash
# 1. Quick check
mvn test -Dtest=InventoryFixTest#scanInventoryIssues -Dspring.profiles.active=keystone-prod | grep "Total products with issues"

# 2. If issues found, apply fixes immediately:
# All products:
mvn test -Dtest=InventoryFixTest#fixInflatedInventoryLevels -DdryRun=false -Dspring.profiles.active=keystone-prod
# Specific item:
mvn test -Dtest=InventoryFixTest#fixInflatedInventoryLevels -DdryRun=false -DwebTagNumber=YOUR_SKU -Dspring.profiles.active=keystone-prod
```

### 🆕 Quick Location Analysis

**Development/Testing:**
```bash
# Check inventory distribution
mvn test -Dtest=InventoryFixTest#showInventoryByLocationOverview | grep "TOP 5"

# Analyze specific SKU
mvn test -Dtest=InventoryFixTest#scanSpecificInventoryByWebTagNumber -DwebTagNumber=YOUR_SKU
```

**Production (keystone-prod):**
```bash
# Check inventory distribution
mvn test -Dtest=InventoryFixTest#showInventoryByLocationOverview -Dspring.profiles.active=keystone-prod | grep "TOP 5"

# Analyze specific SKU
mvn test -Dtest=InventoryFixTest#scanSpecificInventoryByWebTagNumber -DwebTagNumber=YOUR_SKU -Dspring.profiles.active=keystone-prod
```

---

## 📂 File Locations

- **Test class**: `src/test/java/com/gw/service/InventoryFixTest.java`
- **~~DRY_RUN setting~~**: ~~Line ~31 in InventoryFixTest.java~~ (Now uses -DdryRun parameter!)
- **~~TARGET_WEB_TAG_NUMBER~~**: ~~Line ~26 in scanSpecificInventoryByWebTagNumber method~~ (Now uses -DwebTagNumber parameter!)
- **This README**: `README-InventoryFixTool.md`

---

## 🎯 Available Methods Summary

| Method | Purpose | Safety | Output |
|--------|---------|--------|---------|
| `scanInventoryIssues` | Find all inventory issues | ✅ Read-only | Issue summary by status |
| `fixInflatedInventoryLevels` | Fix inventory issues | ⚠️ Respects -DdryRun parameter | Detailed fix plan + results |
| **🆕 `scanSpecificInventoryByWebTagNumber`** | **Deep-dive single SKU analysis** | **✅ Read-only** | **Complete product + location details** |
| **🆕 `showInventoryByLocationOverview`** | **Analyze inventory distribution** | **✅ Read-only** | **Location statistics + rankings** |

---

## 🆘 Support

For questions or issues:
1. Check the troubleshooting section above
2. Review the full documentation in `InventoryFixTest.java`
3. Contact the development team

**Remember**: Always start with the scan method - it's safe and shows you what needs fixing! 

**🆕 For location issues**: Use the new location overview method to understand inventory distribution patterns across your Shopify locations. 