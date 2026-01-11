package EliteSet;

import Solution.Solution;
import Solution.Node;
import Solution.Route;
import Data.Instance;
import SearchMethod.Config;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReadWriteLock;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Elite Set: Maintains a diverse set of high-quality solutions
 *
 * Features:
 * - Size-limited collection (default 10 solutions)
 * - Insertion based on weighted quality + diversity score
 * - Ejection based on lowest combined score
 * - Efficient O(n) incremental score updates using distance matrix
 * - Thread-safe for parallel crossover operations
 * - Monitoring with periodic statistics printing
 *
 * Thread Safety:
 * - Uses ReentrantReadWriteLock for concurrent access
 * - Main algorithm (writer) updates elite set
 * - Crossover thread(s) (readers) can access snapshots
 */
public class EliteSet {

    // Configuration
    private final int maxSize; // Maximum elite set size
    private final double beta; // Diversity weight (quality weight = 1-beta)
    private final double minDiversityThreshold; // Minimum diversity to accept

    // Problem context (needed to create Solution objects)
    private final Instance instance;
    private final Config config;

    // Data structures
    private final ArrayList<EliteSolution> elites; // The elite solutions
    private final double[][] distanceMatrix; // Pairwise distances
    private final double[] distanceSums; // Sum of distances for each solution
    private final DiversityMetric diversityMetric; // Distance calculator

    // Best tracking (for quality score normalization)
    private double bestF; // Best objective value in elite set
    private double worstF; // Worst objective value in elite set

    // Diversity range tracking (for diversity score normalization)
    private double minDiversity; // Minimum diversity in elite set
    private double maxDiversity; // Maximum diversity in elite set

    // Statistics tracking
    private int currentIteration; // Current search iteration
    private double previousBestF = Double.MAX_VALUE;
    private double previousAvgDiversity = 0.0;
    private int iterationsSinceLastPrint = 0;
    private boolean hasReachedMaxSize = false;
    private boolean hasPrintedMaxSize = false;

    // Thread safety
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    // String frequency tracking
    private HashMap<String, Integer> stringFrequencies;
    private int stringLength;
    private long totalStringsExtracted;

    // CSV logging
    private BufferedWriter csvWriter;
    private String csvFilePath;
    private boolean loggingEnabled;
    private int loggingInterval;
    private int loggingTopK;
    private long startTime;

    /**
     * Constructor
     *
     * @param maxSize               Maximum number of solutions in elite set
     * @param beta                  Diversity weight (0 = only quality, 1 = only
     *                              diversity)
     * @param minDiversityThreshold Minimum diversity required (e.g., 0.05)
     * @param instance              Problem instance
     * @param config                Algorithm configuration
     */
    public EliteSet(int maxSize, double beta, double minDiversityThreshold, Instance instance, Config config) {
        this.maxSize = maxSize;
        this.beta = beta;
        this.minDiversityThreshold = minDiversityThreshold;
        this.instance = instance;
        this.config = config;

        this.elites = new ArrayList<>(maxSize);
        this.distanceMatrix = new double[maxSize][maxSize];
        this.distanceSums = new double[maxSize];
        this.diversityMetric = new DiversityMetric();

        this.bestF = Double.MAX_VALUE;
        this.worstF = -Double.MAX_VALUE;
        this.minDiversity = Double.MAX_VALUE;
        this.maxDiversity = -Double.MAX_VALUE;
        this.currentIteration = 0;

        // Initialize string frequency tracking
        this.stringFrequencies = new HashMap<>();
        this.stringLength = config.getStringLength();
        this.totalStringsExtracted = 0;

        // Initialize CSV logging
        this.loggingEnabled = config.isStringFrequencyLoggingEnabled();
        this.loggingInterval = config.getStringFrequencyLoggingInterval();
        this.loggingTopK = config.getStringFrequencyLoggingTopK();
        this.csvWriter = null;
        this.csvFilePath = null;
        this.startTime = System.currentTimeMillis();
    }

    /**
     * Try to insert a new solution into the elite set
     * Called when a new global best is found
     *
     * @param candidate  The solution to insert
     * @param candidateF Objective value of the candidate
     * @param source     Which algorithm generated this solution (AILS, PR, or INITIAL)
     * @return true if solution was inserted, false otherwise
     */
    public boolean tryInsert(Solution candidate, double candidateF, SolutionSource source) {
        lock.writeLock().lock();
        try {
            currentIteration++;

            // First solution: always insert
            if (elites.isEmpty()) {
                insertSolution(candidate, candidateF, 0, source);
                return true;
            }

            // Check for exact duplicates FIRST (distance = 0 to any existing solution)
            for (EliteSolution elite : elites) {
                double distance = diversityMetric.calculateDistance(candidate, elite.solution);
                if (distance < 1e-9) { // Effectively zero (exact duplicate)
                    return false; // Reject exact duplicates
                }
            }

            // Calculate diversity score for candidate
            double candidateDiversity = calculateAverageDistance(candidate);

            // Check minimum diversity threshold (optional filter)
            if (candidateDiversity < minDiversityThreshold) {
                return false; // Too similar to existing solutions
            }

            // Calculate combined score for candidate
            double candidateScore = computeCombinedScore(candidateF, candidateDiversity);

            // If elite set not full: insert only if combined score is reasonable
            if (elites.size() < maxSize) {
                // Find worst current score in elite set
                double worstCurrentScore = Double.MAX_VALUE;
                for (EliteSolution elite : elites) {
                    if (elite.combinedScore < worstCurrentScore) {
                        worstCurrentScore = elite.combinedScore;
                    }
                }

                // Accept if score is better than worst current (or elite set is empty)
                // This ensures we maintain quality even while filling the elite set
                if (elites.isEmpty() || candidateScore >= worstCurrentScore) {
                    insertSolution(candidate, candidateF, elites.size(), source);
                    return true;
                }

                return false; // Reject if score is worse than worst current
            }

            // Elite set is full: find solution to eject
            int ejectIndex = findSolutionToEject();
            double ejectScore = elites.get(ejectIndex).combinedScore;

            // Insert if candidate is better than solution to be ejected
            if (candidateScore > ejectScore) {
                removeSolution(ejectIndex);
                insertSolution(candidate, candidateF, elites.size(), source); // Insert at end after removal
                return true;
            }

            return false;

        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Update iteration counter and check if we should print statistics
     *
     * @param currentIter Current iteration number
     */
    public void updateIteration(int currentIter) {
        // Use write lock because we modify state variables
        lock.writeLock().lock();
        try {
            this.currentIteration = currentIter;
            iterationsSinceLastPrint++;

            // Check if we should print
            boolean shouldPrint = false;

            // First time reaching max size
            if (!hasPrintedMaxSize && elites.size() >= maxSize) {
                hasReachedMaxSize = true;
                hasPrintedMaxSize = true;
                shouldPrint = true;
            }

            // Every 1000 iterations after reaching max size
            if (hasReachedMaxSize && iterationsSinceLastPrint >= 1000) {
                shouldPrint = true;
                iterationsSinceLastPrint = 0;
            }

            if (shouldPrint) {
                printMonitoring();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Calculate average distance from a candidate solution to all elite solutions
     */
    private double calculateAverageDistance(Solution candidate) {
        if (elites.isEmpty())
            return 0.0;

        double sumDistance = 0.0;
        for (EliteSolution elite : elites) {
            sumDistance += diversityMetric.calculateDistance(candidate, elite.solution);
        }

        return sumDistance / elites.size();
    }

    /**
     * Compute combined score: (1-beta)*normalized_quality +
     * beta*normalized_diversity
     *
     * Both quality and diversity are normalized by their observed ranges in the
     * elite set
     * to ensure fair weighting regardless of their natural scales.
     */
    private double computeCombinedScore(double objectiveValue, double avgDistance) {
        // Quality score: normalized by range in elite set (better = higher score)
        double qualityScore = 0.0;
        if (worstF > bestF) {
            qualityScore = (worstF - objectiveValue) / (worstF - bestF);
        } else if (elites.size() == 1) {
            qualityScore = 1.0; // Only one solution, it's the best
        }

        // Diversity score: normalize by observed range in elite set
        double diversityScore = 0.0;
        if (elites.size() >= 2 && maxDiversity > minDiversity) {
            // Normalize diversity to [0, 1] based on observed range
            diversityScore = (avgDistance - minDiversity) / (maxDiversity - minDiversity);
        } else if (elites.size() == 1) {
            diversityScore = 1.0; // Only one solution, maximum diversity
        } else {
            // No diversity range yet, use raw value
            diversityScore = avgDistance;
        }

        // Combined score with normalized components
        return (1 - beta) * qualityScore + beta * diversityScore;
    }

    /**
     * Insert a solution at a specific index (efficient O(n) operation)
     */
    private void insertSolution(Solution candidate, double candidateF, int index, SolutionSource source) {
        // Create elite solution wrapper with proper deep copy
        EliteSolution newElite = new EliteSolution(candidate, candidateF, currentIteration, source, instance, config);

        // Add to list
        if (index >= elites.size()) {
            elites.add(newElite);
        } else {
            elites.set(index, newElite);
        }

        int idx = elites.indexOf(newElite);

        // Update distance matrix: calculate distances to all other solutions
        for (int i = 0; i < elites.size(); i++) {
            if (i == idx) {
                distanceMatrix[idx][idx] = 0.0;
            } else {
                double dist = diversityMetric.calculateDistance(
                        newElite.solution,
                        elites.get(i).solution);
                distanceMatrix[idx][i] = dist;
                distanceMatrix[i][idx] = dist;

                // Update distance sum for existing solution i
                distanceSums[i] += dist;
            }
        }

        // Calculate distance sum for new solution
        distanceSums[idx] = 0.0;
        for (int i = 0; i < elites.size(); i++) {
            if (i != idx) {
                distanceSums[idx] += distanceMatrix[idx][i];
            }
        }

        // Update best and worst objective values
        if (candidateF < bestF)
            bestF = candidateF;
        if (candidateF > worstF)
            worstF = candidateF;

        // Update scores for all solutions (O(n) operation)
        updateAllScores();

        // Extract and record k-node strings from newly inserted solution
        extractAndRecordStrings(newElite.solution);
    }

    /**
     * Remove a solution at a specific index (efficient O(n) operation)
     */
    private void removeSolution(int index) {
        if (index < 0 || index >= elites.size())
            return;

        // Update distance sums for all other solutions
        for (int i = 0; i < elites.size(); i++) {
            if (i != index) {
                distanceSums[i] -= distanceMatrix[i][index];
            }
        }

        // Remove from list
        elites.remove(index);

        // Shift distance matrix and sums
        for (int i = index; i < elites.size(); i++) {
            distanceSums[i] = distanceSums[i + 1];
            for (int j = 0; j < maxSize; j++) {
                distanceMatrix[i][j] = distanceMatrix[i + 1][j];
                distanceMatrix[j][i] = distanceMatrix[j][i + 1];
            }
        }

        // Update best and worst values
        updateBestWorst();

        // Update scores for all remaining solutions
        updateAllScores();
    }

    /**
     * Update best and worst objective values in elite set
     */
    private void updateBestWorst() {
        if (elites.isEmpty()) {
            bestF = Double.MAX_VALUE;
            worstF = -Double.MAX_VALUE;
            return;
        }

        bestF = elites.get(0).objectiveValue;
        worstF = elites.get(0).objectiveValue;

        for (EliteSolution elite : elites) {
            if (elite.objectiveValue < bestF)
                bestF = elite.objectiveValue;
            if (elite.objectiveValue > worstF)
                worstF = elite.objectiveValue;
        }
    }

    /**
     * Update combined scores for all solutions (called after insert/remove)
     * O(n) operation using cached distance sums
     */
    private void updateAllScores() {
        int n = elites.size();
        if (n == 0)
            return;

        // First pass: update average distances and track diversity range
        minDiversity = Double.MAX_VALUE;
        maxDiversity = -Double.MAX_VALUE;

        for (int i = 0; i < n; i++) {
            EliteSolution elite = elites.get(i);

            // Calculate average distance using cached sum
            double avgDistance = (n > 1) ? distanceSums[i] / (n - 1) : 0.0;
            elite.avgDistanceToSet = avgDistance;

            // Track diversity range
            if (avgDistance < minDiversity)
                minDiversity = avgDistance;
            if (avgDistance > maxDiversity)
                maxDiversity = avgDistance;
        }

        // Second pass: compute combined scores using normalized diversity
        for (int i = 0; i < n; i++) {
            EliteSolution elite = elites.get(i);
            elite.combinedScore = computeCombinedScore(elite.objectiveValue, elite.avgDistanceToSet);
        }
    }

    /**
     * Find the solution with the lowest combined score (to be ejected)
     */
    private int findSolutionToEject() {
        int ejectIndex = 0;
        double minScore = elites.get(0).combinedScore;

        for (int i = 1; i < elites.size(); i++) {
            if (elites.get(i).combinedScore < minScore) {
                minScore = elites.get(i).combinedScore;
                ejectIndex = i;
            }
        }

        return ejectIndex;
    }

    /**
     * Print concise monitoring statistics (single line)
     * Shows quality and diversity progression
     */
    private void printMonitoring() {
        if (elites.isEmpty())
            return;

        // Calculate current statistics
        double currentBestF = bestF;
        double currentWorstF = worstF;
        double avgObjective = 0.0;
        double avgDiversity = 0.0;

        for (int i = 0; i < elites.size(); i++) {
            avgObjective += elites.get(i).objectiveValue;
            avgDiversity += elites.get(i).avgDistanceToSet;
        }
        avgObjective /= elites.size();
        avgDiversity /= elites.size();

        // Calculate improvements
        double bestImprovement = 0.0;
        if (previousBestF < Double.MAX_VALUE) {
            bestImprovement = ((previousBestF - currentBestF) / previousBestF) * 100.0;
        }

        double diversityChange = 0.0;
        if (previousAvgDiversity > 0.0) {
            diversityChange = ((avgDiversity - previousAvgDiversity) / previousAvgDiversity) * 100.0;
        }

        // Print single-line summary
        System.out.printf("[Elite] Size:%d/%d | Best:%.2f->%.2f(%+.1f%%) | Avg:%.2f | " +
                "AvgDiv:%.3f->%.3f(%+.1f%%) | DivRange:[%.3f,%.3f] | QualRange:%.2f | Iter:%d\n",
                elites.size(), maxSize,
                previousBestF == Double.MAX_VALUE ? currentBestF : previousBestF,
                currentBestF,
                bestImprovement,
                avgObjective,
                previousAvgDiversity,
                avgDiversity,
                diversityChange,
                minDiversity,
                maxDiversity,
                currentWorstF - currentBestF,
                currentIteration);

        // Update previous values
        previousBestF = currentBestF;
        previousAvgDiversity = avgDiversity;
    }

    // ==================== Thread-Safe Public Methods ====================

    /**
     * Thread-safe snapshot for crossover operations
     * Returns list of elite solutions (solutions are already clones, safe to use)
     *
     * USAGE: Call this from crossover thread to get stable copy
     * Note: Solutions in elite set are already cloned copies, safe for read-only
     * access
     */
    public List<Solution> getSnapshotForCrossover() {
        lock.readLock().lock();
        try {
            List<Solution> snapshot = new ArrayList<>(elites.size());
            for (EliteSolution elite : elites) {
                snapshot.add(elite.solution); // Solutions are already clones
            }
            return snapshot;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get elite solutions metadata (thread-safe, read-only)
     * Returns shallow copy of EliteSolution wrappers
     */
    public List<EliteSolution> getAllEliteSolutionsThreadSafe() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(elites);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Thread-safe access to best solution in elite set
     */
    public Solution getBestSolution() {
        lock.readLock().lock();
        try {
            if (elites.isEmpty())
                return null;

            EliteSolution best = elites.get(0);
            for (EliteSolution elite : elites) {
                if (elite.objectiveValue < best.objectiveValue) {
                    best = elite;
                }
            }

            return best.solution; // Already a cloned solution
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Thread-safe size check
     */
    public int size() {
        lock.readLock().lock();
        try {
            return elites.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Thread-safe empty check
     */
    public boolean isEmpty() {
        lock.readLock().lock();
        try {
            return elites.isEmpty();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Thread-safe full check
     */
    public boolean isFull() {
        lock.readLock().lock();
        try {
            return elites.size() >= maxSize;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get maximum elite set size
     */
    public int getMaxSize() {
        return maxSize;
    }

    /**
     * Get best objective value in elite set (thread-safe)
     */
    public double getBestF() {
        lock.readLock().lock();
        try {
            return bestF;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get worst objective value in elite set (thread-safe)
     */
    public double getWorstF() {
        lock.readLock().lock();
        try {
            return worstF;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Check if a solution with similar objective value already exists in elite set
     * Used to prevent overwriting source attribution when re-inserting similar solutions
     *
     * @param candidate The solution to check
     * @param candidateF The objective value of the solution
     * @return true if a solution with similar objective value exists, false otherwise
     */
    public boolean containsSolution(Solution candidate, double candidateF) {
        lock.readLock().lock();
        try {
            for (EliteSolution elite : elites) {
                // Check if same objective value (within epsilon)
                if (Math.abs(elite.objectiveValue - candidateF) < 0.01) {
                    return true;
                }
            }
            return false;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get distance between two solutions in the elite set (thread-safe)
     */
    public double getDistance(int i, int j) {
        lock.readLock().lock();
        try {
            if (i < 0 || i >= elites.size() || j < 0 || j >= elites.size()) {
                return 0.0;
            }
            return distanceMatrix[i][j];
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get current diversity statistics (thread-safe)
     */
    public DiversityStats getDiversityStats() {
        lock.readLock().lock();
        try {
            if (elites.isEmpty()) {
                return new DiversityStats(0.0, 0.0, 0.0);
            }

            double min = Double.MAX_VALUE;
            double max = 0.0;
            double avg = 0.0;

            for (EliteSolution elite : elites) {
                double d = elite.avgDistanceToSet;
                if (d < min)
                    min = d;
                if (d > max)
                    max = d;
                avg += d;
            }
            avg /= elites.size();

            return new DiversityStats(min, max, avg);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Force print statistics (thread-safe, for debugging or on-demand)
     */
    public void printStatisticsNow() {
        lock.readLock().lock();
        try {
            printMonitoring();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Print detailed elite set information (for debugging)
     */
    public void printDetailedStatistics() {
        lock.readLock().lock();
        try {
            System.out.println("=== Elite Set Detailed Statistics ===");
            System.out.println("Size: " + elites.size() + "/" + maxSize);
            System.out.println("Best F: " + bestF);
            System.out.println("Worst F: " + worstF);
            System.out.println("Beta (diversity weight): " + beta);
            System.out.println("Min Diversity Threshold: " + minDiversityThreshold);
            System.out.println("\nSolutions:");
            for (int i = 0; i < elites.size(); i++) {
                EliteSolution e = elites.get(i);
                System.out.printf("  [%d] F=%.2f, AvgDist=%.4f, Score=%.4f, Iter=%d, Source=%s\n",
                        i, e.objectiveValue, e.avgDistanceToSet, e.combinedScore, e.insertionIteration, e.source.getLabel());
            }

            // Count solutions by source
            int ailsCount = 0, prCount = 0, initialCount = 0;
            for (EliteSolution e : elites) {
                switch (e.source) {
                    case AILS: ailsCount++; break;
                    case PATH_RELINKING: prCount++; break;
                    case INITIAL: initialCount++; break;
                }
            }
            System.out.println("\nSolution Sources:");
            System.out.printf("  AILS: %d (%.1f%%)\n", ailsCount, 100.0 * ailsCount / elites.size());
            System.out.printf("  Path Relinking: %d (%.1f%%)\n", prCount, 100.0 * prCount / elites.size());
            if (initialCount > 0) {
                System.out.printf("  Initial: %d (%.1f%%)\n", initialCount, 100.0 * initialCount / elites.size());
            }

            // String frequency tracking statistics
            System.out.println("\nString Frequency Tracking:");
            StringTrackingStats stats = getStringTrackingStats();
            System.out.println("  " + stats);
            if (stats.uniqueStrings > 0) {
                System.out.println("  Top 5 frequent strings:");
                for (StringFrequency sf : getTopFrequentStrings(5)) {
                    System.out.println("    " + sf);
                }
            }
            System.out.println("====================================");
        } finally {
            lock.readLock().unlock();
        }
    }

    // ==================== String Frequency Tracking Methods ====================

    /**
     * Extract all k-node strings from a solution and update frequency map.
     * Called after a solution is successfully inserted into the elite set.
     *
     * @param solution Solution to extract strings from
     */
    private void extractAndRecordStrings(Solution solution) {
        for (int r = 0; r < solution.numRoutes; r++) {
            extractStringsFromRoute(solution.routes[r]);
        }
    }

    /**
     * Extract k-node consecutive sequences from a route (circular: depot->nodes->depot).
     *
     * @param route Route to extract strings from
     */
    private void extractStringsFromRoute(Route route) {
        if (route.numElements <= 1) return;  // Empty route (only depot)

        // Collect all nodes including depot at boundaries
        List<Integer> nodeSequence = new ArrayList<>();
        Node current = route.first;
        do {
            nodeSequence.add(current.name);
            current = current.next;
        } while (current != route.first);

        // Extract k-node substrings
        for (int i = 0; i <= nodeSequence.size() - stringLength; i++) {
            List<Integer> substring = nodeSequence.subList(i, i + stringLength);
            String canonicalKey = toCanonicalString(substring);
            stringFrequencies.merge(canonicalKey, 1, Integer::sum);
            totalStringsExtracted++;
        }
    }

    /**
     * Convert node sequence to canonical form: (5-12-8) and (8-12-5) -> "5 8 12"
     * Uses lexicographic comparison to choose forward/reverse direction.
     *
     * @param nodes Node sequence (size = k)
     * @return Canonical string (lexicographically smaller direction, space-separated)
     */
    private String toCanonicalString(List<Integer> nodes) {
        // Determine canonical direction (lexicographically smaller)
        boolean useForward = true;
        for (int i = 0; i < nodes.size() / 2; i++) {
            int fromStart = nodes.get(i);
            int fromEnd = nodes.get(nodes.size() - 1 - i);
            if (fromStart < fromEnd) {
                useForward = true;
                break;
            } else if (fromStart > fromEnd) {
                useForward = false;
                break;
            }
        }

        // Build space-separated string
        StringBuilder sb = new StringBuilder();
        if (useForward) {
            for (int i = 0; i < nodes.size(); i++) {
                if (i > 0) sb.append(' ');
                sb.append(nodes.get(i));
            }
        } else {
            for (int i = nodes.size() - 1; i >= 0; i--) {
                if (i < nodes.size() - 1) sb.append(' ');
                sb.append(nodes.get(i));
            }
        }
        return sb.toString();
    }

    // ==================== Public API for Perturbation Operators ====================

    /**
     * Get frequency of a specific string sequence (thread-safe).
     *
     * @param nodes Node sequence in any direction
     * @return Frequency count (0 if never seen)
     */
    public int getStringFrequency(List<Integer> nodes) {
        lock.readLock().lock();
        try {
            String canonical = toCanonicalString(nodes);
            return stringFrequencies.getOrDefault(canonical, 0);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get top-k most frequent strings (thread-safe).
     *
     * @param k Number of top strings to return
     * @return List of (string, frequency) pairs, sorted descending by frequency
     */
    public List<StringFrequency> getTopFrequentStrings(int k) {
        lock.readLock().lock();
        try {
            return stringFrequencies.entrySet().stream()
                .map(e -> new StringFrequency(e.getKey(), e.getValue()))
                .sorted((a, b) -> Integer.compare(b.frequency, a.frequency))
                .limit(k)
                .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get top-k least frequent strings (rare patterns, thread-safe).
     *
     * @param k Number of bottom strings to return
     * @return List of (string, frequency) pairs, sorted ascending by frequency
     */
    public List<StringFrequency> getLeastFrequentStrings(int k) {
        lock.readLock().lock();
        try {
            return stringFrequencies.entrySet().stream()
                .map(e -> new StringFrequency(e.getKey(), e.getValue()))
                .sorted((a, b) -> Integer.compare(a.frequency, b.frequency))
                .limit(k)
                .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get all string frequencies as snapshot (thread-safe).
     *
     * @return Immutable copy of frequency map
     */
    public Map<String, Integer> getAllStringFrequencies() {
        lock.readLock().lock();
        try {
            return new HashMap<>(stringFrequencies);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get statistics about string tracking (thread-safe).
     *
     * @return Statistics object with tracking info
     */
    public StringTrackingStats getStringTrackingStats() {
        lock.readLock().lock();
        try {
            int uniqueStrings = stringFrequencies.size();
            int maxFreq = stringFrequencies.values().stream()
                .max(Integer::compare).orElse(0);
            double avgFreq = totalStringsExtracted / (double) Math.max(1, uniqueStrings);
            return new StringTrackingStats(stringLength, uniqueStrings,
                                           totalStringsExtracted, maxFreq, avgFreq);
        } finally {
            lock.readLock().unlock();
        }
    }

    // ==================== CSV Logging Methods ====================

    /**
     * Initialize CSV file for string frequency logging.
     * Creates output directory and writes header row.
     *
     * @param instanceName Name of the problem instance (for filename)
     */
    public void initializeCSVLogging(String instanceName) {
        if (!loggingEnabled) return;

        try {
            // Create logs directory if it doesn't exist
            Files.createDirectories(Paths.get("logs"));

            // Generate filename with timestamp
            String timestamp = String.format("%tY%<tm%<td_%<tH%<tM%<tS", System.currentTimeMillis());
            csvFilePath = String.format("logs/string_freq_%s_%s.csv", instanceName, timestamp);

            // Open file and write header
            csvWriter = new BufferedWriter(new FileWriter(csvFilePath));
            csvWriter.write("iteration,time_sec,elite_size,unique_strings,total_extracted,rank,string,frequency\n");
            csvWriter.flush();

            System.out.println("[CSV Logger] Initialized: " + csvFilePath);
        } catch (IOException e) {
            System.err.println("[CSV Logger] Failed to initialize: " + e.getMessage());
            loggingEnabled = false;
        }
    }

    /**
     * Log a snapshot of string frequencies to CSV.
     * Called every N iterations based on loggingInterval.
     *
     * @param iteration Current iteration number
     */
    public void logStringFrequencies(int iteration) {
        if (!loggingEnabled || csvWriter == null) return;

        lock.readLock().lock();
        try {
            double elapsedTime = (System.currentTimeMillis() - startTime) / 1000.0;
            int eliteSize = elites.size();
            int uniqueStrings = stringFrequencies.size();

            // Get top-K strings
            List<StringFrequency> topStrings = getTopFrequentStrings(loggingTopK);

            // Write each string as a row
            for (int rank = 0; rank < topStrings.size(); rank++) {
                StringFrequency sf = topStrings.get(rank);
                csvWriter.write(String.format("%d,%.2f,%d,%d,%d,%d,\"%s\",%d\n",
                    iteration, elapsedTime, eliteSize, uniqueStrings, totalStringsExtracted,
                    rank + 1, sf.stringKey, sf.frequency));
            }
            csvWriter.flush();

        } catch (IOException e) {
            System.err.println("[CSV Logger] Write error: " + e.getMessage());
            loggingEnabled = false;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Close CSV file when search completes.
     */
    public void closeCSVLogging() {
        if (csvWriter != null) {
            try {
                csvWriter.close();
                System.out.println("[CSV Logger] Closed: " + csvFilePath);
            } catch (IOException e) {
                System.err.println("[CSV Logger] Error closing file: " + e.getMessage());
            }
        }
    }

    /**
     * Check if logging should occur at this iteration.
     *
     * @param iteration Current iteration number
     * @return true if should log at this iteration
     */
    public boolean shouldLogAtIteration(int iteration) {
        return loggingEnabled && iteration % loggingInterval == 0 && iteration > 0;
    }

    // ==================== Inner Classes ====================

    /**
     * Inner class for diversity statistics
     */
    public static class DiversityStats {
        public final double min;
        public final double max;
        public final double avg;

        public DiversityStats(double min, double max, double avg) {
            this.min = min;
            this.max = max;
            this.avg = avg;
        }

        @Override
        public String toString() {
            return String.format("DiversityStats[min=%.4f, max=%.4f, avg=%.4f]", min, max, avg);
        }
    }

    /**
     * Immutable result class for string frequency queries.
     */
    public static class StringFrequency {
        public final String stringKey;
        public final int frequency;

        public StringFrequency(String stringKey, int frequency) {
            this.stringKey = stringKey;
            this.frequency = frequency;
        }

        /**
         * Parse string back to node list
         */
        public List<Integer> toNodeList() {
            return Arrays.stream(stringKey.split(" "))
                .map(Integer::parseInt)
                .collect(Collectors.toList());
        }

        @Override
        public String toString() {
            return String.format("[%s]: %d", stringKey, frequency);
        }
    }

    /**
     * Statistics about string frequency tracking.
     */
    public static class StringTrackingStats {
        public final int stringLength;
        public final int uniqueStrings;
        public final long totalExtracted;
        public final int maxFrequency;
        public final double avgFrequency;

        public StringTrackingStats(int stringLength, int uniqueStrings,
                                   long totalExtracted, int maxFrequency,
                                   double avgFrequency) {
            this.stringLength = stringLength;
            this.uniqueStrings = uniqueStrings;
            this.totalExtracted = totalExtracted;
            this.maxFrequency = maxFrequency;
            this.avgFrequency = avgFrequency;
        }

        @Override
        public String toString() {
            return String.format("StringStats[k=%d, unique=%d, total=%d, max=%d, avg=%.1f]",
                               stringLength, uniqueStrings, totalExtracted,
                               maxFrequency, avgFrequency);
        }
    }
}
