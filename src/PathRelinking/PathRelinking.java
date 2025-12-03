package PathRelinking;

import Data.Instance;
import Improvement.IntraLocalSearch;
import Improvement.LocalSearch;
import SearchMethod.Config;
import Solution.Node;
import Solution.Route;
import Solution.Solution;

import java.util.*;

/**
 * Algorithm 4: PathRelinking
 *
 * Builds a path from initial solution to guide solution by
 * iteratively moving vertices to match the guide solution's structure.
 *
 * The algorithm:
 * 1. Selects two solutions with same number of routes
 * 2. Randomly assigns initial (si) and guide (sg) roles
 * 3. Pairs routes using Algorithm 5 (RoutePairing)
 * 4. Builds set NF of vertices in wrong routes
 * 5. Iteratively moves vertices based on priority criteria
 * 6. Tracks best solution found along path
 * 7. Applies local search to best solution
 *
 * Reference: Paper Algorithm 4 - PathRelinking
 */
public class PathRelinking {

    private Instance instance;
    private Config config;
    private RoutePairing routePairing;
    private LocalSearch localSearch;
    private Random random;
    private int lastMoveCount = 0; // Track moves from last PR execution

    public PathRelinking(Instance instance, Config config,
            IntraLocalSearch intraLS) {
        this.instance = instance;
        this.config = config;
        this.routePairing = new RoutePairing(instance);
        this.localSearch = new LocalSearch(instance, config, intraLS);
        this.random = new Random();
    }

    /**
     * Apply path relinking between two solutions
     *
     * Algorithm 4 implementation:
     * Input: Solution s with m routes
     * Result: The best solution s^b found in the PR search
     *
     * @param sCurrent Current solution (new best from AILS)
     * @param sElite   Elite solution from elite set
     * @return Best solution found during path relinking, or null if error
     */
    public Solution pathRelink(Solution sCurrent, Solution sElite) {
        // Verify same number of routes (required for PR)
        if (sCurrent.getNumRoutes() != sElite.getNumRoutes()) {
            System.err.println("[PR] Cannot relink: Different #routes (" +
                    sCurrent.getNumRoutes() + " vs " + sElite.getNumRoutes() + ")");
            return null;
        }

        int m = sCurrent.getNumRoutes();

        // Line 1: Randomly choose s' in E_m (done by caller)

        // Line 2: Randomly choose which solution will be initial (si) and guide (sg)
        Solution si = new Solution(instance, config);
        Solution sg = new Solution(instance, config);

        if (random.nextBoolean()) {
            si.clone(sCurrent);
            sg.clone(sElite);
        } else {
            si.clone(sElite);
            sg.clone(sCurrent);
        }

        // Line 3: Find the matching phi between the routes from si and sg
        int[] phi = routePairing.pairRoutes(si, sg);

        // Validate pairing
        if (!routePairing.validatePairing(phi, m)) {
            System.err.println("[PR] Invalid route pairing");
            return null;
        }

        // Line 4: Make NF = union_{k in [1..m]} {R^si_k intersect R^sg_{phi(k)}}
        Set<Integer> NF = calculateNF(si, sg, phi);

        // Line 5: s^b <- si
        Solution sb = new Solution(instance, config);
        sb.clone(si);

        // Line 6: Choose randomly a combination C in C
        PriorityCriteria C = PriorityCriteria.random();

        // Track statistics
        int moveCount = 0;
        int improvementCount = 0;
        double initialF = si.f;

        // Line 7: while |NF| > 0 do
        while (!NF.isEmpty()) {
            // Line 8: Calculate the priority p_v according to the criterion C
            // for all v in NF
            Map<Integer, Double> priorities = calculatePriorities(NF, si, phi, C);

            if (priorities.isEmpty()) {
                System.err.println("[PR] No valid priorities calculated");
                break;
            }

            // Line 9: Let v_hat in NF be the vertex with the highest priority
            // and suppose that v_hat in R^si_k
            int vHat = selectVertexWithHighestPriority(priorities, si, sg, phi);

            if (vHat == -1) {
                System.err.println("[PR] Warning: No valid vertex found in NF");
                break;
            }

            // Line 10: Move v_hat for R^si_{phi(k)} at the position i that results
            // in the minimum cost solution to si, according to Equation (3)
            Node vertexToMove = si.getSolution()[vHat - 1];
            Route originRoute = vertexToMove.route;

            if (originRoute == null) {
                System.err.println("[PR] Warning: Vertex " + vHat + " has no route");
                NF.remove(vHat);
                continue;
            }

            int originRouteIdx = originRoute.nameRoute;
            Route targetRoute = si.getRoutes()[phi[originRouteIdx]];

            // Perform the move
            moveVertexToMinimumCostPosition(si, vertexToMove, targetRoute);
            moveCount++;

            // Line 11: NF <- NF \ {v_hat}
            NF.remove(vHat);

            // Line 12-13: if si is feasible and f(si) < f(s^b) then
            if (si.feasible() && si.f < sb.f) {
                // s^b <- si
                sb.clone(si);
                improvementCount++;
            }
        }

        // Line 16: Apply the local search to s^b
        long lsStart = System.currentTimeMillis();
        localSearch.localSearch(sb, true);

        long lsTime = System.currentTimeMillis() - lsStart;
        if (lsTime > 10000) { // Only log if LS took > 1 second
            System.out.printf("[PR-LS] Local search took %.1fs (f:%.2f->%.2f moves:%d)\n",
                    lsTime / 1000.0, initialF, sb.f, moveCount);
        }
        // Line 17: Update E considering solution s^b (done by caller)

        // Store move count for statistics
        this.lastMoveCount = moveCount;

        // Return best solution found
        return sb;
    }

    /**
     * Calculate NF set: all vertices in si that are not in their target routes in
     * sg
     *
     * NF = union over all routes k of (vertices in R^si_k but not in R^sg_{phi(k)})
     *
     * @param si  Initial solution
     * @param sg  Guide solution
     * @param phi Route pairing function
     * @return Set of vertex IDs that need to be moved
     */
    private Set<Integer> calculateNF(Solution si, Solution sg, int[] phi) {
        Set<Integer> NF = new HashSet<>();

        for (int k = 0; k < si.getNumRoutes(); k++) {
            Route routeSi = si.getRoutes()[k];
            Route routeSg = sg.getRoutes()[phi[k]];

            // Get vertices in each route
            Set<Integer> verticesSi = getRouteVertices(routeSi);
            Set<Integer> verticesSg = getRouteVertices(routeSg);

            // Add vertices that are in si route but not in matched sg route
            for (int v : verticesSi) {
                if (!verticesSg.contains(v)) {
                    NF.add(v);
                }
            }
        }

        return NF;
    }

    /**
     * Calculate priorities for all vertices in NF using given criterion
     *
     * @param NF        Set of vertices to prioritize
     * @param si        Current solution
     * @param phi       Route pairing
     * @param criterion Priority criterion to use
     * @return Map of vertex ID to priority score
     */
    private Map<Integer, Double> calculatePriorities(
            Set<Integer> NF,
            Solution si,
            int[] phi,
            PriorityCriteria criterion) {
        Map<Integer, Double> priorities = new HashMap<>();

        for (int vertexId : NF) {
            Node v = si.getSolution()[vertexId - 1];

            // Skip if vertex has no route
            if (v.route == null) {
                continue;
            }

            Route originRoute = v.route;
            int originRouteIdx = originRoute.nameRoute;
            Route destRoute = si.getRoutes()[phi[originRouteIdx]];

            // Calculate movement cost
            double movementCost = calculateMovementCost(v, destRoute);

            // Calculate priority using criterion
            double priority = criterion.calculatePriority(
                    v,
                    originRoute,
                    destRoute,
                    instance.getCapacity(),
                    movementCost);

            priorities.put(vertexId, priority);
        }

        return priorities;
    }

    /**
     * Select vertex with highest priority
     * Ties broken by minimum cost (Equation 3)
     *
     * @param priorities Map of vertex priorities
     * @param si         Current solution
     * @param sg         Guide solution
     * @param phi        Route pairing
     * @return Vertex ID with highest priority, or -1 if none found
     */
    private int selectVertexWithHighestPriority(
            Map<Integer, Double> priorities,
            Solution si,
            Solution sg,
            int[] phi) {
        int bestVertex = -1;
        double highestPriority = Double.NEGATIVE_INFINITY;
        double lowestCost = Double.MAX_VALUE;

        for (Map.Entry<Integer, Double> entry : priorities.entrySet()) {
            int vertexId = entry.getKey();
            double priority = entry.getValue();

            Node v = si.getSolution()[vertexId - 1];

            if (v.route == null) {
                continue;
            }

            int originRouteIdx = v.route.nameRoute;
            Route targetRoute = si.getRoutes()[phi[originRouteIdx]];
            double cost = calculateMovementCost(v, targetRoute);

            // Select by priority first, break ties by cost
            boolean betterPriority = priority > highestPriority;
            boolean samePriorityLowerCost = (Math.abs(priority - highestPriority) < 0.0001) && (cost < lowestCost);

            if (betterPriority || samePriorityLowerCost) {
                bestVertex = vertexId;
                highestPriority = priority;
                lowestCost = cost;
            }
        }

        return bestVertex;
    }

    /**
     * Move vertex to target route at minimum cost position
     * Implements Equation (3) from paper:
     *
     * i_hat = argmin_{i in [1..m]} min_{l in [0..n_i]} d(v'_l, v) + d(v, v'_{l+1})
     * - d(v'_l, v'_{l+1})
     *
     * @param sol         Solution to modify
     * @param vertex      Vertex to move
     * @param targetRoute Destination route
     */
    private void moveVertexToMinimumCostPosition(
            Solution sol,
            Node vertex,
            Route targetRoute) {
        // Remove from current route
        Route currentRoute = vertex.route;
        if (currentRoute != null) {
            sol.f += currentRoute.remove(vertex);
        }

        // Find best insertion position in target route
        Node bestPosition = targetRoute.findBestPosition(vertex);

        // Insert at best position
        sol.f += targetRoute.addAfter(vertex, bestPosition);
    }

    /**
     * Calculate cost of moving vertex to target route (minimum cost)
     * Equation (3): argmin over all positions
     *
     * @param vertex      Vertex to move
     * @param targetRoute Target route
     * @return Minimum insertion cost
     */
    private double calculateMovementCost(Node vertex, Route targetRoute) {
        Node bestPosition = targetRoute.findBestPosition(vertex);
        return vertex.costInsertAfter(bestPosition);
    }

    /**
     * Get set of vertex IDs in a route (excluding depot)
     *
     * @param route Route to extract vertices from
     * @return Set of customer vertex IDs
     */
    private Set<Integer> getRouteVertices(Route route) {
        Set<Integer> vertices = new HashSet<>();
        Node current = route.first.next;

        while (current != route.first) {
            if (current.name > 0) { // Exclude depot
                vertices.add(current.name);
            }
            current = current.next;
        }

        return vertices;
    }

    /**
     * Get the number of moves performed in the last path relinking execution
     */
    public int getLastMoveCount() {
        return lastMoveCount;
    }
}
