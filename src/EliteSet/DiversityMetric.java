package EliteSet;

import Solution.Solution;
import Solution.Node;
import Solution.Route;
import java.util.HashSet;

/**
 * Calculates edge-based distance between two CVRP solutions
 *
 * Distance metric: Edge-based symmetric difference
 * - Extracts all edges (customer-to-customer connections) from each solution
 * - Computes |edges1 XOR edges2| / |edges1 UNION edges2|
 * - Returns value in [0, 1] where 0 = identical, 1 = completely different
 */
public class DiversityMetric {

    /**
     * Edge representation as a pair of node IDs
     * Normalized to be undirected (smaller ID always comes first)
     */
    private static class Edge {
        int from, to;

        Edge(int from, int to) {
            // Normalize: always store smaller ID first for undirected edges
            if (from <= to) {
                this.from = from;
                this.to = to;
            } else {
                this.from = to;
                this.to = from;
            }
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Edge)) return false;
            Edge other = (Edge) obj;
            return this.from == other.from && this.to == other.to;
        }

        @Override
        public int hashCode() {
            return 31 * from + to;
        }

        @Override
        public String toString() {
            return "(" + from + "," + to + ")";
        }
    }

    /**
     * Extract all edges from a solution
     * Traverses all routes and collects customer-to-customer edges
     *
     * @param sol The solution to extract edges from
     * @return Set of all edges in the solution
     */
    private HashSet<Edge> extractEdges(Solution sol) {
        HashSet<Edge> edges = new HashSet<>();

        for (int r = 0; r < sol.numRoutes; r++) {
            Route route = sol.routes[r];
            if (route == null || route.first == null) continue;

            Node current = route.first.next;  // Start after depot

            // Traverse route and collect edges
            // Note: We use numElements from the route as the limit to avoid infinite loops
            int maxNodes = (route.numElements > 0) ? route.numElements + 10 : 1000;
            int nodeCount = 0;

            while (current != null && current.next != null && nodeCount < maxNodes) {
                edges.add(new Edge(current.name, current.next.name));
                current = current.next;
                nodeCount++;
            }
        }

        return edges;
    }

    /**
     * Calculate edge-based distance between two solutions
     *
     * Distance = |edges1 XOR edges2| / |edges1 UNION edges2|
     *
     * The symmetric difference (XOR) counts edges that appear in one solution
     * but not the other. Normalizing by the union gives a value in [0, 1].
     *
     * @param sol1 First solution
     * @param sol2 Second solution
     * @return Distance in [0, 1] where 0 = identical, 1 = completely different
     */
    public double calculateDistance(Solution sol1, Solution sol2) {
        HashSet<Edge> edges1 = extractEdges(sol1);
        HashSet<Edge> edges2 = extractEdges(sol2);

        // Handle edge cases
        if (edges1.isEmpty() && edges2.isEmpty()) return 0.0;
        if (edges1.isEmpty() || edges2.isEmpty()) return 1.0;

        // Calculate union size
        HashSet<Edge> union = new HashSet<>(edges1);
        union.addAll(edges2);
        int unionSize = union.size();

        // Calculate intersection
        HashSet<Edge> intersection = new HashSet<>(edges1);
        intersection.retainAll(edges2);
        int intersectionSize = intersection.size();

        // XOR size = Union size - Intersection size
        int xorSize = unionSize - intersectionSize;

        // Avoid division by zero (shouldn't happen given edge cases above)
        if (unionSize == 0) return 0.0;

        return (double) xorSize / unionSize;
    }

    /**
     * Alternative distance metric: Normalized by maximum edges
     *
     * Distance = |edges1 XOR edges2| / max(|edges1|, |edges2|)
     *
     * This variant normalizes by the larger solution rather than the union.
     * Can be more appropriate when solutions have very different numbers of edges.
     *
     * @param sol1 First solution
     * @param sol2 Second solution
     * @return Distance in [0, 1+] (can exceed 1 if solutions are very different)
     */
    public double calculateDistanceNormalized(Solution sol1, Solution sol2) {
        HashSet<Edge> edges1 = extractEdges(sol1);
        HashSet<Edge> edges2 = extractEdges(sol2);

        // Calculate symmetric difference
        HashSet<Edge> diff1 = new HashSet<>(edges1);
        diff1.removeAll(edges2);

        HashSet<Edge> diff2 = new HashSet<>(edges2);
        diff2.removeAll(edges1);

        int xorSize = diff1.size() + diff2.size();
        int maxEdges = Math.max(edges1.size(), edges2.size());

        if (maxEdges == 0) return 0.0;

        return (double) xorSize / maxEdges;
    }

    /**
     * Get edge count for a solution (for debugging/statistics)
     *
     * @param sol The solution
     * @return Number of edges in the solution
     */
    public int getEdgeCount(Solution sol) {
        return extractEdges(sol).size();
    }

    /**
     * Calculate Jaccard similarity between two solutions
     *
     * Jaccard = |intersection| / |union|
     * This is the complement of our distance metric: distance = 1 - jaccard
     *
     * @param sol1 First solution
     * @param sol2 Second solution
     * @return Jaccard similarity in [0, 1] where 1 = identical, 0 = no overlap
     */
    public double calculateJaccardSimilarity(Solution sol1, Solution sol2) {
        HashSet<Edge> edges1 = extractEdges(sol1);
        HashSet<Edge> edges2 = extractEdges(sol2);

        if (edges1.isEmpty() && edges2.isEmpty()) return 1.0;

        HashSet<Edge> union = new HashSet<>(edges1);
        union.addAll(edges2);

        HashSet<Edge> intersection = new HashSet<>(edges1);
        intersection.retainAll(edges2);

        if (union.isEmpty()) return 0.0;

        return (double) intersection.size() / union.size();
    }
}
