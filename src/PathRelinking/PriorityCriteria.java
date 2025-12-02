package PathRelinking;

import Solution.Node;
import Solution.Route;

import java.util.Random;

/**
 * Priority Criteria for Path Relinking (C1-C10)
 *
 * Each criterion calculates priority for moving a vertex based on:
 * - Origin route feasibility (before/after removal)
 * - Destination route feasibility (before/after insertion)
 * - Vertex demand
 * - Movement cost
 *
 * Higher priority = more urgent to move
 *
 * Reference: Paper Appendix B - Priority Criteria Details
 */
public enum PriorityCriteria {
    C1, C2, C3, C4, C5, C6, C7, C8, C9, C10;

    private static Random random = new Random();

    /**
     * Calculate priority for moving vertex v from origin to destination route
     *
     * @param v Vertex to move
     * @param originRoute Current route of v
     * @param destRoute Target route in guide solution
     * @param capacity Vehicle capacity
     * @param movementCost Cost of moving v to destRoute (for cost-based criteria)
     * @return Priority value (higher = more urgent to move)
     */
    public double calculatePriority(
        Node v,
        Route originRoute,
        Route destRoute,
        int capacity,
        double movementCost
    ) {
        // Calculate feasibility states
        boolean originFeasBefore = originRoute.isFeasible();
        int originDemandAfter = originRoute.totalDemand - v.demand;
        boolean originFeasAfter = originDemandAfter <= capacity;

        boolean destFeasBefore = destRoute.isFeasible();
        int destDemandAfter = destRoute.totalDemand + v.demand;
        boolean destFeasAfter = destDemandAfter <= capacity;

        // Apply criterion-specific calculation
        return switch(this) {
            case C1 -> calculateC1(originFeasBefore, originFeasAfter,
                                    destFeasBefore, destFeasAfter);
            case C2 -> calculateC2(originFeasBefore, originFeasAfter,
                                    destFeasBefore, destFeasAfter);
            case C3 -> calculateC3(originFeasBefore, originFeasAfter,
                                    destFeasBefore, destFeasAfter, movementCost);
            case C4 -> calculateC4(v.demand);
            case C5 -> calculateC5(v.demand);
            case C6 -> calculateC6(movementCost);
            case C7 -> calculateC7(originFeasBefore, originFeasAfter);
            case C8 -> calculateC8(destFeasBefore, destFeasAfter);
            case C9 -> calculateC9();
            case C10 -> calculateC10(originFeasBefore, originFeasAfter,
                                     destFeasBefore, destFeasAfter,
                                     v.demand, movementCost);
        };
    }

    /**
     * C1: Prioritize moves that restore feasibility
     * Logic: Reward making origin feasible, penalize making destination infeasible
     *
     * Paper: Focus on restoring route feasibility
     */
    private double calculateC1(boolean origBefore, boolean origAfter,
                               boolean destBefore, boolean destAfter) {
        double priority = 0.0;

        // Origin becomes feasible: +1
        if (!origBefore && origAfter) {
            priority += 1.0;
        }

        // Destination becomes infeasible: -1
        if (destBefore && !destAfter) {
            priority -= 1.0;
        }

        return priority;
    }

    /**
     * C2: Reverse of C1
     * Prioritize moves that may create infeasibility (exploratory)
     *
     * Paper: Exploratory criterion for diversification
     */
    private double calculateC2(boolean origBefore, boolean origAfter,
                               boolean destBefore, boolean destAfter) {
        return -calculateC1(origBefore, origAfter, destBefore, destAfter);
    }

    /**
     * C3: Cost-based with feasibility bonus
     * Use cost when both routes feasible, otherwise use feasibility
     *
     * Paper: Hybrid criterion balancing cost and feasibility
     */
    private double calculateC3(boolean origBefore, boolean origAfter,
                               boolean destBefore, boolean destAfter,
                               double cost) {
        // If both routes stay feasible, use cost (negated - lower cost = higher priority)
        if (origBefore && origAfter && destBefore && destAfter) {
            return -cost;  // Lower cost = higher priority
        }

        // Otherwise use feasibility criteria (amplified)
        return calculateC1(origBefore, origAfter, destBefore, destAfter) * 1000.0;
    }

    /**
     * C4: Prioritize largest demand first
     * Move heavy customers early
     *
     * Paper: Demand-based criterion for capacity-focused search
     */
    private double calculateC4(int demand) {
        return demand;  // Higher demand = higher priority
    }

    /**
     * C5: Prioritize smallest demand first
     * Move light customers early
     *
     * Paper: Inverse demand criterion for flexibility
     */
    private double calculateC5(int demand) {
        return -demand;  // Lower demand = higher priority
    }

    /**
     * C6: Pure cost-based
     * Lowest cost moves have highest priority
     *
     * Paper: Greedy cost minimization criterion
     */
    private double calculateC6(double cost) {
        return -cost;  // Lower cost = higher priority
    }

    /**
     * C7: Origin feasibility only
     * Focus on making origin route feasible
     *
     * Paper: Single-route feasibility criterion
     */
    private double calculateC7(boolean origBefore, boolean origAfter) {
        if (!origBefore && origAfter) {
            return 1.0;  // Restores origin feasibility
        }
        return 0.0;
    }

    /**
     * C8: Destination feasibility only
     * Focus on keeping destination feasible
     *
     * Paper: Conservative criterion avoiding infeasibility
     */
    private double calculateC8(boolean destBefore, boolean destAfter) {
        if (destBefore && !destAfter) {
            return -1.0;  // Makes destination infeasible - avoid
        }
        return 0.0;
    }

    /**
     * C9: Random priority
     * Exploratory, random vertex selection
     *
     * Paper: Maximum diversification through randomization
     */
    private double calculateC9() {
        return random.nextDouble();
    }

    /**
     * C10: Combined weighted formula
     * Balances all factors: feasibility, demand, cost
     *
     * Paper: Multi-objective criterion with balanced weights
     */
    private double calculateC10(boolean origBefore, boolean origAfter,
                                boolean destBefore, boolean destAfter,
                                int demand, double cost) {
        double priority = 0.0;

        // Feasibility component (weight: 0.5)
        double feasibilityScore = calculateC1(origBefore, origAfter,
                                              destBefore, destAfter);
        priority += 0.5 * feasibilityScore;

        // Demand component (weight: 0.3, normalized to [0,1])
        double demandScore = demand / 100.0;  // Assuming max demand ~100
        priority += 0.3 * demandScore;

        // Cost component (weight: 0.2, normalized and inverted)
        double costScore = -cost / 100.0;  // Lower cost = better
        priority += 0.2 * costScore;

        return priority;
    }

    /**
     * Get a random criterion
     */
    public static PriorityCriteria random() {
        PriorityCriteria[] values = values();
        return values[random.nextInt(values.length)];
    }

    /**
     * Get description of this criterion
     */
    public String getDescription() {
        return switch(this) {
            case C1 -> "Restore feasibility (origin->feasible: +1, dest->infeasible: -1)";
            case C2 -> "Reverse C1 (exploratory)";
            case C3 -> "Cost-based with feasibility priority";
            case C4 -> "Largest demand first";
            case C5 -> "Smallest demand first";
            case C6 -> "Pure cost minimization";
            case C7 -> "Origin feasibility only";
            case C8 -> "Destination feasibility only";
            case C9 -> "Random (maximum diversification)";
            case C10 -> "Combined weighted (feasibility + demand + cost)";
        };
    }

    @Override
    public String toString() {
        return name() + ": " + getDescription();
    }
}
