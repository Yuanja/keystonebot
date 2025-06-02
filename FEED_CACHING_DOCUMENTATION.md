# Feed Caching Documentation (Simplified)

## Overview

This document describes the **simplified** feed caching functionality that leverages existing file infrastructure and the `dev.mode` flag to improve GraphQL API test performance by avoiding slow live feed downloads.

## Key Features

### Simple File-Based Caching
- **Leverages existing infrastructure**: Uses the existing `tmpFeed*.xml` files that are already downloaded
- **Single flag control**: Only uses the `dev.mode` flag - no complex configuration needed
- **1-day file age checking**: Reuses existing feed files if they're less than 1 day old
- **Automatic fallback**: Falls back to fresh download if files are missing or stale

### Top 100 Fast Testing Cache
- **Generates `top100Feed.txt`**: Creates a simple text file with the top 100 highest SKUs for ultra-fast testing
- **Automatic filtering**: BaseGraphqlTest automatically uses top 100 cache when requesting ≤ 100 items
- **SKU-based sorting**: Sorted by highest `webTagNumber` for most relevant test data

## How It Works

### Feed Loading Logic

```
When getItemsFromFeed() is called:

1. If dev.mode = false:
   → Always download fresh feed data

2. If dev.mode = true:
   → Check if tmpFeed*.xml files exist and are < 1 day old
   → If YES: Load from existing files (fast!)
   → If NO: Download fresh data and generate top100Feed.txt

3. Generate top100Feed.txt with highest 100 SKUs (if dev mode)
```

### Test Loading Logic

```
When getTopFeedItems(count) is called in tests:

1. If count <= 100 and top100Feed.txt exists and is fresh:
   → Load from top 100 cache (ultra-fast!)
   
2. Otherwise:
   → Use regular feed loading (with file caching if dev mode)
```

## Configuration

### Enable Caching
Simply enable dev mode in your properties file:

```properties
# Enable dev mode for feed caching
dev.mode=true
dev.mode.maxReadCount=10
```

### Disable Caching
Set dev mode to false or comment it out:

```properties
# Disable caching (always download fresh)
# dev.mode=false
```

## File Structure

```
TMPFEED_FILE_FOLDER/
├── tmpFeed0.xml          # Downloaded feed batch 0
├── tmpFeed1.xml          # Downloaded feed batch 1  
├── tmpFeed2.xml          # Downloaded feed batch 2
├── tmpFeed3.xml          # Downloaded feed batch 3
└── top100Feed.txt        # Top 100 SKUs (generated when dev.mode=true)
```

## API Methods

### BaseFeedService Methods

```java
// Standard feed loading (with caching if dev mode enabled)
List<FeedItem> getItemsFromFeed()

// Load from top 100 cache for fast testing
List<FeedItem> getItemsFromTop100Feed()

// Force refresh (clears cache and downloads fresh)
List<FeedItem> refreshCache()

// Clear all cached files
void clearCache()

// Get cache status for debugging
String getCacheStatus()
```

### BaseGraphqlTest Methods

```java
// Standard loading (uses top 100 cache automatically when appropriate)
List<FeedItem> getTopFeedItems(int count)

// Force fresh data (bypasses all caching)
List<FeedItem> getTopFeedItemsFresh(int count)

// Clear cache for test isolation
void clearFeedCache()
```

## Performance Benefits

### Without Caching (dev.mode=false)
- **Every test**: Downloads full feed (~8-40 seconds)
- **Network dependent**: Slow and unreliable
- **High API load**: Hammers the live feed server

### With Caching (dev.mode=true)
- **First run**: Downloads once per day (~8-40 seconds)
- **Subsequent runs**: Reuses files (milliseconds)
- **Top 100 cache**: Ultra-fast for small test sets
- **Network independent**: Works offline after first download

## Example Usage

### Enable Dev Mode and Test
```bash
# Edit application-keystone-dev.properties
dev.mode=true

# Run tests - first run downloads fresh data
mvn test -Dtest=SomeGraphqlTest

# Run again - second run uses cached files (much faster!)
mvn test -Dtest=SomeGraphqlTest
```

### Force Fresh Data When Needed
```java
@Test
public void testWithFreshData() throws Exception {
    // This will always download fresh data
    List<FeedItem> items = getTopFeedItemsFresh(10);
    // ... test logic
}
```

### Check Cache Status
```java
@Test 
public void debugCacheStatus() throws Exception {
    String status = keyStoneFeedService.getCacheStatus();
    logger.info("Cache status: {}", status);
    // Example output: "Feed files: 4 files, newest: 2025-06-01T16:08:54 (2 hours old), Top 100 file: exists (1 hours old)"
}
```

## Benefits of This Approach

✅ **Simple**: Only one flag (`dev.mode`) controls everything  
✅ **Leverages existing code**: Uses current tmpFeed file infrastructure  
✅ **Backward compatible**: No changes needed when dev.mode=false  
✅ **Fast**: Dramatic speed improvement for development/testing  
✅ **Reliable**: Automatic fallback when cache is invalid  
✅ **Selective**: Can force fresh data when needed  
✅ **Storage efficient**: Simple text files, not serialized objects  

## Migration from Previous Complex Implementation

The previous implementation with separate cache configuration has been **removed** and replaced with this simpler approach:

### Removed Configuration
```properties
# These are no longer needed:
# feed.cache.enabled=true
# feed.cache.testing.enabled=true
# spring.profiles.active detection
```

### Simplified Configuration
```properties
# Only this is needed:
dev.mode=true
```

This approach is much cleaner, leverages existing infrastructure, and provides the same performance benefits with minimal complexity. 