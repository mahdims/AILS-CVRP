package Perturbation;

import java.util.List;
import java.util.ArrayList;

/**
 * Cached regret computation state for one customer
 *
 * Tracks validity, version, and watched routes for efficient incremental
 * invalidation.
 * This is the core data structure enabling O(n . k_nn) complexity instead of
 * O(n^2).
 *
 * Key design elements:
 * - version: Monotonically increasing, enables lazy PQ with staleness detection
 * - valid: Fast invalidation flag (no recomputation until needed)
 * - watchedRoutes: Enables O(affected) invalidation instead of O(all customers)
 */
public class CustomerRegretState {
    // Validity tracking
    public boolean active; // Still uninserted
    public boolean valid; // Cache is current (not stale)
    public long version; // Incremented on each recomputation

    // Regret data
    public double regretValue; // Computed regret
    public InsertionPosition best; // Best insertion position (c1)
    public List<InsertionPosition> topM; // Top M positions (sorted by cost ascending)

    // Watcher tracking (for efficient invalidation)
    public int[] watchedRoutes; // Route IDs in topM (for unwatching)
    public int watchedCount; // Number of routes being watched

    // Configuration
    private final int M; // Cache size (M >= K)

    /**
     * Create state for a customer
     *
     * @param M Number of positions to cache (typically K + 3 to K + 5)
     */
    public CustomerRegretState(int M) {
        this.M = M;
        this.active = true;
        this.valid = false;
        this.version = 0;
        this.regretValue = 0.0;
        this.best = null;
        this.topM = new ArrayList<>(M);
        this.watchedRoutes = new int[M]; // Max M routes can be watched
        this.watchedCount = 0;
    }

    /**
     * Update state after recomputation
     *
     * FIX: Junior Dev Bug #3 - Now accepts shared route mark array to avoid allocation churn
     *
     * @param regret         Newly computed regret value
     * @param positions      Top M insertion positions (sorted by cost)
     * @param routeMarkArray Reusable boolean array for route deduplication
     * @param maxRoutes      Maximum number of routes (size of mark array)
     */
    public void update(double regret, List<InsertionPosition> positions,
                       boolean[] routeMarkArray, int maxRoutes) {
        this.regretValue = regret;
        this.topM.clear();
        this.topM.addAll(positions);
        this.best = positions.isEmpty() ? null : positions.get(0);
        this.valid = true;
        this.version++;

        // Update watched routes with shared mark array (avoids allocation)
        updateWatchedRoutes(routeMarkArray, maxRoutes);
    }

    /**
     * Mark cache as invalid (needs recomputation)
     */
    public void invalidate() {
        this.valid = false;
    }

    /**
     * Extract unique route IDs from topM for watcher maintenance
     *
     * FIX: Junior Dev Bug #3 - Now uses shared reusable mark array instead of allocating
     *
     * OLD: Allocated new boolean[10000] on EVERY call → massive GC overhead
     * NEW: Reuses shared array from RegretCache → zero allocation overhead
     *
     * @param routeMarkArray Reusable boolean array for deduplication (from RegretCache)
     * @param maxRoutes      Maximum number of routes (size of mark array)
     */
    private void updateWatchedRoutes(boolean[] routeMarkArray, int maxRoutes) {
        // Clear the relevant portion of the reusable mark array
        // Only clear what we might use (up to maxRoutes)
        java.util.Arrays.fill(routeMarkArray, 0, maxRoutes, false);

        watchedCount = 0;

        for (InsertionPosition pos : topM) {
            int rid = pos.routeId;
            if (rid >= 0 && rid < maxRoutes && !routeMarkArray[rid]) {
                routeMarkArray[rid] = true;
                if (watchedCount < watchedRoutes.length) {
                    watchedRoutes[watchedCount++] = rid;
                }
            }
        }
    }

    /**
     * Get old watched routes (for unwatching before recompute)
     *
     * @return Copy of currently watched route IDs
     */
    public int[] getOldWatchedRoutes() {
        int[] old = new int[watchedCount];
        System.arraycopy(watchedRoutes, 0, old, 0, watchedCount);
        return old;
    }

    /**
     * Mark customer as inserted (deactivate)
     */
    public void deactivate() {
        this.active = false;
    }

    @Override
    public String toString() {
        return String.format("RegretState[active=%b, valid=%b, ver=%d, regret=%.2f, topM=%d]",
                active, valid, version, regretValue, topM.size());
    }
}
