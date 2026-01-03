#!/bin/bash

################################################################################
# SLURM Experiment Submitter
# Usage: bash scripts/submit_experiment.sh [experiment_config.txt]
################################################################################

set -e

CONFIG_FILE=${1:-"experiments/experiment_config.txt"}

if [ ! -f "$CONFIG_FILE" ]; then
    echo "Error: Configuration file not found: $CONFIG_FILE"
    exit 1
fi

# Get directory where config file is located
PARAM_DIR=$(dirname "$CONFIG_FILE")

# Load configuration
source "$CONFIG_FILE"

# Setup
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
RESULTS_DIR="experiments/${EXPERIMENT_NAME}_${TIMESTAMP}"
mkdir -p "${RESULTS_DIR}"

echo "========================================="
echo "SLURM Experiment Submission"
echo "========================================="
echo "Name: ${EXPERIMENT_NAME}"
echo "Instances: ${#INSTANCES[@]}"
echo "Configurations: ${#PARAMETER_FILES[@]}"
echo "Time per run: $((TIME_LIMIT / 3600))h $((TIME_LIMIT % 3600 / 60))m"
echo "Results: ${RESULTS_DIR}"
echo "========================================="
echo ""

# Build JAR
echo "Building JAR..."
bash scripts/build_jar.sh
echo "Done."
echo ""

# Save experiment config
cp "$CONFIG_FILE" "${RESULTS_DIR}/experiment_config.txt"

# Detect maximum CPU requirements from parameter files
max_cpus=1
for param_file in "${PARAMETER_FILES[@]}"; do
    param_path="${PARAM_DIR}/${param_file}.txt"
    if [ -f "$param_path" ]; then
        # Extract numWorkerThreads, default to 0 if not found or multiStart disabled
        worker_threads=$(grep "^multiStart.numWorkerThreads=" "$param_path" | cut -d'=' -f2 || echo "0")
        multistart_enabled=$(grep "^multiStart.enabled=" "$param_path" | cut -d'=' -f2 || echo "false")

        if [ "$multistart_enabled" = "true" ]; then
            # Total threads = 1 (main) + N (workers) + 1 (PR)
            total_threads=$((worker_threads + 2))
        else
            # Single thread AILS
            total_threads=1
        fi

        if [ $total_threads -gt $max_cpus ]; then
            max_cpus=$total_threads
        fi
    fi
done

# Calculate walltime (TIME_LIMIT + 10% buffer, minimum 1 hour)
walltime_seconds=$((TIME_LIMIT + TIME_LIMIT / 10))
if [ $walltime_seconds -lt 3600 ]; then
    walltime_seconds=3600
fi
walltime_hours=$((walltime_seconds / 3600))
walltime_mins=$(((walltime_seconds % 3600) / 60))
walltime="${walltime_hours}:$(printf "%02d" $walltime_mins):00"

echo "Resource requirements:"
echo "  CPUs per job: ${max_cpus}"
echo "  Walltime: ${walltime} (${walltime_seconds}s)"
echo ""

# Create job index file (maps array task ID to param_file,instance)
JOB_INDEX="${RESULTS_DIR}/job_index.txt"
job_id=0
for param_file in "${PARAMETER_FILES[@]}"; do
    for instance in "${INSTANCES[@]}"; do
        echo "${param_file},${instance}" >> "${JOB_INDEX}"
        job_id=$((job_id + 1))
    done
done

total_jobs=$(cat "${JOB_INDEX}" | wc -l)

echo "Created job index with ${total_jobs} jobs"
echo ""

# Submit SLURM array job with dynamic resource requests
sbatch \
    --array=1-${total_jobs} \
    --job-name="${EXPERIMENT_NAME}" \
    --output="${RESULTS_DIR}/slurm_%A_%a.out" \
    --error="${RESULTS_DIR}/slurm_%A_%a.err" \
    --cpus-per-task=${max_cpus} \
    --time=${walltime} \
    --mem=16G \
    scripts/slurm_job.sh \
    "${RESULTS_DIR}" \
    "${JOB_INDEX}" \
    "${PARAM_DIR}" \
    "${DATA_DIR}" \
    "${TIME_LIMIT}" \
    "${BEST_KNOWN}"

echo "========================================="
echo "Jobs submitted!"
echo "========================================="
echo ""
echo "Monitor with: squeue -u \$USER"
echo "Cancel with: scancel --name=${EXPERIMENT_NAME}"
echo ""
echo "After completion, aggregate results with:"
echo "  bash scripts/aggregate_results.sh ${RESULTS_DIR}"
echo ""
