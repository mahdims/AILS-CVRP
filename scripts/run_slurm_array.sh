#!/bin/bash
#SBATCH --job-name=ailsii
#SBATCH --output=slurm_logs/ailsii_%A_%a.out
#SBATCH --error=slurm_logs/ailsii_%A_%a.err
#SBATCH --array=0-99
#SBATCH --ntasks=1
#SBATCH --cpus-per-task=1
#SBATCH --mem=16G
#SBATCH --time=24:20:00
#
# AILS-II CVRP Solver - SLURM Job Array Script
# ============================================================================
# Runs AILS-II on CVRP benchmark instances in parallel using SLURM job arrays
#
# The --array directive above is the default (65 jobs) but will be overridden
# when submitted via run_dataset.sh based on the actual number of instances.
#
# Usage:
#   sbatch run_slurm_array.sh                              # Use default (0-64)
#   sbatch --array=0-29 run_slurm_array.sh                 # Custom array size
#   bash scripts/run_dataset.sh Vrp_Set_XL_T               # Automatic (recommended)
# ============================================================================

# Load Java module (adjust version as needed for your cluster)
module load java/17.0.6 || module load java/17 || module load java

# Print job info
echo "============================================================================"
echo "SLURM Job Array - AILS-II CVRP Benchmark"
echo "============================================================================"
echo "Job ID: ${SLURM_JOB_ID}"
echo "Array Task ID: ${SLURM_ARRAY_TASK_ID}"
echo "Node: ${SLURM_NODELIST}"
echo "Java Version: $(java -version 2>&1 | head -n 1)"
echo "Started: $(date)"
echo "============================================================================"
echo ""

# Determine project directory, preferring the SLURM submission directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="${SLURM_SUBMIT_DIR:-$(cd "$SCRIPT_DIR/.." && pwd)}"

# Configuration - use absolute paths
JAR_FILE="$PROJECT_DIR/AILSII.jar"
DATA_DIR="$PROJECT_DIR/data"
RESULTS_DIR="$PROJECT_DIR/results"
LOG_DIR="$PROJECT_DIR/logs"
SOLUTIONS_DIR="$PROJECT_DIR/solutions"
INSTANCE_LIST="$PROJECT_DIR/instance_list.txt"

# AILS-II parameters (can be overridden via environment variables)
TIME_LIMIT=${AILSII_TIME_LIMIT:-86400}       # Default: 24 hours per instance (86400 seconds)
STOPPING_CRITERION=${AILSII_STOP_CRITERION:-Time}  # Time or Iteration
ROUNDED=${AILSII_ROUNDED:-true}              # true or false

# Check for required files
if [ ! -f "$JAR_FILE" ]; then
    echo "Error: JAR file not found: $JAR_FILE"
    echo "Please build the JAR first: bash scripts/build_jar.sh"
    exit 1
fi

if [ ! -f "$INSTANCE_LIST" ]; then
    echo "Error: Instance list not found: $INSTANCE_LIST"
    echo "Please run: bash scripts/run_dataset.sh DATASET_NAME"
    exit 1
fi

# Get total number of instances
TOTAL_INSTANCES=$(wc -l < "$INSTANCE_LIST")

# Calculate number of jobs from SLURM array size
# SLURM_ARRAY_TASK_COUNT is the total number of array tasks
# If not set (manual run), fall back to calculating from min/max, or default to 65
if [ -n "$SLURM_ARRAY_TASK_COUNT" ]; then
    NUM_JOBS=$SLURM_ARRAY_TASK_COUNT
elif [ -n "$SLURM_ARRAY_TASK_MIN" ] && [ -n "$SLURM_ARRAY_TASK_MAX" ]; then
    NUM_JOBS=$((SLURM_ARRAY_TASK_MAX - SLURM_ARRAY_TASK_MIN + 1))
else
    # Fallback for testing/manual runs
    NUM_JOBS=100
fi

# Calculate instances per job
INSTANCES_PER_JOB=$(( (TOTAL_INSTANCES + NUM_JOBS - 1) / NUM_JOBS ))

# Calculate which instances this job should process
START_IDX=$(( SLURM_ARRAY_TASK_ID * INSTANCES_PER_JOB + 1 ))
END_IDX=$(( START_IDX + INSTANCES_PER_JOB - 1 ))

# Don't exceed total instances
if [ $END_IDX -gt $TOTAL_INSTANCES ]; then
    END_IDX=$TOTAL_INSTANCES
fi

echo "Job array size: $NUM_JOBS jobs total"
echo "Processing instances $START_IDX to $END_IDX (out of $TOTAL_INSTANCES)"
echo ""

# Create solutions directory if it doesn't exist
mkdir -p "$SOLUTIONS_DIR"

# Process assigned instances
INSTANCE_COUNT=0
for i in $(seq $START_IDX $END_IDX); do
    # Read instance info from list (format: instance_file,dataset,best_known)
    INSTANCE_INFO=$(sed -n "${i}p" "$INSTANCE_LIST")
    INSTANCE_FILE=$(echo "$INSTANCE_INFO" | cut -d',' -f1)
    DATASET=$(echo "$INSTANCE_INFO" | cut -d',' -f2)
    BEST_KNOWN=$(echo "$INSTANCE_INFO" | cut -d',' -f3)

    INSTANCE_NAME=$(basename "$INSTANCE_FILE")
    INSTANCE_PATH="$DATA_DIR/$DATASET/$INSTANCE_NAME"
    LOG_FILE="$LOG_DIR/${DATASET}_${INSTANCE_NAME%.vrp}_job${SLURM_ARRAY_TASK_ID}.log"

    echo "----------------------------------------"
    echo "Instance $i/$TOTAL_INSTANCES: $INSTANCE_NAME from $DATASET"
    echo "Best Known Solution: $BEST_KNOWN"
    echo "----------------------------------------"

    # Run AILS-II
    START_TIME=$(date +%s)
    java -jar "$JAR_FILE" \
        -file "$INSTANCE_PATH" \
        -rounded "$ROUNDED" \
        -best "$BEST_KNOWN" \
        -limit "$TIME_LIMIT" \
        -stoppingCriterion "$STOPPING_CRITERION" \
        -solutionDir "$SOLUTIONS_DIR" \
        > "$LOG_FILE" 2>&1
    EXIT_CODE=$?
    END_TIME=$(date +%s)
    RUNTIME=$((END_TIME - START_TIME))

    if [ $EXIT_CODE -eq 0 ]; then
        echo "Completed in ${RUNTIME}s"
        # Extract best solution found from log
        BEST_FOUND=$(grep -i "best\|solution\|cost" "$LOG_FILE" | tail -1 || echo "See log for details")
        echo "Result: $BEST_FOUND"
        INSTANCE_COUNT=$((INSTANCE_COUNT + 1))
    else
        echo "ERROR: Failed with exit code $EXIT_CODE"
        echo "Check log file: $LOG_FILE"
    fi
    echo ""
done

# Job summary
echo "============================================================================"
echo "Job ${SLURM_ARRAY_TASK_ID} Complete"
echo "============================================================================"
echo "Processed: $INSTANCE_COUNT instances"
echo "Ended: $(date)"
echo "Log files in: $LOG_DIR"
echo "============================================================================"
