package SearchMethod;

/**
 * Configuration for Multi-Start AILS
 *
 * Controls parallel worker threads that explore from diverse elite solutions.
 *
 * Architecture:
 * - Main thread (Thread-1): Protected, never restarts, receives PR notifications
 * - Worker threads (Thread-2...N+1): Can restart dynamically when stagnant
 * - All threads share same elite set and global time limit
 *
 * Example configurations:
 * - Conservative (4 threads): 1 main + 2 workers + 1 PR
 * - Moderate (17 threads): 1 main + 15 workers + 1 PR
 * - Aggressive (50 threads): 1 main + 48 workers + 1 PR (for 2x Intel 6972P)
 */
public class MultiStartConfig {

    /** Enable/disable multi-start AILS */
    private boolean enabled;

    /** Number of worker AILS threads (excluding main thread) */
    private int numWorkerThreads;

    /** Minimum elite set size before launching workers */
    private int minEliteSizeForWorkers;

    /** Iterations without elite insertion before considering worker stagnant */
    private int stagnationThreshold;

    /** Gap percentage from global best to consider worker competitive (0.0-1.0) */
    private double competitiveThreshold;

    /** Notify main thread when workers find better solutions */
    private boolean notifyMainThread;

    /**
     * Constructor with recommended defaults
     */
    public MultiStartConfig() {
        this.enabled = false;
        this.numWorkerThreads = 2;
        this.minEliteSizeForWorkers = 3;
        this.stagnationThreshold = 2000;
        this.competitiveThreshold = 0.02;  // 2%
        this.notifyMainThread = true;
    }

    // Getters and setters

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getNumWorkerThreads() {
        return numWorkerThreads;
    }

    public void setNumWorkerThreads(int n) {
        if (n < 0) throw new IllegalArgumentException("numWorkerThreads must be >= 0");
        this.numWorkerThreads = n;
    }

    public int getMinEliteSizeForWorkers() {
        return minEliteSizeForWorkers;
    }

    public void setMinEliteSizeForWorkers(int n) {
        if (n < 1) throw new IllegalArgumentException("minEliteSizeForWorkers must be >= 1");
        this.minEliteSizeForWorkers = n;
    }

    public int getStagnationThreshold() {
        return stagnationThreshold;
    }

    public void setStagnationThreshold(int t) {
        if (t < 1) throw new IllegalArgumentException("stagnationThreshold must be >= 1");
        this.stagnationThreshold = t;
    }

    public double getCompetitiveThreshold() {
        return competitiveThreshold;
    }

    public void setCompetitiveThreshold(double t) {
        if (t < 0 || t > 1) throw new IllegalArgumentException("competitiveThreshold must be in [0,1]");
        this.competitiveThreshold = t;
    }

    public boolean isNotifyMainThread() {
        return notifyMainThread;
    }

    public void setNotifyMainThread(boolean notify) {
        this.notifyMainThread = notify;
    }

    @Override
    public String toString() {
        if (!enabled) {
            return "MultiStart[disabled]";
        }
        return String.format("MultiStart[workers=%d, minElite=%d, stagnation=%d, competitive=%.1f%%, notify=%s]",
            numWorkerThreads, minEliteSizeForWorkers,
            stagnationThreshold, competitiveThreshold * 100, notifyMainThread);
    }

    /**
     * Create a deep copy of this configuration
     */
    public MultiStartConfig copy() {
        MultiStartConfig copied = new MultiStartConfig();
        copied.enabled = this.enabled;
        copied.numWorkerThreads = this.numWorkerThreads;
        copied.minEliteSizeForWorkers = this.minEliteSizeForWorkers;
        copied.stagnationThreshold = this.stagnationThreshold;
        copied.competitiveThreshold = this.competitiveThreshold;
        copied.notifyMainThread = this.notifyMainThread;
        return copied;
    }
}
