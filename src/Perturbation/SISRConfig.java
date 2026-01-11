package Perturbation;

/**
 * Configuration parameters for SISR (Slack Induction by String Removals)
 *
 * Based on: Christiaens & Vanden Berghe (2020), Transportation Science
 */
public class SISRConfig {

    // String removal parameters
    public double maxStringLength;    // Lmax: Maximum string length (Equation 5)
    public double splitRate;          // Probability of split string removal
    public double splitDepth;         // beta: Split depth parameter (Figure 4)
    public double avgRemoved;         // c_bar: Average number of customers to remove (Equation 6)

    // Insertion parameter
    public double blinkRate;          // gamma: Position skip probability (Algorithm 3, Line 7)

    /**
     * Default constructor with standard parameter values
     */
    public SISRConfig() {
        this.maxStringLength = 10.0;
        this.splitRate = 0.5;
        this.splitDepth = 0.01;
        this.avgRemoved = 10.0;
        this.blinkRate = 0.01;
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

    public double getAvgRemoved() {
        return avgRemoved;
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

    public void setAvgRemoved(double avgRemoved) {
        this.avgRemoved = avgRemoved;
    }

    @Override
    public String toString() {
        return String.format(
            "SISRConfig[maxStringLength=%.1f, splitRate=%.2f, splitDepth=%.2f, avgRemoved=%.1f, blinkRate=%.3f]",
            maxStringLength, splitRate, splitDepth, avgRemoved, blinkRate
        );
    }

    /**
     * toString with parameter source tracking
     */
    public String toString(java.util.HashMap<String, String> sources) {
        return String.format(
            "SISRConfig[maxStringLength=%.1f (%s), splitRate=%.2f (%s), splitDepth=%.2f (%s), avgRemoved=%.1f (%s), blinkRate=%.3f (%s)]",
            maxStringLength, sources.getOrDefault("sisr.maxStringLength", "default"),
            splitRate, sources.getOrDefault("sisr.splitRate", "default"),
            splitDepth, sources.getOrDefault("sisr.splitDepth", "default"),
            avgRemoved, sources.getOrDefault("sisr.avgRemoved", "default"),
            blinkRate, sources.getOrDefault("sisr.blinkRate", "default")
        );
    }

    /**
     * Create a deep copy of this configuration
     */
    public SISRConfig copy() {
        SISRConfig copied = new SISRConfig();
        copied.maxStringLength = this.maxStringLength;
        copied.splitRate = this.splitRate;
        copied.splitDepth = this.splitDepth;
        copied.avgRemoved = this.avgRemoved;
        copied.blinkRate = this.blinkRate;
        return copied;
    }
}