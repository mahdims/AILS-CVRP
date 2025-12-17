package Perturbation;

import java.util.*;
import Solution.Node;
import Solution.Route;
import Data.Instance;

/**
 * Incremental regret cache with lazy recomputation
 *
 * Implements efficient invalidation using:
 * - reverseKNN: Track which customers have each node in their KNN
 * - routeWatchers: Track which customers are watching each route
 *
 * Achieves O(n . k_nn . log M) complexity instead of O(n^2 . k_nn . log K)
 * by only recomputing regrets for affected customers after each insertion.
 *
 * Based on the plan combining:
 * - Stable position representation (routeId, prevId, nextId)
 * - Lazy priority queue with versioning
 * - Dual-index invalidation (reverseKNN + routeWatchers)
 */
public class RegretCache {
    // Per-customer state
    private CustomerRegretState[] states; // Indexed by customerId - 1

    // Invalidation indices
    private List<Integer>[] reverseKNN; // reverseKNN[nodeId] = customers with nodeId in KNN
    private Set<Integer>[] routeWatchers; // routeWatchers[routeId] = customers watching this route

    // Priority queue (lazy, with versioning)
    private PriorityQueue<HeapEntry> pq;

    // Reusable buffers (avoid allocation in hot path)
    private boolean[] affectedMark; // Mark array for affected customers
    private int[] affectedBuffer; // Buffer for affected customer IDs

    // Configuration
    public final int M; // Cache size per customer (M = K + buffer)
    private final int maxCustomers;
    private final int maxRoutes;

    // Dependencies
    private Instance instance;
    private int granularLimit;

    /**
     * Heap entry for lazy priority queue
     * Uses versioning to detect stale entries without expensive decrease-key
     * operations
     */
    private static class HeapEntry implements Comparable<HeapEntry> {
        int customerId;
        double regretSnapshot;
        long versionSnapshot;

        HeapEntry(int customerId, double regret, long version) {
            this.customerId = customerId;
            this.regretSnapshot = regret;
            this.versionSnapshot = version;
        }

        @Override
        public int compareTo(HeapEntry other) {
            // Max-heap: higher regret first
            int cmp = Double.compare(other.regretSnapshot, this.regretSnapshot);
            if (cmp != 0)
                return cmp;
            // Tie-break: lower customer ID (deterministic)
            return Integer.compare(this.customerId, other.customerId);
        }
    }

    /**
     * Create regret cache
     *
     * @param maxCustomers  Maximum number of customers in instance
     * @param maxRoutes     Maximum number of routes
     * @param K             Number of positions for regret calculation
     * @param instance      Problem instance (for distance calculations)
     * @param granularLimit KNN limit for candidate generation
     */
    @SuppressWarnings("unchecked")
    public RegretCache(int maxCustomers, int maxRoutes, int K, Instance instance, int granularLimit) {
        this.maxCustomers = maxCustomers;
        this.maxRoutes = maxRoutes;
        this.M = Math.max(K + 3, 10); // M = K + 3 buffer, min 10
        this.instance = instance;
        this.granularLimit = granularLimit;

        // Allocate state array
        this.states = new CustomerRegretState[maxCustomers];
        for (int i = 0; i < maxCustomers; i++) {
            states[i] = new CustomerRegretState(M);
        }

        // Allocate indices (will be populated during init)
        this.reverseKNN = (List<Integer>[]) new List[maxCustomers + 1]; // +1 for depot (id=0)
        for (int i = 0; i <= maxCustomers; i++) {
            reverseKNN[i] = new ArrayList<>();
        }

        this.routeWatchers = (Set<Integer>[]) new Set[maxRoutes];
        for (int i = 0; i < maxRoutes; i++) {
            routeWatchers[i] = new HashSet<>();
        }

        // Allocate reusable buffers
        this.affectedMark = new boolean[maxCustomers];
        this.affectedBuffer = new int[maxCustomers];

        // Create priority queue
        this.pq = new PriorityQueue<>(maxCustomers);
    }

    /**
     * Build reverse KNN index (call once during initialization)
     *
     * @param candidates Array of customers to insert
     * @param count      Number of customers in array
     */
    public void buildReverseKNN(Node[] candidates, int count) {
        for (int i = 0; i < count; i++) {
            Node customer = candidates[i];
            int customerId = customer.name;

            // For each neighbor in KNN
            for (int j = 0; j < customer.knn.length && j < granularLimit; j++) {
                int neighborId = customer.knn[j];
                if (neighborId >= 0 && neighborId <= maxCustomers) {
                    reverseKNN[neighborId].add(customerId);
                }
            }
        }
    }

    /**
     * Add customer to route watchers
     *
     * @param customerId Customer ID
     * @param routeIds   Array of route IDs to watch
     * @param count      Number of routes in array
     */
    public void addToWatchers(int customerId, int[] routeIds, int count) {
        for (int i = 0; i < count; i++) {
            int routeId = routeIds[i];
            if (routeId >= 0 && routeId < maxRoutes) {
                routeWatchers[routeId].add(customerId);
            }
        }
    }

    /**
     * Remove customer from route watchers
     *
     * @param customerId Customer ID
     * @param routeIds   Array of route IDs to unwatch
     * @param count      Number of routes in array
     */
    public void removeFromWatchers(int customerId, int[] routeIds, int count) {
        for (int i = 0; i < count; i++) {
            int routeId = routeIds[i];
            if (routeId >= 0 && routeId < maxRoutes) {
                routeWatchers[routeId].remove(customerId);
            }
        }
    }

    /**
     * Get customer state
     *
     * @param customerId Customer ID (1-indexed)
     * @return Customer regret state
     */
    public CustomerRegretState getState(int customerId) {
        return states[customerId - 1];
    }

    /**
     * Push customer into priority queue
     *
     * @param customerId Customer ID
     */
    public void pushToPQ(int customerId) {
        CustomerRegretState state = states[customerId - 1];
        pq.offer(new HeapEntry(customerId, state.regretValue, state.version));
    }

    /**
     * Pop highest regret customer (lazy, validates version)
     *
     * @return Customer ID, or -1 if no valid customer found
     */
    public int popHighestRegret() {
        while (!pq.isEmpty()) {
            HeapEntry entry = pq.poll();
            CustomerRegretState state = states[entry.customerId - 1];

            // Validate entry
            if (!state.active)
                continue; // Already inserted
            if (entry.versionSnapshot != state.version)
                continue; // Stale

            return entry.customerId;
        }
        return -1; // No customers left
    }

    /**
     * Invalidate customers affected by insertion
     *
     * Uses reverseKNN + routeWatchers for O(k_nn + watchers) complexity
     *
     * @param insertedCustomerId Customer that was just inserted
     * @param insertion          Position where customer was inserted
     * @param solutionArray      Array of all nodes (for KNN access)
     */
    public void invalidateAffected(int insertedCustomerId, InsertionPosition insertion,
            Node[] solutionArray) {
        // Clear mark array
        Arrays.fill(affectedMark, false);
        int affectedCount = 0;

        // Strategy 1: Customers with {prev, next, inserted} in their KNN
        int prevId = insertion.prevId;
        int nextId = insertion.nextId;

        affectedCount = addAffected(reverseKNN[prevId], affectedCount);
        affectedCount = addAffected(reverseKNN[nextId], affectedCount);
        affectedCount = addAffected(reverseKNN[insertedCustomerId], affectedCount);

        // Strategy 2: Customers watching the affected route
        int routeId = insertion.routeId;
        if (routeId >= 0 && routeId < maxRoutes) {
            affectedCount = addAffected(routeWatchers[routeId], affectedCount);
        }

        // Strategy 3: Customers in inserted customer's KNN (spatial proximity)
        Node insertedNode = solutionArray[insertedCustomerId - 1];
        for (int i = 0; i < insertedNode.knn.length && i < granularLimit; i++) {
            int neighborId = insertedNode.knn[i];
            if (neighborId > 0 && neighborId <= maxCustomers) {
                CustomerRegretState state = states[neighborId - 1];
                if (state.active && !affectedMark[neighborId - 1]) {
                    affectedMark[neighborId - 1] = true;
                    affectedBuffer[affectedCount++] = neighborId;
                }
            }
        }

        // Mark all affected as invalid
        for (int i = 0; i < affectedCount; i++) {
            int customerId = affectedBuffer[i];
            states[customerId - 1].invalidate();
        }

        // Check for massive invalidation (>50% of remaining customers)
        // If so, rebuild PQ to avoid pollution
        int remainingActive = 0;
        for (CustomerRegretState state : states) {
            if (state.active)
                remainingActive++;
        }

        if (affectedCount > remainingActive * 0.5) {
            rebuildPQ();
        }
    }

    /**
     * Add customers from a collection to affected set (deduplicates using mark
     * array)
     *
     * @param customers     Collection of customer IDs
     * @param affectedCount Current count of affected customers
     * @return Updated count
     */
    private int addAffected(Collection<Integer> customers, int affectedCount) {
        for (int customerId : customers) {
            if (customerId < 1 || customerId > maxCustomers)
                continue;
            CustomerRegretState state = states[customerId - 1];
            if (state.active && !affectedMark[customerId - 1]) {
                affectedMark[customerId - 1] = true;
                affectedBuffer[affectedCount++] = customerId;
            }
        }
        return affectedCount;
    }

    /**
     * Rebuild priority queue from scratch (for massive invalidation)
     */
    private void rebuildPQ() {
        pq.clear();
        for (int i = 0; i < states.length; i++) {
            CustomerRegretState state = states[i];
            if (state.active) {
                pq.offer(new HeapEntry(i + 1, state.regretValue, state.version));
            }
        }
    }

    /**
     * Mark customer as inserted (deactivate)
     *
     * @param customerId Customer ID
     */
    public void markInserted(int customerId) {
        states[customerId - 1].deactivate();
    }

    /**
     * Check if any customers remain
     *
     * @return true if priority queue is not empty
     */
    public boolean hasCustomers() {
        return !pq.isEmpty();
    }

    /**
     * Get number of active customers remaining
     *
     * @return Count of active customers
     */
    public int getActiveCount() {
        int count = 0;
        for (CustomerRegretState state : states) {
            if (state.active)
                count++;
        }
        return count;
    }
}
