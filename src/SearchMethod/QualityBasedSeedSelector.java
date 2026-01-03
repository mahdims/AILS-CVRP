package SearchMethod;

import EliteSet.EliteSolution;
import Solution.Solution;
import java.util.*;

/**
 * Quality-based seed selection strategy
 *
 * Selection criteria (priority order):
 * 1. Prefer unused solutions (usage count = 0)
 * 2. Among solutions with same usage count, prefer higher combined score
 *
 * This ensures:
 * - All elite solutions get explored before reusing any
 * - When reuse is necessary, best quality solutions are selected
 * - Workers start from promising regions of search space
 *
 * Combined score = (1-beta) * quality_score + beta * diversity_score
 * Higher combined score = better balance of solution quality and diversity
 */
public class QualityBasedSeedSelector implements SeedSelectionStrategy {

    @Override
    public Solution selectSeed(List<EliteSolution> elites, Map<Solution, Integer> usageCount) {
        if (elites == null || elites.isEmpty()) {
            return null;
        }

        // Build candidates with usage info
        List<SeedCandidate> candidates = new ArrayList<>();

        for (EliteSolution elite : elites) {
            int usage = usageCount.getOrDefault(elite.solution, 0);
            candidates.add(new SeedCandidate(elite, usage));
        }

        // Sort by: (1) usage count (ascending), (2) combined score (descending)
        candidates.sort((c1, c2) -> {
            // Primary criterion: prefer unused solutions
            if (c1.usageCount != c2.usageCount) {
                return Integer.compare(c1.usageCount, c2.usageCount);
            }

            // Secondary criterion: prefer higher quality
            return Double.compare(c2.elite.combinedScore, c1.elite.combinedScore);
        });

        SeedCandidate selected = candidates.get(0);

        System.out.printf("[SeedSelector] Selected: f=%.2f, score=%.4f, usage=%d, source=%s%n",
            selected.elite.objectiveValue,
            selected.elite.combinedScore,
            selected.usageCount,
            selected.elite.source.getLabel());

        return selected.elite.solution;
    }

    @Override
    public String getStrategyName() {
        return "QualityBased";
    }

    /**
     * Internal class for seed candidates with usage tracking
     */
    private static class SeedCandidate {
        final EliteSolution elite;
        final int usageCount;

        SeedCandidate(EliteSolution elite, int usageCount) {
            this.elite = elite;
            this.usageCount = usageCount;
        }
    }
}
