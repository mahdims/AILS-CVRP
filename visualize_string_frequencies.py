#!/usr/bin/env python3
"""
String Frequency Visualization Script
Generates heatmap and histogram visualizations from CSV logs

Usage:
    python visualize_string_frequencies.py logs/string_freq_X-n101-k25_20260103_120000.csv
"""

import pandas as pd
import matplotlib.pyplot as plt
import seaborn as sns
import numpy as np
import sys
import os
from pathlib import Path

def load_data(csv_file):
    """Load CSV data and return DataFrame"""
    print(f"Loading data from: {csv_file}")
    df = pd.read_csv(csv_file)
    print(f"Loaded {len(df)} rows")
    print(f"Iterations: {df['iteration'].min()} - {df['iteration'].max()}")
    print(f"Unique strings: {df['string'].nunique()}")
    return df

def create_heatmap(df, output_dir, top_n=30):
    """
    Create heatmap showing frequency evolution of top-N strings over iterations

    Args:
        df: DataFrame with string frequency data
        output_dir: Directory to save output
        top_n: Number of top strings to show
    """
    print(f"\nCreating heatmap for top {top_n} strings...")

    # Get overall top-N strings (by maximum frequency reached)
    top_strings_max_freq = df.groupby('string')['frequency'].max().nlargest(top_n)
    top_strings = top_strings_max_freq.index.tolist()

    # Filter data to only top strings
    df_top = df[df['string'].isin(top_strings)].copy()

    # Pivot table: rows = strings, columns = iterations, values = frequency
    pivot = df_top.pivot_table(
        index='string',
        columns='iteration',
        values='frequency',
        fill_value=0
    )

    # Sort by maximum frequency (descending)
    pivot = pivot.loc[pivot.max(axis=1).sort_values(ascending=False).index]

    # Create figure
    fig, ax = plt.subplots(figsize=(16, max(10, top_n * 0.4)))

    # Create heatmap
    sns.heatmap(
        pivot,
        cmap='YlOrRd',
        cbar_kws={'label': 'Frequency'},
        linewidths=0.1,
        linecolor='white',
        ax=ax,
        vmin=0,
        fmt='d'
    )

    # Customize
    ax.set_title(f'String Frequency Evolution - Top {top_n} Strings',
                 fontsize=16, fontweight='bold', pad=20)
    ax.set_xlabel('Iteration', fontsize=12, fontweight='bold')
    ax.set_ylabel('String (Node Sequence)', fontsize=12, fontweight='bold')

    # Rotate x-axis labels for better readability
    plt.xticks(rotation=45, ha='right')
    plt.yticks(rotation=0)

    plt.tight_layout()

    # Save
    output_file = os.path.join(output_dir, 'heatmap_top_strings.png')
    plt.savefig(output_file, dpi=300, bbox_inches='tight')
    print(f"Saved heatmap: {output_file}")
    plt.close()

def create_histogram(df, output_dir):
    """
    Create histograms showing frequency distribution at each snapshot

    Args:
        df: DataFrame with string frequency data
        output_dir: Directory to save output
    """
    print("\nCreating frequency distribution histograms...")

    # Get unique iterations
    iterations = sorted(df['iteration'].unique())

    # Determine number of subplots (max 12 snapshots to keep readable)
    step = max(1, len(iterations) // 12)
    selected_iterations = iterations[::step]

    n_plots = len(selected_iterations)
    n_cols = min(4, n_plots)
    n_rows = (n_plots + n_cols - 1) // n_cols

    # Create figure
    fig, axes = plt.subplots(n_rows, n_cols, figsize=(16, 4 * n_rows))
    if n_plots == 1:
        axes = [axes]
    else:
        axes = axes.flatten() if n_plots > 1 else [axes]

    # Create histogram for each selected iteration
    for idx, iteration in enumerate(selected_iterations):
        ax = axes[idx]

        # Get data for this iteration
        iter_data = df[df['iteration'] == iteration]
        frequencies = iter_data['frequency'].values

        # Create histogram
        ax.hist(frequencies, bins=30, color='steelblue', edgecolor='black', alpha=0.7)

        # Get stats
        unique_strings = iter_data['unique_strings'].iloc[0] if len(iter_data) > 0 else 0
        total_extracted = iter_data['total_extracted'].iloc[0] if len(iter_data) > 0 else 0
        time_sec = iter_data['time_sec'].iloc[0] if len(iter_data) > 0 else 0

        # Customize
        ax.set_title(f'Iter {iteration} ({time_sec:.1f}s)\n{unique_strings} unique strings',
                     fontsize=10, fontweight='bold')
        ax.set_xlabel('Frequency', fontsize=9)
        ax.set_ylabel('Count', fontsize=9)
        ax.grid(True, alpha=0.3, linestyle='--')

        # Add stats text
        ax.text(0.95, 0.95, f'Total: {total_extracted}',
                transform=ax.transAxes,
                fontsize=8,
                verticalalignment='top',
                horizontalalignment='right',
                bbox=dict(boxstyle='round', facecolor='wheat', alpha=0.5))

    # Hide extra subplots if any
    for idx in range(n_plots, len(axes)):
        axes[idx].axis('off')

    # Main title
    fig.suptitle('Frequency Distribution Evolution Over Iterations',
                 fontsize=16, fontweight='bold', y=0.995)

    plt.tight_layout()

    # Save
    output_file = os.path.join(output_dir, 'histogram_frequency_distribution.png')
    plt.savefig(output_file, dpi=300, bbox_inches='tight')
    print(f"Saved histogram: {output_file}")
    plt.close()

def create_rank_evolution(df, output_dir, top_n=15):
    """
    Create line plot showing how string rankings change over iterations

    Args:
        df: DataFrame with string frequency data
        output_dir: Directory to save output
        top_n: Number of top strings to track
    """
    print(f"\nCreating rank evolution plot for top {top_n} strings...")

    # Get overall top-N strings (by maximum frequency)
    top_strings_max_freq = df.groupby('string')['frequency'].max().nlargest(top_n)
    top_strings = top_strings_max_freq.index.tolist()

    # Filter to top strings
    df_top = df[df['string'].isin(top_strings)].copy()

    # Create figure
    fig, ax = plt.subplots(figsize=(14, 8))

    # Plot rank evolution for each string
    colors = plt.cm.tab20(np.linspace(0, 1, top_n))

    for idx, string in enumerate(top_strings):
        string_data = df_top[df_top['string'] == string].sort_values('iteration')
        ax.plot(string_data['iteration'], string_data['rank'],
                marker='o', label=string, color=colors[idx], linewidth=2, markersize=4)

    # Customize
    ax.set_title(f'String Rank Evolution - Top {top_n} Strings',
                 fontsize=16, fontweight='bold', pad=20)
    ax.set_xlabel('Iteration', fontsize=12, fontweight='bold')
    ax.set_ylabel('Rank (1 = Most Frequent)', fontsize=12, fontweight='bold')
    ax.invert_yaxis()  # Lower rank = better (rank 1 at top)
    ax.grid(True, alpha=0.3, linestyle='--')
    ax.legend(bbox_to_anchor=(1.05, 1), loc='upper left', fontsize=8)

    plt.tight_layout()

    # Save
    output_file = os.path.join(output_dir, 'rank_evolution.png')
    plt.savefig(output_file, dpi=300, bbox_inches='tight')
    print(f"Saved rank evolution: {output_file}")
    plt.close()

def create_summary_stats(df, output_dir):
    """
    Create text summary of key statistics

    Args:
        df: DataFrame with string frequency data
        output_dir: Directory to save output
    """
    print("\nGenerating summary statistics...")

    output_file = os.path.join(output_dir, 'summary_stats.txt')

    with open(output_file, 'w') as f:
        f.write("=" * 70 + "\n")
        f.write("STRING FREQUENCY ANALYSIS SUMMARY\n")
        f.write("=" * 70 + "\n\n")

        # Overall stats
        f.write("OVERALL STATISTICS:\n")
        f.write("-" * 70 + "\n")
        f.write(f"Total iterations tracked: {df['iteration'].max()}\n")
        f.write(f"Total snapshots: {df['iteration'].nunique()}\n")
        f.write(f"Unique strings observed: {df['string'].nunique()}\n")
        f.write(f"Time range: 0.00s - {df['time_sec'].max():.2f}s\n")
        f.write("\n")

        # Final iteration stats
        final_iter = df['iteration'].max()
        final_data = df[df['iteration'] == final_iter]
        f.write(f"FINAL ITERATION ({final_iter}):\n")
        f.write("-" * 70 + "\n")
        if len(final_data) > 0:
            f.write(f"Unique strings: {final_data['unique_strings'].iloc[0]}\n")
            f.write(f"Total extracted: {final_data['total_extracted'].iloc[0]}\n")
            f.write(f"Elite set size: {final_data['elite_size'].iloc[0]}\n")
            f.write("\n")

        # Top 20 most frequent strings (final iteration)
        f.write("TOP 20 MOST FREQUENT STRINGS (Final Iteration):\n")
        f.write("-" * 70 + "\n")
        f.write(f"{'Rank':<6} {'String':<25} {'Frequency':<12} {'%':<8}\n")
        f.write("-" * 70 + "\n")

        top20 = final_data.nsmallest(20, 'rank')
        total_freq = final_data['frequency'].sum() if len(final_data) > 0 else 1

        for _, row in top20.iterrows():
            pct = (row['frequency'] / total_freq * 100) if total_freq > 0 else 0
            f.write(f"{row['rank']:<6.0f} {row['string']:<25} {row['frequency']:<12.0f} {pct:<8.2f}\n")

        f.write("\n")

        # String persistence analysis (strings that appear in all snapshots)
        f.write("STRING PERSISTENCE:\n")
        f.write("-" * 70 + "\n")
        string_appearances = df.groupby('string')['iteration'].count()
        max_appearances = df['iteration'].nunique()
        persistent_strings = string_appearances[string_appearances == max_appearances]

        f.write(f"Strings appearing in all {max_appearances} snapshots: {len(persistent_strings)}\n")
        if len(persistent_strings) > 0:
            f.write(f"\nTop 10 persistent strings (by final frequency):\n")
            persistent_final = final_data[final_data['string'].isin(persistent_strings.index)]
            persistent_final = persistent_final.nsmallest(10, 'rank')
            for _, row in persistent_final.iterrows():
                f.write(f"  {row['string']}: freq={row['frequency']:.0f}, rank={row['rank']:.0f}\n")

        f.write("\n")
        f.write("=" * 70 + "\n")

    print(f"Saved summary: {output_file}")

def main():
    if len(sys.argv) < 2:
        print("Usage: python visualize_string_frequencies.py <csv_file>")
        print("Example: python visualize_string_frequencies.py logs/string_freq_X-n101-k25_20260103_120000.csv")
        sys.exit(1)

    csv_file = sys.argv[1]

    if not os.path.exists(csv_file):
        print(f"Error: File not found: {csv_file}")
        sys.exit(1)

    # Create output directory
    output_dir = os.path.dirname(csv_file)
    if not output_dir:
        output_dir = "."

    print("=" * 70)
    print("STRING FREQUENCY VISUALIZATION")
    print("=" * 70)

    # Load data
    df = load_data(csv_file)

    # Generate visualizations
    create_heatmap(df, output_dir, top_n=30)
    create_histogram(df, output_dir)
    create_rank_evolution(df, output_dir, top_n=15)
    create_summary_stats(df, output_dir)

    print("\n" + "=" * 70)
    print("VISUALIZATION COMPLETE!")
    print("=" * 70)
    print(f"\nOutput files saved in: {output_dir}/")
    print("  - heatmap_top_strings.png")
    print("  - histogram_frequency_distribution.png")
    print("  - rank_evolution.png")
    print("  - summary_stats.txt")
    print("\nDone!")

if __name__ == "__main__":
    main()
