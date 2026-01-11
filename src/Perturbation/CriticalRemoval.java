package Perturbation;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;

import Data.Instance;
import DiversityControl.OmegaAdjustment;
import Improvement.IntraLocalSearch;
import SearchMethod.Config;
import Solution.Node;
import Solution.Solution;
import EliteSet.EliteSet;

/**
 * Critical Removal Perturbation Operator (Pattern-Based)
 *
 * Uses string frequency analysis to identify and remove "critical" customers that appear
 * predominantly in low-frequency (rare) patterns. These customers are likely in suboptimal
 * configurations that prevent the search from finding better solutions.
 *
 * PHILOSOPHY:
 * -----------
 * Inverse of Pattern Injection:
 * - Pattern Injection: Reinforces HIGH-frequency patterns (successful structures)
 * - Critical Removal: Disrupts LOW-frequency patterns (unsuccessful structures)
 *
 * MOTIVATION:
 * -----------
 * In a mature elite set, high-frequency patterns represent successful structural components.
 * Customers that appear primarily in LOW-frequency patterns are:
 * 1. In unusual/rare configurations that don't generalize well
 * 2. Potentially blocking better placements
 * 3. Not part of the "consensus" structure emerging in the elite set
 *
 * Removing these customers forces the search to reconsider their placement.
 *
 * DYNAMIC ACTIVATION:
 * -------------------
 * Operator mode depends on string frequency maturity (checked EVERY call):
 * - IMMATURE: Random removal (fallback mode)
 * - MATURE: Pattern-based disruption (active mode)
 *
 * Maturity criteria (ALL must hold - adaptive to instance size):
 * 1. Elite set ≥75% full (statistical significance)
 * 2. Coefficient of Variation ≥0.40 (structured distribution, not flat noise)
 *    - Min unique patterns: adaptive (200 for small, 1000 for 10k nodes)
 * 3. Top pattern concentration ≥30% (appears in 30%+ of elite solutions)
 * 4. Coverage: adaptive threshold based on instance size
 *    - Small (100 nodes): 30% with top 50 patterns
 *    - Medium (1000 nodes): 25% with top 100 patterns
 *    - Large (10k nodes): 15% with top 1000 patterns
 *
 * If ANY criterion fails, operator automatically deactivates and uses random removal.
 * This ensures robustness: operator never uses unreliable pattern data.
 *
 * MECHANISM (Pattern-Based Mode):
 * --------------------------------
 * 1. Compute median string frequency across all patterns
 * 2. For each customer, count appearances in:
 *    - Frequent patterns (frequency ≥ median)
 *    - Rare patterns (frequency < median)
 * 3. Calculate criticality score: rare_count / (frequent_count + 1)
 *    - High score = appears mostly in rare patterns (critical)
 *    - Low score = appears mostly in frequent patterns (stable)
 * 4. Select omega customers with highest criticality scores
 * 5. Remove and reinsert using standard insertion heuristics
 *
 * PERFORMANCE:
 * ------------
 * Optimized for speed (all operations are O(n) where n is reasonable):
 * - Elite set queries: O(1) cached results
 * - Pattern analysis: O(k*m) where k=1000 patterns, m=3 pattern length → ~3000 ops
 * - Customer scoring: O(n) where n=number of customers
 * - Total: O(k) ~ linear in pattern count, still very fast (<1ms)
 *
 * COMPLEMENTARITY WITH PATTERN INJECTION:
 * ----------------------------------------
 * These operators work in tandem:
 * - Pattern Injection: Assembles successful patterns into solutions
 * - Critical Removal: Disrupts unsuccessful patterns to explore new structures
 *
 * Together they create a push-pull dynamic that reinforces good patterns while
 * disrupting bad ones.
 */
public class CriticalRemoval extends Perturbation
{
	/**
	 * Constructor for CriticalRemoval operator.
	 *
	 * @param instance Problem instance
	 * @param config Configuration parameters
	 * @param omegaSetup Omega adjustment setup for adaptive perturbation strength
	 * @param intraLocalSearch Intra-route local search operator
	 * @param ailsInstance Reference to AILSII for accessing elite set
	 */
	public CriticalRemoval(Instance instance, Config config,
		HashMap<String, OmegaAdjustment> omegaSetup,
		IntraLocalSearch intraLocalSearch,
		SearchMethod.AILSII ailsInstance)
	{
		super(instance, config, omegaSetup, intraLocalSearch, ailsInstance);
		this.perturbationType = PerturbationType.CriticalRemoval;
	}

	/**
	 * Apply CriticalRemoval perturbation to solution.
	 *
	 * ALGORITHM:
	 * 1. Check if string frequency data is mature (dynamic activation)
	 * 2. If IMMATURE: use random removal (fallback)
	 * 3. If MATURE: identify critical customers using pattern analysis
	 * 4. Remove critical customers and reinsert using standard heuristics
	 *
	 * @param s Solution to perturb
	 */
	@Override
	public void applyPerturbation(Solution s)
	{
		setSolution(s);

		EliteSet eliteSet = ailsInstance.getEliteSet();

		// ========== DYNAMIC ACTIVATION CHECK ==========
		// Check maturity EVERY call (allows deactivation if conditions degrade)
		if (!isStringFrequencyMature(eliteSet)) {
			// Fallback mode: Random removal
			if (ailsInstance.getIterator() % 500 == 0) {
				System.out.printf("[CriticalRemoval] iter:%d | mode:random_fallback (frequencies immature)%n",
					ailsInstance.getIterator());
			}
			removeRandomCustomers();
			setOrder();
			addCandidates();
			assignSolution(s);
			return;
		}

		// ========== PATTERN-BASED DISRUPTION MODE ==========
		List<Integer> criticalCustomers = selectCriticalCustomers(eliteSet, (int)omega);

		if (criticalCustomers.isEmpty()) {
			// Safety fallback (shouldn't happen if mature)
			removeRandomCustomers();
			setOrder();
			addCandidates();
			assignSolution(s);
			return;
		}

		// Diagnostic logging (every 500 iterations)
		if (ailsInstance.getIterator() % 500 == 0) {
			System.out.printf("[CriticalRemoval] iter:%d | mode:pattern_disruption critical:%d omega:%d%n",
				ailsInstance.getIterator(), criticalCustomers.size(), (int)omega);
		}

		// Remove critical customers from current solution
		removeCustomers(criticalCustomers);

		// Reinsert using standard insertion heuristics
		setOrder();
		addCandidates();

		// Update solution
		assignSolution(s);
	}

	/**
	 * Check if string frequency data is mature enough for pattern-based operations.
	 *
	 * PERFORMANCE: O(n) where n ~ 1000 (pattern sample) → fast, ~microseconds
	 *
	 * CRITERIA (all must hold - adaptive to instance size):
	 * 1. Elite set ≥75% full
	 * 2. Coefficient of Variation ≥0.40 (structured, not flat)
	 *    - Min unique patterns: max(200, size/5) up to 1000
	 * 3. Top pattern concentration ≥30%
	 * 4. Coverage: adaptive threshold (30% for small → 15% for large instances)
	 *    - Sample size: max(50, size/10) up to 1000 patterns
	 *
	 * @param eliteSet Elite set containing string frequencies
	 * @return true if frequencies are mature and reliable
	 */
	private boolean isStringFrequencyMature(EliteSet eliteSet)
	{
		int iter = ailsInstance.getIterator();
		boolean logDiagnostics = (iter % 2000 == 0);  // Log every 2000 iterations

		// ========== CRITERION 1: Elite Set Population ==========
		int eliteSize = eliteSet.size();
		int maxEliteSize = config.getEliteSetSize();
		double eliteFillRatio = (double) eliteSize / maxEliteSize;

		if (eliteFillRatio < 0.75) {  // Need at least 75% full (9/12 solutions)
			if (logDiagnostics) {
				System.out.printf("[CriticalRemoval-Trigger] iter:%d FAILED criterion 1: eliteFill=%.2f < 0.75%n",
					iter, eliteFillRatio);
			}
			return false;
		}

		// ========== CRITERION 2: Frequency Distribution Structure ==========
		// Get wider sample of patterns for analysis (include both high and low frequency)
		// CRITICAL: Must include rare patterns to get meaningful CV
		List<EliteSet.StringFrequency> topStrings = eliteSet.getTopFrequentStrings(1000);

		// Adaptive minimum diversity threshold based on instance size
		// Small (100 nodes): 200, Medium (1000 nodes): 200, Large (10000 nodes): 1000
		int minUniquePatterns = Math.min(1000, Math.max(200, size / 5));

		if (topStrings.size() < minUniquePatterns) {  // Need minimum diversity
			if (logDiagnostics) {
				System.out.printf("[CriticalRemoval-Trigger] iter:%d FAILED criterion 2a: uniquePatterns=%d < %d%n",
					iter, topStrings.size(), minUniquePatterns);
			}
			return false;
		}

		// Calculate coefficient of variation (CV = stddev / mean)
		// High CV → structured distribution (some patterns dominant)
		// Low CV → flat distribution (all patterns similar frequency)
		double sum = 0;
		for (EliteSet.StringFrequency sf : topStrings) {
			sum += sf.frequency;
		}
		double mean = sum / topStrings.size();

		double varianceSum = 0;
		for (EliteSet.StringFrequency sf : topStrings) {
			double diff = sf.frequency - mean;
			varianceSum += diff * diff;
		}
		double variance = varianceSum / topStrings.size();
		double stdDev = Math.sqrt(variance);

		double coefficientOfVariation = stdDev / mean;

		if (coefficientOfVariation < 0.40) {  // Too flat, not structured
			if (logDiagnostics) {
				System.out.printf("[CriticalRemoval-Trigger] iter:%d FAILED criterion 2b: CV=%.3f < 0.40 (mean=%.1f, std=%.1f, topFreq=%d)%n",
					iter, coefficientOfVariation, mean, stdDev,
					topStrings.isEmpty() ? 0 : topStrings.get(0).frequency);
			}
			return false;
		}

		// ========== CRITERION 3: Top Pattern Concentration ==========
		// Top pattern should appear in significant fraction of elite solutions
		if (topStrings.isEmpty()) {
			return false;
		}

		int topFrequency = topStrings.get(0).frequency;
		double topConcentration = (double) topFrequency / eliteSize;

		if (topConcentration < 0.3) {  // Top pattern must appear in 30%+ of elite
			if (logDiagnostics) {
				System.out.printf("[CriticalRemoval-Trigger] iter:%d FAILED criterion 3: topConc=%.3f < 0.30 (topFreq=%d, eliteSize=%d)%n",
					iter, topConcentration, topFrequency, eliteSize);
			}
			return false;
		}

		// ========== CRITERION 4: Customer Coverage ==========
		// Top patterns should cover meaningful portion of problem
		// Adaptive thresholds based on instance size
		Set<Integer> coveredCustomers = new HashSet<>();

		// Adaptive coverage sample size: scales with instance size
		// Small (100 nodes): 50 patterns, Medium (1000 nodes): 100, Large (10000 nodes): 1000
		int topN = Math.min(1000, Math.max(50, size / 10));
		topN = Math.min(topN, topStrings.size());

		for (int i = 0; i < topN; i++) {
			List<Integer> pattern = topStrings.get(i).toNodeList();
			for (Integer customer : pattern) {
				if (customer != 0) {  // Skip depot
					coveredCustomers.add(customer);
				}
			}
		}

		double coverageRatio = (double) coveredCustomers.size() / size;

		// Adaptive coverage threshold: decreases for larger instances
		// Small (100 nodes): 30%, Medium (1000 nodes): 25%, Large (10000 nodes): 15%
		double coverageThreshold = Math.max(0.15, 0.30 - (size / 10000.0) * 0.15);

		if (coverageRatio < coverageThreshold) {
			if (logDiagnostics) {
				System.out.printf("[CriticalRemoval-Trigger] iter:%d FAILED criterion 4: coverage=%.3f < %.3f (covered=%d/%d, topN=%d)%n",
					iter, coverageRatio, coverageThreshold, coveredCustomers.size(), size, topN);
			}
			return false;
		}

		// ========== ALL CRITERIA MET ==========
		if (logDiagnostics) {
			System.out.printf("[CriticalRemoval-Trigger] iter:%d SUCCESS! All criteria met: eliteFill=%.2f CV=%.3f topConc=%.3f coverage=%.3f%n",
				iter, eliteFillRatio, coefficientOfVariation, topConcentration, coverageRatio);
		}
		return true;
	}

	/**
	 * Select critical customers using pattern frequency analysis.
	 *
	 * STRATEGY:
	 * 1. Calculate median string frequency (threshold for rare vs frequent)
	 * 2. For each customer, count appearances in rare vs frequent patterns
	 * 3. Criticality score = rare_count / (frequent_count + 1)
	 * 4. Select omega customers with highest criticality
	 *
	 * PERFORMANCE: O(k*m + n) where k=100 patterns, m=3 length, n=customers → ~500 ops
	 *
	 * @param eliteSet Elite set containing string frequencies
	 * @param targetCount Number of critical customers to select (omega)
	 * @return List of customer IDs (1-indexed) to remove
	 */
	private List<Integer> selectCriticalCustomers(EliteSet eliteSet, int targetCount)
	{
		// Get broad sample of patterns for analysis (include rare patterns!)
		// Use large sample to ensure we capture low-frequency patterns
		List<EliteSet.StringFrequency> allPatterns = eliteSet.getTopFrequentStrings(1000);

		if (allPatterns.isEmpty()) {
			return new ArrayList<>();
		}

		// Calculate median frequency (threshold for rare vs frequent)
		List<Integer> frequencies = new ArrayList<>();
		for (EliteSet.StringFrequency sf : allPatterns) {
			frequencies.add(sf.frequency);
		}
		frequencies.sort(Integer::compareTo);
		int medianFreq = frequencies.get(frequencies.size() / 2);

		// Count customer appearances in rare vs frequent patterns
		Map<Integer, Integer> rareCount = new HashMap<>();
		Map<Integer, Integer> frequentCount = new HashMap<>();

		for (EliteSet.StringFrequency sf : allPatterns) {
			List<Integer> pattern = sf.toNodeList();
			boolean isFrequent = sf.frequency >= medianFreq;

			for (Integer customer : pattern) {
				if (customer == 0) continue;  // Skip depot

				if (isFrequent) {
					frequentCount.merge(customer, 1, Integer::sum);
				} else {
					rareCount.merge(customer, 1, Integer::sum);
				}
			}
		}

		// Calculate criticality scores
		// High score = appears mostly in rare patterns (bad)
		// Low score = appears mostly in frequent patterns (good)
		List<CustomerScore> scores = new ArrayList<>();

		for (Map.Entry<Integer, Integer> entry : rareCount.entrySet()) {
			int customer = entry.getKey();
			int rare = entry.getValue();
			int frequent = frequentCount.getOrDefault(customer, 0);

			// Criticality = rare / (frequent + 1)
			// +1 prevents division by zero and penalizes customers with no frequent appearances
			double criticality = (double) rare / (frequent + 1);

			scores.add(new CustomerScore(customer, criticality));
		}

		// Select top omega critical customers
		scores.sort((a, b) -> Double.compare(b.score, a.score));  // Descending order

		List<Integer> result = new ArrayList<>();
		int limit = Math.min(targetCount, scores.size());
		for (int i = 0; i < limit; i++) {
			result.add(scores.get(i).customerId);
		}

		return result;
	}

	/**
	 * Remove specified customers from current solution.
	 *
	 * @param customerIds List of customer IDs (1-indexed) to remove
	 */
	private void removeCustomers(List<Integer> customerIds)
	{
		for (Integer customerId : customerIds) {
			if (countCandidates >= (int)omega) break;

			// customerId is 1-indexed, solution array is 0-indexed
			if (customerId < 1 || customerId > size) continue;

			Node node = solution[customerId - 1];

			if (!node.nodeBelong) continue;  // Already removed

			candidates[countCandidates++] = node;

			// Store old position for potential restoration
			node.prevOld = node.prev;
			node.nextOld = node.next;

			// Remove from route
			f += node.route.remove(node);
		}
	}

	/**
	 * Fallback: Remove random customers.
	 * Used when string frequency data is immature or unavailable.
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

	/**
	 * Override to prevent self-reinforcement.
	 *
	 * CriticalRemoval does NOT record its own removals since it uses
	 * pattern-based selection rather than historical removal tracking.
	 * This prevents any potential feedback loops.
	 */
	@Override
	protected void recordCandidates() {
		// Intentionally empty - pattern-based operator doesn't use removal tracking
	}

	/**
	 * Helper class for customer criticality scoring.
	 */
	private static class CustomerScore {
		final int customerId;
		final double score;

		CustomerScore(int customerId, double score) {
			this.customerId = customerId;
			this.score = score;
		}
	}
}