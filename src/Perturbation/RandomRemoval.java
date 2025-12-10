package Perturbation;

import java.util.HashMap;

import Data.Instance;
import DiversityControl.OmegaAdjustment;
import Improvement.IntraLocalSearch;
import SearchMethod.Config;
import Solution.Solution;

/**
 * Random Removal Perturbation Operator
 *
 * Removes omega customers uniformly at random from the solution.
 * This operator provides unbiased perturbation without spatial or structural assumptions.
 *
 * PURPOSE:
 * --------
 * - Baseline diversification strategy
 * - No assumptions about problem structure
 * - Pure exploration without bias
 * - Useful when other operators become too specialized
 *
 * MECHANISM:
 * ----------
 * 1. Randomly select omega customers from current solution
 * 2. Remove them from their routes
 * 3. Reinsert using inherited insertion heuristic
 *
 * This is extracted from DiversificationRemoval's fallback mechanism,
 * which was performing well (0.67% effectiveness) even when the spatial
 * diversification logic never activated.
 */
public class RandomRemoval extends Perturbation
{
	public RandomRemoval(Instance instance, Config config,
		HashMap<String, OmegaAdjustment> omegaSetup,
		IntraLocalSearch intraLocalSearch,
		SearchMethod.AILSII ailsInstance)
	{
		super(instance, config, omegaSetup, intraLocalSearch, ailsInstance);
		this.perturbationType = PerturbationType.RandomRemoval;
	}

	@Override
	public void applyPerturbation(Solution s)
	{
		setSolution(s);

		// Remove omega random customers
		removeRandomCustomers();

		// Randomize insertion order
		setOrder();

		// Reinsert using inherited insertion heuristic
		addCandidates();

		// Update solution
		assignSolution(s);
	}

	/**
	 * Remove omega customers uniformly at random.
	 * This is the exact logic from DiversificationRemoval's fallback.
	 */
	private void removeRandomCustomers()
	{
		// Remove omega random customers from solution
		while (countCandidates < (int)omega && countCandidates < size)
		{
			// Select random customer
			node = solution[rand.nextInt(size)];

			// Ensure customer is in solution (nodeBelong = true)
			while (!node.nodeBelong)
			{
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
}
