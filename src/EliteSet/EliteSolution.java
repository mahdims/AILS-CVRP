package EliteSet;

import Solution.Solution;
import Data.Instance;
import SearchMethod.Config;

/**
 * Wrapper class for solutions in the elite set
 *
 * Stores a solution along with cached metadata for efficient elite set management:
 * - Objective value (quality metric)
 * - Average distance to other elite solutions (diversity metric)
 * - Combined score for insertion/ejection decisions
 * - Insertion iteration for tracking
 */
public class EliteSolution {

    /** The actual solution (deep copy) */
    public Solution solution;

    /** Cached objective value (solution.f) */
    public double objectiveValue;

    /** Iteration when this solution was added to elite set */
    public int insertionIteration;

    /** Average distance to other solutions in the elite set (cached) */
    public double avgDistanceToSet;

    /** Combined score: (1-beta)*quality_score + beta*diversity_score (cached) */
    public double combinedScore;

    /**
     * Constructor
     *
     * @param sol The solution to wrap (will be deep copied)
     * @param objValue Objective value of the solution
     * @param iteration Current iteration when solution is added
     * @param instance Problem instance (needed to create Solution)
     * @param config Algorithm configuration (needed to create Solution)
     */
    public EliteSolution(Solution sol, double objValue, int iteration, Instance instance, Config config) {
        // Create a NEW solution and clone into it (proper deep copy)
        this.solution = new Solution(instance, config);
        this.solution.clone(sol);
        this.objectiveValue = objValue;
        this.insertionIteration = iteration;
        this.avgDistanceToSet = 0.0;
        this.combinedScore = 0.0;
    }

    /**
     * Update cached metrics
     *
     * @param avgDist New average distance to elite set
     * @param score New combined score
     */
    public void updateMetrics(double avgDist, double score) {
        this.avgDistanceToSet = avgDist;
        this.combinedScore = score;
    }

    /**
     * String representation for debugging
     */
    @Override
    public String toString() {
        return String.format("EliteSolution[f=%.2f, avgDist=%.4f, score=%.4f, iter=%d]",
                objectiveValue, avgDistanceToSet, combinedScore, insertionIteration);
    }

    /**
     * Comparison by objective value (for sorting by quality)
     */
    public int compareByQuality(EliteSolution other) {
        return Double.compare(this.objectiveValue, other.objectiveValue);
    }

    /**
     * Comparison by diversity (for sorting by diversity)
     */
    public int compareByDiversity(EliteSolution other) {
        return Double.compare(this.avgDistanceToSet, other.avgDistanceToSet);
    }

    /**
     * Comparison by combined score (for sorting by overall value)
     */
    public int compareByCombinedScore(EliteSolution other) {
        return Double.compare(this.combinedScore, other.combinedScore);
    }
}
