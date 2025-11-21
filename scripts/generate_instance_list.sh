#!/bin/bash
# ============================================================================
# Generate Instance List for AILS-II SLURM Jobs
# ============================================================================
# Creates instance_list.txt containing all instances from specified datasets
# Format: instance_file,dataset,best_known_solution
#
# Usage:
#   ./generate_instance_list.sh DATASET_NAME [DATASET_NAME2 ...]
#
# Examples:
#   ./generate_instance_list.sh Vrp_Set_X
#   ./generate_instance_list.sh Vrp_Set_A Vrp_Set_X Vrp_Set_XL_T
# ============================================================================

# Parse options
IGNORE_BEST_KNOWN=false
while [[ "$1" =~ ^-- ]]; do
    case "$1" in
        --no-best-known)
            IGNORE_BEST_KNOWN=true
            shift
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

# Check if dataset name provided
if [ $# -eq 0 ]; then
    echo "Error: No dataset name provided"
    echo ""
    echo "Usage: $0 [--no-best-known] DATASET_NAME [DATASET_NAME2 ...]"
    echo ""
    echo "Options:"
    echo "  --no-best-known    Set all best known solutions to 0"
    echo ""
    echo "Available datasets in data/:"
    ls -d data/Vrp_Set_* 2>/dev/null | xargs -n1 basename || echo "  (none found)"
    exit 1
fi

# Get script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
DATA_DIR="$PROJECT_DIR/data"
OUTPUT_FILE="$PROJECT_DIR/instance_list.txt"

echo "============================================================================"
echo "Generating Instance List"
echo "============================================================================"
echo "Datasets: $@"
echo ""

# Clear output file
> "$OUTPUT_FILE"

TOTAL_INSTANCES=0

# Process each dataset
for DATASET in "$@"; do
    DATASET_DIR="$DATA_DIR/$DATASET"

    if [ ! -d "$DATASET_DIR" ]; then
        echo "Warning: Dataset directory not found: $DATASET_DIR"
        continue
    fi

    echo "Processing dataset: $DATASET"

    # Find all .vrp files
    INSTANCE_COUNT=0
    for VRP_FILE in "$DATASET_DIR"/*.vrp; do
        if [ ! -f "$VRP_FILE" ]; then
            continue
        fi

        INSTANCE_NAME=$(basename "$VRP_FILE")
        INSTANCE_BASE="${INSTANCE_NAME%.vrp}"
        SOL_FILE="$DATASET_DIR/${INSTANCE_BASE}.sol"

        # Extract best known solution from .sol file
        if [ "$IGNORE_BEST_KNOWN" = true ]; then
            BEST_KNOWN="0"
        elif [ -f "$SOL_FILE" ]; then
            # Look for "Cost XXXX" line in .sol file
            BEST_KNOWN=$(grep -i "^Cost" "$SOL_FILE" | awk '{print $2}')
            if [ -z "$BEST_KNOWN" ]; then
                BEST_KNOWN="0"
                echo "  Warning: Could not extract cost from $SOL_FILE, using 0"
            fi
        else
            BEST_KNOWN="0"
            echo "  Warning: No .sol file found for $INSTANCE_NAME, using 0"
        fi

        # Write to instance list: instance_file,dataset,best_known
        echo "$INSTANCE_NAME,$DATASET,$BEST_KNOWN" >> "$OUTPUT_FILE"
        INSTANCE_COUNT=$((INSTANCE_COUNT + 1))
        TOTAL_INSTANCES=$((TOTAL_INSTANCES + 1))
    done

    echo "  Found $INSTANCE_COUNT instances"
done

echo ""
echo "============================================================================"
echo "Instance List Generated"
echo "============================================================================"
echo "Total instances: $TOTAL_INSTANCES"
echo "Output file: $OUTPUT_FILE"
echo ""

if [ $TOTAL_INSTANCES -eq 0 ]; then
    echo "Error: No instances found"
    rm -f "$OUTPUT_FILE"
    exit 1
fi

# Calculate recommended number of jobs
# For large datasets (100+ instances), aim for 1 instance per job
# For smaller datasets, aim for ~2-3 instances per job
if [ $TOTAL_INSTANCES -ge 100 ]; then
    RECOMMENDED_JOBS=$TOTAL_INSTANCES
elif [ $TOTAL_INSTANCES -ge 30 ]; then
    RECOMMENDED_JOBS=$((TOTAL_INSTANCES / 2))
else
    RECOMMENDED_JOBS=$((TOTAL_INSTANCES / 3))
fi

# Ensure at least 1 job
if [ $RECOMMENDED_JOBS -lt 1 ]; then
    RECOMMENDED_JOBS=1
fi

# Cap at 100 jobs to avoid overwhelming the scheduler
if [ $RECOMMENDED_JOBS -gt 100 ]; then
    RECOMMENDED_JOBS=100
fi

echo "$RECOMMENDED_JOBS" > "$PROJECT_DIR/.recommended_jobs"
echo "Recommended SLURM jobs: $RECOMMENDED_JOBS (~$((TOTAL_INSTANCES / RECOMMENDED_JOBS)) instances per job)"
echo ""
echo "Preview (first 10 instances):"
head -10 "$OUTPUT_FILE"
if [ $TOTAL_INSTANCES -gt 10 ]; then
    echo "... (and $((TOTAL_INSTANCES - 10)) more)"
fi
echo "============================================================================"
