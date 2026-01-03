package SearchMethod;

import EliteSet.EliteSet;
import EliteSet.EliteSolution;
import EliteSet.SolutionSource;
import Solution.Solution;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Monitors AILS thread performance and manages dynamic restarts
 *
 * Responsibilities:
 * 1. Track per-thread statistics (iterations, insertions, best found)
 * 2. Detect stagnant threads (no contribution to elite set + far from global best)
 * 3. Provide restart seeds (least recently used PR solutions)
 * 4. Avoid terminating threads that are intensifying (competitive with global best)
 */
public class ThreadMonitor {

    /** Shared elite set (read-only access) */
    private EliteSet eliteSet;

    /** Statistics for each thread */
    private Map<Integer, ThreadStats> threadStats;

    /** Seed selection strategy */
    private SeedSelectionStrategy seedSelector;

    /** Track usage count for each solution used as restart seed */
    private Map<Solution, Integer> seedUsageCount;

    /** Configuration */
    private int stagnationThreshold;      // Iterations without insertion before considering stagnant
    private double competitiveThreshold;  // Gap from global best to consider competitive (e.g., 0.01 = 1%)

    /** Protected thread ID (main thread, never restarts) */
    private static final int PROTECTED_THREAD_ID = 1;

    /** Current global best objective value */
    private volatile double globalBestF;

    /**
     * Constructor
     * @param eliteSet Shared elite set
     * @param stagnationThreshold Iterations without insertion to consider stagnant
     * @param competitiveThreshold Gap percentage to consider competitive (0.0-1.0)
     * @param seedSelector Strategy for selecting restart seeds
     */
    public ThreadMonitor(EliteSet eliteSet, int stagnationThreshold, double competitiveThreshold,
                        SeedSelectionStrategy seedSelector) {
        this.eliteSet = eliteSet;
        this.threadStats = new ConcurrentHashMap<>();
        this.seedSelector = seedSelector;
        this.seedUsageCount = new ConcurrentHashMap<>();
        this.stagnationThreshold = stagnationThreshold;
        this.competitiveThreshold = competitiveThreshold;
        this.globalBestF = Double.MAX_VALUE;
    }

    /**
     * Register a new thread
     * @param threadId Thread identifier
     */
    public void registerThread(int threadId) {
        threadStats.put(threadId, new ThreadStats(threadId));
        System.out.println("[ThreadMonitor] Registered Thread-" + threadId);
    }

    /**
     * Get statistics for a thread
     * @param threadId Thread identifier
     * @return Thread statistics
     */
    public ThreadStats getThreadStats(int threadId) {
        return threadStats.get(threadId);
    }

    /**
     * Update global best objective value
     * @param bestF New global best
     */
    public synchronized void updateGlobalBest(double bestF) {
        if (bestF < this.globalBestF) {
            this.globalBestF = bestF;
        }
    }

    /**
     * Get current global best
     * @return Global best objective value
     */
    public double getGlobalBest() {
        return globalBestF;
    }

    /**
     * Check if a thread is stagnant and should be restarted
     *
     * Main thread (Thread-1) is protected and NEVER restarts
     * Worker threads can restart based on stagnation criteria
     *
     * @param threadId Thread identifier
     * @return true if thread should be restarted
     */
    public boolean shouldRestart(int threadId) {
        // Main thread NEVER restarts (protected)
        if (threadId == PROTECTED_THREAD_ID) {
            return false;
        }

        ThreadStats stats = threadStats.get(threadId);
        if (stats == null || !stats.isActive()) {
            return false;
        }

        return stats.isStagnant(globalBestF, stagnationThreshold, competitiveThreshold);
    }

    /**
     * Get a restart seed solution from elite set
     *
     * Uses configured seed selection strategy (e.g., quality-based, diversity-based)
     * Tracks usage count to avoid repeatedly selecting same solutions
     *
     * @param threadId Thread identifier (for logging)
     * @return Solution to restart from, or null if no suitable seed found
     */
    public Solution getRestartSeed(int threadId) {
        // Get all elite solutions
        List<EliteSolution> allElite = eliteSet.getAllEliteSolutionsThreadSafe();

        if (allElite.isEmpty()) {
            System.out.println("[ThreadMonitor] No elite solutions available for restart");
            return null;
        }

        // Use strategy to select seed
        Solution selected = seedSelector.selectSeed(allElite, seedUsageCount);

        if (selected != null) {
            // Increment usage count
            int newUsage = seedUsageCount.getOrDefault(selected, 0) + 1;
            seedUsageCount.put(selected, newUsage);

            System.out.printf("[ThreadMonitor] Thread-%d restarting (seed usage now: %d)%n",
                threadId, newUsage);
        } else {
            System.out.println("[ThreadMonitor] No suitable seed found for Thread-" + threadId);
        }

        return selected;
    }

    /**
     * Print monitoring summary
     */
    public void printSummary() {
        System.out.println("\n=== Thread Monitor Summary ===");
        System.out.printf("Global Best: %.2f%n", globalBestF);
        System.out.printf("Stagnation Threshold: %d iterations%n", stagnationThreshold);
        System.out.printf("Competitive Threshold: %.1f%%%n", competitiveThreshold * 100);
        System.out.println("\nThread Statistics:");

        // Sort by thread ID
        List<ThreadStats> statsList = new ArrayList<>(threadStats.values());
        statsList.sort(Comparator.comparingInt(ThreadStats::getThreadId));

        for (ThreadStats stats : statsList) {
            double gap = stats.getGapFromGlobalBest(globalBestF);
            boolean stagnant = stats.isStagnant(globalBestF, stagnationThreshold, competitiveThreshold);

            System.out.printf("  Thread-%d: iter=%d, inserts=%d (%.2f/1k), bestF=%.2f, gap=%.2f%%, stagnant=%d, restarts=%d %s%n",
                    stats.getThreadId(),
                    stats.getTotalIterations(),
                    stats.getEliteInsertions(),
                    stats.getInsertionRate(),
                    stats.getCurrentBestF(),
                    gap * 100,
                    stats.getIterationsSinceLastInsertion(),
                    stats.getRestartCount(),
                    stagnant ? "[STAGNANT]" : stats.isActive() ? "[ACTIVE]" : "[TERMINATED]");
        }

        System.out.println("==============================\n");
    }

    /**
     * Get total elite insertions across all threads
     * @return Total insertions
     */
    public int getTotalInsertions() {
        return threadStats.values().stream()
                .mapToInt(ThreadStats::getEliteInsertions)
                .sum();
    }

    /**
     * Get total restarts across all threads
     * @return Total restarts
     */
    public int getTotalRestarts() {
        return threadStats.values().stream()
                .mapToInt(ThreadStats::getRestartCount)
                .sum();
    }

    /**
     * Get active thread count
     * @return Number of active threads
     */
    public int getActiveThreadCount() {
        return (int) threadStats.values().stream()
                .filter(ThreadStats::isActive)
                .count();
    }

    /**
     * Get seed selection strategy name
     * @return Strategy name
     */
    public String getSeedStrategyName() {
        return seedSelector.getStrategyName();
    }
}
