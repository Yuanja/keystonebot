# Force Update Test for Production

## Overview

The Force Update Test provides comprehensive force update capabilities for production environments. This functionality allows you to:

1. **Force update ALL items** with fresh feed data (smallest web_tag_number first)
2. **Force update specific items** by web_tag_number using database data
3. **Sync database only** without making any Shopify API calls
4. **Retry failed items** that have STATUS_UPDATE_FAILED status
5. **Validate and fix eBay metafields** to ensure proper configuration and pinning
6. **Fix empty product descriptions** by updating from FeedItem data
7. **Analyze impact** before making changes (read-only dry run)
8. **Production-safe batch processing** with detailed logging

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

### 6. 🔄 RETRY ITEMS WITH STATUS_UPDATE_FAILED

Retry all items in the database that have STATUS_UPDATE_FAILED using the ProductUpdatePipeline. This is useful for recovering from previous failed update attempts.

#### Development Retry:
```bash
mvn test -Dtest=ForceUpdateTest#retryUpdateFailedItems -Dspring.profiles.active=keystone-dev
```

#### Production Retry:
```bash
mvn test -Dtest=ForceUpdateTest#retryUpdateFailedItems -Dspring.profiles.active=keystone-prod
```

**What this does:**
- 🔍 **Finds all items** with STATUS_UPDATE_FAILED in database
- 🛍️ **Filters to retryable items** (only those with Shopify IDs)
- 🔄 **Simple for loop approach** - no batching, processes one by one
- ⚡ **Uses ProductUpdatePipeline** for efficient individual item updates
- 🏷️ **Handles collection errors gracefully** (treats as non-critical)
- 📊 **Provides detailed progress tracking** and final statistics

**Features:**
- ✅ Uses existing database data (no feed refresh)
- ✅ Simple and straightforward approach
- ✅ Individual item processing with detailed logging
- ✅ Safe error handling for collection association failures
- ✅ Comprehensive success/failure reporting

**Output Example:**
```
📊 Update Failed Items Analysis:
  - Items with STATUS_UPDATE_FAILED: 23
  - Items with Shopify ID (retryable): 20
  - Items without Shopify ID (skipped): 3

📋 Sample of items to retry:
  📝 12345 (Shopify ID: gid://shopify/Product/8961333461231)
  📝 12346 (Shopify ID: gid://shopify/Product/8961333461232)
  ...

🔄 Starting simple retry process...
🔄 Retrying item 1/20: 12345
✅ Successfully retried item: 12345
🔄 Retrying item 2/20: 12346
⚠️ Collection association issues for item: 12346
✅ Item retry succeeded despite collection issues

📊 Retry Results:
  - Total items processed: 20
  - Total items succeeded: 18
  - Total items still failed: 2
  - Success rate: 90.00%
```

### 7. 📝 FIX EMPTY PRODUCT DESCRIPTIONS

Identify products with empty descriptions in Shopify and fix them using data from corresponding FeedItems.

#### Development (DRY RUN):
```bash
mvn test -Dtest=ForceUpdateTest#fixEmptyDescriptions -Dspring.profiles.active=keystone-dev -Ddry.run=true
```

#### Development (REAL RUN):
```bash
mvn test -Dtest=ForceUpdateTest#fixEmptyDescriptions -Dspring.profiles.active=keystone-dev -Ddry.run=false
```

#### Production (DRY RUN):
```bash
mvn test -Dtest=ForceUpdateTest#fixEmptyDescriptions -Dspring.profiles.active=keystone-prod -Ddry.run=true
```

#### Production (REAL RUN):
```bash
mvn test -Dtest=ForceUpdateTest#fixEmptyDescriptions -Dspring.profiles.active=keystone-prod -Ddry.run=false
```

**What this does:**
- 🛍️ **Gets all products from Shopify** and checks if they have descriptions
- 🔍 **Matches products with FeedItems** by SKU (product.variants[0].sku = feedItem.webTagNumber)
- 📝 **Identifies empty descriptions** (null or empty bodyHtml)
- 🔧 **Force updates empty descriptions** using feedItem.webDescriptionShort as source data
- 📦 **Processes in controlled batches** (25 items) with 2-second delays
- ✅ **Validates results** to ensure descriptions were properly updated

**Features:**
- ✅ Uses existing database data (no feed refresh)
- ✅ Only updates products that actually need fixing
- ✅ Matches products to FeedItems by SKU for data source
- ✅ Comprehensive validation before and after updates
- ✅ Safe batch processing with detailed logging
- ✅ Graceful error handling for individual items
- ✅ **Configurable DRY RUN mode** via `-Ddry.run=true/false` parameter

**Output Example:**
```
📊 Description Validation Results:
  - Total products checked: 1,247
  - Products with descriptions: 1,180
  - Products with empty descriptions: 67
  - Products without matching feed items: 15
  - Products ready for description fix: 52

📋 Sample products with empty descriptions:
  - SKU: 12345
  - SKU: 12378
  - SKU: 12401
  ...

📦 Processing description fix batch 1/3 (items 0-24)
🔢 Batch web_tag_numbers: 12345, 12378, 12401, ...
🔧 Fixing description for SKU: 12345
✅ Fixed description for: 12345
...

📊 Description Fix Results:
  - Total items processed: 52
  - Total descriptions fixed: 50
  - Total errors: 2
  - Success rate: 96.15%

📊 Post-Fix Validation Results:
  - Total products checked: 1,247
  - Products with descriptions: 1,230
  - Products still missing descriptions: 17
  - Description completion rate: 98.64%
```

### 8. 🔧 VALIDATE AND FIX EBAY METAFIELDS

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

**Option B: Fix Empty Descriptions**
If products have empty descriptions that need to be populated:
```bash
# First, analyze what would be fixed (DRY RUN)
mvn test -Dtest=ForceUpdateTest#fixEmptyDescriptions -Dspring.profiles.active=keystone-prod -Ddry.run=true

# Then, apply the fixes (REAL RUN)
mvn test -Dtest=ForceUpdateTest#fixEmptyDescriptions -Dspring.profiles.active=keystone-prod -Ddry.run=false
```

**Option C: Specific Item Fix**
If you need to fix a specific item:
```bash
mvn test -P force-update-item-prod -Dforce.update.web_tag_number=200376
```

**Option D: Full Force Update (High Risk)**
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
| `sync-database-only-dev` | Dev | Database sync | ✅ Safe | Yes | No |
| `sync-database-only-prod` | Production | Database sync | ✅ Safe | Yes | No |
| `retryUpdateFailedItems` (dev) | Dev | Retry failed items | ⚠️ Medium | No | Yes |
| `retryUpdateFailedItems` (prod) | Production | Retry failed items | ⚠️ Medium | No | Yes |
| `fixEmptyDescriptions` (dev) | Dev | Fix empty descriptions | ⚠️ Medium | No | Yes |
| `fixEmptyDescriptions` (prod) | Production | Fix empty descriptions | ⚠️ Medium | No | Yes |
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