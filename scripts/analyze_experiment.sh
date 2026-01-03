#!/bin/bash

################################################################################
# Experiment Results Analyzer
# Usage: bash scripts/analyze_experiment.sh <results_directory>
################################################################################

RESULTS_DIR=$1

if [ -z "$RESULTS_DIR" ] || [ ! -d "$RESULTS_DIR" ]; then
    echo "Usage: bash analyze_experiment.sh <results_directory>"
    exit 1
fi

RESULTS_CSV="${RESULTS_DIR}/results.csv"

if [ ! -f "$RESULTS_CSV" ]; then
    echo "Error: Results file not found: $RESULTS_CSV"
    exit 1
fi

echo "Analyzing results..."

# Create comparison table (Excel-friendly)
COMPARISON_CSV="${RESULTS_DIR}/comparison_table.csv"

# Get unique parameter files and instances
param_files=$(tail -n +2 "${RESULTS_CSV}" | cut -d',' -f1 | sort -u)
instances=$(tail -n +2 "${RESULTS_CSV}" | cut -d',' -f2 | sort -u)

# Create header
echo -n "Instance," > "${COMPARISON_CSV}"
for param in $param_files; do
    echo -n "${param}," >> "${COMPARISON_CSV}"
done
echo "Best,Best_Config" >> "${COMPARISON_CSV}"

# Create data rows
for instance in $instances; do
    echo -n "${instance}," >> "${COMPARISON_CSV}"

    best_obj=""
    best_config=""

    for param in $param_files; do
        obj=$(grep "^${param},${instance}," "${RESULTS_CSV}" | cut -d',' -f3)

        if [ -z "$obj" ] || [ "$obj" = "FAILED" ]; then
            obj="N/A"
        fi

        echo -n "${obj}," >> "${COMPARISON_CSV}"

        # Track best
        if [ "$obj" != "N/A" ] && [ "$obj" != "FAILED" ]; then
            if [ -z "$best_obj" ] || (( $(echo "$obj < $best_obj" | bc -l 2>/dev/null || echo 0) )); then
                best_obj=$obj
                best_config=$param
            fi
        fi
    done

    echo "${best_obj},${best_config}" >> "${COMPARISON_CSV}"
done

# Create summary statistics
SUMMARY_CSV="${RESULTS_DIR}/summary_statistics.csv"

echo "Parameter_File,Avg_Objective,Best_Objective,Worst_Objective,Avg_Time_To_Best,Success_Rate" > "${SUMMARY_CSV}"

for param in $param_files; do
    param_data=$(grep "^${param}," "${RESULTS_CSV}")

    # Count instances
    total=$(echo "$param_data" | wc -l)
    failed=$(echo "$param_data" | grep "FAILED" | wc -l || echo 0)
    success=$((total - failed))
    success_rate=$(echo "scale=2; 100*$success/$total" | bc)

    # Calculate statistics (excluding FAILED)
    valid_data=$(echo "$param_data" | grep -v "FAILED")

    if [ -n "$valid_data" ]; then
        avg_obj=$(echo "$valid_data" | awk -F',' '{sum+=$3; count++} END {printf "%.2f", sum/count}')
        best_obj=$(echo "$valid_data" | awk -F',' '{print $3}' | sort -n | head -1)
        worst_obj=$(echo "$valid_data" | awk -F',' '{print $3}' | sort -n | tail -1)
        avg_time=$(echo "$valid_data" | awk -F',' '{if ($4!="N/A") {sum+=$4; count++}} END {if (count>0) printf "%.2f", sum/count; else print "N/A"}')
    else
        avg_obj="N/A"
        best_obj="N/A"
        worst_obj="N/A"
        avg_time="N/A"
    fi

    echo "${param},${avg_obj},${best_obj},${worst_obj},${avg_time},${success_rate}%" >> "${SUMMARY_CSV}"
done

# Create text summary
SUMMARY_TXT="${RESULTS_DIR}/summary.txt"

cat > "${SUMMARY_TXT}" <<EOF
================================================================================
EXPERIMENT RESULTS SUMMARY
================================================================================
Directory: ${RESULTS_DIR}
Generated: $(date)

OVERALL STATISTICS
------------------
EOF

cat "${SUMMARY_CSV}" | column -t -s',' >> "${SUMMARY_TXT}"

cat >> "${SUMMARY_TXT}" <<EOF

RANKING (by average objective)
-------------------------------
EOF

tail -n +2 "${SUMMARY_CSV}" | sort -t',' -k2 -n | awk -F',' '{print NR". "$1" - Avg: "$2}' >> "${SUMMARY_TXT}"

cat >> "${SUMMARY_TXT}" <<EOF

INSTANCE-BY-INSTANCE COMPARISON
--------------------------------
Best configuration for each instance:

EOF

tail -n +2 "${COMPARISON_CSV}" | awk -F',' '{print $1": "$NF" ("$(NF-1)")"}' >> "${SUMMARY_TXT}"

echo "
================================================================================
" >> "${SUMMARY_TXT}"

# Display summary
echo "========================================="
echo "Analysis Complete!"
echo "========================================="
echo ""
cat "${SUMMARY_TXT}"
echo ""
echo "Files created:"
echo "  - ${COMPARISON_CSV}"
echo "  - ${SUMMARY_CSV}"
echo "  - ${SUMMARY_TXT}"
