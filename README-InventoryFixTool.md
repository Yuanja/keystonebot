# 🚨 Production Inventory Fix Tool - Quick Reference

## 🛡️ CRITICAL SAFETY UPDATE 🛡️

**IMPORTANT**: This tool has been fixed to be production-safe! 
- ✅ **NO LONGER** extends BaseGraphqlTest (which would delete all data)
- ✅ **SAFE** to run in production environments
- ✅ **PRESERVES** existing Shopify products and database records

## ⚡ Quick Start Commands

### 1. Scan for Issues (Safe - Read Only)
```bash
mvn test -Dtest=InventoryFixTest#scanInventoryIssues
```

### 2. Dry Run Fix (Safe - Shows What Would Change)
```bash
mvn test -Dtest=InventoryFixTest#fixInflatedInventoryLevels
```

### 3. Live Fix (⚠️ Makes Real Changes)
```bash
mvn test -Dtest=InventoryFixTest#fixInflatedInventoryLevels -DdryRun=false
```

### 4. 🆕 Scan Specific Item by SKU (Safe - Read Only)
```bash
# Parameter method (recommended)
mvn test -Dtest=InventoryFixTest#scanSpecificInventoryByWebTagNumber -DwebTagNumber=YOUR_SKU

# Alternative: Edit TARGET_WEB_TAG_NUMBER in method, then run
mvn test -Dtest=InventoryFixTest#scanSpecificInventoryByWebTagNumber
```

### 5. 🆕 Inventory by Location Overview (Safe - Read Only)
```bash
mvn test -Dtest=InventoryFixTest#showInventoryByLocationOverview
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

If inventory issues are urgent:

```bash
# 1. Quick check
mvn test -Dtest=InventoryFixTest#scanInventoryIssues | grep "Total products with issues"

# 2. If issues found, apply fixes immediately:
mvn test -Dtest=InventoryFixTest#fixInflatedInventoryLevels -DdryRun=false
```

### 🆕 Quick Location Analysis
For location-specific issues:

```bash
# Check inventory distribution
mvn test -Dtest=InventoryFixTest#showInventoryByLocationOverview | grep "TOP 5"

# Analyze specific SKU (parameter method)
mvn test -Dtest=InventoryFixTest#scanSpecificInventoryByWebTagNumber -DwebTagNumber=YOUR_SKU
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