package Perturbation;

/**
 * Insertion heuristics for repair operators in destroy-repair framework
 *
 * Greedy methods (Distance, Cost):
 * - Fast, myopic insertion based on cheapest position
 *
 * Regret methods (Regret2, Regret3, Regret4):
 * - Look-ahead insertion considering alternative positions
 * - Prioritize customers with high "regret" (cost of delaying insertion)
 * - Based on Ropke & Pisinger (2006) ALNS framework
 *
 * Randomized methods (RegretRandom):
 * - Adds noise to regret values for diversification
 */
public enum InsertionHeuristic
{
	Distance(1),      // Greedy: Cheapest distance increase
	Cost(2),          // Greedy: Cheapest cost increase (same as Distance for CVRP)
	Regret2(3),       // Regret-2: (2nd best - best)
	Regret3(4),       // Regret-3: (2nd best - best) + (3rd best - best)
	Regret4(5),       // Regret-4: Sum of regrets for top 4 positions
	RegretRandom(6);  // Regret-2 with random noise for diversification

	final int heuristic;

	InsertionHeuristic(int heuristic)
	{
		this.heuristic=heuristic;
	}

	/**
	 * Check if this is a regret-based heuristic
	 * @return true if this heuristic uses regret calculation
	 */
	public boolean isRegret() {
		return this == Regret2 || this == Regret3 || this == Regret4 || this == RegretRandom;
	}

	/**
	 * Get K value for regret calculation (number of positions to consider)
	 * @return K value for this heuristic
	 */
	public int getK() {
		switch(this) {
			case Regret2:
			case RegretRandom:
				return 2;
			case Regret3:
				return 3;
			case Regret4:
				return 4;
			default:
				return 1; // Greedy only needs best position
		}
	}
}
