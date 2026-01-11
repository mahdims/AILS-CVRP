package Perturbation;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

import Data.Instance;
import DiversityControl.OmegaAdjustment;
import Improvement.IntraLocalSearch;
import SearchMethod.Config;
import Solution.Node;
import Solution.Solution;
import EliteSet.EliteSet;

/**
 * Pattern Injection Perturbation Operator
 *
 * Actively assembles high-frequency k-node patterns from the elite set into a single solution.
 * This operator reinforces successful patterns identified during search, particularly in later
 * stages when ideal distance is low (exploitation phase).
 *
 * MOTIVATION:
 * -----------
 * String frequency tracking in the elite set identifies k-node sequences that appear frequently
 * in high-quality solutions. These patterns represent successful structural components.
 * Pattern Injection actively combines multiple non-overlapping high-frequency patterns into a
 * single solution, potentially creating new high-quality solutions by assembling proven components.
 *
 * MECHANISM:
 * ----------
 * 1. Calculate search progress based on ideal distance decay (0.0 = early, 1.0 = late)
 * 2. Adaptively determine number of patterns to inject: minPatterns (early) → maxPatterns (late)
 * 3. Select patterns using frequency-weighted probabilistic selection (roulette wheel)
 *    - Pool size: adaptive min(100, uniqueStrings * 0.1)
 *    - Non-overlapping constraint: selected patterns cannot share customers
 * 4. Destroy phase: Remove customers NOT in selected patterns (creates space for patterns)
 * 5. Repair phase: Force inject selected patterns into routes
 *    - May create capacity violations (relies on FeasibilityPhase to repair)
 *    - Remaining removed customers are reinserted using standard insertion heuristics
 *
 * ADAPTATION:
 * -----------
 * The number of patterns injected adapts based on search progress:
 * - Early search (high idealDist): 1 pattern → conservative, exploratory
 * - Late search (low idealDist): 5 patterns → aggressive, exploitative pattern reinforcement
 *
 * SAFETY CONSTRAINTS:
 * -------------------
 * - Maximum patterns limited by omega capacity: maxPatterns ≤ omega / (k-1)
 * - Fallback to random removal if no patterns available or insufficient statistics
 * - Skip injection if no non-overlapping patterns can be selected
 *
 * CAPACITY VIOLATIONS:
 * --------------------
 * Pattern injection may create capacity-infeasible solutions. The existing FeasibilityPhase
 * operator (applied after perturbation) repairs violations using inter-route moves (SHIFT, SWAP, CROSS).
 *
 * Key features:
 * - Frequency-weighted probabilistic pattern selection (not deterministic)
 * - Adaptive pattern count tied to ideal distance decay
 * - Non-overlapping pattern assembly (combines multiple patterns in one solution)
 * - Force injection with feasibility repair
 */
public class PatternInjection extends Perturbation
{
	private int minPatterns;  // Minimum patterns to inject (early search)
	private int maxPatterns;  // Maximum patterns to inject (late search)

	/**
	 * Constructor for PatternInjection operator.
	 *
	 * @param instance Problem instance
	 * @param config Configuration parameters
	 * @param omegaSetup Omega adjustment setup for adaptive perturbation strength
	 * @param intraLocalSearch Intra-route local search operator
	 * @param ailsInstance Reference to AILSII for accessing elite set and ideal distance
	 */
	public PatternInjection(Instance instance, Config config,
		HashMap<String, OmegaAdjustment> omegaSetup,
		IntraLocalSearch intraLocalSearch,
		SearchMethod.AILSII ailsInstance)
	{
		super(instance, config, omegaSetup, intraLocalSearch, ailsInstance);
		this.perturbationType = PerturbationType.PatternInjection;

		this.minPatterns = config.getPatternInjectionMinPatterns();
		this.maxPatterns = config.getPatternInjectionMaxPatterns();
	}

	/**
	 * Apply PatternInjection perturbation to solution.
	 *
	 * ALGORITHM:
	 * 1. Calculate search progress from ideal distance decay
	 * 2. Determine number of patterns to inject (adaptive: min → max)
	 * 3. Select patterns using frequency-weighted probabilistic selection
	 * 4. Destroy: Remove customers NOT in selected patterns
	 * 5. Repair: Force inject selected patterns, then reinsert remaining customers
	 *
	 * @param s Solution to perturb
	 */
	@Override
	public void applyPerturbation(Solution s)
	{
		setSolution(s);

		// Get elite set for pattern frequencies
		EliteSet eliteSet = ailsInstance.getEliteSet();

		// Calculate search progress based on ideal distance
		double progress = calculateSearchProgress();

		// Determine number of patterns to inject (adaptive)
		int numPatterns = minPatterns + (int)(progress * (maxPatterns - minPatterns));

		// Safety check: ensure numPatterns doesn't exceed omega capacity
		// Each pattern of length k requires k-1 customers (excluding depot)
		int stringLength = config.getStringLength();
		int maxSafePatterns = (int)omega / Math.max(1, stringLength - 1);
		numPatterns = Math.min(numPatterns, maxSafePatterns);

		// Diagnostic logging (every 500 iterations)
		if (ailsInstance.getIterator() % 500 == 0) {
			System.out.printf("[PatternInjection] iter:%d | progress:%.2f numPatterns:%d omega:%d%n",
				ailsInstance.getIterator(), progress, numPatterns, (int)omega);
		}

		// Select patterns using frequency-weighted probabilistic selection
		List<List<Integer>> selectedPatterns = selectPatterns(eliteSet, numPatterns);

		// Fallback: If no patterns selected, use random removal
		if (selectedPatterns.isEmpty()) {
			if (ailsInstance.getIterator() % 500 == 0) {
				System.out.printf("[PatternInjection] iter:%d | mode:fallback_random (no patterns available)%n",
					ailsInstance.getIterator());
			}
			removeRandomCustomers();
			setOrder();
			addCandidates();
			assignSolution(s);
			return;
		}

		// Destroy phase: Remove customers NOT in selected patterns
		Set<Integer> patternCustomers = new HashSet<>();
		for (List<Integer> pattern : selectedPatterns) {
			// Skip depot (node 0)
			for (Integer nodeId : pattern) {
				if (nodeId != 0) {
					patternCustomers.add(nodeId);
				}
			}
		}

		removeCustomersNotInPatterns(patternCustomers);

		// Repair phase: Force inject patterns
		injectPatterns(selectedPatterns);

		// Update solution
		assignSolution(s);
	}

	/**
	 * Calculate search progress based on ideal distance decay.
	 *
	 * Progress metric: (distMMax - currentIdealDist) / (distMMax - distMMin)
	 * - 0.0 = early search (idealDist near distMMax, exploration)
	 * - 1.0 = late search (idealDist near distMMin, exploitation)
	 *
	 * @return Progress value in [0.0, 1.0]
	 */
	private double calculateSearchProgress()
	{
		double currentIdealDist = ailsInstance.getIdealDist().idealDist;
		double distMMin = config.getDMin();
		double distMMax = config.getDMax();

		// Avoid division by zero
		if (distMMax <= distMMin) {
			return 0.0;
		}

		// Progress: 0.0 (early, high idealDist) -> 1.0 (late, low idealDist)
		double progress = (distMMax - currentIdealDist) / (distMMax - distMMin);
		return Math.max(0.0, Math.min(1.0, progress));
	}

	/**
	 * Select patterns using frequency-weighted probabilistic selection.
	 *
	 * STRATEGY:
	 * 1. Get top-k patterns from elite set (adaptive pool size)
	 * 2. Use roulette wheel selection based on frequency
	 * 3. Ensure non-overlapping patterns (selected patterns share no customers)
	 *
	 * @param eliteSet Elite set containing string frequencies
	 * @param numPatterns Target number of patterns to select
	 * @return List of selected patterns (each pattern is a list of node IDs)
	 */
	private List<List<Integer>> selectPatterns(EliteSet eliteSet, int numPatterns)
	{
		// Get pattern pool (adaptive size)
		int uniqueStrings = eliteSet.getStringTrackingStats().uniqueStrings;
		int poolSize = Math.min(100, Math.max(1, (int)(uniqueStrings * 0.1)));

		List<EliteSet.StringFrequency> topPatterns = eliteSet.getTopFrequentStrings(poolSize);

		if (topPatterns.isEmpty()) {
			return new ArrayList<>();
		}

		// Calculate total frequency for probability normalization
		int totalFrequency = 0;
		for (EliteSet.StringFrequency sf : topPatterns) {
			totalFrequency += sf.frequency;
		}

		// Safety check
		if (totalFrequency == 0) {
			return new ArrayList<>();
		}

		// Select patterns using roulette wheel selection
		List<List<Integer>> selectedPatterns = new ArrayList<>();
		Set<Integer> usedCustomers = new HashSet<>();

		// Attempt to select numPatterns non-overlapping patterns
		// Limit attempts to avoid infinite loop if insufficient non-overlapping patterns exist
		int maxAttempts = Math.min(numPatterns * 5, topPatterns.size());
		for (int attempt = 0; attempt < maxAttempts && selectedPatterns.size() < numPatterns; attempt++) {
			EliteSet.StringFrequency selected = rouletteWheelSelection(topPatterns, totalFrequency);

			if (selected == null) break;

			List<Integer> pattern = selected.toNodeList();

			// Check for overlap with already selected patterns
			boolean hasOverlap = false;
			for (Integer nodeId : pattern) {
				if (nodeId != 0 && usedCustomers.contains(nodeId)) {
					hasOverlap = true;
					break;
				}
			}

			// Skip if overlaps, otherwise add
			if (!hasOverlap) {
				selectedPatterns.add(pattern);
				for (Integer nodeId : pattern) {
					if (nodeId != 0) {
						usedCustomers.add(nodeId);
					}
				}
			}
		}

		return selectedPatterns;
	}

	/**
	 * Roulette wheel selection based on frequency.
	 *
	 * Selects a pattern with probability proportional to its frequency.
	 * Higher frequency patterns are more likely to be selected.
	 *
	 * @param patterns Pool of candidate patterns
	 * @param totalFrequency Sum of all pattern frequencies
	 * @return Selected pattern (null if selection fails)
	 */
	private EliteSet.StringFrequency rouletteWheelSelection(
		List<EliteSet.StringFrequency> patterns, int totalFrequency)
	{
		if (patterns.isEmpty() || totalFrequency == 0) return null;

		// Generate random value in [0, totalFrequency)
		int randomValue = rand.nextInt(totalFrequency);

		// Find pattern corresponding to random value
		int cumulativeFreq = 0;
		for (EliteSet.StringFrequency sf : patterns) {
			cumulativeFreq += sf.frequency;
			if (randomValue < cumulativeFreq) {
				return sf;
			}
		}

		// Fallback (shouldn't reach here due to rounding)
		return patterns.get(patterns.size() - 1);
	}

	/**
	 * Destroy phase: Remove customers NOT in selected patterns.
	 *
	 * Creates space in routes for pattern injection by removing customers
	 * that are not part of the selected patterns.
	 *
	 * @param patternCustomers Set of customer IDs in selected patterns
	 */
	private void removeCustomersNotInPatterns(Set<Integer> patternCustomers)
	{
		for (int i = 0; i < size && countCandidates < (int)omega; i++) {
			node = solution[i];

			// Skip if node not in solution
			if (!node.nodeBelong) continue;

			// Remove if NOT in selected patterns
			if (!patternCustomers.contains(node.name)) {
				candidates[countCandidates++] = node;

				// Store old position
				node.prevOld = node.prev;
				node.nextOld = node.next;

				// Remove from route
				f += node.route.remove(node);
			}
		}
	}

	/**
	 * Repair phase: Force inject selected patterns into routes.
	 *
	 * STRATEGY:
	 * 1. For each pattern:
	 *    - Find route with minimum load (most available capacity)
	 *    - Insert pattern nodes sequentially into that route
	 * 2. Reinsert remaining removed customers using standard insertion heuristics
	 *
	 * NOTE: This may create capacity violations. The existing FeasibilityPhase
	 * operator (applied after perturbation) will repair violations.
	 *
	 * @param patterns Selected patterns to inject
	 */
	private void injectPatterns(List<List<Integer>> patterns)
	{
		for (List<Integer> pattern : patterns) {
			// Find route with minimum load (most capacity available)
			int bestRouteIdx = -1;
			double minLoad = Double.MAX_VALUE;

			for (int r = 0; r < numRoutes; r++) {
				double currentLoad = routes[r].totalDemand;
				if (currentLoad < minLoad) {
					minLoad = currentLoad;
					bestRouteIdx = r;
				}
			}

			// Inject pattern into selected route
			if (bestRouteIdx >= 0) {
				Node insertAfter = routes[bestRouteIdx].first;

				for (Integer nodeId : pattern) {
					// Skip depot
					if (nodeId == 0) continue;

					// Get node (1-indexed in pattern, 0-indexed in solution)
					if (nodeId > 0 && nodeId <= size) {
						Node nodeToInsert = solution[nodeId - 1];

						// Only insert if not already in a route
						if (!nodeToInsert.nodeBelong) {
							f += routes[bestRouteIdx].addAfter(nodeToInsert, insertAfter);
							insertAfter = nodeToInsert;
						}
					}
				}
			}
		}

		// Reinsert any remaining candidates using standard insertion heuristics
		// (customers that were removed but not part of injected patterns)
		setOrder();
		addCandidates();
	}

	/**
	 * Fallback method: remove random customers.
	 * Used when insufficient pattern statistics exist or pattern selection fails.
	 *
	 * Ensures the operator is always productive even without pattern data.
	 */
	private void removeRandomCustomers()
	{
		while (countCandidates < (int)omega && countCandidates < size) {
			node = solution[rand.nextInt(size)];

			while (!node.nodeBelong) {
				node = solution[rand.nextInt(size)];
			}

			candidates[countCandidates++] = node;

			node.prevOld = node.prev;
			node.nextOld = node.next;

			f += node.route.remove(node);
		}
	}
}
