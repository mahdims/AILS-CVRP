package PathRelinking;

import Data.Instance;
import Solution.Node;
import Solution.Route;
import Solution.Solution;

import java.util.HashSet;
import java.util.Set;

/**
 * Algorithm 5: Pairing solution routes with solution routes
 *
 * Matches routes from initial solution Si with routes from guide solution Sg
 * based on the number of common vertices (customers).
 *
 * This procedure creates a bijector function phi where route k in Si is paired
 * with route phi(k) in Sg based on maximum vertex overlap.
 *
 * Reference: Paper Section 5.3 - Pairing of Routes
 */
public class RoutePairing {

    private Instance instance;

    public RoutePairing(Instance instance) {
        this.instance = instance;
    }

    /**
     * Pair routes from Si to Sg using greedy matching based on vertex overlap
     *
     * Algorithm 5 from paper:
     * 1. Let phi_si and phi_sg be the solution's route sets si and sg respectively
     * 2. For i = 1...m do
     * 3.   Find route pair (R^si_k, R^sg_l) such that |R^si_k intersect R^sg_l| is maximum
     * 4.   Set phi_si(k) <- i and phi_sg(l) <- j
     * 5.   phi_si <- phi_si \ {k} and phi_sg <- phi_sg \ {l}
     * 6. phi(k) <- l
     *
     * @param si Initial solution
     * @param sg Guide solution
     * @return phi - bijector function where phi[k] = index in sg matched to route k in si
     * @throws IllegalArgumentException if solutions have different number of routes
     */
    public int[] pairRoutes(Solution si, Solution sg) {
        int m = si.getNumRoutes();

        if (m != sg.getNumRoutes()) {
            throw new IllegalArgumentException(
                "Solutions must have same number of routes: " +
                m + " vs " + sg.getNumRoutes()
            );
        }

        int[] phi = new int[m];
        Set<Integer> pairedInSg = new HashSet<>();

        // Line 2: for i = 1...m do
        for (int i = 0; i < m; i++) {
            int bestMatch = -1;
            int maxCommonVertices = -1;

            // Line 3: Find route pair (R^si_i, R^sg_j) such that
            // |R^si_i intersect R^sg_j| = max over all j not yet paired
            for (int j = 0; j < m; j++) {
                // Skip if route j in sg is already paired
                if (pairedInSg.contains(j)) {
                    continue;
                }

                // Count common vertices (intersection size)
                int commonVertices = countCommonVertices(
                    si.getRoutes()[i],
                    sg.getRoutes()[j]
                );

                // Track best match (maximum intersection)
                if (commonVertices > maxCommonVertices) {
                    maxCommonVertices = commonVertices;
                    bestMatch = j;
                }
            }

            // Line 4-5: Set phi(k) <- l and mark as paired
            phi[i] = bestMatch;
            pairedInSg.add(bestMatch);
        }

        // Line 6: Return phi(k) <- l
        return phi;
    }

    /**
     * Count common vertices between two routes (excluding depot)
     *
     * Computes |R1 intersect R2| where R1 and R2 are sets of customer vertices
     *
     * @param r1 First route
     * @param r2 Second route
     * @return Number of common customer vertices
     */
    private int countCommonVertices(Route r1, Route r2) {
        Set<Integer> vertices1 = getRouteVertices(r1);
        Set<Integer> vertices2 = getRouteVertices(r2);

        // Compute intersection
        vertices1.retainAll(vertices2);

        return vertices1.size();
    }

    /**
     * Extract vertex IDs from a route (excluding depot)
     *
     * @param route Route to extract vertices from
     * @return Set of customer vertex IDs (depot excluded)
     */
    private Set<Integer> getRouteVertices(Route route) {
        Set<Integer> vertices = new HashSet<>();
        Node current = route.first.next;

        while (current != route.first) {
            if (current.name > 0) {  // Exclude depot (name = 0)
                vertices.add(current.name);
            }
            current = current.next;
        }

        return vertices;
    }

    /**
     * Print pairing for debugging
     *
     * @param phi Pairing function
     * @param si Initial solution
     * @param sg Guide solution
     */
    public void printPairing(int[] phi, Solution si, Solution sg) {
        System.out.println("Route Pairing (Si -> Sg):");
        System.out.println("-------------------------");

        int totalCommon = 0;
        int totalVertices = 0;

        for (int i = 0; i < phi.length; i++) {
            Set<Integer> verticesSi = getRouteVertices(si.getRoutes()[i]);
            Set<Integer> verticesSg = getRouteVertices(sg.getRoutes()[phi[i]]);

            int commonCount = countCommonVertices(
                si.getRoutes()[i],
                sg.getRoutes()[phi[i]]
            );

            totalCommon += commonCount;
            totalVertices += verticesSi.size();

            System.out.printf("  Route %d -> Route %d: %d common vertices " +
                            "(Si has %d, Sg has %d vertices)\n",
                i, phi[i], commonCount,
                verticesSi.size(), verticesSg.size());
        }

        double matchPercentage = totalVertices > 0
            ? (100.0 * totalCommon / totalVertices)
            : 0.0;

        System.out.printf("Total: %d/%d vertices match (%.1f%%)\n",
            totalCommon, totalVertices, matchPercentage);
        System.out.println("-------------------------");
    }

    /**
     * Validate pairing function
     *
     * Checks that phi is a valid bijection:
     * - Each route in Si is paired to exactly one route in Sg
     * - Each route in Sg is paired from exactly one route in Si
     *
     * @param phi Pairing function to validate
     * @param m Number of routes
     * @return true if valid, false otherwise
     */
    public boolean validatePairing(int[] phi, int m) {
        if (phi.length != m) {
            System.err.println("Pairing validation failed: phi.length != m");
            return false;
        }

        Set<Integer> usedIndices = new HashSet<>();

        for (int i = 0; i < m; i++) {
            int j = phi[i];

            // Check bounds
            if (j < 0 || j >= m) {
                System.err.println("Pairing validation failed: phi[" + i + "] = " + j + " out of bounds");
                return false;
            }

            // Check uniqueness (bijection)
            if (usedIndices.contains(j)) {
                System.err.println("Pairing validation failed: phi[" + i + "] = " + j + " already used");
                return false;
            }

            usedIndices.add(j);
        }

        return true;
    }
}
