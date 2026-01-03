package SearchMethod;

/**
 * Tracks performance statistics for an AILS thread
 * Used by ThreadMonitor to detect stagnation and decide when to restart threads
 */
public class ThreadStats {

    /** Thread ID (1, 2, 3, ...) - 0 reserved for main thread */
    private int threadId;

    /** Total iterations executed by this thread */
    private int totalIterations;

    /** Number of successful insertions to shared elite set */
    private int eliteInsertions;

    /** Number of times this thread found a new global best */
    private int globalBestImprovements;

    /** Iterations since last successful insertion to elite set */
    private int iterationsSinceLastInsertion;

    /** Current best objective value found by this thread */
    private double currentBestF;

    /** Number of times this thread has been restarted */
    private int restartCount;

    /** Last restart iteration */
    private int lastRestartIteration;

    /** Thread is currently active (not terminated) */
    private volatile boolean active;

    /**
     * Constructor
     * 
     * @param threadId Thread identifier
     */
    public ThreadStats(int threadId) {
        this.threadId = threadId;
        this.totalIterations = 0;
        this.eliteInsertions = 0;
        this.globalBestImprovements = 0;
        this.iterationsSinceLastInsertion = 0;
        this.currentBestF = Double.MAX_VALUE;
        this.restartCount = 0;
        this.lastRestartIteration = 0;
        this.active = true;
    }

    /**
     * Record an iteration
     */
    public synchronized void recordIteration() {
        totalIterations++;
        iterationsSinceLastInsertion++;
    }

    /**
     * Record successful insertion to elite set
     */
    public synchronized void recordEliteInsertion() {
        eliteInsertions++;
        iterationsSinceLastInsertion = 0;
    }

    /**
     * Record new global best improvement
     */
    public synchronized void recordGlobalBestImprovement() {
        globalBestImprovements++;
    }

    /**
     * Update current best objective value
     * 
     * @param bestF New best objective value
     */
    public synchronized void updateBestF(double bestF) {
        this.currentBestF = bestF;
    }

    /**
     * Record a restart
     */
    public synchronized void recordRestart() {
        restartCount++;
        lastRestartIteration = totalIterations;
        iterationsSinceLastInsertion = 0;
    }

    /**
     * Calculate insertion rate (insertions per 1000 iterations)
     * 
     * @return Insertion rate
     */
    public synchronized double getInsertionRate() {
        if (totalIterations == 0)
            return 0.0;
        return (eliteInsertions * 1000.0) / totalIterations;
    }

    /**
     * Calculate gap from global best (percentage)
     * 
     * @param globalBestF Global best objective value
     * @return Gap percentage (0.0 = same, 0.1 = 10% worse)
     */
    public synchronized double getGapFromGlobalBest(double globalBestF) {
        if (globalBestF == 0)
            return 0.0;
        return (currentBestF - globalBestF) / globalBestF;
    }

    /**
     * Check if thread is stagnant
     *
     * Criteria:
     * 1. No insertion to elite set for stagnationThreshold iterations
     * 2. Current best is competitiveThreshold% worse than global best
     *
     * If thread is competitive with global best, it's likely intensifying -> not
     * stagnant
     * If thread is far from global best and not inserting -> stagnant
     *
     * @param globalBestF          Global best objective value
     * @param stagnationThreshold  Iterations without insertion before considering
     *                             stagnant
     * @param competitiveThreshold Gap percentage threshold (e.g., 0.01 = 1%)
     * @return true if thread is stagnant and should be restarted
     */
    public synchronized boolean isStagnant(double globalBestF, int stagnationThreshold, double competitiveThreshold) {
        // Must have run for at least stagnationThreshold iterations
        if (totalIterations < stagnationThreshold) {
            return false;
        }

        // Criterion 1: No contribution to elite set recently
        if (iterationsSinceLastInsertion >= stagnationThreshold) {

            // Criterion 2: Far from global best (not intensifying)
            double gap = getGapFromGlobalBest(globalBestF);

            if (gap > competitiveThreshold) {
                // Stagnant: no insertions + far from global best
                return true;
            }
            // else: Competitive with global best -> likely intensifying -> keep running
        }

        return false;
    }

    // Getters
    public int getThreadId() {
        return threadId;
    }

    public int getTotalIterations() {
        return totalIterations;
    }

    public int getEliteInsertions() {
        return eliteInsertions;
    }

    public int getGlobalBestImprovements() {
        return globalBestImprovements;
    }

    public int getIterationsSinceLastInsertion() {
        return iterationsSinceLastInsertion;
    }

    public double getCurrentBestF() {
        return currentBestF;
    }

    public int getRestartCount() {
        return restartCount;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public String toString() {
        return String.format("Thread-%d[iter=%d, inserts=%d, bestF=%.2f, stagnant=%d, restarts=%d, active=%s]",
                threadId, totalIterations, eliteInsertions, currentBestF,
                iterationsSinceLastInsertion, restartCount, active);
    }
}
