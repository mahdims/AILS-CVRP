# AILSII Modifications for Multi-Start Support

## Overview

AILSII needs to support both single-thread and multi-thread modes:
- **Single-thread mode**: Standard AILS (current behavior, no changes)
- **Multi-thread mode**: AILS can run as main thread or worker thread

## Required Changes

### 1. Add Thread-Related Fields

Add these fields to AILSII class:

```java
// Thread support
private ThreadMonitor threadMonitor;
private int threadId;
private volatile boolean shouldTerminate;
```

### 2. Add New Constructor for Multi-Start

Add constructor that accepts initial solution + thread support:

```java
/**
 * Constructor for multi-start AILS (worker threads)
 *
 * @param instance Problem instance
 * @param config Configuration
 * @param sharedEliteSet Shared elite set (thread-safe)
 * @param initialSolution Solution to start from (for workers)
 * @param threadId Thread identifier (1=main, 2+=workers)
 * @param threadMonitor Thread monitor for coordination
 */
public AILSII(Instance instance, Config config, EliteSet sharedEliteSet,
              Solution initialSolution, int threadId, ThreadMonitor threadMonitor) {
    // Call existing constructor
    this(instance, config);

    // Override elite set with shared one
    this.eliteSet = sharedEliteSet;

    // Set thread-specific fields
    this.threadId = threadId;
    this.threadMonitor = threadMonitor;
    this.shouldTerminate = false;

    // Register with thread monitor
    if (threadMonitor != null) {
        threadMonitor.registerThread(threadId);
    }

    // Start from provided solution (if worker thread)
    if (initialSolution != null) {
        this.bestSolution.clone(initialSolution);
        this.referenceSolution.clone(initialSolution);
        this.bestF = initialSolution.f;

        System.out.printf("[Thread-%d] Starting from elite seed: f=%.2f%n",
            threadId, bestF);
    }
}
```

### 3. Update Main Loop for Thread Monitoring

In the main AILS loop, add monitoring hooks:

```java
// At start of each iteration
if (threadMonitor != null) {
    // Report iteration
    threadMonitor.getThreadStats(threadId).recordIteration();
    threadMonitor.getThreadStats(threadId).updateBestF(bestF);

    // Check for termination signal (workers only, not main thread)
    if (threadId > 1 && threadMonitor.shouldRestart(threadId)) {
        System.out.printf("[Thread-%d] Stagnation detected, terminating for restart...%n",
            threadId);
        break;
    }
}

// Check for external termination
if (shouldTerminate) {
    System.out.printf("[Thread-%d] External termination signal received%n", threadId);
    break;
}
```

### 4. Report Elite Set Insertions

When solution is inserted to elite set:

```java
if (inserted && threadMonitor != null) {
    threadMonitor.getThreadStats(threadId).recordEliteInsertion();
}
```

### 5. Report Global Best Improvements

When new global best is found:

```java
if (newGlobalBest && threadMonitor != null) {
    threadMonitor.getThreadStats(threadId).recordGlobalBestImprovement();
    threadMonitor.updateGlobalBest(bestF);
}
```

### 6. Add Method for Worker→Main Notifications

Rename or alias `notifyPRBetterSolution` to be more generic:

```java
/**
 * Notify this AILS thread of a better solution found elsewhere
 * (from PR thread or worker thread)
 *
 * @param betterSolution The better solution
 * @param betterF Its objective value
 */
public void notifyBetterSolution(Solution betterSolution, double betterF) {
    // Same implementation as notifyPRBetterSolution
    synchronized (prSolutionLock) {
        if (betterF < this.bestF - epsilon) {
            this.pendingPRSolution = betterSolution;
            this.pendingPRF = betterF;
            this.hasPendingPR = true;
        }
    }
}

// Keep backward compatibility
public void notifyPRBetterSolution(Solution betterSolution, double betterF) {
    notifyBetterSolution(betterSolution, betterF);
}
```

### 7. Add Termination Method

```java
/**
 * Signal this thread to terminate gracefully
 */
public void terminate() {
    this.shouldTerminate = true;
}
```

### 8. Add Getter for Best Solution

```java
public Solution getBestSolution() {
    return bestSolution;
}
```

## Integration Points

### Where to Add Monitoring Code

1. **Start of iteration** (in main while loop):
   - Record iteration
   - Update best F
   - Check termination signals

2. **After elite set insertion** (line ~451, ~655):
   ```java
   if (inserted && threadMonitor != null) {
       threadMonitor.getThreadStats(threadId).recordEliteInsertion();
   }
   ```

3. **After finding new global best** (line ~655):
   ```java
   if (newGlobalBest && threadMonitor != null) {
       threadMonitor.getThreadStats(threadId).recordGlobalBestImprovement();
       threadMonitor.updateGlobalBest(bestF);
   }
   ```

## Backward Compatibility

All changes are backward compatible:
- Existing single-thread constructor works as before
- Thread monitoring code only executes if `threadMonitor != null`
- Default `threadId = 0` for single-thread mode

## Example Usage

### Single-thread mode (current):
```java
AILSII ails = new AILSII(instance, config);
ails.run();
```

### Multi-start main thread:
```java
AILSII main = new AILSII(instance, config, sharedEliteSet,
                         null, 1, threadMonitor);
main.run();
```

### Multi-start worker thread:
```java
Solution seed = elite.solution;  // From elite set
AILSII worker = new AILSII(instance, config, sharedEliteSet,
                           seed, 2, threadMonitor);
worker.run();
```

## Summary of Changes

- ✅ Add 3 new fields (threadMonitor, threadId, shouldTerminate)
- ✅ Add new constructor for multi-start
- ✅ Add monitoring hooks in main loop (3 locations)
- ✅ Add terminate() method
- ✅ Add getBestSolution() getter
- ✅ Rename/alias notifyPRBetterSolution → notifyBetterSolution

**Result**: AILS can run as single-thread (backward compatible) or as part of multi-start framework.
