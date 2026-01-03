package SearchMethod;

import EliteSet.EliteSolution;
import Solution.Solution;
import java.util.*;

/**
 * Strategy interface for selecting restart seeds from elite set
 *
 * Implementations determine which elite solution to use when restarting
 * a stagnant worker thread.
 *
 * Common strategies:
 * - Quality-based: Prefer unused solutions with high combined scores
 * - Diversity-based: Prefer solutions furthest from current search regions
 * - Hybrid: Combine quality and diversity metrics
 *
 * The selection strategy can be easily changed by implementing this interface
 * and passing a different strategy to ThreadMonitor.
 */
public interface SeedSelectionStrategy {

    /**
     * Select next restart seed from elite set
     *
     * @param elites Available elite solutions (from elite set)
     * @param usageCount Map tracking how many times each solution has been used as seed
     * @return Selected solution, or null if no suitable seed available
     */
    Solution selectSeed(List<EliteSolution> elites, Map<Solution, Integer> usageCount);

    /**
     * Get strategy name for logging
     *
     * @return Strategy name (e.g., "QualityBased", "DiversityBased", "Hybrid")
     */
    String getStrategyName();
}
