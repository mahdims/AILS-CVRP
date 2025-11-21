#!/bin/bash
# ============================================================================
# Run AILS-II on SLURM for Specific Dataset(s)
# ============================================================================
# Convenient wrapper to generate instance list and submit SLURM job
#
# Usage:
#   ./run_dataset.sh DATASET_NAME [DATASET_NAME2 ...]
#
# Examples:
#   ./run_dataset.sh Vrp_Set_XL_T
#   ./run_dataset.sh Vrp_Set_A Vrp_Set_X
#   ./run_dataset.sh Vrp_Set_A Vrp_Set_X Vrp_Set_XL_T  # All datasets
#
# This script will:
#   1. Build AILSII.jar if it doesn't exist
#   2. Generate instance_list.txt for the specified dataset(s)
#   3. Submit the SLURM job array
# ============================================================================

# Parse options
EXTRA_ARGS=""
while [[ "$1" =~ ^-- ]]; do
    case "$1" in
        --no-best-known)
            EXTRA_ARGS="$EXTRA_ARGS $1"
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
    echo "Examples:"
    echo "  $0 Vrp_Set_XL_T"
    echo "  $0 --no-best-known Vrp_Set_XL_T"
    echo "  $0 Vrp_Set_A Vrp_Set_X"
    echo ""
    echo "Available datasets in data/:"
    ls -d data/Vrp_Set_* 2>/dev/null | xargs -n1 basename || echo "  (none found)"
    exit 1
fi

# Get script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "============================================================================"
echo "AILS-II CVRP Benchmark - Dataset Run"
echo "============================================================================"
echo "Datasets: $@"
echo "Project directory: $PROJECT_DIR"
echo ""

# Step 1: Build JAR if needed
if [ ! -f "$PROJECT_DIR/AILSII.jar" ]; then
    echo "Step 1: Building AILSII.jar..."
    bash "$SCRIPT_DIR/build_jar.sh"
    if [ $? -ne 0 ]; then
        echo "Error: Failed to build JAR file"
        exit 1
    fi
    echo ""
else
    echo "Step 1: AILSII.jar found, skipping build"
    echo ""
fi

# Step 2: Generate instance list
echo "Step 2: Generating instance list..."
bash "$SCRIPT_DIR/generate_instance_list.sh" $EXTRA_ARGS "$@"

if [ $? -ne 0 ]; then
    echo "Error: Failed to generate instance list"
    exit 1
fi

# Check if instance list was created and is not empty
if [ ! -f "$PROJECT_DIR/instance_list.txt" ]; then
    echo "Error: instance_list.txt was not created"
    exit 1
fi

TOTAL_INSTANCES=$(wc -l < "$PROJECT_DIR/instance_list.txt")
if [ "$TOTAL_INSTANCES" -eq 0 ]; then
    echo "Error: No instances found in specified dataset(s)"
    exit 1
fi

echo ""
echo "============================================================================"
echo "Step 3: Submitting SLURM job array..."
echo "============================================================================"

# Read recommended job count
if [ -f "$PROJECT_DIR/.recommended_jobs" ]; then
    RECOMMENDED_JOBS=$(cat "$PROJECT_DIR/.recommended_jobs")
    ARRAY_SPEC="0-$((RECOMMENDED_JOBS - 1))"
    echo "Using recommended job array: --array=$ARRAY_SPEC ($RECOMMENDED_JOBS jobs)"
else
    echo "Warning: Could not read recommended job count, using default (0-64)"
    ARRAY_SPEC="0-64"
fi

echo ""

# Navigate to project directory for sbatch
cd "$PROJECT_DIR"

# Submit the job with dynamic array size
# The --array parameter overrides the #SBATCH directive in the script
sbatch --array="$ARRAY_SPEC" scripts/run_slurm_array.sh

if [ $? -eq 0 ]; then
    echo ""
    echo "============================================================================"
    echo "AILS-II Jobs Submitted Successfully!"
    echo "============================================================================"
    echo "Total instances: $TOTAL_INSTANCES"
    echo "SLURM jobs: $RECOMMENDED_JOBS (array $ARRAY_SPEC)"
    echo ""
    echo "Monitor progress with:"
    echo "  squeue -u \$USER"
    echo "  watch -n 10 'squeue -u \$USER'"
    echo ""
    echo "Check logs in:"
    echo "  slurm_logs/ailsii_*.out"
    echo "  logs/*.log"
    echo ""
    echo "Customize parameters by setting environment variables:"
    echo "  AILSII_TIME_LIMIT=7200 bash scripts/run_dataset.sh Vrp_Set_X"
    echo "============================================================================"
else
    echo "Error: Failed to submit SLURM job"
    exit 1
fi
