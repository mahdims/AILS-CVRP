package Perturbation;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

import Data.Instance;
import DiversityControl.OmegaAdjustment;
import Improvement.IntraLocalSearch;
import SearchMethod.Config;
import Solution.Node;
import Solution.Solution;

/**
 * Critical Removal Perturbation Operator
 *
 * History-based operator that targets customers frequently removed during search.
 * These "unstable" customers are on the boundary between multiple good placements.
 * Removing all unstable customers at once forces global rearrangement.
 *
 * MOTIVATION:
 * -----------
 * When a customer is repeatedly removed and reinserted by different operators,
 * it indicates the customer is "unstable" - the search cannot definitively decide
 * where it should be placed. This happens when:
 * - Customer is equidistant from multiple service regions
 * - Customer demand creates capacity trade-offs between routes
 * - Customer is on the boundary of optimal sectoring
 *
 * By removing ALL unstable customers simultaneously, this operator forces the
 * algorithm to rethink the entire structure rather than making incremental changes.
 *
 * MECHANISM:
 * ----------
 * 1. Track removal count for each customer across all operator applications
 * 2. Apply periodic decay (95% retention every 1000 iterations) to:
 *    - Prevent unbounded growth
 *    - Give more weight to recent patterns
 *    - Adapt to changing search phases
 * 3. Identify customers with removal counts > 1.5 * average
 * 4. Remove omega highest-frequency customers
 * 5. Do NOT record own removals (prevents self-reinforcement)
 *
 * COLD START PROTECTION:
 * ----------------------
 * Requires minimum 500 iterations before activating history-based selection.
 * Before this threshold, falls back to random removal to build initial statistics.
 *
 * FALLBACK MECHANISM:
 * -------------------
 * If insufficient high-frequency customers exist (< omega), uses relative ranking:
 * - Sort all customers by removal count (descending)
 * - Take top omega customers
 * This ensures the operator is always productive even in early search phases.
 *
 * SELF-REINFORCEMENT PREVENTION:
 * ------------------------------
 * Overrides recordCandidates() to be empty, ensuring CriticalRemoval's own
 * removals are NOT tracked. This prevents a positive feedback loop where
 * frequently removed customers become even more likely to be removed.
 *
 * Key features:
 * - Tracks removal frequency per customer
 * - Applies periodic decay (0.95 every 1000 iterations)
 * - Cold start protection (requires 500 iterations minimum)
 * - Falls back to ranking if insufficient high-frequency customers
 * - Prevents self-reinforcement by not recording own removals
 */
public class CriticalRemoval extends Perturbation
{
	// Array to track removal counts for each customer
	// Maintained by AILSII across all operator invocations
	private int[] removalCounts;

	/**
	 * Constructor for CriticalRemoval operator.
	 *
	 * @param instance Problem instance
	 * @param config Configuration parameters
	 * @param omegaSetup Omega adjustment setup for adaptive perturbation strength
	 * @param intraLocalSearch Intra-route local search operator
	 * @param ailsInstance Reference to AILSII for accessing removal tracking
	 */
	public CriticalRemoval(Instance instance, Config config,
		HashMap<String, OmegaAdjustment> omegaSetup,
		IntraLocalSearch intraLocalSearch,
		SearchMethod.AILSII ailsInstance)
	{
		super(instance, config, omegaSetup, intraLocalSearch, ailsInstance);
		this.perturbationType = PerturbationType.CriticalRemoval;

		// Get reference to shared removal counts array from AILSII
		this.removalCounts = ailsInstance.getCustomerRemovalCounts();
	}

	/**
	 * Apply CriticalRemoval perturbation to solution.
	 *
	 * ALGORITHM:
	 * 1. Check if sufficient history exists (>= 500 iterations)
	 * 2. Calculate average removal count across current solution customers
	 * 3. Set threshold = 1.5 * average (identifies high-frequency customers)
	 * 4. Collect customers above threshold
	 * 5. If sufficient high-frequency customers exist:
	 *    - Randomly select omega from high-frequency set
	 * 6. Otherwise (insufficient high-frequency):
	 *    - Rank all customers by removal count
	 *    - Select top omega customers
	 * 7. Remove selected customers
	 * 8. Reinsert using inherited insertion heuristic
	 *
	 * @param s Solution to perturb
	 */
	@Override
	public void applyPerturbation(Solution s)
	{
		setSolution(s);

		// ========== COLD START PROTECTION ==========
		// Require minimum 500 iterations to build meaningful statistics
		if (ailsInstance.getIterator() < 500) {
			// Insufficient history - use random removal

			// Diagnostic logging (every 100 iterations)
			if (ailsInstance.getIterator() % 100 == 0) {
				System.out.printf("[CriticalRemoval] iter:%d | mode:cold_start_fallback%n",
					ailsInstance.getIterator());
			}

			removeRandomCustomers();
			setOrder();
			addCandidates();
			assignSolution(s);
			return;
		}

		// ========== CALCULATE STATISTICS ==========
		// Compute average removal count for customers in current solution
		double sum = 0;
		int count = 0;
		for (int i = 0; i < size; i++) {
			if (solution[i].nodeBelong) {
				sum += removalCounts[solution[i].name];
				count++;
			}
		}

		// Safety check: if no customers in solution, fall back to random
		if (count == 0) {
			removeRandomCustomers();
			setOrder();
			addCandidates();
			assignSolution(s);
			return;
		}

		double avgCount = sum / count;
		// Threshold = 1.5 * average (identifies customers significantly above average)
		double threshold = avgCount * 1.5;

		// ========== COLLECT HIGH-FREQUENCY CUSTOMERS ==========
		// Find customers with removal count above threshold
		List<Node> highFreqCustomers = new ArrayList<>();
		for (int i = 0; i < size; i++) {
			if (solution[i].nodeBelong &&
				removalCounts[solution[i].name] > threshold) {
				highFreqCustomers.add(solution[i]);
			}
		}

		// ========== REMOVAL STRATEGY ==========
		if (highFreqCustomers.size() >= (int)omega) {
			// CASE 1: Sufficient high-frequency customers

			// Diagnostic logging (every 500 iterations)
			if (ailsInstance.getIterator() % 500 == 0) {
				System.out.printf("[CriticalRemoval] iter:%d | mode:threshold avgCount:%.1f threshold:%.1f highFreqCandidates:%d omega:%d%n",
					ailsInstance.getIterator(), avgCount, threshold, highFreqCustomers.size(), (int)omega);
			}

			// Randomly select omega from high-frequency set to avoid determinism
			Collections.shuffle(highFreqCustomers, rand);
			for (int i = 0; i < (int)omega; i++) {
				Node node = highFreqCustomers.get(i);
				candidates[countCandidates++] = node;

				// Store old position for potential restoration
				node.prevOld = node.prev;
				node.nextOld = node.next;

				// Remove from current route
				f += node.route.remove(node);
			}
		} else {
			// CASE 2: Insufficient high-frequency customers
			// Use relative ranking to ensure omega removals

			// Diagnostic logging (every 500 iterations)
			if (ailsInstance.getIterator() % 500 == 0) {
				System.out.printf("[CriticalRemoval] iter:%d | mode:ranking avgCount:%.1f threshold:%.1f highFreqCandidates:%d (< omega:%d)%n",
					ailsInstance.getIterator(), avgCount, threshold, highFreqCustomers.size(), (int)omega);
			}

			// Collect all customers from current solution
			List<Node> allCustomers = new ArrayList<>();
			for (int i = 0; i < size; i++) {
				if (solution[i].nodeBelong) {
					allCustomers.add(solution[i]);
				}
			}

			// Sort by removal count (descending order - highest first)
			allCustomers.sort((a, b) ->
				removalCounts[b.name] - removalCounts[a.name]);

			// Take top omega customers by removal count
			for (int i = 0; i < Math.min((int)omega, allCustomers.size()); i++) {
				Node node = allCustomers.get(i);
				candidates[countCandidates++] = node;

				// Store old position for potential restoration
				node.prevOld = node.prev;
				node.nextOld = node.next;

				// Remove from current route
				f += node.route.remove(node);
			}
		}

		// ========== REINSERTION ==========
		// Randomize insertion order to avoid bias
		setOrder();

		// Reinsert removed customers using inherited insertion heuristic
		// (Distance-based or Cost-based, selected by setSolution)
		addCandidates();

		// Update solution with new routes and objective value
		assignSolution(s);
	}

	/**
	 * Fallback method: remove random customers.
	 * Used when insufficient history exists or in edge cases.
	 *
	 * Ensures the operator is always productive even without historical data.
	 */
	private void removeRandomCustomers() {
		// Remove omega random customers from solution
		while (countCandidates < (int)omega && countCandidates < size) {
			// Select random customer
			node = solution[rand.nextInt(size)];

			// Ensure customer is in solution (nodeBelong = true)
			while (!node.nodeBelong) {
				node = solution[rand.nextInt(size)];
			}

			// Add to removal candidates
			candidates[countCandidates++] = node;

			// Store old position
			node.prevOld = node.prev;
			node.nextOld = node.next;

			// Remove from route
			f += node.route.remove(node);
		}
	}

	/**
	 * Override to prevent self-reinforcement.
	 *
	 * CRITICAL: CriticalRemoval does NOT record its own removals.
	 *
	 * WHY: If CriticalRemoval recorded its own removals, it would create a
	 * positive feedback loop:
	 * 1. CriticalRemoval identifies customer X as frequently removed
	 * 2. CriticalRemoval removes customer X
	 * 3. Customer X's removal count increases
	 * 4. Next time, customer X is even more likely to be removed
	 * 5. Loop continues, causing excessive removal of same customers
	 *
	 * By NOT recording, CriticalRemoval only targets customers removed by
	 * OTHER operators, maintaining objectivity in its selection criteria.
	 */
	@Override
	protected void recordCandidates() {
		// Intentionally empty - do not track CriticalRemoval's own removals
		// This prevents self-reinforcement and maintains operator independence
	}
}
