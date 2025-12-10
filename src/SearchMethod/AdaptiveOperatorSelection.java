package SearchMethod;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Adaptive Operator Selection (AOS) for Perturbation Operators
 *
 * Implements a mini-segment approach based on Ropke & Pisinger (2006)
 * "An Adaptive Large Neighborhood Search Heuristic for the Pickup and Delivery
 * Problem with Time Windows"
 *
 * KEY DESIGN PRINCIPLES:
 * ----------------------
 * 1. **Segmented Learning:** Accumulate scores over N iterations, then update
 * probabilities
 * 2. **Hard Reset:** After each segment, reset scores to 0 (prevents drift)
 * 3. **Floor Constraint:** All operators maintain minimum 5% probability
 * (prevents extinction)
 * 4. **Roulette Wheel:** Probabilistic selection based on learned weights
 *
 * SCORING SYSTEM (from Ropke & Pisinger):
 * ----------------------------------------
 * - delta_1 = 33 points: Operator led to new global best solution
 * - delta_2 = 9 points: Operator led to improved solution
 * - delta_3 = 13 points: Operator led to accepted solution
 * - delta_4 = 0 points: Operator led to rejected solution
 *
 * PROBABILITY UPDATE (at segment boundaries):
 * -------------------------------------------
 * pi_i^(k+1) = pi_i^k * (1-r) + r * (theta_i^k / rho_i^k) / max_avg_score
 *
 * Where:
 * - pi_i^k = probability of operator i in segment k
 * - r = reaction factor (how much weight to give new evidence)
 * - theta_i^k = total score accumulated by operator i in segment k
 * - rho_i^k = number of times operator i was used in segment k
 * - max_avg_score = normalization factor (highest average score)
 *
 * COMPARISON TO ALTERNATIVES:
 * ---------------------------
 * 1. Per-iteration EMA: Too reactive, unstable (thrashing)
 * 2. Large segments (100 iter): Too slow to adapt (lag)
 * 3. Mini-segments (20 iter): Balance of stability and responsiveness
 */
public class AdaptiveOperatorSelection {

	// Configuration parameters (from Config)
	private final int SEGMENT_LENGTH;
	private final double REACTION_FACTOR;
	private final double MIN_PROBABILITY;
	private final double SCORE_GLOBAL_BEST;
	private final double SCORE_IMPROVED;
	private final double SCORE_ACCEPTED;
	private final double SCORE_REJECTED;

	// Operator probabilities (updated at segment boundaries)
	private Map<String, Double> probabilities = new HashMap<>();

	// Score accumulators (reset at segment boundaries)
	private Map<String, Double> scores = new HashMap<>(); // theta_i: total score
	private Map<String, Integer> uses = new HashMap<>(); // rho_i: total uses

	// Iteration tracking
	private int iterationCounter = 0;

	// Random number generator
	private Random random;

	/**
	 * Constructor
	 *
	 * @param operators Array of operator names (e.g., ["Sequential", "Concentric",
	 *                  "SISR", ...])
	 * @param random    Random number generator (shared with main algorithm)
	 * @param config    Configuration containing AOS parameters
	 */
	public AdaptiveOperatorSelection(String[] operators, Random random, Config config) {
		this.random = random;
		this.SEGMENT_LENGTH = config.getAosSegmentLength();
		this.REACTION_FACTOR = config.getAosReactionFactor();
		this.MIN_PROBABILITY = config.getAosMinProbability();
		this.SCORE_GLOBAL_BEST = config.getAosScoreGlobalBest();
		this.SCORE_IMPROVED = config.getAosScoreImproved();
		this.SCORE_ACCEPTED = config.getAosScoreAccepted();
		this.SCORE_REJECTED = config.getAosScoreRejected();

		// Initialize equal probabilities for all operators
		double initialProb = 1.0 / operators.length;
		for (String op : operators) {
			probabilities.put(op, initialProb);
			scores.put(op, 0.0);
			uses.put(op, 0);
		}
	}

	/**
	 * Select an operator using Roulette Wheel selection
	 *
	 * ALGORITHM:
	 * 1. Generate random number r \in [0, 1)
	 * 2. Accumulate probabilities until r is exceeded
	 * 3. Return the operator that pushed cumulative over r
	 *
	 * Example (3 operators):
	 * - Sequential: 0.20 (0.00 to 0.20)
	 * - Concentric: 0.35 (0.20 to 0.55)
	 * - SISR: 0.45 (0.55 to 1.00)
	 *
	 * If r = 0.37 -> selects Concentric
	 *
	 * @return Name of selected operator
	 */
	public String selectOperator() {
		double r = random.nextDouble();
		double cumulative = 0.0;

		for (Map.Entry<String, Double> entry : probabilities.entrySet()) {
			cumulative += entry.getValue();
			if (r <= cumulative) {
				return entry.getKey();
			}
		}

		// Fallback (should rarely happen due to float precision)
		// Return first operator if cumulative never exceeds r
		return probabilities.keySet().iterator().next();
	}

	/**
	 * Record outcome of operator application
	 *
	 * WORKFLOW:
	 * 1. Map outcome type to score
	 * 2. Accumulate score for this operator
	 * 3. Increment use counter
	 * 4. Check if segment boundary reached
	 * 5. If yes: update probabilities and reset scores
	 *
	 * @param operator    Name of operator that was used
	 * @param outcomeType Type of outcome:
	 *                    1 = New global best
	 *                    2 = Improved solution
	 *                    3 = Accepted solution
	 *                    0 = Rejected solution
	 */
	public void recordOutcome(String operator, int outcomeType) {
		// Map outcome type to reward score
		double reward;
		switch (outcomeType) {
			case 1:
				reward = SCORE_GLOBAL_BEST;
				break;
			case 2:
				reward = SCORE_IMPROVED;
				break;
			case 3:
				reward = SCORE_ACCEPTED;
				break;
			default:
				reward = SCORE_REJECTED;
				break;
		}

		// Accumulate score within segment
		scores.put(operator, scores.get(operator) + reward);
		uses.put(operator, uses.get(operator) + 1);

		iterationCounter++;

		// Check if segment boundary reached
		if (iterationCounter % SEGMENT_LENGTH == 0) {
			updateProbabilities();
			resetScores();
		}
	}

	/**
	 * Update operator probabilities at segment boundary
	 *
	 * ALGORITHM (from Ropke & Pisinger 2006):
	 * 1. Calculate average score per use for each operator: theta_i / rho_i
	 * 2. Find maximum average score (for normalization)
	 * 3. For each operator:
	 * pi_new = pi_old * (1-r) + r * (avg_score / max_avg_score)
	 * 4. Normalize and apply floor constraint
	 *
	 * NORMALIZATION RATIONALE:
	 * - Ensures probabilities sum to 1.0
	 * - Prevents operators from being completely excluded
	 * - Allows differentiation while maintaining diversity
	 */
	private void updateProbabilities() {
		// Calculate average score per use for each operator
		Map<String, Double> avgScores = new HashMap<>();
		double maxAvgScore = 0.0;

		for (String op : probabilities.keySet()) {
			double avgScore = 0.0;
			if (uses.get(op) > 0) {
				avgScore = scores.get(op) / uses.get(op);
			}
			avgScores.put(op, avgScore);
			if (avgScore > maxAvgScore) {
				maxAvgScore = avgScore;
			}
		}

		// Update probabilities using Ropke's formula
		// pi_new = pi_old * (1-r) + r * (normalized_avg_score)
		if (maxAvgScore > 0) {
			for (String op : probabilities.keySet()) {
				double oldProb = probabilities.get(op);
				double normalizedScore = avgScores.get(op) / maxAvgScore;
				double newProb = oldProb * (1.0 - REACTION_FACTOR) +
						REACTION_FACTOR * normalizedScore;
				probabilities.put(op, newProb);
			}
		}

		// Normalize and apply floor constraint
		applyFloorConstraint();
	}

	/**
	 * Apply floor constraint to probabilities
	 *
	 * RATIONALE:
	 * Prevents operator extinction. Even if an operator performs poorly,
	 * it maintains MIN_PROBABILITY chance of being selected. This is crucial
	 * because operator effectiveness can change during search (e.g., SISR
	 * might be ineffective early but powerful later).
	 *
	 * ALGORITHM:
	 * 1. Reserve MIN_PROBABILITY for each operator
	 * 2. Distribute remaining probability mass proportionally
	 * 3. Result: probabilities \in [MIN_PROB, 1 - (N-1)*MIN_PROB]
	 *
	 * Example (4 operators, MIN_PROB=0.05):
	 * - Reserved: 4 * 0.05 = 0.20
	 * - Available: 1.0 - 0.20 = 0.80
	 * - Each gets: 0.05 + (proportion of 0.80)
	 */
	private void applyFloorConstraint() {
		int n = probabilities.size();

		// Calculate total probability
		double totalProb = probabilities.values().stream()
				.mapToDouble(Double::doubleValue).sum();

		// Avoid division by zero
		if (totalProb == 0) {
			// All operators failed equally - reset to uniform
			double uniformProb = 1.0 / n;
			for (String op : probabilities.keySet()) {
				probabilities.put(op, uniformProb);
			}
			return;
		}

		// Available probability mass after reserving floors
		double availableMass = 1.0 - (n * MIN_PROBABILITY);

		// Distribute available mass proportionally
		for (String op : probabilities.keySet()) {
			double rawProb = probabilities.get(op) / totalProb;
			// Scale to available mass and add floor
			double finalProb = MIN_PROBABILITY + (rawProb * availableMass);
			probabilities.put(op, finalProb);
		}
	}

	/**
	 * Hard reset of scores at segment boundary
	 *
	 * RATIONALE:
	 * Prevents score drift and ensures each segment starts fresh.
	 * This is critical for stability - without reset, scores could
	 * accumulate indefinitely and old evidence would dominate.
	 *
	 * Ropke & Pisinger: "At the end of each segment, scores are reset to zero."
	 */
	public void resetScores() {
		for (String op : scores.keySet()) {
			scores.put(op, 0.0);
			uses.put(op, 0);
		}
	}

	/**
	 * Print current operator probabilities (for monitoring)
	 *
	 * @param iteration Current iteration number
	 */
	public void printStats(int iteration) {
		System.out.printf("[AOS] iter:%d | Probabilities: ", iteration);
		probabilities.forEach((op, prob) -> System.out.printf("%s:%.1f%% ", op, prob * 100));
		System.out.println();
	}

	/**
	 * Get current probabilities (for logging/analysis)
	 *
	 * @return Map of operator names to probabilities
	 */
	public Map<String, Double> getProbabilities() {
		return new HashMap<>(probabilities);
	}

	/**
	 * Get current iteration counter
	 *
	 * @return Current iteration count
	 */
	public int getIterationCounter() {
		return iterationCounter;
	}
}
