package Perturbation;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;

import Data.Instance;
import Solution.Node;
import Solution.Route;
import Solution.Solution;

/**
 * Regret-based insertion heuristics for CVRP repair operators
 *
 * Implements Regret-2, Regret-3, Regret-4, and RegretRandom from:
 * Ropke, S., & Pisinger, D. (2006). An adaptive large neighborhood search
 * heuristic for the pickup and delivery problem with time windows.
 * Transportation Science, 40(4), 455-472.
 *
 * Key features:
 * - Look-ahead insertion: considers alternative positions before committing
 * - Priority-based: inserts "regretful" customers first (high opportunity cost)
 * - Granular neighborhood: uses existing distance matrix for speedup
 * - Optimized: heap-based top-K selection O(routes*positions*log K)
 *
 * @author Based on Ropke & Pisinger (2006) ALNS framework
 */
public class RegretInsertion {

    private Instance instance;
    private Random rand;
    private int granularLimit; // From existing KNN structure

    /**
     * Insertion candidate with cost information
     */
    public static class InsertionData implements Comparable<InsertionData> {
        public Route route;
        public Node insertAfter;  // Insert customer after this node
        public double costDelta;
        public int customerId;

        public InsertionData(Route route, Node insertAfter, double costDelta, int customerId) {
            this.route = route;
            this.insertAfter = insertAfter;
            this.costDelta = costDelta;
            this.customerId = customerId;
        }

        @Override
        public int compareTo(InsertionData other) {
            return Double.compare(this.costDelta, other.costDelta);
        }
    }

    /**
     * Customer with regret value for priority insertion
     */
    private static class CustomerRegret {
        int customerId;
        InsertionData bestInsertion;

        CustomerRegret(int customerId, InsertionData bestInsertion) {
            this.customerId = customerId;
            this.bestInsertion = bestInsertion;
        }
    }

    public RegretInsertion(Instance instance, int granularLimit) {
        this.instance = instance;
        this.rand = new Random();
        this.granularLimit = granularLimit;
    }

    /**
     * Insert customers using regret-based heuristic (array-based API for Perturbation operators)
     *
     * @param solutionArray Array of all nodes
     * @param routes Array of routes
     * @param numRoutes Number of active routes
     * @param candidates Array of customers to insert
     * @param count Number of customers in array
     * @param heuristic Which regret heuristic to use
     * @return Cost delta from insertions
     */
    public double insertWithRegret(Node[] solutionArray, Route[] routes, int numRoutes,
                                   Node[] candidates, int count, InsertionHeuristic heuristic) {
        // Create list of customers to insert
        List<Integer> customersToInsert = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            customersToInsert.add(candidates[i].name);
        }

        int K = heuristic.getK();
        boolean addNoise = (heuristic == InsertionHeuristic.RegretRandom);

        double totalCostDelta = 0.0;

        // Iteratively insert customers with highest regret
        while (!customersToInsert.isEmpty()) {
            CustomerRegret bestCustomer = null;
            double maxRegret = -Double.MAX_VALUE;

            // Calculate regret for each uninserted customer
            for (int customerId : customersToInsert) {
                Node customer = solutionArray[customerId - 1];

                // Find top K best insertion positions using granular neighborhood
                List<InsertionData> topKPositions = findTopKPositions(solutionArray, routes, numRoutes, customer, K);

                if (topKPositions.isEmpty()) {
                    // Cannot insert anywhere - skip this customer
                    continue;
                }

                // Calculate regret value
                double regret = calculateRegret(topKPositions, K, addNoise);

                if (regret > maxRegret) {
                    maxRegret = regret;
                    bestCustomer = new CustomerRegret(customerId, topKPositions.get(0));
                }
            }

            // Insert customer with highest regret
            if (bestCustomer != null) {
                double costDelta = applyInsertionDirect(solutionArray, bestCustomer.bestInsertion);
                totalCostDelta += costDelta;
                customersToInsert.remove(Integer.valueOf(bestCustomer.customerId));
            } else {
                // No valid insertions found - stop
                break;
            }
        }

        return totalCostDelta;
    }

    /**
     * Insert customers using regret-based heuristic (Solution-based API)
     *
     * @param solution Solution to insert customers into
     * @param candidates Array of customers to insert
     * @param count Number of customers in array
     * @param heuristic Which regret heuristic to use
     */
    public void insertWithRegret(Solution solution, Node[] candidates, int count, InsertionHeuristic heuristic) {
        // Create list of customers to insert
        List<Integer> customersToInsert = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            customersToInsert.add(candidates[i].name);
        }

        int K = heuristic.getK();
        boolean addNoise = (heuristic == InsertionHeuristic.RegretRandom);

        // Iteratively insert customers with highest regret
        while (!customersToInsert.isEmpty()) {
            CustomerRegret bestCustomer = null;
            double maxRegret = -Double.MAX_VALUE;

            // Calculate regret for each uninserted customer
            for (int customerId : customersToInsert) {
                Node customer = solution.getSolution()[customerId - 1];

                // Find top K best insertion positions using granular neighborhood
                List<InsertionData> topKPositions = findTopKPositions(solution, customer, K);

                if (topKPositions.isEmpty()) {
                    // Cannot insert anywhere - force insertion or handle infeasibility
                    // For now, skip this customer (should not happen in perturbation)
                    continue;
                }

                // Calculate regret value
                double regret = calculateRegret(topKPositions, K, addNoise);

                if (regret > maxRegret) {
                    maxRegret = regret;
                    bestCustomer = new CustomerRegret(customerId, topKPositions.get(0));
                }
            }

            // Insert customer with highest regret
            if (bestCustomer != null) {
                applyInsertion(solution, bestCustomer.bestInsertion);
                customersToInsert.remove(Integer.valueOf(bestCustomer.customerId));
            } else {
                // No valid insertions found - stop
                break;
            }
        }
    }

    /**
     * Calculate regret value based on top K positions
     *
     * @param topK List of top K insertion positions (sorted by cost)
     * @param K Number of positions to consider
     * @param addNoise Whether to add random noise (RegretRandom)
     * @return Regret value
     */
    private double calculateRegret(List<InsertionData> topK, int K, boolean addNoise) {
        if (topK.size() == 0) {
            return 0;
        }

        if (topK.size() == 1) {
            // Only one feasible position - extremely high regret
            return 1e9;
        }

        double regret = 0;
        double bestCost = topK.get(0).costDelta;

        // Regret-K: sum of (j-th best - best) for j = 2..K
        for (int j = 1; j < Math.min(K, topK.size()); j++) {
            regret += (topK.get(j).costDelta - bestCost);
        }

        // Add random noise for diversification (RegretRandom)
        if (addNoise && regret > 0) {
            double noise = rand.nextDouble() * regret * 0.1; // 10% noise
            regret += noise;
        }

        return regret;
    }

    /**
     * Find top K best insertion positions (array-based for Perturbation operators)
     * Uses KNN structure for efficiency (same as greedy insertion)
     *
     * @param solutionArray Array of all nodes
     * @param routes Array of routes
     * @param numRoutes Number of active routes
     * @param customer Customer to insert
     * @param K Number of best positions to track
     * @return List of K best InsertionData, sorted by costDelta (ascending)
     */
    private List<InsertionData> findTopKPositions(Node[] solutionArray, Route[] routes, int numRoutes,
                                                   Node customer, int K) {
        // Max-heap to track top K
        PriorityQueue<InsertionData> topK = new PriorityQueue<>(
            K + 1,
            (a, b) -> Double.compare(b.costDelta, a.costDelta)
        );

        int customerId = customer.name;

        // Use KNN structure to limit search (same strategy as greedy insertion)
        // Only check positions near customer's K nearest neighbors
        int knnChecked = 0;
        for (int i = 0; i < customer.knn.length && knnChecked < granularLimit; i++) {
            if (customer.knn[i] == 0) {
                // Depot - try insertion at start of all routes
                for (int r = 0; r < numRoutes; r++) {
                    if (routes[r].first != null && routes[r].totalDemand + customer.demand <= instance.getCapacity()) {
                        tryInsertion(routes[r].first, customer, customerId, topK, K);
                    }
                }
                knnChecked++;
            } else {
                // Check neighbor node
                Node neighbor = solutionArray[customer.knn[i] - 1];
                if (neighbor.nodeBelong && neighbor.route != null) {
                    // Check capacity before trying insertion
                    if (neighbor.route.totalDemand + customer.demand <= instance.getCapacity()) {
                        // Try inserting before and after the neighbor
                        tryInsertion(neighbor.prev, customer, customerId, topK, K);
                        tryInsertion(neighbor, customer, customerId, topK, K);
                    }
                    knnChecked++;
                }
            }
        }

        // Convert heap to sorted list
        List<InsertionData> result = new ArrayList<>(topK);
        result.sort((a, b) -> Double.compare(a.costDelta, b.costDelta));

        // Fallback: if KNN didn't find any positions, try all routes
        if (result.isEmpty()) {
            for (int r = 0; r < numRoutes; r++) {
                Route route = routes[r];
                if (route.first != null && route.totalDemand + customer.demand <= instance.getCapacity()) {
                    Node current = route.first;
                    while (current != null && current.next != null) {
                        tryInsertion(current, customer, customerId, topK, K);
                        current = current.next;
                    }
                }
            }
            result = new ArrayList<>(topK);
            result.sort((a, b) -> Double.compare(a.costDelta, b.costDelta));
        }

        return result;
    }

    /**
     * Try inserting customer after a given node and add to top-K heap if good
     */
    private void tryInsertion(Node insertAfter, Node customer, int customerId,
                             PriorityQueue<InsertionData> topK, int K) {
        // Validate insertion position
        if (insertAfter == null || insertAfter.next == null || insertAfter.route == null) {
            return;
        }

        // Additional validation: ensure route has capacity
        if (insertAfter.route.totalDemand + customer.demand > instance.getCapacity()) {
            return;
        }

        Node next = insertAfter.next;

        // Calculate insertion cost
        double costBefore = instance.dist(insertAfter.name, next.name);
        double costAfter = instance.dist(insertAfter.name, customerId)
                         + instance.dist(customerId, next.name);
        double costDelta = costAfter - costBefore;

        InsertionData insertion = new InsertionData(insertAfter.route, insertAfter, costDelta, customerId);

        topK.offer(insertion);
        if (topK.size() > K) {
            topK.poll();
        }
    }

    /**
     * Find top K best insertion positions for a customer
     * Uses KNN structure for efficiency (same as greedy insertion)
     *
     * @param solution Current solution
     * @param customer Customer to insert
     * @param K Number of best positions to track
     * @return List of K best InsertionData, sorted by costDelta (ascending)
     */
    private List<InsertionData> findTopKPositions(Solution solution, Node customer, int K) {
        // Delegate to array-based version
        return findTopKPositions(solution.getSolution(), solution.getRoutes(),
                                solution.getNumRoutes(), customer, K);
    }

    /**
     * Apply insertion directly to arrays (for Perturbation operators)
     *
     * @param solutionArray Array of all nodes
     * @param insertion Insertion data
     * @return Cost delta from insertion
     */
    private double applyInsertionDirect(Node[] solutionArray, InsertionData insertion) {
        Node customer = solutionArray[insertion.customerId - 1];
        Node insertAfter = insertion.insertAfter;
        Route route = insertion.route;

        // Use Route's addAfter method to ensure proper bookkeeping
        // This handles: node pointers, modified flags, numElements, fRoute, depot handling
        double actualCost = route.addAfter(customer, insertAfter);

        return actualCost;
    }

    /**
     * Apply insertion to solution
     *
     * @param solution Solution to modify
     * @param insertion Insertion data
     */
    private void applyInsertion(Solution solution, InsertionData insertion) {
        Node customer = solution.getSolution()[insertion.customerId - 1];
        Node insertAfter = insertion.insertAfter;
        Route route = insertion.route;

        // Insert customer after insertAfter node
        customer.next = insertAfter.next;
        customer.prev = insertAfter;

        if (insertAfter.next != null) {
            insertAfter.next.prev = customer;
        }
        insertAfter.next = customer;

        // Update route membership
        customer.route = route;
        route.totalDemand += customer.demand;

        // Update cost
        solution.f += insertion.costDelta;

        // Mark route as modified for local search
        route.modified = true;
    }
}
