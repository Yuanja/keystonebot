# eBay Metafield Backfill Instructions

## Overview

The `EbayMetafieldBackfillService` has been refactored to use the authoritative `FeedItem` data from the database instead of parsing Shopify product content. This ensures consistency with the normal product publishing process and provides more accurate metafield data.

## What the Refactored Service Does

### Before Refactoring
- Retrieved all Shopify products
- Parsed product titles, descriptions, and tags using regex patterns
- Extracted watch information using hardcoded brand lists and text matching
- Created metafields based on parsed/guessed data

### After Refactoring  
- Retrieves all Shopify products
- Loads corresponding `FeedItem` records from the database by matching SKU
- Uses the exact same metafield creation logic as `KeyStoneShopifyProductFactoryService`
- Creates metafields from authoritative `FeedItem` data (not parsed content)

## Key Improvements

1. **Data Accuracy**: Uses actual `FeedItem` fields instead of regex parsing
2. **Consistency**: Follows the same metafield creation pattern as normal publishing
3. **Reliability**: No dependency on product title/description formatting
4. **Completeness**: Access to all watch-specific fields from the database
5. **Maintainability**: Single source of truth for metafield creation logic

## Metafields Created from FeedItem Data

The service creates eBay metafields using the following FeedItem fields:

| Metafield Key | FeedItem Field | Type | Description |
|---------------|----------------|------|-------------|
| `brand` | `webDesigner` | single_line_text_field | Watch brand/manufacturer |
| `model` | `webWatchModel` | single_line_text_field | Watch model |
| `reference_number` | `webWatchManufacturerReferenceNumber` | single_line_text_field | Manufacturer reference number |
| `serial_number` | `webSerialNumber` | single_line_text_field | Watch serial number |
| `year` | `webWatchYear` | single_line_text_field | Year of manufacture |
| `case_material` | `webMetalType` | single_line_text_field | Case material |
| `movement` | `webWatchMovement` | single_line_text_field | Movement type |
| `case` | `webWatchCase` | multi_line_text_field | Case information |
| `dial` | `webWatchDial` | multi_line_text_field | Dial information |
| `dial_general` | `webWatchGeneralDial` | single_line_text_field | General dial information |
| `dial_markers` | `webWatchDialMarkers` | single_line_text_field | Dial markers |
| `strap` | `webWatchStrap` | multi_line_text_field | Strap/bracelet information |
| `band_material` | `webWatchBandMaterial` | single_line_text_field | Band material |
| `band_type` | `webWatchBandType` | single_line_text_field | Band type |
| `condition` | `webWatchCondition` | single_line_text_field | Watch condition |
| `diameter` | `webWatchDiameter` | single_line_text_field | Case diameter |
| `bezel_type` | `webWatchBezelType` | single_line_text_field | Bezel type |
| `case_crown` | `webWatchCaseCrown` | single_line_text_field | Case crown information |
| `box_papers` | `webWatchBoxPapers` | single_line_text_field | Box and papers information |
| `category` | `webCategory` | single_line_text_field | Watch category |
| `style` | `webStyle` | single_line_text_field | Watch style |
| `price_ebay` | `webPriceEbay` | number_decimal | eBay price |
| `auction_flag` | `webFlagEbayauction` | single_line_text_field | eBay auction flag |
| `notes` | `webNotes` | multi_line_text_field | Additional notes |

## Prerequisites

### 1. Verify Database Connection
```bash
# Ensure the application can connect to the database with FeedItem records
mysql -h [host] -u [user] -p [database] -e "SELECT COUNT(*) FROM feed_item;"
```

### 2. Check FeedItem Data Quality
```bash
# Verify FeedItem records have good watch data
mysql -h [host] -u [user] -p [database] -e "
SELECT 
  COUNT(*) as total_items,
  COUNT(web_designer) as has_brand,
  COUNT(web_watch_model) as has_model,
  COUNT(web_metal_type) as has_material,
  COUNT(web_watch_movement) as has_movement
FROM feed_item 
WHERE shopify_item_id IS NOT NULL;
"
```

### 3. Verify Shopify Products Have Matching FeedItems
```bash
# Run test to check product-feeditem matching
mvn test -Dtest=EbayMetafieldBackfillTest#testProductFeedItemMatching -Dspring.profiles.active=keystone-dev
```

## Backfill Execution Methods

### Method 1: Service-Based Backfill (Recommended)

This uses the refactored `EbayMetafieldBackfillService` directly.

#### Development Environment

```bash
# 1. Test the backfill logic
mvn test -Dtest=EbayMetafieldBackfillTest -Dspring.profiles.active=keystone-dev

# 2. Run backfill via service test
mvn test -Dtest=EbayMetafieldBackfillTest#testEbayMetafieldBackfillWithFeedItemData -Dspring.profiles.active=keystone-dev
```

#### Production Environment

```bash
# 1. Test on a single product first
mvn test -Dtest=EbayMetafieldBackfillTest#testEbayMetafieldBackfillWithFeedItemData -Dspring.profiles.active=keystone-prod

# 2. Run full backfill (after testing)
# TODO: Create a runner class or add to main application
```

### Method 2: Test-Based Backfill (Controlled Testing)

Use the dedicated test to run controlled backfills.

```bash
# Development - test with single item
mvn test -Dtest=EbayMetafieldBackfillTest -Dspring.profiles.active=keystone-dev

# Production - after thorough testing
mvn test -Dtest=EbayMetafieldBackfillTest -Dspring.profiles.active=keystone-prod
```

### Method 3: Create a Runner Class (Recommended for Production)

Create a dedicated runner for production backfills:

```java
@Component
public class EbayMetafieldBackfillRunner {
    
    @Autowired
    private EbayMetafieldBackfillService backfillService;
    
    public void runBackfill() {
        backfillService.executeBackfill();
    }
}
```

## Pre-Backfill Checklist

### 1. Database Validation
```sql
-- Check FeedItem data completeness
SELECT 
  'Total FeedItems' as metric, COUNT(*) as count
FROM feed_item
UNION ALL
SELECT 
  'Published FeedItems', COUNT(*)
FROM feed_item 
WHERE shopify_item_id IS NOT NULL
UNION ALL
SELECT 
  'With Brand Data', COUNT(*)
FROM feed_item 
WHERE shopify_item_id IS NOT NULL AND web_designer IS NOT NULL
UNION ALL
SELECT 
  'With Watch Data', COUNT(*)
FROM feed_item 
WHERE shopify_item_id IS NOT NULL 
  AND (web_watch_model IS NOT NULL OR web_metal_type IS NOT NULL);
```

### 2. Shopify Product Validation
```bash
# Check if products exist in Shopify and have correct SKUs
mvn test -Dtest=EbayMetafieldBackfillTest#testProductFeedItemMatching -Dspring.profiles.active=keystone-[env]
```

### 3. Test Service Configuration
```bash
# Verify service beans are properly wired
mvn test -Dtest=EbayMetafieldBackfillTest#testBackfillAnalysisOnly -Dspring.profiles.active=keystone-[env]
```

## Step-by-Step Execution

### Development Environment

```bash
# Step 1: Backup database (optional)
mysqldump -h [host] -u [user] -p [database] > backup_before_ebay_backfill_$(date +%Y%m%d_%H%M%S).sql

# Step 2: Test the service
mvn test -Dtest=EbayMetafieldBackfillTest -Dspring.profiles.active=keystone-dev

# Step 3: Check one product manually
# - Go to Shopify admin
# - Find a watch product
# - Check if it has eBay metafields in the metafields section

# Step 4: Run the backfill
mvn test -Dtest=EbayMetafieldBackfillTest#testEbayMetafieldBackfillWithFeedItemData -Dspring.profiles.active=keystone-dev

# Step 5: Verify results
# - Check Shopify admin for eBay metafields
# - Run the test again to verify skip logic works
```

### Production Environment

```bash
# Step 1: MANDATORY database backup
mysqldump -h [prod-host] -u [user] -p [database] > backup_before_ebay_backfill_$(date +%Y%m%d_%H%M%S).sql

# Step 2: Test service configuration
mvn test -Dtest=EbayMetafieldBackfillTest#testBackfillAnalysisOnly -Dspring.profiles.active=keystone-prod

# Step 3: Test with single item
mvn test -Dtest=EbayMetafieldBackfillTest#testEbayMetafieldBackfillWithFeedItemData -Dspring.profiles.active=keystone-prod

# Step 4: Manual verification of test results
# - Check the test product in Shopify admin
# - Verify metafields were created correctly
# - Confirm data matches FeedItem fields

# Step 5: Get business approval
# - Show test results to stakeholders
# - Confirm go/no-go decision

# Step 6: Execute full backfill (after approval)
# TODO: Implement production runner or use direct service call

# Step 7: Monitor execution
# - Watch logs for progress and errors
# - Check Shopify API rate limits
# - Verify random products during execution

# Step 8: Post-execution validation
mvn test -Dtest=EbayMetafieldBackfillTest -Dspring.profiles.active=keystone-prod
```

## Monitoring and Validation

### During Execution

1. **Service Logs**
   ```bash
   tail -f logs/application.log | grep "EbayMetafieldBackfillService\|Processing product\|Added.*eBay metafields"
   ```

2. **Progress Tracking**
   - The service logs progress: "Processing product X/Y"
   - Shows SKU matching: "Matched product 'X' with feed item SKU 'Y'"
   - Reports results: "Added N eBay metafields to product"

3. **Error Monitoring**
   ```bash
   grep -i "error\|exception" logs/application.log | grep -i "ebay\|metafield"
   ```

### Post-Execution Validation

1. **Database Check**
   ```sql
   -- Count products that should have been processed
   SELECT COUNT(*) as watch_products_with_feeditems
   FROM feed_item 
   WHERE shopify_item_id IS NOT NULL 
     AND web_category LIKE '%Watch%';
   ```

2. **Shopify Verification**
   - Check random products in Shopify admin
   - Verify eBay metafields section is populated
   - Confirm metafield values match FeedItem data

3. **Test Validation**
   ```bash
   # Run comprehensive test suite
   mvn test -Dtest=EbayMetafieldBackfillTest -Dspring.profiles.active=keystone-[env]
   ```

## Troubleshooting

### Common Issues

1. **No Products Matched with FeedItems**
   ```
   ERROR: Found 0 watch products with matching feed items to process
   ```
   - **Solution**: Check that product variants have correct SKUs
   - **Check**: Verify FeedItem.webTagNumber matches Product.variants[0].sku

2. **FeedItem Not Found for Product**
   ```
   WARN: No feed item found for product 'ProductName' with SKU 'SKU123'
   ```
   - **Solution**: Check if SKU exists in database
   - **Query**: `SELECT * FROM feed_item WHERE web_tag_number = 'SKU123'`

3. **Missing Watch Data in FeedItems**
   ```
   DEBUG: No eBay metafield data could be created from feed item: SKU123
   ```
   - **Solution**: Check FeedItem has watch-specific fields populated
   - **Query**: `SELECT web_designer, web_watch_model, web_metal_type FROM feed_item WHERE web_tag_number = 'SKU123'`

4. **Service Bean Not Found**
   ```
   ERROR: No qualifying bean of type 'EbayMetafieldBackfillService'
   ```
   - **Solution**: Check Spring profile is correct
   - **Fix**: Ensure application context loads all required services

### Data Quality Issues

1. **Check FeedItem Data Completeness**
   ```sql
   SELECT 
     web_tag_number,
     web_designer,
     web_watch_model,
     web_metal_type,
     web_watch_movement,
     web_watch_year
   FROM feed_item 
   WHERE shopify_item_id IS NOT NULL
     AND (web_designer IS NULL OR web_watch_model IS NULL)
   LIMIT 10;
   ```

2. **Verify Product-FeedItem Matching**
   ```bash
   mvn test -Dtest=EbayMetafieldBackfillTest#testProductFeedItemMatching
   ```

## Service Configuration

### Required Dependencies

The service requires these Spring beans:
- `ShopifyGraphQLService` - for Shopify API operations
- `FeedItemService` - for database FeedItem queries

### Environment-Specific Settings

No special configuration needed. The service uses existing:
- Database connection settings
- Shopify API credentials
- Spring profile configuration

## Success Criteria

The backfill is successful when:

1. ✅ All watch products are matched with FeedItem records
2. ✅ eBay metafields are created using FeedItem data (not parsed content)
3. ✅ Metafield values exactly match corresponding FeedItem fields
4. ✅ Products with existing eBay metafields are skipped
5. ✅ No errors in service execution logs
6. ✅ All tests pass after backfill
7. ✅ Random spot checks confirm data accuracy

## Rollback Plan

If issues are discovered:

### Immediate Steps
1. **Stop any running backfill processes**
2. **Restore database from backup** (if needed)
   ```bash
   mysql -h [host] -u [user] -p [database] < backup_before_ebay_backfill.sql
   ```

### Metafield Cleanup (if needed)
If only metafields need to be removed:
```bash
# Run a custom cleanup service to remove eBay metafields
# This would be safer than full database restore
```

## Post-Backfill Actions

1. **Reset Any Test Data**
   - Clean up test products created during testing
   - Remove temporary database records

2. **Update Documentation**
   - Record number of products processed
   - Note any issues encountered
   - Update this documentation with lessons learned

3. **Monitor Normal Operations**
   - Verify new product publishing still works correctly
   - Confirm eBay metafields are created for new products
   - Check that existing backfilled products continue to work

## Contact Information

- **Technical Lead**: [Your Name]
- **Database Admin**: [DB Contact]
- **Business Owner**: [Business Contact]
- **Documentation**: This file and `EbayMetafieldBackfillTest.java`

## Related Files

- `EbayMetafieldBackfillService.java` - Main service implementation
- `EbayMetafieldBackfillTest.java` - Comprehensive test suite
- `KeyStoneShopifyProductFactoryService.java` - Reference implementation for metafield creation
- `FeedItemService.java` - Database service for FeedItem queries 