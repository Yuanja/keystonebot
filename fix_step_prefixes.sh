#!/bin/bash

# Script to remove "Step X:" and "STEP X:" prefixes from comments and log messages
# while preserving the remaining descriptive text

echo "🧹 Removing Step X: and STEP X: prefixes from all files..."

# Define the files to process
FILES=(
    "src/main/java/com/gw/services/sync/ProductPublishPipeline.java"
    "src/main/java/com/gw/services/sync/ProductUpdatePipeline.java"
    "src/main/java/com/gw/services/sync/CollectionManagementService.java"
    "src/main/java/com/gw/services/product/ProductCreationService.java"
    "src/test/java/com/gw/service/SyncDeletedItemsScenarioTest.java"
    "src/test/java/com/gw/service/FeedItemUpdateDetectionTest.java"
    "src/test/java/com/gw/service/SyncUpdatedItemsOnlyTest.java"
    "src/test/java/com/gw/service/CreateProductWithOptionsCorrectWayTest.java"
    "src/test/java/com/gw/service/SyncVariantOptionsUpdateTest.java"
    "src/test/java/com/gw/service/MetafieldCreationVerificationTest.java"
    "src/test/java/com/gw/service/ForceUpdateTest.java"
    "src/test/java/com/gw/service/PinnedMetafieldsTest.java"
    "RECONCILIATION.md"
    "FORCE_UPDATE_INSTRUCTIONS.md"
)

# Process each file
for file in "${FILES[@]}"; do
    if [[ -f "$file" ]]; then
        echo "📝 Processing: $file"
        
        # Use perl for more reliable pattern matching and replacement
        perl -i -pe '
            # Handle various Step patterns
            s{// Step \d+: }{// }g;
            s{// STEP \d+: }{// }g;
            s{\* Step \d+: }{* }g;
            s{\* STEP \d+: }{* }g;
            s{logger\.info\("Step \d+: }{logger.info("}g;
            s{logger\.info\("STEP \d+: }{logger.info("}g;
            s{logger\.info\("([^"]*?)Step \d+: }{logger.info("$1}g;
            s{logger\.info\("([^"]*?)STEP \d+: }{logger.info("$1}g;
            s{### Step \d+: }{### }g;
            s{### STEP \d+: }{### }g;
            s{## Step \d+: }{## }g;
            s{## STEP \d+: }{## }g;
            s{# Step \d+: }{# }g;
            s{# STEP \d+: }{# }g;
            s{(📊|📡|🔄|🔍|📌) Step \d+: }{$1 }g;
            s{(📊|📡|🔄|🔍|📌) STEP \d+: }{$1 }g;
        ' "$file"
        
        # Check if any Step patterns remain
        remaining=$(grep -c "Step [0-9]\+:\|STEP [0-9]\+:" "$file" 2>/dev/null || echo "0")
        
        if [[ $remaining -eq 0 ]]; then
            echo "✅ Completed: $file"
        else
            echo "⚠️  Warning: $file still has $remaining Step references"
        fi
    else
        echo "❌ File not found: $file"
    fi
done

echo ""
echo "🎉 Processing complete!"
echo ""

# Final verification
echo "🔍 Final verification - any remaining Step references:"
remaining_found=false
for file in "${FILES[@]}"; do
    if [[ -f "$file" ]]; then
        remaining=$(grep -n "Step [0-9]\+:\|STEP [0-9]\+:" "$file" 2>/dev/null || true)
        if [[ -n "$remaining" ]]; then
            echo "⚠️  $file:"
            echo "$remaining"
            remaining_found=true
        fi
    fi
done

if [[ "$remaining_found" = false ]]; then
    echo "🎉 SUCCESS! All Step X: and STEP X: prefixes have been removed!"
else
    echo "⚠️  Some Step references remain. You may need to handle them manually."
fi

echo ""
echo "✅ Script execution complete!" 