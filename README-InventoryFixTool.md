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
# FIRST: Edit src/test/java/com/gw/service/InventoryFixTest.java
# Change: private static boolean DRY_RUN = false;

mvn test -Dtest=InventoryFixTest#fixInflatedInventoryLevels

# IMMEDIATELY AFTER: Change back to DRY_RUN = true;
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

### Fix Results
```
📊 Inventory Fix Results:
  - Successful fixes: 23
  - Failed fixes: 2
  - Success rate: 92.00%
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
- [ ] Restore `DRY_RUN = true`

---

## 🔧 Troubleshooting

| Issue | Meaning | Action |
|-------|---------|--------|
| "No inventory issues found" | ✅ All good! | Normal - no action needed |
| "Feed item not found" | SKU in Shopify but not in DB | Review manually, may be test data |
| "Failed to fix inventory" | API call failed | Check network/API limits |
| High failure rate | System issues | Stop and investigate |

---

## 📅 Recommended Schedule

- **Weekly**: Run scan to monitor issues
- **As needed**: Fix when >5% of products affected
- **After deployments**: Scan after sync system changes
- **Emergency**: Use quick commands above

---

## ⚡ Emergency Quick Fix

If inventory issues are urgent:

```bash
# 1. Quick check
mvn test -Dtest=InventoryFixTest#scanInventoryIssues | grep "Total products with issues"

# 2. If issues found, edit file to set DRY_RUN = false, then:
mvn test -Dtest=InventoryFixTest#fixInflatedInventoryLevels

# 3. Immediately set DRY_RUN = true
```

---

## 📂 File Locations

- **Test class**: `src/test/java/com/gw/service/InventoryFixTest.java`
- **DRY_RUN setting**: Line ~31 in InventoryFixTest.java
- **This README**: `README-InventoryFixTool.md`

---

## 🆘 Support

For questions or issues:
1. Check the troubleshooting section above
2. Review the full documentation in `InventoryFixTest.java`
3. Contact the development team

**Remember**: Always start with the scan method - it's safe and shows you what needs fixing! 