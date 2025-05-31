# Skip Image Download Configuration

## Overview

The Keystonebot now supports skipping image downloads during product publishing and updating operations. This feature is particularly useful for:

- **Faster Development Testing**: Skip time-consuming image downloads during development
- **Network Bandwidth Saving**: Reduce network usage when images are not needed
- **Debugging**: Focus on other aspects of the sync process without image download delays
- **CI/CD Environments**: Speed up automated testing

## Configuration

### Property Settings

The feature is controlled by the `skip.image.download` property in your application properties files.

#### Default Configuration (Production)
```properties
# src/main/resources/application.properties
skip.image.download=false
```

#### Development Configuration  
```properties
# src/main/resources/application-keystone-dev.properties
skip.image.download=true
```

### Environment-Specific Settings

- **Production**: `skip.image.download=false` (images are downloaded)
- **Development**: `skip.image.download=true` (images are skipped)
- **Testing**: Can be set to `true` for faster test execution

## How It Works

When `skip.image.download=true`:

1. **Publishing Items**: `publishItemToShopify()` skips `imageService.downloadImages(item)`
2. **Updating Items**: `updateItemOnShopify()` skips `imageService.downloadImages(item)`
3. **Logging**: Clear messages indicate when downloads are skipped
4. **Product Creation**: Products are still created with image URLs (but images aren't downloaded locally)

### Example Log Output

When skipping is enabled:
```
[INFO] BaseShopifySyncService - Publishing Sku: 100108
[INFO] BaseShopifySyncService - Skipping image download for SKU: 100108 (skip.image.download=true)
```

When skipping is disabled:
```
[INFO] BaseShopifySyncService - Publishing Sku: 100108
[INFO] BaseShopifySyncService - Downloading images for SKU: 100108
[INFO] ImageService - Downloading image from: https://...
```

## Performance Impact

### With Image Download (skip.image.download=false)
- **Time**: 15-30+ seconds per item (depending on image count and network)
- **Network**: Downloads all product images locally
- **Storage**: Requires local disk space for images

### Without Image Download (skip.image.download=true)
- **Time**: 3-8 seconds per item (significantly faster)
- **Network**: Minimal network usage for API calls only
- **Storage**: No local image storage required

## Use Cases

### 1. Development Testing
```properties
# application-keystone-dev.properties
skip.image.download=true
```
Perfect for rapid development cycles and testing sync logic.

### 2. Production Deployment
```properties
# application-keystone-prod.properties  
skip.image.download=false
```
Ensures all images are properly downloaded and available.

### 3. CI/CD Pipeline
```properties
# application-test.properties
skip.image.download=true
```
Speeds up automated test execution.

### 4. Network-Constrained Environments
Set to `true` when operating with limited bandwidth or metered connections.

## Configuration Examples

### Enable Skipping for All Environments
```properties
skip.image.download=true
```

### Enable Skipping Only for Development
```properties
# application.properties (default)
skip.image.download=false

# application-keystone-dev.properties (override)
skip.image.download=true
```

### Runtime Override (if needed)
```bash
java -jar keystonebot.jar --skip.image.download=true
```

## Verification

### Test the Configuration
Run the test to verify the feature is working:
```bash
mvn test -Dtest=KeystoneGraphqlTest#testSkipImageDownloadConfiguration
```

### Check Log Output
Look for these log messages to confirm the setting:
- `Skipping image download for SKU: {sku} (skip.image.download=true)` 
- `Downloading images for SKU: {sku}`

### Performance Indicators
- **Fast execution (< 10 seconds)**: Likely indicates skipping is enabled
- **Slower execution (15+ seconds)**: Likely indicates images are being downloaded

## Notes

- **Product Creation**: Products are still created normally in Shopify
- **Image URLs**: Product image URLs are still set (pointing to external sources)
- **Image Processing**: Local image processing (compression, format conversion) is skipped
- **Shopify Images**: Images in Shopify will reference external URLs rather than locally hosted files

## Troubleshooting

### Images Not Showing in Shopify
If `skip.image.download=true` and images aren't visible:
1. Check that external image URLs are accessible
2. Verify CSS hosting URL configuration
3. Consider setting `skip.image.download=false` for production

### Slow Performance with Skipping Enabled
If performance is still slow with skipping enabled:
1. Check network connectivity to Shopify API
2. Review collection publishing operations
3. Verify GraphQL query efficiency

## Related Configuration

```properties
# Image-related settings
css.hosting.url.base=http://ebay.gruenbergwatches.com/gwebaycss
image.source.ip=fm.gruenbergwatches.com
image.store.dir=/path/to/local/images
do_heic_to_jpg_convert=true
skip.image.download=true  # <-- New setting
```

This feature provides significant performance improvements for development and testing while maintaining full functionality for production deployments. 