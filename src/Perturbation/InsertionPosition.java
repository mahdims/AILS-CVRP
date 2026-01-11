package Perturbation;

import Solution.Route;
import Solution.Node;

/**
 * Stable representation of an insertion position for incremental regret caching
 *
 * Uses (routeId, prevId, nextId) instead of Node references to survive route mutations.
 * This stability is critical for incremental regret computation where cached positions
 * may become stale between recomputation and actual insertion.
 *
 * Design rationale:
 * - Node references can become invalid after insertions/removals
 * - Integer IDs remain stable and can be validated in O(1)
 * - Enables lazy recomputation with structural validation
 */
public class InsertionPosition implements Comparable<InsertionPosition> {
    // Stable identifiers (survive route mutations)
    public final int routeId;       // Route.nameRoute value
    public final int prevId;         // Customer ID of predecessor (0 = depot)
    public final int nextId;         // Customer ID of successor (0 = depot)
    public final double costDelta;   // Insertion cost delta
    public final int customerId;     // Customer being inserted

    /**
     * Create stable insertion position
     *
     * @param route Route containing the insertion edge
     * @param prev Node before insertion point
     * @param next Node after insertion point
     * @param costDelta Cost increase from insertion
     * @param customerId Customer to be inserted
     */
    public InsertionPosition(Route route, Node prev, Node next,
                            double costDelta, int customerId) {
        this.routeId = route.nameRoute;
        this.prevId = prev.name;
        this.nextId = next.name;
        this.costDelta = costDelta;
        this.customerId = customerId;
    }

    /**
     * Validate position is still structurally valid
     *
     * Checks that prev.next == next in the route's current state.
     * This detects if other insertions have invalidated this cached position.
     *
     * FIX: Junior Dev Bug - Added cycle guard to prevent infinite loop on circular routes
     * Circular lists have no null pointers, so we must track when we've returned to start
     *
     * @param route Route to validate against
     * @return true if prev and next are still consecutive in the route
     */
    public boolean isValid(Route route) {
        if (route == null || route.first == null) return false;
        if (route.nameRoute != routeId) return false;

        // Walk route to find prev node with cycle guard
        Node current = route.first;
        Node start = current;
        int iterCount = 0;
        int maxIter = route.numElements + 2; // Safety limit

        while (current != null && iterCount < maxIter) {
            if (current.name == prevId) {
                // Found prev - check if next follows immediately
                return current.next != null && current.next.name == nextId;
            }
            current = current.next;
            iterCount++;

            // Cycle detection: if we've returned to start, stop
            if (current == start && iterCount > 0) {
                break;
            }
        }
        return false;
    }

    /**
     * Find and return the prev node in the route
     * Used when applying insertion after validation
     *
     * FIX: Junior Dev Bug - Added cycle guard to prevent infinite loop on circular routes
     *
     * @param route Route to search
     * @return Prev node, or null if not found
     */
    public Node findPrev(Route route) {
        if (route == null || route.first == null) return null;

        Node current = route.first;
        Node start = current;
        int iterCount = 0;
        int maxIter = route.numElements + 2; // Safety limit

        while (current != null && iterCount < maxIter) {
            if (current.name == prevId) {
                return current;
            }
            current = current.next;
            iterCount++;

            // Cycle detection: if we've returned to start, stop
            if (current == start && iterCount > 0) {
                break;
            }
        }
        return null;
    }

    @Override
    public int compareTo(InsertionPosition other) {
        return Double.compare(this.costDelta, other.costDelta);
    }

    @Override
    public String toString() {
        return String.format("InsertPos[route=%d, (%d->%d), delta=%.2f, cust=%d]",
            routeId, prevId, nextId, costDelta, customerId);
    }
}
