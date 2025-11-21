package Perturbation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import Data.Instance;
import DiversityControl.OmegaAdjustment;
import Improvement.IntraLocalSearch;
import SearchMethod.Config;
import Solution.Node;
import Solution.Route;
import Solution.Solution;

/**
 * SISR (Slack Induction by String Removals) Operator
 *
 * Based on: Christiaens & Vanden Berghe (2020), Transportation Science
 * "Slack Induction by String Removals for Vehicle Routing Problems"
 *
 * Implements:
 * - Algorithm 2: Ruin Method (Section 5.2)
 * - Algorithm 3: Greedy Insertion with Blinks (Section 5.3)
 * - Equations 5-9: Parameter calculations
 *
 * Key Features:
 * - String-based removal (regular and split)
 * - Weighted recreate ordering (Random, Demand, Far, Close)
 * - Blink rate for insertion randomization
 * - Uses existing KNN structure for adjacency
 * - Uses omega parameter for removal count
 */
public class SISR extends Perturbation {

	// ========== SISR-SPECIFIC FIELDS ==========

	private SISRConfig sisrConfig;              // SISR configuration parameters
	private List<Node> sisrAbsent;              // Set A in paper: removed customers
	private List<Node> sisrInsertionStack;      // Track insertion order
	private List<RemovalPlan> removalPlan;      // Temporary removal planning

	// ========== INNER CLASSES ==========

	/**
	 * Removal plan entry: position and node to remove
	 */
	private static class RemovalPlan {
		int position;    // Position in route (0-based, excluding depot)
		Node node;       // Node to remove

		RemovalPlan(int position, Node node) {
			this.position = position;
			this.node = node;
		}
	}

	/**
	 * SISR parameters calculated from Equations 5-7
	 */
	private static class SISRParams {
		double ell_s_max;  // Maximum string length (Equation 5)
		int k_s;           // Number of routes to ruin (Equation 7)
	}

	/**
	 * Insertion position tracking
	 */
	private static class InsertionPosition {
		int routeIdx;      // Route index
		int position;      // Position in route
		Node insertAfter;  // Node to insert after
		double cost;       // Insertion cost
		boolean valid;     // Is position valid?

		InsertionPosition() {
			this.cost = Double.MAX_VALUE;
			this.valid = false;
		}
	}

	// ========== CONSTRUCTOR ==========

	public SISR(Instance instance, Config config,
	            HashMap<String, OmegaAdjustment> omegaSetup,
	            IntraLocalSearch intraLocalSearch) {
		super(instance, config, omegaSetup, intraLocalSearch);

		this.perturbationType = PerturbationType.SISR;
		this.sisrConfig = config.getSisrConfig();
		this.sisrAbsent = new ArrayList<>();
		this.sisrInsertionStack = new ArrayList<>();
		this.removalPlan = new ArrayList<>();
	}

	// ========== MAIN OVERRIDE METHODS ==========

	/**
	 * Apply SISR perturbation: Ruin phase
	 * Override of base Perturbation.applyPerturbation()
	 */
	@Override
	public void applyPerturbation(Solution s) {
		setSolution(s);
		resetBuffers();
		applySISRRuin();
		assignSolution(s);
	}

	/**
	 * Recreate phase: Reinsert removed customers
	 * Override of base Perturbation.addCandidates()
	 */
	@Override
	public void addCandidates() {
		applySISRRecreate();
	}

	// ========== RUIN PHASE METHODS (Algorithm 2) ==========

	/**
	 * Reset SISR buffers before new perturbation
	 */
	private void resetBuffers() {
		sisrAbsent.clear();
		sisrInsertionStack.clear();
	}

	/**
	 * Main ruin method - Algorithm 2 from paper
	 * Lines 1-10: Calculate parameters, select seed, ruin routes
	 */
	private void applySISRRuin() {
		// Lines 1-2: Calculate parameters
		SISRParams params = calculateSISRParameters();
		if (params.k_s == 0) return;  // No routes to ruin

		// Line 3: Select seed customer
		int seedCustomer = selectSeedCustomer();
		if (seedCustomer < 0) return;  // No served customers

		// Line 4: Initialize ruined routes set R
		Set<Integer> ruinedRoutes = new HashSet<>();
		int routesProcessed = 0;

		// Line 5: for c ∈ adj(c_s^seed) and |R| < k_s do
		// *** USE EXISTING KNN STRUCTURE ***
		if (seedCustomer > 0 && seedCustomer <= solution.length) {
			Node seedNode = solution[seedCustomer - 1];
			int[] adjList = seedNode.knn;  // Use existing KNN!

			for (int i = 0; i < adjList.length; i++) {
				// Check termination: |R| < k_s
				if (routesProcessed >= params.k_s) break;

				int neighbor = adjList[i];

				// Line 6: if c ∉ A and t ∉ R then
				if (neighbor == 0) continue;  // Skip depot
				if (neighbor > solution.length) continue;

				Node neighborNode = solution[neighbor - 1];
				if (!neighborNode.nodeBelong) continue;  // c ∈ A (already removed)

				Route neighborRoute = neighborNode.route;
				if (ruinedRoutes.contains(neighborRoute.nameRoute)) continue; // t ∈ R

				// Lines 7-10: Process this route
				if (processRouteRemoval(neighborRoute, neighbor, params, ruinedRoutes)) {
					routesProcessed++;
				}
			}
		}
	}

	/**
	 * Calculate SISR parameters - Equations 5-7 from paper
	 * Equation 5: ell_s_max = min{Lmax, avg_cardinality}
	 * Equation 6: k_s_max = floor(4*c̄/(1+ell_s_max)) - 1
	 * Equation 7: k_s = floor(U(1, k_s_max+1))
	 */
	private SISRParams calculateSISRParameters() {
		SISRParams params = new SISRParams();

		// Count non-empty routes and their cardinalities
		double sumCardinality = 0.0;
		int numNonEmpty = 0;

		for (int i = 0; i < numRoutes; i++) {
			int routeSize = routes[i].numElements - 1;  // Exclude depot
			if (routeSize > 0) {
				sumCardinality += routeSize;
				numNonEmpty++;
			}
		}

		if (numNonEmpty == 0) {
			params.ell_s_max = 0;
			params.k_s = 0;
			return params;
		}

		// ===== EQUATION 5: ell_s_max = min{Lmax, avg_cardinality} =====
		double avgCardinality = sumCardinality / numNonEmpty;
		params.ell_s_max = Math.min(sisrConfig.maxStringLength, avgCardinality);

		// ===== EQUATION 6: k_s_max = floor(4*c̄/(1+ell_s_max)) - 1 =====
		// NOTE: Use omega (inherited from Perturbation) instead of avgRemoved
		double k_s_max = Math.floor(4.0 * omega / (1.0 + params.ell_s_max)) - 1;

		// ===== EQUATION 7: k_s = floor(U(1, k_s_max+1)) =====
		if (k_s_max < 1) {
			params.k_s = 1;
		} else {
			params.k_s = (int) Math.floor(rand.nextDouble() * k_s_max + 1.0);
		}

		params.k_s = Math.max(1, Math.min(params.k_s, numNonEmpty));

		return params;
	}

	/**
	 * Select seed customer - Algorithm 2, Line 3
	 * Returns customer name (1-based), or -1 if none available
	 */
	private int selectSeedCustomer() {
		List<Integer> servedCustomers = new ArrayList<>();

		// Collect all customers currently in routes
		for (int i = 0; i < solution.length; i++) {
			if (solution[i].nodeBelong && solution[i].name != 0) {
				servedCustomers.add(solution[i].name);
			}
		}

		if (servedCustomers.isEmpty()) {
			return -1;
		}

		// Random selection
		return servedCustomers.get(rand.nextInt(servedCustomers.size()));
	}

	/**
	 * Process removal from a single route - Algorithm 2, Lines 6-10
	 */
	private boolean processRouteRemoval(Route route, int closestCus,
	                                     SISRParams params, Set<Integer> ruinedRoutes) {
		if (route.numElements <= 1) return false;  // Only depot

		// Line 8: Calculate string length (Equations 8-9)
		int stringLength = calculateStringLength(route, params.ell_s_max);
		if (stringLength <= 0) return false;

		// Split decision
		boolean splitString = (stringLength > 1) &&
		                      (rand.nextDouble() < sisrConfig.splitRate);

		// Line 9: Select customers to remove
		removalPlan.clear();
		if (!splitString) {
			regularStringRemoval(route, stringLength, closestCus);
		} else {
			splitStringRemoval(route, stringLength, closestCus);
		}

		// Execute removals
		int removedCount = executeRemovalPlan(route);

		// Line 10: Mark route as ruined
		ruinedRoutes.add(route.nameRoute);

		return removedCount > 0;
	}

	/**
	 * Calculate string length for route - Equations 8-9
	 * Equation 8: l_t_max = min{|t|, l_s_max}
	 * Equation 9: l_t = floor(U(1, l_t_max+1))
	 */
	private int calculateStringLength(Route route, double ell_s_max) {
		int routeSize = route.numElements - 1;  // Exclude depot

		// ===== EQUATION 8: l_t_max = min{|t|, l_s_max} =====
		double l_t_max = Math.min(routeSize, ell_s_max);
		if (l_t_max <= 0) return 0;

		// ===== EQUATION 9: l_t = floor(U(1, l_t_max+1)) =====
		return (int) Math.floor(rand.nextDouble() * l_t_max + 1.0);
	}

	/**
	 * Regular string removal - Figure 3 from paper
	 * Removes consecutive customers including closestCus
	 */
	private void regularStringRemoval(Route route, int stringLength, int closestCus) {
		// Find position of closestCus in route
		int closestCusPos = findCustomerPositionInRoute(route, closestCus);

		if (closestCusPos < 0) {
			// Fallback: use middle of route
			int routeSize = route.numElements - 1;
			closestCusPos = Math.max(0, routeSize / 2);
		}

		// Calculate valid start positions that include closestCusPos
		// The string [startPos, startPos+stringLength-1] must contain closestCusPos
		int routeSize = route.numElements - 1;
		int minStart = Math.max(0, closestCusPos - stringLength + 1);
		int maxStart = Math.min(closestCusPos, routeSize - stringLength);

		if (minStart > maxStart) {
			// Edge case: adjust to valid range
			minStart = Math.max(0, routeSize - stringLength);
			maxStart = minStart;
		}

		// Randomly select start position
		int startPos = minStart;
		if (maxStart > minStart) {
			startPos = minStart + rand.nextInt(maxStart - minStart + 1);
		}

		// Add segment to removal plan
		addSegmentToRemovalPlan(route, startPos, stringLength);
	}

	/**
	 * Split string removal - Figure 4 from paper
	 * Removes customers from window, preserving interior substring
	 */
	private void splitStringRemoval(Route route, int stringLength, int closestCus) {
		int routeSize = route.numElements - 1;

		// Step 1: Determine m (number of preserved customers)
		// m_max = |t| - l (route size minus string length)
		int m_max = routeSize - stringLength;
		if (m_max < 0) m_max = 0;

		int m = 1;
		// CORRECTED LOGIC: Continue while m < m_max AND U(0,1) ≤ β
		while (m < m_max && rand.nextDouble() <= sisrConfig.splitDepth) {
			m++;
		}

		// Step 2: Find closestCus position
		int closestCusPos = findCustomerPositionInRoute(route, closestCus);
		if (closestCusPos < 0) {
			closestCusPos = routeSize / 2;
		}

		// Step 3: Select l+m window that includes closestCusPos
		int windowSize = Math.min(stringLength + m, routeSize);
		int minWindowStart = Math.max(0, closestCusPos - windowSize + 1);
		int maxWindowStart = Math.min(closestCusPos, routeSize - windowSize);

		if (minWindowStart > maxWindowStart) {
			minWindowStart = 0;
			maxWindowStart = Math.max(0, routeSize - windowSize);
		}

		int windowStart = minWindowStart;
		if (maxWindowStart > minWindowStart) {
			windowStart = minWindowStart + rand.nextInt(maxWindowStart - minWindowStart + 1);
		}
		int windowEnd = windowStart + windowSize - 1;

		// Step 4: Randomly place m preserved customers within window
		int minPreserveStart = windowStart;
		int maxPreserveStart = windowEnd - m + 1;

		int preserveStart = minPreserveStart;
		if (maxPreserveStart > minPreserveStart) {
			preserveStart = minPreserveStart + rand.nextInt(maxPreserveStart - minPreserveStart + 1);
		}
		int preserveEnd = preserveStart + m - 1;

		// Step 5: Remove customers outside preserved substring
		// Remove BEFORE preserved substring
		if (preserveStart > windowStart) {
			addSegmentToRemovalPlan(route, windowStart, preserveStart - windowStart);
		}

		// Remove AFTER preserved substring
		if (preserveEnd < windowEnd) {
			addSegmentToRemovalPlan(route, preserveEnd + 1, windowEnd - preserveEnd);
		}
	}

	/**
	 * Find position of customer in route
	 * Returns 0-based position (excluding depot), or -1 if not found
	 */
	private int findCustomerPositionInRoute(Route route, int customerName) {
		Node current = route.first.next;  // Start after depot
		int position = 0;

		while (current != route.first) {
			if (current.name == customerName) {
				return position;
			}
			current = current.next;
			position++;
		}

		return -1;  // Not found
	}

	/**
	 * Add segment of customers to removal plan
	 */
	private void addSegmentToRemovalPlan(Route route, int startPos, int length) {
		if (length <= 0) return;

		Node current = route.first.next;  // Start after depot

		// Navigate to start position
		for (int i = 0; i < startPos && current != route.first; i++) {
			current = current.next;
		}

		// Add nodes to removal plan
		for (int i = 0; i < length && current != route.first; i++) {
			removalPlan.add(new RemovalPlan(startPos + i, current));
			current = current.next;
		}
	}

	/**
	 * Execute removal plan - Algorithm 2, Line 9
	 * Removes nodes from route and adds to sisrAbsent
	 */
	private int executeRemovalPlan(Route route) {
		if (removalPlan.isEmpty()) return 0;

		// Sort by position (descending) to avoid index shifting issues
		Collections.sort(removalPlan, (a, b) -> Integer.compare(b.position, a.position));

		// Remove duplicates
		Set<Node> seen = new HashSet<>();
		removalPlan.removeIf(rp -> !seen.add(rp.node));

		// Execute removals
		int removedCount = 0;
		for (RemovalPlan rp : removalPlan) {
			if (rp.node != null && rp.node.nodeBelong) {
				// Store old neighbors (for potential tracking)
				rp.node.prevOld = rp.node.prev;
				rp.node.nextOld = rp.node.next;

				// Remove from route
				f += route.remove(rp.node);

				// Add to absent list
				sisrAbsent.add(rp.node);
				removedCount++;
			}
		}

		return removedCount;
	}

	// ========== RECREATE PHASE METHODS (Algorithm 3) ==========

	/**
	 * Main recreate method - Algorithm 3 from paper
	 * Lines 1-14: Sort absent customers and reinsert
	 */
	private void applySISRRecreate() {
		if (sisrAbsent.isEmpty()) return;

		// Copy to working list
		List<Node> pending = new ArrayList<>(sisrAbsent);
		sisrAbsent.clear();
		sisrInsertionStack.clear();

		// Step 1: Select ordering strategy (Line 2)
		SISRRecreateOrder selectedOrder = selectRecreateOrder();

		// Step 2: Sort customers by selected ordering (Line 2)
		sortAbsentCustomers(pending, selectedOrder);

		// Step 3: Insert each customer (Lines 3-14)
		for (Node customer : pending) {
			// Lines 4-9: Find best insertion position
			InsertionPosition bestPos = findBestInsertionPosition(customer);

			// Lines 10-12: Create new route if no valid position
			if (!bestPos.valid && numRoutes < instance.getMaxNumberRoutes()) {
				createNewRouteWithCustomer(customer);
				continue;
			}

			// Lines 13-14: Insert at best position
			if (!insertCustomerAtPosition(customer, bestPos)) {
				// Failed to insert - keep in absent list
				sisrAbsent.add(customer);
			}
		}
	}

	/**
	 * Select recreate ordering strategy - Algorithm 3, Line 2
	 * Weighted selection: Random=4, Demand=4, Far=2, Close=1 (total=11)
	 */
	private SISRRecreateOrder selectRecreateOrder() {
		int r = rand.nextInt(11) + 1;  // 1 to 11

		if (r <= 4) {
			return SISRRecreateOrder.RANDOM;      // 4/11 = 36.4%
		} else if (r <= 8) {
			return SISRRecreateOrder.DEMAND;      // 4/11 = 36.4%
		} else if (r <= 10) {
			return SISRRecreateOrder.FAR;         // 2/11 = 18.2%
		} else {
			return SISRRecreateOrder.CLOSE;       // 1/11 = 9.1%
		}
	}

	/**
	 * Sort absent customers by selected ordering - Algorithm 3, Line 2
	 */
	private void sortAbsentCustomers(List<Node> customers, SISRRecreateOrder order) {
		switch (order) {
			case RANDOM:
				Collections.shuffle(customers, rand);
				break;

			case DEMAND:
				customers.sort((a, b) -> {
					// Largest demand first
					if (a.demand == b.demand) {
						return Integer.compare(a.name, b.name);
					}
					return Integer.compare(b.demand, a.demand);
				});
				break;

			case FAR:
				customers.sort((a, b) -> {
					// Farthest from depot first
					double distA = instance.dist(0, a.name);
					double distB = instance.dist(0, b.name);
					if (Math.abs(distA - distB) < 0.001) {
						return Integer.compare(a.name, b.name);
					}
					return Double.compare(distB, distA);
				});
				break;

			case CLOSE:
				customers.sort((a, b) -> {
					// Closest to depot first
					double distA = instance.dist(0, a.name);
					double distB = instance.dist(0, b.name);
					if (Math.abs(distA - distB) < 0.001) {
						return Integer.compare(a.name, b.name);
					}
					return Double.compare(distA, distB);
				});
				break;
		}
	}

	/**
	 * Find best insertion position for customer - Algorithm 3, Lines 4-9
	 * Uses blink rate to skip positions randomly
	 */
	private InsertionPosition findBestInsertionPosition(Node customer) {
		InsertionPosition best = new InsertionPosition();

		// Random route order (Line 5: "in random order")
		List<Integer> routeOrder = new ArrayList<>();
		for (int i = 0; i < numRoutes; i++) {
			routeOrder.add(i);
		}
		Collections.shuffle(routeOrder, rand);

		// Try each route
		for (int routeIdx : routeOrder) {
			Route route = routes[routeIdx];

			// Check capacity (Line 5: "which can serve c")
			if (route.totalDemand + customer.demand > instance.getCapacity()) {
				continue;
			}

			// Try all positions in route (Line 6)
			Node current = route.first;
			int position = 0;

			do {
				// *** BLINK RATE (Line 7) ***
				// "With probability γ, the position is skipped"
				if (rand.nextDouble() < sisrConfig.blinkRate) {
					current = current.next;
					position++;
					continue;  // Skip this position
				}

				// Calculate insertion cost (Line 8)
				double deltaCost = instance.dist(current.name, customer.name) +
				                  instance.dist(customer.name, current.next.name) -
				                  instance.dist(current.name, current.next.name);

				// Update best (Lines 8-9)
				if (deltaCost < best.cost) {
					best.cost = deltaCost;
					best.routeIdx = routeIdx;
					best.position = position;
					best.insertAfter = current;
					best.valid = true;
				}

				current = current.next;
				position++;
			} while (current != route.first);
		}

		return best;
	}

	/**
	 * Insert customer at specified position - Algorithm 3, Lines 13-14
	 */
	private boolean insertCustomerAtPosition(Node customer, InsertionPosition insertPos) {
		if (!insertPos.valid) return false;

		Route route = routes[insertPos.routeIdx];

		// Insert customer (Line 13-14)
		f += route.addAfter(customer, insertPos.insertAfter);
		sisrInsertionStack.add(customer);

		return true;
	}

	/**
	 * Create new route with customer - Algorithm 3, Lines 10-12
	 */
	private void createNewRouteWithCustomer(Node customer) {
		// Find depot node
		Node depot = solution[0];  // Depot is always first

		// Create new route
		Route newRoute = new Route(instance, config, depot, numRoutes);

		// Add customer to new route
		f += newRoute.addAfter(customer, newRoute.first);

		// Add route to solution
		routes[numRoutes] = newRoute;
		numRoutes++;

		// Track insertion
		sisrInsertionStack.add(customer);
	}
}
