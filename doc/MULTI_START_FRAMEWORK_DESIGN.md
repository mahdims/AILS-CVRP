# Multi-Start AILS Framework with Dynamic Thread Restart

## Overview

This framework extends AILS-II with **parallel multi-start** capabilities and **adaptive thread management**:

- **Multiple AILS threads** run in parallel, each starting from different PR-discovered solutions
- **Thread monitoring** detects stagnant threads and dynamically restarts them
- **Intelligent restart strategy** avoids killing intensifying threads
- **Unlimited restarts** with smart termination criteria
- **Shared elite set** for global knowledge sharing (NOT per-thread elite sets)

---

## Architecture

```
┌─────────────────────────────────────────────────┐
│        Shared Elite Set (Thread-Safe)           │
│   ↑ All threads contribute                      │
│   ↑ All threads read from (PR seeds, crossover) │
└───┬─────────────────────────────────────────────┘
    │
    ↓ (monitors)
┌─────────────────────────────────────────────────┐
│           Thread Monitor (Coordinator)          │
│  - Tracks per-thread statistics                 │
│  - Detects stagnation                           │
│  - Provides restart seeds (LRU PR solutions)    │
│  - Avoids killing intensifying threads          │
└───┬─────────────────────────────────────────────┘
    │
    ↓ (manages)
┌───┴──────────┬──────────────┬──────────────┐
│              │              │              │
↓              ↓              ↓              ↓
┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐
│ AILS-1   │ │ AILS-2   │ │ AILS-3   │ │    PR    │
│ Thread   │ │ Thread   │ │ Thread   │ │  Thread  │
│          │ │          │ │          │ │          │
│ Start:   │ │ Start:   │ │ Start:   │ │ Generates│
│ PR sol 1 │ │ PR sol 2 │ │ PR sol 3 │ │ PR seeds │
│          │ │          │ │          │ │          │
│ Stats:   │ │ Stats:   │ │ Stats:   │ │          │
│ Inserts: │ │ Inserts: │ │ Inserts: │ │          │
│   15     │ │    2     │ │   8      │ │          │
│ BestF:   │ │ BestF:   │ │ BestF:   │ │          │
│ 94300    │ │ 95000    │ │ 94500    │ │          │
│ Gap:     │ │ Gap:     │ │ Gap:     │ │          │
│  0.1%    │ │  0.8%    │ │  0.3%    │ │          │
│          │ │          │ │          │ │          │
│ [ACTIVE] │ │[STAGNANT]│ │ [ACTIVE] │ │          │
└──────────┘ └────┬─────┘ └──────────┘ └──────────┘
                  │
                  │ Restart from unused PR solution
                  ↓
            ┌──────────┐
            │ AILS-2   │
            │ (reborn) │
            │ Start:   │
            │ PR sol 4 │
            └──────────┘
```

---

## Key Design Decisions

### ✅ Shared Elite Set (NOT Per-Thread)

**Why shared?**
- Global knowledge sharing across all threads
- PR needs access to all discoveries for trajectory exploration
- Prevents redundant exploration of same regions
- Clear quality metric: **contribution to shared elite set**

**Why NOT per-thread elite sets?**
- Creates information silos
- Misleading quality metrics (local quality ≠ global contribution)
- Defeats purpose of parallel collaboration
- Cripples PR effectiveness

### ✅ Thread Performance Tracking

Each thread tracks:
- **Total iterations** executed
- **Elite insertions** (contributions to shared elite set)
- **Global best improvements** (new global best found)
- **Iterations since last insertion** (stagnation detector)
- **Current best F** (local best for gap calculation)
- **Restart count** (how many times restarted)

### ✅ Smart Stagnation Detection

A thread is **stagnant** if BOTH conditions are met:

1. **No contribution**: No insertion to shared elite set for N iterations
2. **Far from global best**: Thread's best is >X% worse than global best

**Key insight**: If thread hasn't inserted recently BUT is competitive with global best → it's **intensifying** → **keep running**

```java
boolean isStagnant(double globalBestF, int stagnationThreshold, double competitiveThreshold) {
    // Criterion 1: No contribution to elite set recently
    if (iterationsSinceLastInsertion >= stagnationThreshold) {

        // Criterion 2: Far from global best (not intensifying)
        double gap = (currentBestF - globalBestF) / globalBestF;

        if (gap > competitiveThreshold) {
            return true;  // Stagnant: no insertions + far from global best
        }
        // else: Competitive → likely intensifying → keep running
    }
    return false;
}
```

**Example scenarios:**

| Scenario | Iterations Since Insertion | Gap from Global Best | Decision | Reason |
|----------|---------------------------|---------------------|----------|---------|
| Thread A | 5000 | 0.1% | **Keep running** | Intensifying near optimal |
| Thread B | 5000 | 2.0% | **Restart** | Stagnant in suboptimal region |
| Thread C | 100 | 2.0% | **Keep running** | Too early to judge |
| Thread D | 5000 | 0.5% | **Depends on threshold** | Borderline case |

### ✅ Restart Strategy: Least Recently Used PR Solutions

When restarting a thread:
1. Get all PR-generated solutions from shared elite set
2. Sort by insertion iteration (oldest first)
3. Select first **unused** PR solution
4. If all used, reuse oldest PR solution

**Why this strategy?**
- Ensures **all PR discoveries get explored** by AILS threads
- Maximizes utilization of PR's diverse search regions
- Prevents repeatedly restarting from same solution

### ✅ Unlimited Restarts

No hard limit on restart count because:
- Smart stagnation detection prevents unnecessary restarts
- Intensifying threads won't be killed
- Allows thorough exploration of all PR-discovered regions
- Natural termination: algorithm time limit

---

## Implementation Components

### 1. ThreadStats Class
**Location**: `src/SearchMethod/ThreadStats.java`

**Purpose**: Track performance metrics for one AILS thread

**Key Methods**:
- `recordIteration()` - Increment iteration counter
- `recordEliteInsertion()` - Record contribution to elite set
- `updateBestF(double)` - Update thread's local best
- `isStagnant(globalBestF, threshold, competitive)` - Check if should restart
- `getInsertionRate()` - Calculate insertions per 1000 iterations
- `getGapFromGlobalBest(globalBestF)` - Calculate % gap from global best

### 2. ThreadMonitor Class
**Location**: `src/SearchMethod/ThreadMonitor.java`

**Purpose**: Coordinate all AILS threads, detect stagnation, provide restart seeds

**Key Methods**:
- `registerThread(threadId)` - Register new thread
- `getThreadStats(threadId)` - Get stats for thread
- `updateGlobalBest(bestF)` - Update global best value
- `shouldRestart(threadId)` - Check if thread should restart
- `getRestartSeed()` - Get LRU PR solution for restart
- `printSummary()` - Display monitoring statistics

### 3. Modified AILSII Integration

**Changes needed**:
```java
public class AILSII {
    private ThreadMonitor threadMonitor;  // NEW
    private int threadId;                 // NEW
    private volatile boolean shouldTerminate;  // NEW

    // Constructor accepting thread monitor and ID
    public AILSII(..., ThreadMonitor monitor, int threadId) {
        this.threadMonitor = monitor;
        this.threadId = threadId;
        this.shouldTerminate = false;

        if (monitor != null) {
            monitor.registerThread(threadId);
        }
    }

    // Main loop modifications
    public void run() {
        while (!shouldTerminate && !timeLimitExceeded()) {
            // Report iteration
            if (threadMonitor != null) {
                threadMonitor.getThreadStats(threadId).recordIteration();
                threadMonitor.getThreadStats(threadId).updateBestF(bestF);
            }

            // ... existing AILS logic ...

            // Report elite insertion
            if (inserted && threadMonitor != null) {
                threadMonitor.getThreadStats(threadId).recordEliteInsertion();
            }

            // Report global best improvement
            if (newGlobalBest && threadMonitor != null) {
                threadMonitor.getThreadStats(threadId).recordGlobalBestImprovement();
                threadMonitor.updateGlobalBest(bestF);
            }

            // Check for termination signal
            if (threadMonitor != null && threadMonitor.shouldRestart(threadId)) {
                System.out.println("[Thread-" + threadId + "] Stagnation detected, terminating...");
                break;  // Exit loop, will be restarted by coordinator
            }
        }
    }

    // External termination
    public void terminate() {
        this.shouldTerminate = true;
    }
}
```

### 4. Multi-Start Coordinator

**Location**: `src/SearchMethod/MultiStartAILS.java` (NEW)

**Purpose**: Launch and manage multiple AILS threads with dynamic restarts

**Pseudocode**:
```java
public class MultiStartAILS {
    private EliteSet sharedEliteSet;
    private ThreadMonitor threadMonitor;
    private List<Thread> ailsThreads;
    private int numThreads;

    public void run() {
        // 1. Start PR thread (generates seeds)
        startPRThread();

        // 2. Wait for PR to generate initial seeds
        waitForPRSeeds(numThreads);

        // 3. Launch initial AILS threads
        for (int i = 1; i <= numThreads; i++) {
            Solution seed = threadMonitor.getRestartSeed();
            launchAILSThread(i, seed);
        }

        // 4. Monitor loop (runs until time limit)
        while (!timeLimitExceeded()) {
            for (int i = 1; i <= numThreads; i++) {
                if (threadMonitor.shouldRestart(i)) {
                    // Terminate stagnant thread
                    terminateThread(i);

                    // Get new restart seed
                    Solution newSeed = threadMonitor.getRestartSeed();

                    // Relaunch thread with new seed
                    launchAILSThread(i, newSeed);

                    threadMonitor.getThreadStats(i).recordRestart();
                }
            }

            Thread.sleep(1000);  // Check every second
        }

        // 5. Cleanup
        terminateAllThreads();
        threadMonitor.printSummary();
    }
}
```

---

## Configuration Parameters

**New parameters for `parameters.txt`**:

```properties
# ============================================================
# Multi-Start AILS Parameters
# ============================================================

# Enable multi-start AILS (parallel AILS threads from PR seeds)
multiStart.enabled=true

# Number of parallel AILS threads to launch
# Each thread starts from a different PR-generated solution
# Recommended: 2-4 threads (balance parallelism vs. resource usage)
multiStart.numThreads=3

# Stagnation detection threshold (iterations without elite insertion)
# Thread is considered stagnant if no insertion for this many iterations
# AND thread is far from global best (see competitiveThreshold)
# Recommended: 1000-5000 iterations
multiStart.stagnationThreshold=2000

# Competitive threshold (percentage gap from global best)
# Thread is considered competitive if within this % of global best
# Even if no insertions, competitive threads keep running (intensifying)
# Recommended: 0.01 (1%) - 0.05 (5%)
multiStart.competitiveThreshold=0.02

# How to distribute time budget across threads
# Options: "shared" (all threads share total time) or "individual" (each gets full time)
multiStart.timeBudgetMode=shared
```

---

## Expected Benefits

### 1. **Parallel Exploration of PR Discoveries**
- All PR-generated solutions get dedicated AILS thread
- No wasted PR discoveries

### 2. **Adaptive Resource Allocation**
- Unpromising threads get terminated and restarted
- Computational resources focus on productive search regions

### 3. **Avoiding Premature Termination**
- Threads near global best (intensifying) are protected
- Only stagnant threads far from optimum are restarted

### 4. **Diverse Final Elite Set**
- Solutions from multiple threads exploring different regions
- Better quality-diversity tradeoff

### 5. **Better CPU Utilization**
- Multi-core processors fully utilized
- Faster convergence to high-quality solutions

---

## Example Run Scenario

**Setup**: 3 AILS threads, PR enabled

**Timeline**:

1. **Iterations 0-100**: PR generates 5 solutions, all inserted to elite set
2. **Iteration 100**: Launch AILS-1, AILS-2, AILS-3 from best 3 PR solutions
3. **Iterations 100-2000**: All threads exploring actively
   - AILS-1: 20 insertions, best=94300 (gap=0.1%)
   - AILS-2: 3 insertions, best=95000 (gap=0.8%)
   - AILS-3: 15 insertions, best=94500 (gap=0.3%)
4. **Iteration 2100**: Thread-2 detected as stagnant
   - No insertions for 2000 iterations
   - Gap = 0.8% > 0.02 (2%) threshold
   - **Action**: Terminate Thread-2, restart from unused PR solution #4
5. **Iterations 2100-5000**: Thread-2 reborn, explores new region
   - Thread-2 (restarted): 8 insertions, best=94400 (gap=0.2%)
6. **Final Result**: Elite set contains solutions from all threads + PR
   - AILS-1: 4 solutions
   - AILS-2: 2 solutions
   - AILS-3: 3 solutions
   - PR: 1 solution
   - Global best: 94280 (from AILS-1)

---

## Next Steps

1. ✅ Create `ThreadStats` class
2. ✅ Create `ThreadMonitor` class
3. ⏳ Modify `AILSII` to integrate with `ThreadMonitor`
4. ⏳ Create `MultiStartAILS` coordinator
5. ⏳ Add configuration parameters to `parameters.txt`
6. ⏳ Test with benchmark instances

---

## Summary

**Core Innovation**: Smart stagnation detection that avoids killing intensifying threads

**Key Formula**:
```
Stagnant = (No insertions for N iterations) AND (Gap > X%)
```

This ensures:
- Threads exploring far regions → get restarted if unproductive
- Threads near optimum → keep running even without insertions (intensifying)
- All PR discoveries → get explored by dedicated AILS threads
- Computational resources → dynamically allocated to promising regions

**Result**: Better solutions, better resource utilization, better exploration-exploitation balance.
