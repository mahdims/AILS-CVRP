#!/bin/bash

################################################################################
# Simple AILS-CVRP Experiment Runner
# Usage: bash scripts/run_experiment.sh [experiment_config.txt]
################################################################################

set -e

CONFIG_FILE=${1:-"experiments/experiment_config.txt"}

if [ ! -f "$CONFIG_FILE" ]; then
    echo "Error: Configuration file not found: $CONFIG_FILE"
    exit 1
fi

# Get directory where config file is located (for parameter files)
PARAM_DIR=$(dirname "$CONFIG_FILE")

# Load configuration
source "$CONFIG_FILE"

# Setup
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
RESULTS_DIR="experiments/${EXPERIMENT_NAME}_${TIMESTAMP}"
mkdir -p "${RESULTS_DIR}"

echo "========================================="
echo "AILS-CVRP Experiment"
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

# Initialize results CSV
RESULTS_CSV="${RESULTS_DIR}/results.csv"
echo "parameter_file,instance,objective,time_to_best,total_time" > "${RESULTS_CSV}"

# Run experiments
total_runs=$(( ${#PARAMETER_FILES[@]} * ${#INSTANCES[@]} ))
current_run=0

for param_file in "${PARAMETER_FILES[@]}"; do
    param_path="${PARAM_DIR}/${param_file}.txt"

    if [ ! -f "$param_path" ]; then
        echo "Error: Parameter file not found: $param_path"
        continue
    fi

    echo "========================================="
    echo "Configuration: ${param_file}"
    echo "========================================="

    # Create output directories for this configuration
    CONFIG_DIR="${RESULTS_DIR}/${param_file}"
    mkdir -p "${CONFIG_DIR}/logs"
    mkdir -p "${CONFIG_DIR}/solutions"

    for instance in "${INSTANCES[@]}"; do
        current_run=$((current_run + 1))

        echo "[$current_run/$total_runs] Running: ${param_file} on ${instance}"

        instance_file="${DATA_DIR}/${instance}.vrp"
        log_file="${CONFIG_DIR}/logs/${instance}.log"

        start_time=$(date +%s)

        # Run AILS with custom parameter file
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

        # Save to CSV
        echo "${param_file},${instance},${objective},${time_to_best},${actual_time}" >> "${RESULTS_CSV}"

        if [ $exit_code -eq 0 ]; then
            echo "  ✓ Completed: ${objective} (${actual_time}s)"
        else
            echo "  ✗ Failed or timeout"
        fi
        echo ""
    done
done

echo "========================================="
echo "Experiment Complete!"
echo "========================================="
echo "Results: ${RESULTS_DIR}"
echo ""

# Run analysis
bash scripts/analyze_experiment.sh "${RESULTS_DIR}"
