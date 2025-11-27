package Perturbation;

/**
 * Configuration parameters for SISR (Slack Induction by String Removals)
 *
 * Based on: Christiaens & Vanden Berghe (2020), Transportation Science
 *
 * Note: avgRemoved (c_bar) is calculated as: avgRemovedPercent * totalNodes
 * Note: adjacencyLimit uses existing KNN structure (knnLimit from Config)
 */
public class SISRConfig {

    // String removal parameters
    public double maxStringLength;    // Lmax: Maximum string length (Equation 5)
    public double splitRate;          // Probability of split string removal
    public double splitDepth;         // beta: Split depth parameter (Figure 4)
    public double avgRemovedPercent;  // Percentage of nodes to remove (for calculating c_bar in Equation 6)

    // Insertion parameter
    public double blinkRate;          // gamma: Position skip probability (Algorithm 3, Line 7)

    /**
     * Default constructor with standard parameter values
     */
    public SISRConfig() {
        this.maxStringLength = 15.0;    // Default max string length
        this.splitRate = 0.5;            // 50% chance of split removal
        this.splitDepth = 0.3;           // beta = 0.3
        this.avgRemovedPercent = 0.06;   // Default 6% of nodes removed
        this.blinkRate = 0.01;           // gamma = 0.01 (1% blink rate)
    }

    // Getters
    public double getMaxStringLength() {
        return maxStringLength;
    }

    public double getSplitRate() {
        return splitRate;
    }

    public double getSplitDepth() {
        return splitDepth;
    }

    public double getBlinkRate() {
        return blinkRate;
    }

    public double getAvgRemovedPercent() {
        return avgRemovedPercent;
    }

    // Setters
    public void setMaxStringLength(double maxStringLength) {
        this.maxStringLength = maxStringLength;
    }

    public void setSplitRate(double splitRate) {
        this.splitRate = splitRate;
    }

    public void setSplitDepth(double splitDepth) {
        this.splitDepth = splitDepth;
    }

    public void setBlinkRate(double blinkRate) {
        this.blinkRate = blinkRate;
    }

    public void setAvgRemovedPercent(double avgRemovedPercent) {
        this.avgRemovedPercent = avgRemovedPercent;
    }

    @Override
    public String toString() {
        return String.format(
            "SISRConfig[maxStringLength=%.1f, splitRate=%.2f, splitDepth=%.2f, avgRemovedPercent=%.2f, blinkRate=%.3f]",
            maxStringLength, splitRate, splitDepth, avgRemovedPercent, blinkRate
        );
    }

    /**
     * toString with parameter source tracking
     */
    public String toString(java.util.HashMap<String, String> sources) {
        return String.format(
            "SISRConfig[maxStringLength=%.1f (%s), splitRate=%.2f (%s), splitDepth=%.2f (%s), avgRemovedPercent=%.2f (%s), blinkRate=%.3f (%s)]",
            maxStringLength, sources.getOrDefault("sisr.maxStringLength", "default"),
            splitRate, sources.getOrDefault("sisr.splitRate", "default"),
            splitDepth, sources.getOrDefault("sisr.splitDepth", "default"),
            avgRemovedPercent, sources.getOrDefault("sisr.avgRemovedPercent", "default"),
            blinkRate, sources.getOrDefault("sisr.blinkRate", "default")
        );
    }
}