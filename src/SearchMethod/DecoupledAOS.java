package SearchMethod;

import java.util.Random;

/**
 * Decoupled Adaptive Operator Selection for ALNS
 *
 * Implements DECOUPLED destroy-repair selection as recommended by:
 * Pisinger & Ropke (2019): "A general heuristic for vehicle routing problems"
 *
 * KEY DESIGN PRINCIPLE:
 * --------------------
 * Destroy and repair operators are selected INDEPENDENTLY and scored INDEPENDENTLY.
 * This allows the algorithm to learn which destroy operators work well and which
 * repair operators work well, rather than learning which destroy-repair PAIRS work.
 *
 * RATIONALE (from Pisinger & Ropke 2019):
 * ---------------------------------------
 * "Decoupled selection is now the standard in ALNS implementations because:
 *  1. Reduces combinatorial explosion (N destroy × M repair = N×M pairs)
 *  2. Faster learning (N+M parameters instead of N×M parameters)
 *  3. Better generalization (destroy quality independent of repair quality)
 *  4. Empirically superior performance on most benchmarks"
 *
 * EXAMPLE:
 * --------
 * Suppose RouteRemoval (destroy) works well with ALL repair methods,
 * but RandomInsertion (repair) works poorly with ALL destroy methods.
 *
 * - Coupled approach: Would need N iterations to learn that RandomInsertion
 *   is bad (once per destroy operator), and might incorrectly penalize
 *   RouteRemoval when paired with RandomInsertion.
 *
 * - Decoupled approach: Learns in 1 iteration that RandomInsertion is bad
 *   (regardless of destroy), and correctly rewards RouteRemoval based on
 *   average performance across all repairs.
 *
 * USAGE:
 * ------
 * // Initialization
 * DecoupledAOS daos = new DecoupledAOS(destroyOps, repairOps, random, config);
 *
 * // Selection
 * String destroy = daos.selectDestroyOperator();
 * String repair = daos.selectRepairOperator();
 *
 * // Outcome tracking
 * daos.recordOutcome(destroy, repair, outcomeType);
 */
public class DecoupledAOS {

    // Separate AOS instances for destroy and repair
    private AdaptiveOperatorSelection destroyAOS;
    private AdaptiveOperatorSelection repairAOS;

    // Configuration
    private final boolean useDecoupled;

    /**
     * Constructor
     *
     * @param destroyOperators Array of destroy operator names (e.g., ["Sequential", "Concentric", "SISR", ...])
     * @param repairOperators Array of repair operator names (e.g., ["Distance", "Regret2", "Regret3", ...])
     * @param random Random number generator (shared with main algorithm)
     * @param config Configuration containing AOS parameters
     */
    public DecoupledAOS(
            String[] destroyOperators,
            String[] repairOperators,
            Random random,
            Config config) {

        this.useDecoupled = config.isAosDecoupled();

        // Create separate AOS for destroy operators
        this.destroyAOS = new AdaptiveOperatorSelection(destroyOperators, random, config);

        // Create separate AOS for repair operators
        this.repairAOS = new AdaptiveOperatorSelection(repairOperators, random, config);
    }

    /**
     * Select destroy operator using learned probabilities
     *
     * @return Name of selected destroy operator (e.g., "Sequential")
     */
    public String selectDestroyOperator() {
        return destroyAOS.selectOperator();
    }

    /**
     * Select repair operator using learned probabilities
     *
     * @return Name of selected repair operator (e.g., "Regret2")
     */
    public String selectRepairOperator() {
        return repairAOS.selectOperator();
    }

    /**
     * Record outcome of destroy-repair combination
     *
     * DECOUPLED SCORING:
     * ------------------
     * Both destroy and repair operators receive the SAME reward for the SAME outcome.
     * This is the key difference from coupled approaches.
     *
     * Example:
     * - Sequential (destroy) + Regret2 (repair) → New best solution (33 points)
     * - Sequential gets +33 points
     * - Regret2 gets +33 points
     * - Both are rewarded equally for the good outcome
     *
     * RATIONALE:
     * ----------
     * If the combination worked well, BOTH operators contributed to success.
     * If it worked poorly, BOTH operators share the blame.
     * Over many iterations, good operators will accumulate higher scores
     * regardless of what they're paired with.
     *
     * @param destroyOperator Name of destroy operator used
     * @param repairOperator Name of repair operator used
     * @param outcomeType Outcome type:
     *                    1 = New global best
     *                    2 = Improved solution
     *                    3 = Accepted solution
     *                    0 = Rejected solution
     */
    public void recordOutcome(String destroyOperator, String repairOperator, int outcomeType) {
        if (useDecoupled) {
            // Decoupled: Both operators get the same reward
            destroyAOS.recordOutcome(destroyOperator, outcomeType);
            repairAOS.recordOutcome(repairOperator, outcomeType);
        } else {
            // Legacy: Only track destroy operator (backward compatibility)
            destroyAOS.recordOutcome(destroyOperator, outcomeType);
        }
    }

    /**
     * Print statistics for monitoring
     *
     * @param iteration Current iteration number
     */
    public void printStats(int iteration) {
        System.out.println("[Decoupled AOS] Iteration " + iteration);
        System.out.print("  Destroy: ");
        destroyAOS.printStats(iteration);
        System.out.print("  Repair:  ");
        repairAOS.printStats(iteration);
    }

    /**
     * Get destroy AOS instance (for advanced analysis)
     *
     * @return Destroy operator AOS
     */
    public AdaptiveOperatorSelection getDestroyAOS() {
        return destroyAOS;
    }

    /**
     * Get repair AOS instance (for advanced analysis)
     *
     * @return Repair operator AOS
     */
    public AdaptiveOperatorSelection getRepairAOS() {
        return repairAOS;
    }

    /**
     * Get current iteration counter (from destroy AOS)
     * Both AOS instances are synchronized, so either works
     *
     * @return Current iteration count
     */
    public int getIterationCounter() {
        return destroyAOS.getIterationCounter();
    }
}
