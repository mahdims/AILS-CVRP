#!/bin/bash

################################################################################
# SLURM Job Script - Single (parameter_file, instance) run
# This script is called by SLURM array job
# Resource requirements are set dynamically by submit_experiment.sh
################################################################################

#SBATCH --ntasks=1

set -e

# Determine project directory, preferring the SLURM submission directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="${SLURM_SUBMIT_DIR:-$(cd "$SCRIPT_DIR/.." && pwd)}"

# Change to project directory (where AILSII.jar is)
cd "$PROJECT_DIR"

# Arguments passed from submit_experiment.sh
RESULTS_DIR=$1
JOB_INDEX=$2
PARAM_DIR=$3
DATA_DIR=$4
TIME_LIMIT=$5
BEST_KNOWN=$6

# Get this job's assignment from index file
job_line=$(sed -n "${SLURM_ARRAY_TASK_ID}p" "${JOB_INDEX}")
param_file=$(echo "$job_line" | cut -d',' -f1)
instance=$(echo "$job_line" | cut -d',' -f2)

# Setup paths
param_path="${PARAM_DIR}/${param_file}.txt"
instance_file="${DATA_DIR}/${instance}.vrp"

# Create output directories for this configuration
CONFIG_DIR="${RESULTS_DIR}/${param_file}"
mkdir -p "${CONFIG_DIR}/logs"
mkdir -p "${CONFIG_DIR}/solutions"

log_file="${CONFIG_DIR}/logs/${instance}.log"
result_file="${CONFIG_DIR}/results/${instance}.result"
mkdir -p "${CONFIG_DIR}/results"

echo "========================================="
echo "SLURM Array Job ${SLURM_ARRAY_JOB_ID}_${SLURM_ARRAY_TASK_ID}"
echo "========================================="
echo "Configuration: ${param_file}"
echo "Instance: ${instance}"
echo "Node: $(hostname)"
echo "Working directory: $(pwd)"
echo ""
echo "Paths:"
echo "  Parameter file: ${param_path}"
echo "  Instance file: ${instance_file}"
echo "  Log file: ${log_file}"
echo ""
echo "File checks:"
[ -f "${param_path}" ] && echo "  ✓ Parameter file exists" || echo "  ✗ Parameter file NOT FOUND"
[ -f "${instance_file}" ] && echo "  ✓ Instance file exists" || echo "  ✗ Instance file NOT FOUND"
[ -f "AILSII.jar" ] && echo "  ✓ AILSII.jar exists" || echo "  ✗ AILSII.jar NOT FOUND"
echo "========================================="
echo ""

start_time=$(date +%s)

# Run AILS
timeout $((TIME_LIMIT + 60)) java -jar AILSII.jar \
    -params "${param_path}" \
    -file "${instance_file}" \
    -limit ${TIME_LIMIT} \
    -best ${BEST_KNOWN} \
    -solutionDir "${CONFIG_DIR}/solutions" \
    > "${log_file}" 2>&1

exit_code=$?
end_time=$(date +%s)
actual_time=$((end_time - start_time))

# Extract results
objective=$(grep "Best solution:" "${log_file}" | tail -1 | awk '{print $3}')
time_to_best=$(grep "time:" "${log_file}" | grep "iter:" | tail -1 | sed 's/.*time:\([0-9.]*\)s.*/\1/' || echo "N/A")

if [ -z "$objective" ]; then
    objective="FAILED"
    time_to_best="N/A"
fi

# Save to individual result file (NO SHARED FILE WRITING)
echo "${param_file},${instance},${objective},${time_to_best},${actual_time}" > "${result_file}"

if [ $exit_code -eq 0 ]; then
    echo "✓ Completed: ${objective} (${actual_time}s)"
else
    echo "✗ Failed or timeout"
fi
