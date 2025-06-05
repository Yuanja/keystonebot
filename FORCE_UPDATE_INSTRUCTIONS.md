# Force Update Test for Production

## Overview

The Force Update Test provides comprehensive force update capabilities for production environments. This functionality allows you to:

1. **Force update ALL items** with fresh feed data (smallest web_tag_number first)
2. **Force update specific items** by web_tag_number using database data
3. **Sync database only** without making any Shopify API calls
4. **Validate and fix eBay metafields** to ensure proper configuration and pinning
5. **Analyze impact** before making changes (read-only dry run)
6. **Production-safe batch processing** with detailed logging

## ⚠️ Important Safety Notes

- **ALWAYS analyze impact first** before running production force updates
- **Test in development environment** before running in production
- **Force updates bypass change detection** - they will update ALL items regardless
- **Use specific item updates** for surgical fixes rather than full updates
- **Metafield fixes are safe** - they only affect admin interface visibility
- **Monitor logs carefully** during execution

## Available Commands

### 1. 📊 ANALYZE IMPACT (Read-Only) - ALWAYS RUN FIRST

Analyze what would be force updated without making any changes.

#### Development Analysis:
```bash
mvn test -P analyze-force-update
```

#### Production Analysis:
```bash
mvn test -P analyze-force-update-prod
```

**Output Example:**
```
📊 Force Update Impact Analysis:
  - Total feed items: 1,247
  - Items that would be updated: 1,247 (100.00%)
  - New items that would be created: 0
  - Items that would be deleted: 0
  - Update percentage: 100.00%

🔢 Processing order (by web_tag_number):
  - First item: 10001
  - Last item: 99999

📋 Sample of items that would be updated:
  📝 10001 (Status: PUBLISHED → PUBLISHED)
  📝 10002 (Status: PUBLISHED → PUBLISHED)
  ...
```

### 2. 🎯 FORCE UPDATE SPECIFIC ITEM

Update a single item by web_tag_number using existing database data (no feed refresh).

#### Development:
```bash
mvn test -P force-update-item-dev -Dforce.update.web_tag_number=12345
```

#### Production:
```bash
mvn test -P force-update-item-prod -Dforce.update.web_tag_number=12345
```

**Features:**
- ✅ Uses existing database data (no web feed refresh)
- ✅ Surgical precision for specific item fixes
- ✅ Detailed validation and logging
- ✅ Safe for production use

### 3. 🏷️ VALIDATE AND FIX EBAY METAFIELDS

Ensure eBay metafield definitions are properly configured and pinned in Shopify admin.

#### Development:
```bash
mvn test -P fix-ebay-metafields-dev
```

#### Production:
```bash
mvn test -P fix-ebay-metafields-prod
```

**What it does:**
- 🔍 **Analyzes current metafield state** (count, structure, pinning status)
- 🗑️ **Removes corrupted metafields** if structure is invalid or incomplete
- 🏗️ **Recreates fresh metafields** with correct definitions and pinning
- 📌 **Fixes pinning only** if metafields exist but aren't pinned
- ✅ **Validates results** to ensure everything is correct

**Output Example:**
```
📊 Current eBay Metafield State:
  - Found metafields: 13
  - Expected metafields: 13
  - Pinned metafields: 13/13
  - All pinned: true

📋 Current eBay Metafields:
  - brand (Brand) - 📌 Position 1
  - case_material (Case Material) - 📌 Position 2
  - category (Category) - 📌 Position 3
  ...

✅ All eBay metafields are correctly configured and pinned
🎉 No action needed - metafields are in perfect state!
```

### 4. 🚀 FORCE UPDATE ALL ITEMS (DANGEROUS)

Force update ALL items with fresh feed data, processed from smallest to largest web_tag_number.

#### Development:
```bash
mvn test -P force-update-all-dev
```

#### Production (USE WITH EXTREME CAUTION):
```bash
mvn test -P force-update-all-prod
```

**Features:**
- 📡 Refreshes live feed data
- 🔢 Processes from smallest web_tag_number first
- 📦 Controlled batches of 25 items
- ⏱️ 2-second delays between batches
- 📊 Comprehensive progress tracking

### 5. 🗄️ DATABASE-ONLY SYNC (No Shopify Operations)

Sync the database with feed data without making any Shopify API calls. This is useful for updating database state without affecting Shopify products.

#### Development Database Sync:
```bash
mvn test -P sync-database-only-dev
```

#### Production Database Sync:
```bash
mvn test -P sync-database-only-prod
```

**What this does:**
- Refreshes live feed data
- Compares feed with database
- Adds new items to database (status: FEED_SYNCED)
- Updates changed items in database (preserves Shopify IDs and statuses)
- Removes deleted items from database (⚠️ Shopify products remain orphaned)
- **NO Shopify API calls are made**

**Output Example:**
```
📊 Change Analysis:
  - New items (not in DB): 15
  - Changed items (different data): 23
  - Deleted items (in DB but not in feed): 2
  - Total feed items: 1,247

📊 Database-only sync results:
  - Total operations: 40
  - Total errors: 0
  - Success rate: 100.00%

ℹ️ NOTE: No Shopify products were modified - database only sync
```

### 6. 🔧 VALIDATE AND FIX EBAY METAFIELDS

Ensure eBay metafield definitions are properly configured and pinned in Shopify admin.

#### Development:
```bash
mvn test -P fix-ebay-metafields-dev
```

#### Production:
```bash
mvn test -P fix-ebay-metafields-prod
```

**What it does:**
- 🔍 **Analyzes current metafield state** (count, structure, pinning status)
- 🗑️ **Removes corrupted metafields** if structure is invalid or incomplete
- 🏗️ **Recreates fresh metafields** with correct definitions and pinning
- 📌 **Fixes pinning only** if metafields exist but aren't pinned
- ✅ **Validates results** to ensure everything is correct

**Output Example:**
```
📊 Current eBay Metafield State:
  - Found metafields: 13
  - Expected metafields: 13
  - Pinned metafields: 13/13
  - All pinned: true

📋 Current eBay Metafields:
  - brand (Brand) - 📌 Position 1
  - case_material (Case Material) - 📌 Position 2
  - category (Category) - 📌 Position 3
  ...

✅ All eBay metafields are correctly configured and pinned
🎉 No action needed - metafields are in perfect state!
```

## Typical Production Workflow

### Always Analyze First
```bash
mvn test -P analyze-force-update-prod
```

Review the output to understand:
- How many items will be affected
- What the processing order will be
- Sample of items that would change

### Choose Your Approach

**Option A: Fix eBay Metafields**
If metafields are causing issues or not properly pinned:
```bash
mvn test -P fix-ebay-metafields-prod
```

**Option B: Specific Item Fix**
If you need to fix a specific item:
```bash
mvn test -P force-update-item-prod -Dforce.update.web_tag_number=200376
```

**Option C: Full Force Update (High Risk)**
Only if you need to force update everything:
```bash
mvn test -P force-update-all-prod
```

### Monitor Progress

Watch the logs carefully for:
- ✅ Successful updates
- ❌ Any errors or failures
- 📊 Batch completion statistics
- 🎉 Final validation results

## Command Reference Table

| Command | Environment | Purpose | Risk Level | Refreshes Feed | Modifies Products |
|---------|-------------|---------|------------|----------------|-------------------|
| `analyze-force-update` | Dev | Analysis only | ✅ Safe | Yes | No |
| `analyze-force-update-prod` | Production | Analysis only | ✅ Safe | Yes | No |
| `fix-ebay-metafields-dev` | Dev | Fix metafields | ✅ Safe | No | No |
| `fix-ebay-metafields-prod` | Production | Fix metafields | ✅ Safe | No | No |
| `force-update-item-dev` | Dev | Single item | ⚠️ Medium | No | Yes |
| `force-update-item-prod` | Production | Single item | ⚠️ Medium | No | Yes |
| `force-update-all-dev` | Dev | All items | 🚨 High | Yes | Yes |
| `force-update-all-prod` | Production | All items | 🚨 EXTREME | Yes | Yes |

## eBay Metafield Details

The system manages 13 eBay metafield definitions using the `EbayMetafieldDefinition` enum as the single source of truth:

### Metafield Definitions

All metafield definitions are centrally managed in `src/main/java/com/gw/domain/EbayMetafieldDefinition.java`:

1. **brand** - Watch brand/manufacturer
2. **case_material** - Case material (gold, steel, etc.)
3. **category** - Product category
4. **condition** - Watch condition (new, used, etc.)
5. **dial** - Dial color/type
6. **diameter** - Case diameter
7. **model** - Watch model
8. **movement** - Movement type (automatic, quartz, etc.)
9. **reference_number** - Manufacturer reference number
10. **box_papers** - Includes box and papers
11. **strap** - Strap/bracelet type
12. **style** - Watch style category
13. **year** - Year of manufacture

### Enum Benefits

**Single Source of Truth:**
- All metafield definitions are centralized in the enum
- Easy to add, remove, or modify metafield definitions
- Compile-time validation of metafield structure
- Consistent validation across all services

**Easy Maintenance:**
- Adding a new metafield: Just add it to the enum
- Removing a metafield: Remove it from the enum and recompile
- Changing metafield properties: Update the enum constructor call
- Automatic count validation using `EbayMetafieldDefinition.getCount()`

**Usage in Code:**
```java
// Get all metafield keys for validation
List<String> allKeys = EbayMetafieldDefinition.getAllKeys();

// Check if a key exists
boolean exists = EbayMetafieldDefinition.hasKey("brand");

// Get total count for validation
int expectedCount = EbayMetafieldDefinition.getCount(); // Returns 13
```

**Why Pinning Matters:**
- Pinned metafields appear prominently in Shopify admin
- Unpinned metafields are hidden in a collapsible section
- Proper pinning improves data entry workflow
- Makes metafields more accessible to staff

## Error Handling

The force update process includes comprehensive error handling:

- **Batch-level errors**: Isolated to prevent cascade failures
- **Individual item errors**: Logged with details but don't stop the batch
- **Validation errors**: Comprehensive pre/post update validation
- **Rate limiting**: Built-in delays to prevent API overwhelm
- **Metafield validation**: Checks structure, count, and pinning status

## Configuration

Key settings in `ForceUpdateTest.java`:

```java
private static final int BATCH_SIZE = 25;  // Items per batch
private static final boolean DRY_RUN = false;  // Set to true for testing
```

- **BATCH_SIZE**: Controls how many items are processed together
- **DRY_RUN**: When true, simulates the process without making changes

## Monitoring and Logs

The force update process provides detailed logging:

```
=== Starting Production Force Update (All Items) ===
🚀 This will force update ALL items in production
📡 Will refresh live feed data
🔢 Processing from smallest web_tag_number first

📊 Analyzing current state...
📋 Database State:
  - Total items in database: 1,247
  - Items with Shopify ID: 1,198

📡 Refreshing live feed data...
📊 Fresh feed items loaded: 1,247

🔢 Items sorted by web_tag_number (smallest first)
📋 First 5 items: [10001, 10002, 10003, 10004, 10005]

🔄 Force updating items in batches of 25...
📦 Processing batch 1/50 (items 0-24)
🔢 Batch web_tag_numbers: 10001, 10002, 10003, ...
✅ Batch completed: 25 processed, 25 updated, 0 errors

📊 Overall Results (ALL-ITEMS):
  - Total items processed: 1,247
  - Total items updated: 1,247
  - Total errors: 0
  - Success rate: 100.00%

🎉 Production force update completed successfully!
```

## Support

For issues or questions:
1. Check the detailed logs for specific error messages
2. Verify your environment configuration
3. Test in development first
4. Contact the development team with log excerpts

---

**⚠️ Remember**: Force updates are powerful tools. Always analyze first, test in development, and use with caution in production! 