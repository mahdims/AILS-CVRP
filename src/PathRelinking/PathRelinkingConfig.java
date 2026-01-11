package PathRelinking;

/**
 * Configuration for Path Relinking algorithm
 * Controls when and how PR runs in parallel with AILS
 *
 * Path Relinking is an intensification strategy that explores trajectories
 * connecting elite solutions by iteratively transforming one solution into another.
 */
public class PathRelinkingConfig {

    // Enable/disable PR
    private boolean enabled;

    // Delay before starting PR (wait for elite set to populate)
    private int startIterationDelay;

    // How often to run PR (every N AILS iterations)
    private int prFrequency;

    // Maximum iterations for PR thread
    private int prMaxIterations;

    // Time limit for PR thread (seconds)
    private double prTimeLimit;

    // Minimum elite set size before PR can start
    private int minEliteSizeForPR;

    /**
     * Default constructor with conservative settings
     */
    public PathRelinkingConfig() {
        this.enabled = true;
        this.startIterationDelay = 100;
        this.prFrequency = 50;
        this.prMaxIterations = 1000;
        this.prTimeLimit = 60.0;
        this.minEliteSizeForPR = 2;
    }

    /**
     * Constructor with custom parameters
     */
    public PathRelinkingConfig(boolean enabled, int startIterationDelay,
                              int prFrequency, int prMaxIterations,
                              double prTimeLimit, int minEliteSizeForPR) {
        this.enabled = enabled;
        this.startIterationDelay = startIterationDelay;
        this.prFrequency = prFrequency;
        this.prMaxIterations = prMaxIterations;
        this.prTimeLimit = prTimeLimit;
        this.minEliteSizeForPR = minEliteSizeForPR;
    }

    // Getters and setters
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getStartIterationDelay() {
        return startIterationDelay;
    }

    public void setStartIterationDelay(int delay) {
        this.startIterationDelay = delay;
    }

    public int getPrFrequency() {
        return prFrequency;
    }

    public void setPrFrequency(int frequency) {
        this.prFrequency = frequency;
    }

    public int getPrMaxIterations() {
        return prMaxIterations;
    }

    public void setPrMaxIterations(int maxIter) {
        this.prMaxIterations = maxIter;
    }

    public double getPrTimeLimit() {
        return prTimeLimit;
    }

    public void setPrTimeLimit(double timeLimit) {
        this.prTimeLimit = timeLimit;
    }

    public int getMinEliteSizeForPR() {
        return minEliteSizeForPR;
    }

    public void setMinEliteSizeForPR(int minSize) {
        this.minEliteSizeForPR = minSize;
    }

    @Override
    public String toString() {
        return "PathRelinkingConfig{" +
                "enabled=" + enabled +
                ", startIterationDelay=" + startIterationDelay +
                ", prFrequency=" + prFrequency +
                ", prMaxIterations=" + prMaxIterations +
                ", prTimeLimit=" + prTimeLimit +
                ", minEliteSizeForPR=" + minEliteSizeForPR +
                '}';
    }

    /**
     * Create a conservative configuration (safer, slower)
     */
    public static PathRelinkingConfig conservative() {
        return new PathRelinkingConfig(
            true,    // enabled
            200,     // startIterationDelay
            100,     // prFrequency
            500,     // prMaxIterations
            30.0,    // prTimeLimit
            3        // minEliteSizeForPR
        );
    }

    /**
     * Create a balanced configuration (default)
     */
    public static PathRelinkingConfig balanced() {
        return new PathRelinkingConfig();
    }

    /**
     * Create an aggressive configuration (faster, more intensive)
     */
    public static PathRelinkingConfig aggressive() {
        return new PathRelinkingConfig(
            true,    // enabled
            50,      // startIterationDelay
            20,      // prFrequency
            5000,    // prMaxIterations
            120.0,   // prTimeLimit
            2        // minEliteSizeForPR
        );
    }

    /**
     * Create a disabled configuration (for baseline comparison)
     */
    public static PathRelinkingConfig disabled() {
        PathRelinkingConfig config = new PathRelinkingConfig();
        config.setEnabled(false);
        return config;
    }

    /**
     * Create a deep copy of this configuration
     */
    public PathRelinkingConfig copy() {
        return new PathRelinkingConfig(
            this.enabled,
            this.startIterationDelay,
            this.prFrequency,
            this.prMaxIterations,
            this.prTimeLimit,
            this.minEliteSizeForPR
        );
    }
}
