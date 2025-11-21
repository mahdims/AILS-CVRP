package Perturbation;

/**
 * Configuration parameters for SISR (Slack Induction by String Removals)
 *
 * Based on: Christiaens & Vanden Berghe (2020), Transportation Science
 *
 * Note: avgRemoved parameter uses omega from Perturbation base class
 * Note: adjacencyLimit uses existing KNN structure (knnLimit from Config)
 */
public class SISRConfig {

    // String removal parameters
    public double maxStringLength;    // Lmax: Maximum string length (Equation 5)
    public double splitRate;          // Probability of split string removal
    public double splitDepth;         // β: Split depth parameter (Figure 4)

    // Insertion parameter
    public double blinkRate;          // γ: Position skip probability (Algorithm 3, Line 7)

    /**
     * Default constructor with standard parameter values
     */
    public SISRConfig() {
        this.maxStringLength = 15.0;    // Default max string length
        this.splitRate = 0.5;            // 50% chance of split removal
        this.splitDepth = 0.3;           // β = 0.3
        this.blinkRate = 0.01;           // γ = 0.01 (1% blink rate)
    }

    @Override
    public String toString() {
        return String.format(
            "SISRConfig[maxStringLength=%.1f, splitRate=%.2f, splitDepth=%.2f, blinkRate=%.3f]",
            maxStringLength, splitRate, splitDepth, blinkRate
        );
    }
}
