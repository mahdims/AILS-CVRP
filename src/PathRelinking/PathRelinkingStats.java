package PathRelinking;

import java.util.ArrayList;
import java.util.List;

/**
 * Statistics tracking for Path Relinking
 *
 * Tracks and reports PR performance metrics:
 * - Total iterations
 * - Successful elite set insertions
 * - Best solution quality found
 * - Improvement history
 * - Running time
 */
public class PathRelinkingStats {

    private int totalIterations;
    private int successfulInsertions;
    private double bestFFound;
    private List<Double> improvementHistory;
    private long startTime;
    private int totalMovesPerformed;
    private int totalSolutionsPaired;

    public PathRelinkingStats() {
        this.totalIterations = 0;
        this.successfulInsertions = 0;
        this.bestFFound = Double.MAX_VALUE;
        this.improvementHistory = new ArrayList<>();
        this.startTime = System.currentTimeMillis();
        this.totalMovesPerformed = 0;
        this.totalSolutionsPaired = 0;
    }

    /**
     * Record a PR iteration
     *
     * @param inserted Whether solution was inserted into elite set
     * @param f        Objective value of solution
     */
    public void recordIteration(boolean inserted, double f) {
        totalIterations++;

        if (inserted) {
            successfulInsertions++;
        }

        if (f < bestFFound) {
            bestFFound = f;
            improvementHistory.add(f);
        }
    }

    /**
     * Record a solution pairing attempt
     */
    public void recordPairing() {
        totalSolutionsPaired++;
    }

    /**
     * Record vertex moves performed
     */
    public void recordMoves(int moves) {
        totalMovesPerformed += moves;
    }

    /**
     * Print periodic statistics (single line)
     *
     * @param iteration   Current iteration number
     * @param globalBestF Global best objective value (from AILS or PR)
     */
    public void printStats(int iteration, double globalBestF) {
        double elapsed = (System.currentTimeMillis() - startTime) / 1000.0;
        double insertionRate = totalIterations > 0
                ? 100.0 * successfulInsertions / totalIterations
                : 0.0;

        System.out.printf(
                "[PR] time:%.1fs iter:%d | insertions:%d/%d(%.1f%%) bestF:%.2f " +
                        "paired:%d moves:%d\n",
                elapsed,
                iteration,
                successfulInsertions,
                totalIterations,
                insertionRate,
                globalBestF, // Use global best instead of local bestFFound
                totalSolutionsPaired,
                totalMovesPerformed);
    }

    /**
     * Print final statistics summary
     */
    public void printFinalStats() {
        double elapsed = (System.currentTimeMillis() - startTime) / 1000.0;
        double insertionRate = totalIterations > 0
                ? 100.0 * successfulInsertions / totalIterations
                : 0.0;
        double avgMovesPerIteration = totalIterations > 0
                ? (double) totalMovesPerformed / totalIterations
                : 0.0;

        System.out.println("\n==================================================");
        System.out.println("    Path Relinking Final Statistics              ");
        System.out.println("==================================================");
        System.out.printf("  Total iterations:        %-23d  \n", totalIterations);
        System.out.printf("  Successful insertions:   %-23d  \n", successfulInsertions);
        System.out.printf("  Insertion rate:          %-22.2f%%  \n", insertionRate);
        System.out.printf("  Best F found:            %-23.2f  \n", bestFFound);
        System.out.printf("  Total improvements:      %-23d  \n", improvementHistory.size());
        System.out.printf("  Solutions paired:        %-23d  \n", totalSolutionsPaired);
        System.out.printf("  Total vertex moves:      %-23d  \n", totalMovesPerformed);
        System.out.printf("  Avg moves/iteration:     %-23.1f  \n", avgMovesPerIteration);
        System.out.printf("  Total time:              %-22.2fs  \n", elapsed);
        System.out.println("==================================================\n");
    }

    /**
     * Print compact summary (for debugging)
     */
    public void printCompactSummary() {
        double elapsed = (System.currentTimeMillis() - startTime) / 1000.0;
        System.out.printf("[PR] %d iterations, %d insertions (%.1f%%), best=%.2f, time=%.1fs\n",
                totalIterations,
                successfulInsertions,
                totalIterations > 0 ? 100.0 * successfulInsertions / totalIterations : 0.0,
                bestFFound,
                elapsed);
    }

    // Getters
    public int getTotalIterations() {
        return totalIterations;
    }

    public int getSuccessfulInsertions() {
        return successfulInsertions;
    }

    public double getBestFFound() {
        return bestFFound;
    }

    public double getInsertionRate() {
        return totalIterations > 0
                ? 100.0 * successfulInsertions / totalIterations
                : 0.0;
    }

    public List<Double> getImprovementHistory() {
        return new ArrayList<>(improvementHistory);
    }

    public double getElapsedTime() {
        return (System.currentTimeMillis() - startTime) / 1000.0;
    }
}
