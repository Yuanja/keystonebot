#!/bin/bash

# Script to remove "Step X:" and "STEP X:" prefixes from comments and log messages
# while preserving the remaining descriptive text

echo "🧹 Removing Step X: and STEP X: prefixes from all files..."

# Define the files to process (based on grep search results)
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

# Counter for changes made
total_changes=0

# Process each file
for file in "${FILES[@]}"; do
    if [[ -f "$file" ]]; then
        echo "📝 Processing: $file"
        
        # Create a temporary file for processing
        temp_file="${file}.tmp"
        
        # Process the file line by line to handle all patterns
        while IFS= read -r line; do
            # Handle different patterns:
            # 1. // Step X: -> //
            # 2. // STEP X: -> //
            # 3. * Step X: -> *
            # 4. * STEP X: -> *
            # 5. logger.info("Step X: text") -> logger.info("text")
            # 6. logger.info("STEP X: text") -> logger.info("text")
            # 7. logger.info("emoji STEP X: text") -> logger.info("emoji text")
            # 8. ### Step X: -> ###
            
            modified_line="$line"
            
            # Handle comment patterns
            modified_line=$(echo "$modified_line" | sed 's|// Step [0-9]\+: |// |g')
            modified_line=$(echo "$modified_line" | sed 's|// STEP [0-9]\+: |// |g')
            modified_line=$(echo "$modified_line" | sed 's|\* Step [0-9]\+: |* |g')
            modified_line=$(echo "$modified_line" | sed 's|\* STEP [0-9]\+: |* |g')
            
            # Handle logger.info patterns - more comprehensive
            modified_line=$(echo "$modified_line" | sed 's|logger\.info("Step [0-9]\+: |logger.info("|g')
            modified_line=$(echo "$modified_line" | sed 's|logger\.info("STEP [0-9]\+: |logger.info("|g')
            modified_line=$(echo "$modified_line" | sed 's|logger\.info("\([^"]*\)Step [0-9]\+: |logger.info("\1|g')
            modified_line=$(echo "$modified_line" | sed 's|logger\.info("\([^"]*\)STEP [0-9]\+: |logger.info("\1|g')
            
            # Handle markdown headers
            modified_line=$(echo "$modified_line" | sed 's|### Step [0-9]\+: |### |g')
            modified_line=$(echo "$modified_line" | sed 's|### STEP [0-9]\+: |### |g')
            modified_line=$(echo "$modified_line" | sed 's|## Step [0-9]\+: |## |g')
            modified_line=$(echo "$modified_line" | sed 's|## STEP [0-9]\+: |## |g')
            modified_line=$(echo "$modified_line" | sed 's|# Step [0-9]\+: |# |g')
            modified_line=$(echo "$modified_line" | sed 's|# STEP [0-9]\+: |# |g')
            
            # Handle emoji patterns in log messages
            modified_line=$(echo "$modified_line" | sed 's|\(📊\|📡\|🔄\|🔍\|📌\) Step [0-9]\+: |\1 |g')
            modified_line=$(echo "$modified_line" | sed 's|\(📊\|📡\|🔄\|🔍\|📌\) STEP [0-9]\+: |\1 |g')
            
            echo "$modified_line"
        done < "$file" > "$temp_file"
        
        # Replace original file with modified version
        mv "$temp_file" "$file"
        
        # Count remaining Step patterns
        remaining_steps=$(grep -c "Step [0-9]\+:\|STEP [0-9]\+:" "$file" 2>/dev/null || echo "0")
        
        if [[ $remaining_steps -eq 0 ]]; then
            echo "✅ Completed: $file"
        else
            echo "⚠️  Warning: $file still has $remaining_steps Step references"
        fi
        
        ((total_changes++))
    else
        echo "❌ File not found: $file"
    fi
done

echo ""
echo "🎉 Processing complete!"
echo "📊 Files processed: $total_changes"
echo ""
echo "🔍 Verifying results..."

# Verify no Step X: patterns remain
echo "Remaining Step references:"
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
    echo "🎉 All Step X: and STEP X: prefixes have been successfully removed!"
fi

echo ""
echo "✅ Script execution complete!"
echo "💡 Review the changes and commit them if they look correct." 