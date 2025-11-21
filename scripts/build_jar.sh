#!/bin/bash
# ============================================================================
# Build AILSII.jar from Source
# ============================================================================
# Compiles all Java source files and packages them into a JAR file
#
# Usage:
#   ./build_jar.sh
# ============================================================================

# Get script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
SRC_DIR="$PROJECT_DIR/src"
BUILD_DIR="$PROJECT_DIR/build"
JAR_FILE="$PROJECT_DIR/AILSII.jar"

echo "============================================================================"
echo "Building AILSII.jar"
echo "============================================================================"
echo "Project directory: $PROJECT_DIR"
echo "Source directory: $SRC_DIR"
echo ""

# Check if source directory exists
if [ ! -d "$SRC_DIR" ]; then
    echo "Error: Source directory not found: $SRC_DIR"
    exit 1
fi

# Create build directory
echo "Step 1: Creating build directory..."
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"

# Find all Java files
echo ""
echo "Step 2: Finding Java source files..."
JAVA_FILES=$(find "$SRC_DIR" -name "*.java")
NUM_FILES=$(echo "$JAVA_FILES" | wc -l)
echo "Found $NUM_FILES Java source files"

# Compile all Java files
echo ""
echo "Step 3: Compiling Java sources..."
javac -d "$BUILD_DIR" -sourcepath "$SRC_DIR" $JAVA_FILES

if [ $? -ne 0 ]; then
    echo ""
    echo "Error: Compilation failed"
    exit 1
fi

echo "Compilation successful!"

# Create manifest file
echo ""
echo "Step 4: Creating JAR manifest..."
MANIFEST_FILE="$BUILD_DIR/MANIFEST.MF"
cat > "$MANIFEST_FILE" <<EOF
Manifest-Version: 1.0
Main-Class: SearchMethod.AILSII
Created-By: build_jar.sh
EOF

# Create JAR file
echo ""
echo "Step 5: Creating JAR file..."
cd "$BUILD_DIR"
jar cfm "$JAR_FILE" MANIFEST.MF .

if [ $? -ne 0 ]; then
    echo ""
    echo "Error: JAR creation failed"
    exit 1
fi

cd "$PROJECT_DIR"

# Verify JAR file
echo ""
echo "Step 6: Verifying JAR file..."
if [ -f "$JAR_FILE" ]; then
    JAR_SIZE=$(du -h "$JAR_FILE" | cut -f1)
    echo "JAR file created successfully: $JAR_FILE ($JAR_SIZE)"
else
    echo "Error: JAR file not found after build"
    exit 1
fi

# Test JAR file
echo ""
echo "Step 7: Testing JAR file..."
java -jar "$JAR_FILE" --help > /dev/null 2>&1
TEST_EXIT_CODE=$?

if [ $TEST_EXIT_CODE -eq 0 ] || [ $TEST_EXIT_CODE -eq 1 ]; then
    # Exit codes 0 or 1 are acceptable (1 might mean missing args, which is fine)
    echo "JAR file is executable"
else
    echo "Warning: JAR file may not be properly configured (exit code: $TEST_EXIT_CODE)"
    echo "This might be normal if the application requires specific arguments"
fi

# Cleanup build directory (optional)
echo ""
echo "Step 8: Cleaning up..."
rm -rf "$BUILD_DIR"
echo "Build directory removed"

echo ""
echo "============================================================================"
echo "Build Complete!"
echo "============================================================================"
echo "JAR file: $JAR_FILE"
echo ""
echo "Test the JAR with:"
echo "  java -jar AILSII.jar -file data/Vrp_Set_X/X-n101-k25.vrp -rounded true -best 27591 -limit 60 -stoppingCriterion Time"
echo "============================================================================"
