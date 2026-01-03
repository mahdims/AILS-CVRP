#!/bin/bash

################################################################################
# Aggregate Results from SLURM Jobs
# Usage: bash scripts/aggregate_results.sh <results_directory>
################################################################################

set -e

RESULTS_DIR=$1

if [ -z "$RESULTS_DIR" ] || [ ! -d "$RESULTS_DIR" ]; then
    echo "Usage: bash aggregate_results.sh <results_directory>"
    exit 1
fi

echo "========================================="
echo "Aggregating Results"
echo "========================================="
echo "Directory: ${RESULTS_DIR}"
echo ""

# Create results CSV from individual result files
RESULTS_CSV="${RESULTS_DIR}/results.csv"
echo "parameter_file,instance,objective,time_to_best,total_time" > "${RESULTS_CSV}"

# Find all .result files and concatenate
find "${RESULTS_DIR}" -name "*.result" -type f | while read result_file; do
    cat "$result_file" >> "${RESULTS_CSV}"
done

num_results=$(tail -n +2 "${RESULTS_CSV}" | wc -l)
echo "Aggregated ${num_results} results"
echo ""

# Run analysis
echo "Running analysis..."
bash scripts/analyze_experiment.sh "${RESULTS_DIR}"

echo ""
echo "========================================="
echo "Aggregation Complete!"
echo "========================================="
echo "Results file: ${RESULTS_CSV}"
echo ""
