# Price Change Detection Backfill Instructions

## Overview

After modifying the price change detection logic to only consider the `webPriceKeystone` field (ignoring all other pricing fields), a backfill operation may be necessary to ensure all existing products are properly synchronized with the new logic.

## What Changed

### Before
The `equalsForShopify()` method compared ALL pricing fields:
- `webPriceRetail`
- `webPriceSale` 
- `webPriceEbay`
- `webPriceKeystone`
- `webPriceChronos`
- `webPriceWholesale`
- `costInvoiced`

### After
The `equalsForShopify()` method now only compares:
- `webPriceKeystone` (ONLY this pricing field matters)
- All other pricing fields are **completely ignored**

## Why Backfill May Be Needed

1. **Previously Suppressed Updates**: Items that had `webPriceKeystone` changes but were blocked from updating due to other pricing field differences
2. **Consistency Check**: Ensure all products reflect the current feed state using the new comparison logic
3. **Shopify Product Alignment**: Verify that Shopify products match the current feed items under the new rules

## Backfill Execution Methods

### Method 1: Force Update via Configuration (Recommended for Testing)

This method uses the existing `shopify.force.update` property to bypass normal change detection.

#### Development Environment

```bash
# 1. Test the backfill logic first (dry run)
mvn test -Panalyze-reconciliation

# 2. Run backfill with force update enabled
mvn clean compile exec:java -Dspring.profiles.active=keystone-dev -Dshopify.force.update=true
```

#### Production Environment (Use with EXTREME caution)

```bash
# 1. First, analyze what would be updated (read-only)
mvn test -Panalyze-reconciliation-prod

# 2. Only if analysis looks correct, run the backfill
mvn clean compile exec:java -Dspring.profiles.active=keystone-prod -Dshopify.force.update=true
```

### Method 2: Test-Based Backfill (Recommended for Production)

Create a specific test that processes items in controlled batches.

#### Step 1: Create Backfill Test

```bash
# Copy and modify an existing sync test
cp src/test/java/com/gw/service/SyncTest.java src/test/java/com/gw/service/PriceChangeBackfillTest.java
```

#### Step 2: Run Backfill Test

```bash
# Development
mvn test -Dtest=PriceChangeBackfillTest -Dspring.profiles.active=keystone-dev

# Production (after testing)
mvn test -Dtest=PriceChangeBackfillTest -Dspring.profiles.active=keystone-prod
```

### Method 3: Gradual Sync Using Existing Sync Process

This method relies on the normal scheduled sync to gradually process changes.

```bash
# Simply let the normal sync run with the new logic
# No special action needed - changes will be detected naturally
```

## Pre-Backfill Checklist

### 1. Verify Changes Are Deployed
```bash
# Check that the new equalsForShopify logic is active
grep -A 10 -B 5 "webPriceKeystone" src/main/java/com/gw/domain/FeedItem.java
```

### 2. Test the New Logic
```bash
# Run the updated test to verify new behavior
mvn test -Dtest=FeedItemUpdateDetectionTest
```

### 3. Check Database State
```bash
# Connect to database and check current state
# SELECT COUNT(*) FROM feed_item WHERE shopify_item_id IS NOT NULL;
```

### 4. Check Shopify State
```bash
# Use Shopify admin or API to verify current product count
# This helps establish baseline before backfill
```

## Backfill Execution Steps

### Development Environment

```bash
# Step 1: Backup database (optional but recommended)
mysqldump -h [host] -u [user] -p [database] > backup_before_backfill.sql

# Step 2: Run analysis
mvn test -Panalyze-reconciliation

# Step 3: Run limited test backfill
mvn test -Dtest=SyncTest -Dspring.profiles.active=keystone-dev

# Step 4: If test successful, run full backfill
mvn clean compile exec:java -Dspring.profiles.active=keystone-dev -Dshopify.force.update=true

# Step 5: Verify results
mvn test -Dtest=FeedItemUpdateDetectionTest
```

### Production Environment

```bash
# Step 1: MANDATORY database backup
mysqldump -h [prod-host] -u [user] -p [database] > backup_before_backfill_$(date +%Y%m%d_%H%M%S).sql

# Step 2: Run read-only analysis
mvn test -Panalyze-reconciliation-prod

# Step 3: Review analysis results thoroughly
# Look for:
# - Number of items that would be updated
# - Any unexpected changes
# - Validation that only expected items are affected

# Step 4: If analysis confirms expectations, proceed with backfill
# ONLY after approval from business stakeholders
mvn clean compile exec:java -Dspring.profiles.active=keystone-prod -Dshopify.force.update=true

# Step 5: Monitor during execution
# - Check logs for errors
# - Monitor Shopify API rate limits
# - Verify products are being updated correctly

# Step 6: Post-backfill verification
mvn test -Dtest=FeedItemUpdateDetectionTest -Dspring.profiles.active=keystone-prod
```

## Configuration Options

### Force Update Configuration

Add to your `application-keystone-[env].properties`:

```properties
# Enable force update for backfill
shopify.force.update=true

# Increase safety limits if needed (carefully!)
MAX_TO_DELETE_COUNT=1000

# Enable detailed logging
logging.level.com.gw.services=DEBUG
```

### Environment-Specific Settings

#### Development (`application-keystone-dev.properties`)
```properties
shopify.force.update=true
MAX_TO_DELETE_COUNT=100
dev.mode=false  # Ensure full feed processing
```

#### Production (`application-keystone-prod.properties`)
```properties
shopify.force.update=false  # Only enable during backfill
MAX_TO_DELETE_COUNT=350
```

## Monitoring and Validation

### During Execution

1. **Monitor Logs**
   ```bash
   tail -f logs/application.log | grep "Changed Item SKU\|UPDATED\|PUBLISHED"
   ```

2. **Check API Rate Limits**
   - Monitor Shopify admin for API call usage
   - Look for rate limit warnings in logs

3. **Track Progress**
   ```bash
   # Count database changes
   SELECT status, COUNT(*) FROM feed_item GROUP BY status;
   ```

### Post-Execution Validation

1. **Run Verification Tests**
   ```bash
   mvn test -Dtest=FeedItemUpdateDetectionTest
   mvn test -Dtest=SyncTest
   ```

2. **Spot Check Products**
   - Compare a sample of products between database and Shopify
   - Verify pricing field changes are now ignored
   - Confirm webPriceKeystone changes still trigger updates

3. **Business Validation**
   - Check that products display correct information
   - Verify pricing appears correct on storefront
   - Confirm inventory levels are accurate

## Rollback Plan

If issues are discovered after backfill:

### Immediate Steps
1. **Disable scheduled sync**
   ```properties
   cron.schedule=0 0 0 31 2 ?  # Impossible schedule
   ```

2. **Restore database from backup**
   ```bash
   mysql -h [host] -u [user] -p [database] < backup_before_backfill.sql
   ```

3. **Revert code changes if necessary**
   ```bash
   git revert [commit-hash]
   ```

### Communication
- Notify stakeholders immediately
- Document what was observed
- Plan corrective actions

## Safety Measures

### Rate Limiting
- The application includes built-in Shopify API rate limiting
- Monitor for 429 (Too Many Requests) errors
- Execution will automatically slow down if rate limits are hit

### Batch Processing
- Changes are processed one item at a time
- Each item update is logged individually
- Failed items don't prevent other items from processing

### Safety Thresholds
- `MAX_TO_DELETE_COUNT` prevents accidental mass deletions
- Change detection validates items before processing
- Database transactions ensure consistency

## Troubleshooting

### Common Issues

1. **"Feed has dupes" Error**
   - Check for duplicate SKUs in feed
   - Review feed source data quality

2. **Shopify API Errors**
   - Check API credentials and permissions
   - Verify network connectivity
   - Review Shopify API status

3. **Database Connection Issues**
   - Verify database credentials
   - Check network connectivity
   - Ensure sufficient connection pool size

4. **Memory Issues**
   - Increase JVM heap size: `-Xmx4g`
   - Monitor memory usage during execution

### Getting Help

1. **Check Logs First**
   ```bash
   grep -i "error\|exception\|failed" logs/application.log
   ```

2. **Database Diagnostics**
   ```sql
   SELECT status, COUNT(*) FROM feed_item GROUP BY status;
   SELECT * FROM feed_item WHERE status LIKE '%FAILED%' LIMIT 10;
   ```

3. **Shopify Diagnostics**
   - Check Shopify admin for any product issues
   - Review API call logs in Shopify partner dashboard

## Success Criteria

The backfill is considered successful when:

1. ✅ All tests pass: `mvn test`
2. ✅ No failed status items in database
3. ✅ Shopify product count matches expected count
4. ✅ Spot check confirms pricing logic works correctly
5. ✅ No errors in application logs
6. ✅ Business stakeholder approval

## Post-Backfill Actions

1. **Reset Configuration**
   ```properties
   shopify.force.update=false  # Return to normal operation
   ```

2. **Re-enable Scheduled Sync**
   ```properties
   cron.schedule=0 * * * * *  # Or your normal schedule
   ```

3. **Document Results**
   - Number of items processed
   - Any issues encountered
   - Time taken to complete
   - Lessons learned

4. **Monitor Normal Operations**
   - Watch next few scheduled syncs
   - Verify pricing change detection works as expected
   - Confirm no regressions in other functionality

## Contact Information

- **Technical Lead**: [Your Name]
- **Business Owner**: [Business Contact]
- **Emergency Contact**: [Emergency Contact]
- **Documentation**: This file and related test files 