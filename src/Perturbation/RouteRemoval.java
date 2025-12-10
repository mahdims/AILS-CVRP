package Perturbation;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;

import Data.Instance;
import DiversityControl.OmegaAdjustment;
import Improvement.IntraLocalSearch;
import SearchMethod.Config;
import Solution.Node;
import Solution.Route;
import Solution.Solution;

/**
 * Route Removal Perturbation Operator
 *
 * This operator provides structural/topological diversification by removing
 * entire routes rather than scattered customers. This forces re-sectoring
 * of the solution and can escape local optima that other operators cannot.
 *
 * Based on principles from:
 * - Ropke & Pisinger (2006): Cluster Removal in ALNS
 * - Christiaens & Vanden Berghe (2020): SISR string removal (limit case)
 *
 * Key difference from other operators:
 * - Sequential: Removes customers in sequence (linear)
 * - Concentric: Removes customers in spatial circles
 * - SISR: Removes spatially adjacent strings
 * - RouteRemoval: Removes structurally defined routes (topological)
 */
public class RouteRemoval extends Perturbation
{

	public RouteRemoval(Instance instance, Config config,
	HashMap<String, OmegaAdjustment> omegaSetup, IntraLocalSearch intraLocalSearch,
	SearchMethod.AILSII ailsInstance)
	{
		super(instance, config, omegaSetup, intraLocalSearch, ailsInstance);
		this.perturbationType = PerturbationType.RouteRemoval;
	}

	public void applyPerturbation(Solution s)
	{
		setSolution(s);

		// Calculate how many routes to remove based on omega
		// omega represents target number of customers to remove
		// Convert this to routes: omega / avgCustomersPerRoute

		if (numRoutes < 2) {
			// Edge case: only 1 route, fall back to removing random customers
			removeRandomCustomers();
		} else {
			removeCompleteRoutes();
		}

		// Shuffle candidates for reinsertion
		setOrder();

		// Reinsert using insertion heuristic (recreate phase)
		addCandidates();

		// Copy back to solution
		assignSolution(s);
	}

	/**
	 * Remove complete routes based on omega target
	 */
	private void removeCompleteRoutes()
	{
		// Calculate average customers per route
		int totalCustomers = 0;
		for (int i = 0; i < numRoutes; i++) {
			totalCustomers += routes[i].numElements;
		}
		double avgCustomersPerRoute = (double) totalCustomers / numRoutes;

		// Calculate how many routes to remove (at least 1, at most 25% of fleet)
		int routesToRemove = Math.max(1, (int) Math.ceil(omega / avgCustomersPerRoute));
		routesToRemove = Math.min(routesToRemove, Math.max(1, numRoutes / 4));

		// Create list of route indices
		List<Integer> routeIndices = new ArrayList<>();
		for (int i = 0; i < numRoutes; i++) {
			routeIndices.add(i);
		}

		// Randomly shuffle to select routes for removal
		// This ensures unbiased selection
		for (int i = routeIndices.size() - 1; i > 0; i--) {
			int j = rand.nextInt(i + 1);
			int temp = routeIndices.get(i);
			routeIndices.set(i, routeIndices.get(j));
			routeIndices.set(j, temp);
		}

		// Remove customers from selected routes
		for (int r = 0; r < routesToRemove; r++) {
			int routeIndex = routeIndices.get(r);
			Route route = routes[routeIndex];

			// Remove all customers from this route
			Node node = route.first.next;
			while (node.name != 0 && countCandidates < size) {
				Node nextNode = node.next;

				// Add to candidates for reinsertion
				candidates[countCandidates++] = node;

				// Store old links for potential rollback
				node.prevOld = node.prev;
				node.nextOld = node.next;

				// Remove from route
				f += route.remove(node);

				node = nextNode;
			}

			// Stop if we've removed enough customers (omega target)
			if (countCandidates >= (int) omega) {
				break;
			}
		}
	}

	/**
	 * Fallback: Remove random customers if only 1 route exists
	 */
	private void removeRandomCustomers()
	{
		while (countCandidates < (int) omega && countCandidates < size) {
			// Pick random customer
			node = solution[rand.nextInt(size)];

			// Find one that's in a route
			while (!node.nodeBelong) {
				node = solution[rand.nextInt(size)];
			}

			// Add to candidates
			candidates[countCandidates++] = node;

			// Store old links
			node.prevOld = node.prev;
			node.nextOld = node.next;

			// Remove from route
			f += node.route.remove(node);
		}
	}

}
