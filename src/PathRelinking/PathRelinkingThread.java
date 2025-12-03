package PathRelinking;

import Data.Instance;
import EliteSet.EliteSet;
import EliteSet.EliteSolution;
import Improvement.IntraLocalSearch;
import SearchMethod.Config;
import SearchMethod.AILSII;
import Solution.Solution;

import java.util.*;

/**
 * Thread wrapper for running Path Relinking in parallel with AILS
 *
 * This thread:
 * 1. Waits for elite set to have sufficient solutions
 * 2. Periodically selects pairs of elite solutions
 * 3. Applies path relinking between them
 * 4. Inserts improved solutions back into elite set
 * 5. Runs independently until stopped or time limit reached
 *
 * Thread-safe: Only reads from elite set (using read locks)
 * Only writes via tryInsert (using write locks)
 */
public class PathRelinkingThread implements Runnable {

    private EliteSet eliteSet;
    private Instance instance;
    private Config config;
    private PathRelinkingConfig prConfig;
    private PathRelinking pathRelinking;
    private PathRelinkingStats stats;
    private volatile boolean shouldStop;
    private int prIterations;
    private AILSII ails; // Reference to AILSII for communication

    // Global time limit tracking
    private long globalStartTime;
    private double globalTimeLimit;

    // Stagnation detection
    private int lastStagnationCheckInsertions = 0;
    private static final int STAGNATION_CHECK_INTERVAL = 10000;
    private static final int MIN_ITERATIONS_BEFORE_STAGNATION_CHECK = 100000;

    public PathRelinkingThread(
            EliteSet eliteSet,
            Instance instance,
            Config config,
            PathRelinkingConfig prConfig,
            IntraLocalSearch intraLS,
            AILSII ails) {
        this.eliteSet = eliteSet;
        this.instance = instance;
        this.config = config;
        this.prConfig = prConfig;
        this.shouldStop = false;
        this.prIterations = 0;
        this.stats = new PathRelinkingStats();
        this.ails = ails;

        // Initialize to 0 - will be set when thread starts
        this.globalStartTime = 0;
        this.globalTimeLimit = 0;

        // Create PR components
        this.pathRelinking = new PathRelinking(instance, config, intraLS);
    }

    @Override
    public void run() {
        System.out.println("[PR Thread] Started");
        System.out.println("[PR Thread] Configuration: " + prConfig);

        long startTime = System.currentTimeMillis();

        try {
            // Wait for elite set to have sufficient solutions
            System.out.println("[PR Thread] Waiting for elite set to have >= " +
                    prConfig.getMinEliteSizeForPR() + " solutions...");

            while (!shouldStop && !isTimeLimitExceeded() && eliteSet.size() < prConfig.getMinEliteSizeForPR()) {
                Thread.sleep(100);
            }

            if (shouldStop || isTimeLimitExceeded()) {
                System.out.println("[PR Thread] Stopped before elite set ready" +
                        (isTimeLimitExceeded() ? " (time limit reached)" : ""));
                return;
            }

            System.out.println("[PR Thread] Elite set ready (" + eliteSet.size() +
                    " solutions), beginning PR iterations");

            // Main PR loop - runs until stopped or time limit reached
            while (!shouldStop && !isTimeLimitExceeded()) {

                // Check if elite set has enough solutions
                if (eliteSet.size() < 2) {
                    Thread.sleep(100);
                    continue;
                }

                // Get snapshot of elite set (thread-safe)
                List<Solution> eliteSolutions = eliteSet.getSnapshotForCrossover();

                // Select two solutions based on combined score (quality + diversity)
                SolutionPair pair = selectSolutionPairByScore(eliteSolutions);

                if (pair == null) {
                    // No valid pairs found, wait and retry
                    Thread.sleep(100);
                    continue;
                }

                // Record pairing
                stats.recordPairing();

                // Log selected pair (one line)
                // System.out.printf("[PR-%d] Select: s1(f=%.2f,r=%d) + s2(f=%.2f,r=%d) ",
                // prIterations,
                // pair.current.f, pair.current.getNumRoutes(),
                // pair.elite.f, pair.elite.getNumRoutes());

                // Apply Path Relinking
                Solution result = applyPathRelinking(pair.current, pair.elite);

                // Try to insert result into elite set
                if (result != null) {
                    // Check if result is better than global best
                    double currentBestF = ails.getBestF();
                    boolean isBetterThanGlobalBest = result.f < currentBestF;

                    // Notify AILS if we found a better solution
                    if (isBetterThanGlobalBest) {
                        ails.notifyPRBetterSolution(result, result.f);
                    }

                    boolean inserted = eliteSet.tryInsert(result, result.f);

                    stats.recordIteration(inserted, result.f);

					// Record number of moves performed in this PR iteration
					stats.recordMoves(pathRelinking.getLastMoveCount());

                    // Complete the log line
                    // System.out.printf("-> Result: f=%.2f %s%s\n",
                    // result.f,
                    // inserted ? "[INSERTED]" : "[rejected]",
                    // isBetterThanGlobalBest ? " [NEW BEST!]" : "");
                } else {
                    System.out.println("-> Result: ERROR");
                }

                prIterations++;

                // Stagnation detection (every 10,000 iterations)
                if (prIterations % STAGNATION_CHECK_INTERVAL == 0 &&
                    prIterations >= MIN_ITERATIONS_BEFORE_STAGNATION_CHECK) {

                    int currentInsertions = stats.getSuccessfulInsertions();

                    if (currentInsertions == lastStagnationCheckInsertions) {
                        // No new insertions in last 10k iterations - terminate
                        double elapsed = (System.currentTimeMillis() - startTime) / 1000.0;
                        System.out.println("[PR Thread] Stagnation detected: no insertions in last " +
                                STAGNATION_CHECK_INTERVAL + " iterations");
                        System.out.println("[PR Thread] Terminating PR after " + prIterations +
                                " iterations (" + String.format("%.1f", elapsed) + "s)");
                        break;
                    }

                    // Update for next check
                    lastStagnationCheckInsertions = currentInsertions;
                }

                // Periodic statistics (every 100 iterations)
                if (prIterations % 100 == 0) {
                    // Pass global best from AILS (thread-safe via volatile/synchronized)
                    stats.printStats(prIterations, ails.getBestF());
                }

                // Small sleep to avoid busy-waiting
                Thread.sleep(10);
            }

        } catch (InterruptedException e) {
            System.out.println("[PR Thread] Interrupted");
        } catch (Exception e) {
            System.err.println("[PR Thread] Error: " + e.getMessage());
            e.printStackTrace();
        }

        double elapsed = (System.currentTimeMillis() - startTime) / 1000.0;
        String reason = isTimeLimitExceeded() ? " (time limit reached)" :
                        shouldStop ? " (stopped by AILS)" : " (stagnation)";
        System.out.println("[PR Thread] Terminated after " +
                prIterations + " iterations (" +
                String.format("%.1f", elapsed) + "s)" + reason);

        stats.printFinalStats();
    }

    /**
     * Select pair of solutions using roulette wheel selection based on combined
     * score
     * Higher score solutions have higher probability of selection
     *
     * @param eliteSolutions List of elite solutions
     * @return Pair of solutions, or null if no valid pair found
     */
    private SolutionPair selectSolutionPairByScore(List<Solution> eliteSolutions) {
        // Get elite solutions with metadata (includes combinedScore)
        List<EliteSolution> eliteMetadata = eliteSet.getAllEliteSolutionsThreadSafe();

        // Group by number of routes (same as before)
        Map<Integer, List<EliteSolution>> byRoutes = new HashMap<>();

        for (EliteSolution elite : eliteMetadata) {
            byRoutes.computeIfAbsent(
                    elite.solution.getNumRoutes(),
                    k -> new ArrayList<>()).add(elite);
        }

        // Find groups with at least 2 solutions
        List<Integer> validGroups = new ArrayList<>();

        for (Map.Entry<Integer, List<EliteSolution>> entry : byRoutes.entrySet()) {
            if (entry.getValue().size() >= 2) {
                validGroups.add(entry.getKey());
            }
        }

        if (validGroups.isEmpty()) {
            return null;
        }

        // Select group with most solutions (more options)
        int bestGroup = validGroups.get(0);
        int maxSize = byRoutes.get(bestGroup).size();
        for (int group : validGroups) {
            if (byRoutes.get(group).size() > maxSize) {
                maxSize = byRoutes.get(group).size();
                bestGroup = group;
            }
        }

        List<EliteSolution> group = byRoutes.get(bestGroup);

        // Roulette wheel selection: select first solution
        EliteSolution elite1 = rouletteWheelSelect(group);

        // Remove selected solution AND any solutions with same objective value
        // to ensure we select two genuinely different solutions
        List<EliteSolution> remainingGroup = new ArrayList<>();
        for (EliteSolution elite : group) {
            // Exclude by object reference AND by objective value
            if (elite != elite1 && Math.abs(elite.objectiveValue - elite1.objectiveValue) > 0.01) {
                remainingGroup.add(elite);
            }
        }

        // If no different solutions available, return null (will wait and retry)
        if (remainingGroup.isEmpty()) {
            return null;
        }

        EliteSolution elite2 = rouletteWheelSelect(remainingGroup);

        return new SolutionPair(elite1.solution, elite2.solution);
    }

    /**
     * Roulette wheel selection based on combined scores
     * Converts scores to probabilities and selects randomly with those
     * probabilities
     *
     * @param candidates List of elite solutions to select from
     * @return Selected elite solution
     */
    private EliteSolution rouletteWheelSelect(List<EliteSolution> candidates) {
        if (candidates.isEmpty()) {
            return null;
        }

        if (candidates.size() == 1) {
            return candidates.get(0);
        }

        // Find minimum score (could be negative)
        double minScore = Double.MAX_VALUE;
        for (EliteSolution elite : candidates) {
            if (elite.combinedScore < minScore) {
                minScore = elite.combinedScore;
            }
        }

        // Shift scores to make all positive (add offset if minScore is negative)
        double offset = minScore < 0 ? -minScore + 0.01 : 0.0;

        // Calculate total shifted score
        double totalScore = 0.0;
        for (EliteSolution elite : candidates) {
            totalScore += elite.combinedScore + offset;
        }

        // If all scores are same (or total is zero), select randomly
        if (totalScore <= 0.0 || Double.isNaN(totalScore)) {
            Random rand = new Random();
            return candidates.get(rand.nextInt(candidates.size()));
        }

        // Generate random value in [0, totalScore)
        Random rand = new Random();
        double randomValue = rand.nextDouble() * totalScore;

        // Select solution using cumulative probability
        double cumulativeScore = 0.0;
        for (EliteSolution elite : candidates) {
            cumulativeScore += elite.combinedScore + offset;
            if (cumulativeScore >= randomValue) {
                return elite;
            }
        }

        // Fallback (shouldn't reach here)
        return candidates.get(candidates.size() - 1);
    }

    /**
     * Select pair of solutions with same number of routes (RANDOM - legacy)
     * One is treated as "current" (best), other as "elite"
     *
     * @param eliteSolutions List of elite solutions
     * @return Pair of solutions, or null if no valid pair found
     */
    @SuppressWarnings("unused")
    private SolutionPair selectSolutionPair(List<Solution> eliteSolutions) {
        // Group solutions by number of routes
        Map<Integer, List<Solution>> byRoutes = new HashMap<>();

        for (Solution sol : eliteSolutions) {
            byRoutes.computeIfAbsent(
                    sol.getNumRoutes(),
                    k -> new ArrayList<>()).add(sol);
        }

        // Find groups with at least 2 solutions
        List<Integer> validGroups = new ArrayList<>();

        for (Map.Entry<Integer, List<Solution>> entry : byRoutes.entrySet()) {
            if (entry.getValue().size() >= 2) {
                validGroups.add(entry.getKey());
            }
        }

        if (validGroups.isEmpty()) {
            return null;
        }

        // Randomly select a group
        Random rand = new Random();
        int numRoutes = validGroups.get(rand.nextInt(validGroups.size()));
        List<Solution> group = byRoutes.get(numRoutes);

        // Randomly select two different solutions
        int idx1 = rand.nextInt(group.size());
        int idx2;
        do {
            idx2 = rand.nextInt(group.size());
        } while (idx2 == idx1);

        return new SolutionPair(group.get(idx1), group.get(idx2));
    }

    /**
     * Apply path relinking between two solutions
     *
     * @param s1 First solution
     * @param s2 Second solution
     * @return Best solution found, or null if error
     */
    private Solution applyPathRelinking(Solution s1, Solution s2) {
        try {
            // Clone solutions to avoid modifying elite set copies
            Solution sCurrent = new Solution(instance, config);
            Solution sElite = new Solution(instance, config);

            sCurrent.clone(s1);
            sElite.clone(s2);

            // Apply PR (internally decides which is si and which is sg)
            return pathRelinking.pathRelink(sCurrent, sElite);

        } catch (Exception e) {
            System.err.println("[PR Thread] Error in path relinking: " +
                    e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Check if time limit exceeded (LEGACY - not used, PR runs until AILS stops)
     *
     * @param startTime Start time in milliseconds
     * @return true if time limit exceeded
     */
    @SuppressWarnings("unused")
    private boolean timeLimitExceeded(long startTime) {
        double elapsed = (System.currentTimeMillis() - startTime) / 1000.0;
        return elapsed >= prConfig.getPrTimeLimit();
    }

    /**
     * Set the global time limit for PR thread
     * Should be called before starting the thread
     *
     * @param startTime Global start time in milliseconds
     * @param timeLimit Time limit in seconds
     */
    public void setGlobalTimeLimit(long startTime, double timeLimit) {
        this.globalStartTime = startTime;
        this.globalTimeLimit = timeLimit;
    }

    /**
     * Check if global time limit has been exceeded
     *
     * @return true if time limit exceeded
     */
    private boolean isTimeLimitExceeded() {
        if (globalTimeLimit <= 0 || globalStartTime <= 0) {
            return false; // No time limit set
        }
        double elapsed = (System.currentTimeMillis() - globalStartTime) / 1000.0;
        return elapsed >= globalTimeLimit;
    }

    /**
     * Stop the PR thread
     */
    public void stop() {
        this.shouldStop = true;
    }

    /**
     * Get statistics
     *
     * @return PR statistics object
     */
    public PathRelinkingStats getStats() {
        return stats;
    }

    /**
     * Inner class for solution pairs
     */
    private static class SolutionPair {
        Solution current;
        Solution elite;

        SolutionPair(Solution current, Solution elite) {
            this.current = current;
            this.elite = elite;
        }
    }
}
