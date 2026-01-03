package SearchMethod;

import Data.Instance;
import EliteSet.EliteSet;
import Improvement.IntraLocalSearch;
import PathRelinking.*;
import Solution.Solution;
import java.util.*;

/**
 * Multi-Start AILS Coordinator
 *
 * Manages:
 * - 1 protected main AILS thread (never restarts)
 * - N worker AILS threads (restart when stagnant)
 * - 1 PR thread (generates seeds)
 * - Shared elite set (thread-safe)
 * - Synchronized time limits
 */
public class MultiStartAILS implements Runnable {

    // Components
    private Instance instance;
    private Config config;
    private EliteSet sharedEliteSet;
    private ThreadMonitor threadMonitor;
    private IntraLocalSearch intraLS;

    // Threads
    private AILSII mainAILS;
    private List<AILSII> workerAILS;
    private Thread mainThread;
    private List<Thread> workerThreads;
    private Thread prThread;
    private PathRelinkingThread prTask;

    // Configuration
    private int numWorkerThreads;
    private boolean notifyMainThread;

    // Time tracking
    private long globalStartTime;
    private double globalTimeLimit;
    private double optimalValue;

    public MultiStartAILS(Instance instance, Config config, IntraLocalSearch intraLS, double timeLimit, double optimalValue) {
        this.instance = instance;
        this.config = config;
        this.intraLS = intraLS;
        this.globalTimeLimit = timeLimit;
        this.optimalValue = optimalValue;

        // Create shared elite set
        this.sharedEliteSet = new EliteSet(
            config.getEliteSetSize(),
            config.getEliteSetBeta(),
            config.getEliteSetMinDiversity(),
            instance,
            config
        );

        // Create thread monitor with quality-based seed selector
        MultiStartConfig msConfig = config.getMsConfig();
        this.threadMonitor = new ThreadMonitor(
            sharedEliteSet,
            msConfig.getStagnationThreshold(),
            msConfig.getCompetitiveThreshold(),
            new QualityBasedSeedSelector()
        );

        this.numWorkerThreads = msConfig.getNumWorkerThreads();
        this.notifyMainThread = msConfig.isNotifyMainThread();
        this.workerAILS = new ArrayList<>();
        this.workerThreads = new ArrayList<>();
    }

    @Override
    public void run() {
        this.globalStartTime = System.currentTimeMillis();

        printHeader();

        // 1. Launch main AILS thread
        launchMainThread();

        // 2. Launch PR thread (if enabled)
        if (config.getPrConfig().isEnabled()) {
            launchPRThread();
        }

        // 3. Wait for elite set to have enough seeds
        waitForInitialSeeds();

        // 4. Launch worker threads
        launchWorkerThreads();

        // 5. Monitor and restart workers
        monitorAndRestartWorkers();

        // 6. Wait for completion
        waitForCompletion();

        // 7. Print final statistics
        printFinalStatistics();
    }

    private void printHeader() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("MULTI-START AILS");
        System.out.println("=".repeat(60));
        System.out.println("Configuration:");
        System.out.println("  Main thread: Thread-1 (protected, never restarts)");
        System.out.println("  Worker threads: " + numWorkerThreads);
        System.out.println("  PR thread: " + (config.getPrConfig().isEnabled() ? "enabled" : "disabled"));
        System.out.println("  Seed strategy: " + threadMonitor.getSeedStrategyName());
        System.out.println("  Notify main: " + notifyMainThread);
        System.out.println("  Time limit: " + globalTimeLimit + "s");
        System.out.println("=".repeat(60) + "\n");
    }

    private void launchMainThread() {
        System.out.println("[MultiStart] Launching main thread (Thread-1)...");

        // Main thread gets full time limit for adaptive parameters
        mainAILS = new AILSII(instance, config, sharedEliteSet,
                              null, 1, threadMonitor, optimalValue, globalTimeLimit,
                              globalStartTime, globalTimeLimit);

        mainThread = new Thread(mainAILS);
        mainThread.start();
    }

    private void launchPRThread() {
        int startDelay = config.getPrConfig().getStartIterationDelay();
        System.out.println("[MultiStart] Will start PR thread after " + startDelay + " iterations");

        // Wait briefly
        try {
            Thread.sleep(startDelay * 10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long prStartTime = System.currentTimeMillis();
        double prTimeRemaining = globalTimeLimit - (prStartTime - globalStartTime) / 1000.0;

        System.out.println("[MultiStart] Starting PR thread (time remaining: " +
                          String.format("%.1f", prTimeRemaining) + "s)");

        // Use existing PR configuration from config
        prTask = new PathRelinkingThread(sharedEliteSet, instance, config,
                                         config.getPrConfig(), intraLS, mainAILS);
        prTask.setGlobalTimeLimit(prStartTime, prTimeRemaining);

        prThread = new Thread(prTask);
        prThread.start();
    }

    private void waitForInitialSeeds() {
        int minEliteSize = config.getMsConfig().getMinEliteSizeForWorkers();

        System.out.println("[MultiStart] Waiting for elite set >= " +
                          minEliteSize + " solutions...");

        while (!timeLimitExceeded() && sharedEliteSet.size() < minEliteSize) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        if (timeLimitExceeded()) {
            System.out.println("[MultiStart] Time limit reached before workers could start");
            return;
        }

        System.out.println("[MultiStart] Elite set ready (" + sharedEliteSet.size() +
                          " solutions)");
    }

    private void launchWorkerThreads() {
        long workerStartTime = System.currentTimeMillis();
        double workerTimeRemaining = globalTimeLimit - (workerStartTime - globalStartTime) / 1000.0;

        for (int i = 0; i < numWorkerThreads; i++) {
            int threadId = i + 2;

            Solution seed = threadMonitor.getRestartSeed(threadId);

            if (seed == null) {
                System.out.println("[MultiStart] No seed available for Worker-" + threadId);
                continue;
            }

            System.out.println("[MultiStart] Launching Worker-" + threadId +
                              " (time remaining: " + String.format("%.1f", workerTimeRemaining) + "s)");

            // Workers get remaining time for adaptive parameters + global timing for eta alignment
            AILSII worker = new AILSII(instance, config, sharedEliteSet,
                                       seed, threadId, threadMonitor, optimalValue, workerTimeRemaining,
                                       globalStartTime, globalTimeLimit);

            Thread thread = new Thread(worker);

            workerAILS.add(worker);
            workerThreads.add(thread);
            thread.start();
        }
    }

    private void monitorAndRestartWorkers() {
        System.out.println("[MultiStart] Starting monitoring loop...");

        while (!timeLimitExceeded()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            // Check worker improvements (notify main)
            if (notifyMainThread) {
                checkWorkerImprovements();
            }

            // Check for stagnant workers
            checkAndRestartWorkers();
        }

        System.out.println("[MultiStart] Monitoring loop terminated");
    }

    private void checkWorkerImprovements() {
        double mainBest = mainAILS.getBestF();

        for (int i = 0; i < workerAILS.size(); i++) {
            int threadId = i + 2;
            ThreadStats stats = threadMonitor.getThreadStats(threadId);

            if (stats == null) continue;

            double workerBest = stats.getCurrentBestF();

            if (workerBest < mainBest - config.getEpsilon()) {
                AILSII worker = workerAILS.get(i);
                Solution workerSolution = worker.getBestSolution();

                mainAILS.notifyBetterSolution(workerSolution, workerBest);

                System.out.printf("[Worker-%d->Main] Better solution: %.2f%n",
                                 threadId, workerBest);
            }
        }
    }

    private void checkAndRestartWorkers() {
        for (int i = 0; i < workerAILS.size(); i++) {
            int threadId = i + 2;

            if (threadMonitor.shouldRestart(threadId)) {
                System.out.println("[MultiStart] Restarting Worker-" + threadId);

                // Terminate old worker
                AILSII oldWorker = workerAILS.get(i);
                Thread oldThread = workerThreads.get(i);
                oldWorker.terminate();

                try {
                    oldThread.join(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                // Get restart seed
                Solution seed = threadMonitor.getRestartSeed(threadId);

                if (seed == null) {
                    System.out.println("[MultiStart] No restart seed for Worker-" + threadId);
                    continue;
                }

                // Calculate remaining time
                long restartTime = System.currentTimeMillis();
                double timeRemaining = globalTimeLimit - (restartTime - globalStartTime) / 1000.0;

                if (timeRemaining <= 0) {
                    System.out.println("[MultiStart] No time remaining for restart");
                    break;
                }

                // Launch new worker with remaining time for adaptive parameters + global timing for eta alignment
                AILSII newWorker = new AILSII(instance, config, sharedEliteSet,
                                              seed, threadId, threadMonitor, optimalValue, timeRemaining,
                                              globalStartTime, globalTimeLimit);

                Thread newThread = new Thread(newWorker);

                workerAILS.set(i, newWorker);
                workerThreads.set(i, newThread);
                newThread.start();

                threadMonitor.getThreadStats(threadId).recordRestart();

                System.out.printf("[MultiStart] Worker-%d restarted (%.1fs remaining, %d restarts)%n",
                                 threadId, timeRemaining,
                                 threadMonitor.getThreadStats(threadId).getRestartCount());
            }
        }
    }

    private void waitForCompletion() {
        System.out.println("[MultiStart] Waiting for all threads to complete...");

        try {
            mainThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        for (Thread t : workerThreads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (prThread != null) {
            prTask.stop();
            try {
                prThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        System.out.println("[MultiStart] All threads completed");
    }

    private void printFinalStatistics() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("MULTI-START AILS - FINAL STATISTICS");
        System.out.println("=".repeat(60));

        threadMonitor.printSummary();
        sharedEliteSet.printDetailedStatistics();

        System.out.println("\nBest solution: " + String.format("%.2f", mainAILS.getBestF()));
        System.out.println("=".repeat(60) + "\n");
    }

    private boolean timeLimitExceeded() {
        double elapsed = (System.currentTimeMillis() - globalStartTime) / 1000.0;
        return elapsed >= globalTimeLimit;
    }

    public Solution getBestSolution() {
        return mainAILS.getBestSolution();
    }

    public double getBestF() {
        return mainAILS.getBestF();
    }
}
