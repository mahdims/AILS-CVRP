# Multi-Start AILS - Testing & Verification Checklist

## Pre-Implementation Verification

### 1. Design Review
- [ ] **Architecture correctness**: Review thread coordination design
  - Main thread protected (Thread-1 never restarts)
  - Workers can restart dynamically
  - Shared elite set is thread-safe
  - Time synchronization across all threads

- [ ] **Seed selection logic**: Verify QualityBasedSeedSelector algorithm
  - Primary: Unused solutions (usage count = 0)
  - Secondary: Higher combined score
  - Usage count increments after selection
  - All elite solutions explored before reuse

- [ ] **Stagnation criteria**: Verify dual-criteria implementation
  - Criterion 1: No elite insertions for N iterations
  - Criterion 2: Gap from global best > X%
  - Both must be true to trigger restart
  - Main thread exempt from restart checks

---

## Post-Implementation Testing

### Phase 1: Code Verification

#### 1.1 AILSII Modifications
- [ ] **Fields added correctly**
  - `ThreadMonitor threadMonitor`
  - `int threadId`
  - `volatile boolean shouldTerminate`

- [ ] **New constructor implemented**
  - Signature: `AILSII(Instance, Config, EliteSet, Solution, int, ThreadMonitor)`
  - Calls existing constructor for base initialization
  - Overrides `eliteSet` with shared instance
  - Sets threadId and threadMonitor
  - Registers with ThreadMonitor
  - Clones initialSolution to bestSolution and referenceSolution (if not null)
  - Prints startup message with threadId and initial f value

- [ ] **Runnable implementation**
  - `implements Runnable` in class declaration
  - `run()` method delegates to `search()`

- [ ] **Monitoring hooks added**
  - **Start of iteration** (in main while loop):
    ```java
    if (threadMonitor != null) {
        threadMonitor.getThreadStats(threadId).recordIteration();
        threadMonitor.getThreadStats(threadId).updateBestF(bestF);

        if (threadId > 1 && threadMonitor.shouldRestart(threadId)) {
            System.out.printf("[Thread-%d] Stagnation detected, terminating...%n", threadId);
            break;
        }
    }

    if (shouldTerminate) {
        System.out.printf("[Thread-%d] External termination signal%n", threadId);
        break;
    }
    ```

  - **After elite insertion** (~line 451, 655):
    ```java
    if (inserted && threadMonitor != null) {
        threadMonitor.getThreadStats(threadId).recordEliteInsertion();
    }
    ```

  - **After global best improvement** (~line 655):
    ```java
    if (newGlobalBest && threadMonitor != null) {
        threadMonitor.getThreadStats(threadId).recordGlobalBestImprovement();
        threadMonitor.updateGlobalBest(bestF);
    }
    ```

- [ ] **New methods added**
  - `terminate()` sets shouldTerminate flag
  - `getBestSolution()` returns bestSolution
  - `notifyBetterSolution(Solution, double)` implemented (delegates to PR notification logic)

#### 1.2 Compilation Check
- [ ] Project compiles without errors
- [ ] No warnings related to thread safety
- [ ] All new classes resolve correctly

---

### Phase 2: Backward Compatibility Testing

**Goal**: Ensure single-thread mode works identically to original implementation

#### 2.1 Configuration
```properties
multiStart.enabled=false
```

#### 2.2 Tests
- [ ] **Identical behavior**: Run same instance with same seed
  - Compare final objective value (should be identical or very close)
  - Compare iteration count at termination
  - Compare elite set final composition

- [ ] **No performance degradation**
  - Time to solution should be same ± 2%
  - Memory usage should be same ± 5%

- [ ] **No threading artifacts**
  - No thread-related log messages
  - ThreadMonitor remains null
  - No shared elite set overhead

#### 2.3 Expected Output
```
[Standard AILS output, no multi-start messages]
```

---

### Phase 3: Basic Multi-Start Functionality

**Goal**: Verify multi-start works with minimal configuration

#### 3.1 Configuration
```properties
multiStart.enabled=true
multiStart.numWorkerThreads=2
multiStart.minEliteSizeForWorkers=3
multiStart.stagnationThreshold=500   # Low for testing
multiStart.competitiveThreshold=0.05
multiStart.notifyMainThread=true
pathRelinking.enabled=true
```

#### 3.2 Tests

##### 3.2.1 Thread Lifecycle
- [ ] **Main thread starts immediately**
  - Log: `[MultiStart] Launching main thread (Thread-1)...`
  - Thread-1 appears in thread monitor

- [ ] **PR thread starts after delay**
  - Log: `[MultiStart] Starting PR thread...`
  - PR thread runs in parallel

- [ ] **Workers wait for elite set**
  - Log: `[MultiStart] Waiting for elite set >= 3 solutions...`
  - Workers don't start until condition met
  - Log: `[MultiStart] Elite set ready (X solutions)`

- [ ] **Workers launch correctly**
  - Log: `[MultiStart] Launching Worker-2 (time remaining: Xs)`
  - Log: `[MultiStart] Launching Worker-3 (time remaining: Xs)`
  - Each worker prints: `[Thread-X] Starting from elite seed: f=Y`

##### 3.2.2 Seed Selection
- [ ] **First worker gets best unused solution**
  - Worker-2 seed has usage count = 0
  - Worker-2 seed has highest combined score among unused

- [ ] **Second worker gets next best unused**
  - Worker-3 seed has usage count = 0
  - Worker-3 seed has second-highest combined score
  - Different solution than Worker-2

- [ ] **Usage count increments**
  - After Worker-2 starts: seed usage = 1
  - After Worker-3 starts: seed usage = 1
  - Log: `[ThreadMonitor] Thread-X restarting (seed usage now: 1)`

##### 3.2.3 Elite Set Integration
- [ ] **All threads insert to same elite set**
  - Elite set shows contributions from Thread-1, Thread-2, Thread-3, PR
  - No duplicate elite sets created

- [ ] **Thread-safe insertions**
  - No race conditions or crashes
  - Elite set size never exceeds configured limit

- [ ] **Source attribution correct**
  - Solutions from main thread: `SolutionSource.AILS`
  - Solutions from PR thread: `SolutionSource.PATH_RELINKING`
  - Solutions retain correct source

##### 3.2.4 Thread Monitoring
- [ ] **Statistics tracked per thread**
  - Each thread has ThreadStats entry
  - Iteration count increases
  - Best F updates correctly
  - Elite insertions counted

- [ ] **Global best updates**
  - ThreadMonitor.globalBestF reflects overall best
  - Updates when any thread finds better solution

##### 3.2.5 Termination
- [ ] **All threads terminate at time limit**
  - Main thread terminates
  - Workers terminate
  - PR thread terminates
  - Log: `[MultiStart] All threads completed`

- [ ] **Final statistics printed**
  - Thread monitor summary
  - Elite set statistics
  - Best solution reported

#### 3.3 Expected Output Pattern
```
==========================================================
MULTI-START AILS
==========================================================
Configuration:
  Main thread: Thread-1 (protected, never restarts)
  Worker threads: 2
  PR thread: enabled
  Seed strategy: QualityBased
  Notify main: true
  Time limit: 300.0s
==========================================================

[MultiStart] Launching main thread (Thread-1)...
[ThreadMonitor] Registered Thread-1
[Thread-1] Starting from elite seed: f=...

[MultiStart] Will start PR thread after 50 iterations
[MultiStart] Waiting for elite set >= 3 solutions...
[MultiStart] Elite set ready (3 solutions)

[MultiStart] Launching Worker-2 (time remaining: 285.3s)
[ThreadMonitor] Registered Thread-2
[SeedSelector] Selected: f=..., score=..., usage=0, source=...
[Thread-2] Starting from elite seed: f=...

[MultiStart] Launching Worker-3 (time remaining: 285.1s)
[ThreadMonitor] Registered Thread-3
[SeedSelector] Selected: f=..., score=..., usage=0, source=...
[Thread-3] Starting from elite seed: f=...

[MultiStart] Starting monitoring loop...
[MultiStart] All threads completed

==========================================================
MULTI-START AILS - FINAL STATISTICS
==========================================================
=== Thread Monitor Summary ===
...
```

---

### Phase 4: Restart Logic Testing

**Goal**: Verify workers restart correctly when stagnant

#### 4.1 Configuration
```properties
multiStart.enabled=true
multiStart.numWorkerThreads=2
multiStart.stagnationThreshold=1000  # Moderate threshold
multiStart.competitiveThreshold=0.02
multiStart.notifyMainThread=true
```

#### 4.2 Tests

##### 4.2.1 Stagnation Detection
- [ ] **Worker becomes stagnant**
  - Worker runs 1000+ iterations without elite insertion
  - Worker's bestF is > 2% worse than global best
  - Log: `[Thread-X] Stagnation detected, terminating...`

- [ ] **Main thread never triggers stagnation**
  - Even if Thread-1 meets stagnation criteria
  - Thread-1 runs until time limit
  - No restart messages for Thread-1

##### 4.2.2 Competitive Thread Protection
- [ ] **Worker close to global best doesn't restart**
  - Worker has gap < 2% from global best
  - Even if no elite insertions for 1000 iterations
  - Worker continues running (intensification)

- [ ] **Worker far from global best restarts**
  - Worker has gap > 2% from global best
  - No elite insertions for 1000 iterations
  - Worker triggers restart

##### 4.2.3 Restart Execution
- [ ] **Old worker terminates gracefully**
  - `oldWorker.terminate()` called
  - Worker breaks from main loop
  - Thread joins within 2 seconds

- [ ] **New seed selected**
  - ThreadMonitor.getRestartSeed() called
  - New seed different from previous (if possible)
  - Usage count for new seed increments
  - Log: `[SeedSelector] Selected: f=..., score=..., usage=X, source=...`

- [ ] **New worker launches**
  - New AILSII instance created with seed
  - Thread ID reused (same ID as old worker)
  - New thread started
  - Log: `[MultiStart] Worker-X restarted (Xs remaining, Y restarts)`

- [ ] **Statistics updated**
  - ThreadStats.restartCount increments
  - ThreadStats resets iterations since last insertion
  - ThreadStats preserves total iteration count (across restarts)

##### 4.2.4 Multiple Restarts
- [ ] **Worker can restart multiple times**
  - First restart works
  - Second restart works
  - Restart count accumulates: 1, 2, 3, ...

- [ ] **Different seeds each time**
  - Usage tracking ensures variety
  - If all seeds used, reuses least-used seed

- [ ] **Time budget reduces**
  - Later restarts get less remaining time
  - Eventually no time for restart → worker not restarted

#### 4.3 Expected Output Pattern
```
[MultiStart] Starting monitoring loop...

[Worker-2] iter=1000, no insertions for 1000 iter, gap=3.5%
[Thread-2] Stagnation detected, terminating for restart...
[MultiStart] Restarting Worker-2
[ThreadMonitor] Thread-2 restarting (seed usage now: 2)
[SeedSelector] Selected: f=1520.3, score=0.8234, usage=1, source=AILS
[MultiStart] Worker-2 restarted (180.5s remaining, 1 restarts)
[Thread-2] Starting from elite seed: f=1520.3

[Worker-2] iter=1000, no insertions for 1000 iter, gap=2.8%
[Thread-2] Stagnation detected, terminating for restart...
[MultiStart] Restarting Worker-2
[ThreadMonitor] Thread-2 restarting (seed usage now: 3)
[SeedSelector] Selected: f=1505.7, score=0.8451, usage=2, source=PATH_RELINKING
[MultiStart] Worker-2 restarted (90.2s remaining, 2 restarts)
[Thread-2] Starting from elite seed: f=1505.7
```

---

### Phase 5: Worker→Main Notification Testing

**Goal**: Verify workers can notify main thread of better solutions

#### 5.1 Configuration
```properties
multiStart.enabled=true
multiStart.numWorkerThreads=2
multiStart.notifyMainThread=true
```

#### 5.2 Tests

##### 5.2.1 Notification Trigger
- [ ] **Worker finds better solution than main**
  - Worker's bestF < Main's bestF - epsilon
  - Monitoring loop detects improvement
  - Log: `[Worker-X→Main] Better solution: Y`

- [ ] **Main thread receives notification**
  - mainAILS.notifyBetterSolution() called
  - Main thread's pending solution updated
  - Main thread adopts worker solution in next iteration

##### 5.2.2 Notification Disabled
- [ ] **With notifyMainThread=false**
  - Workers find better solutions
  - No notification sent to main
  - Main thread independent

#### 5.3 Expected Output
```
[Worker-2] Found better solution: f=1485.3
[Main] Current best: f=1492.7
[Worker-2→Main] Better solution: 1485.3
[Thread-1] Received better solution from worker: f=1485.3
```

---

### Phase 6: Edge Cases & Error Scenarios

#### 6.1 Empty Elite Set
- [ ] **No seeds available initially**
  - Workers wait until elite set ≥ minEliteSizeForWorkers
  - Workers don't launch if elite set remains empty
  - No crashes or deadlocks

#### 6.2 Time Limit Exhausted
- [ ] **Time limit reached before workers launch**
  - Elite set builds slowly
  - Time expires before minEliteSizeForWorkers reached
  - Log: `[MultiStart] Time limit reached before workers could start`
  - Main thread still produces result

- [ ] **No time remaining for restart**
  - Worker becomes stagnant near time limit
  - Restart check finds timeRemaining ≤ 0
  - Log: `[MultiStart] No time remaining for restart`
  - Worker not restarted

#### 6.3 All Workers Stagnant Simultaneously
- [ ] **Multiple workers trigger restart at same time**
  - Monitoring loop handles all restarts
  - No race conditions
  - Each gets different seed (if available)

#### 6.4 All Elite Solutions Used
- [ ] **Usage count > 0 for all elite solutions**
  - Seed selector picks least-used solution
  - No null seed returned
  - Algorithm continues working

#### 6.5 Single Elite Solution
- [ ] **Elite set has only 1 solution**
  - All workers start from same solution
  - Usage count increments for that solution
  - All restarts reuse same solution
  - No crashes

#### 6.6 Very Short Time Limit
- [ ] **Time limit < 60 seconds**
  - Workers may not have time to become stagnant
  - No restarts occur
  - Algorithm still produces result

---

### Phase 7: Scalability Testing

**Goal**: Verify performance with many workers

#### 7.1 Configuration
```properties
multiStart.enabled=true
multiStart.numWorkerThreads=10   # Then 20, 48, 94
multiStart.stagnationThreshold=2000
multiStart.competitiveThreshold=0.02
```

#### 7.2 Tests

##### 7.2.1 Thread Scaling
- [ ] **10 workers**: All launch correctly
- [ ] **20 workers**: All launch correctly
- [ ] **48 workers**: All launch correctly (your target)
- [ ] **94 workers**: All launch correctly (full CPU)

##### 7.2.2 Resource Utilization
- [ ] **CPU utilization**
  - With 48 workers: ~100% CPU (Task Manager / top)
  - All cores active
  - No single-core bottleneck

- [ ] **Memory usage**
  - Linear growth with worker count
  - No memory leaks over 10-minute run
  - JVM heap usage stable

##### 7.2.3 Thread Coordination
- [ ] **Elite set remains consistent**
  - No corruption with 48 concurrent insertions
  - Elite set size never exceeds limit
  - No deadlocks or race conditions

- [ ] **Restart coordination**
  - Multiple workers can restart simultaneously
  - No seed conflicts (each gets different seed if possible)

#### 7.3 Performance Metrics
- [ ] **Iteration throughput increases linearly**
  - 48 workers → ~48x iteration rate vs single thread
  - Measure total iterations across all threads

- [ ] **Solution quality improves**
  - Best solution with 48 workers ≤ best with single thread
  - Elite set more diverse with multi-start

- [ ] **Time to target solution decreases**
  - If target known, measure time to reach it
  - Multi-start should be faster

---

### Phase 8: Configuration Validation

#### 8.1 Invalid Parameters
- [ ] **numWorkerThreads < 0**: Throws exception or logs error
- [ ] **stagnationThreshold < 1**: Throws exception or logs error
- [ ] **competitiveThreshold < 0 or > 1**: Throws exception or logs error
- [ ] **minEliteSizeForWorkers < 1**: Throws exception or logs error

#### 8.2 Extreme Parameters
- [ ] **stagnationThreshold = 1**: Workers restart very frequently
- [ ] **stagnationThreshold = 1000000**: Workers never restart
- [ ] **competitiveThreshold = 0.0**: Only best worker protected
- [ ] **competitiveThreshold = 1.0**: All workers protected

---

### Phase 9: Integration Testing

#### 9.1 With Path Relinking
- [ ] **PR + Multi-Start enabled**
  - PR thread runs in parallel
  - PR solutions appear in elite set with PATH_RELINKING source
  - Workers can restart from PR solutions
  - No conflicts between PR and workers

#### 9.2 With Fleet Minimization
- [ ] **Fleet minimization active**
  - Works correctly in multi-threaded mode
  - Each thread respects fleet minimization logic

#### 9.3 With Warm Start
- [ ] **Warm start + Multi-Start**
  - Main thread starts from warm start solution
  - Workers start from elite seeds
  - No conflicts

---

### Phase 10: Output Validation

#### 10.1 Console Output
- [ ] **Clear thread identification**
  - Messages prefixed with [Thread-X] or [Worker-X→Main]
  - Thread lifecycle events logged

- [ ] **Summary statistics accurate**
  - Thread monitor summary shows all threads
  - Restart counts correct
  - Elite insertion counts sum correctly

- [ ] **No garbled output**
  - Concurrent System.out.println() doesn't overlap
  - (If needed, use synchronized printing)

#### 10.2 Final Statistics
- [ ] **Best solution reported**
  - Matches actual best across all threads
  - Source attribution correct

- [ ] **Elite set statistics**
  - Size, diversity, source breakdown
  - All numbers consistent

---

## Critical Invariants to Verify

### Thread Safety
1. **Elite set never corrupted**
   - Size ≤ configured limit
   - No null entries
   - No duplicate solutions (within diversity threshold)

2. **ThreadMonitor state consistent**
   - globalBestF monotonically decreasing (or stays same)
   - Thread registration complete before use
   - No race conditions on seedUsageCount

3. **No deadlocks**
   - All threads eventually terminate
   - No circular waiting

### Correctness
1. **Main thread protected**
   - Thread-1 NEVER restarts
   - shouldRestart(1) always returns false

2. **Seed uniqueness**
   - Before reuse, all seeds used at least once
   - Usage count accurate

3. **Stagnation criteria**
   - Both conditions must be true: (no insertions) AND (far from best)
   - Competitive threads not restarted

4. **Time synchronization**
   - All threads respect global time limit
   - No thread runs beyond globalTimeLimit

### Performance
1. **No performance regression**
   - Single-thread mode same speed as before
   - Multi-thread mode faster than single-thread

2. **Linear scalability**
   - 48 workers → ~48x iteration throughput
   - (May saturate below linear due to shared elite set contention)

---

## Testing Tools & Commands

### Compilation
```bash
cd e:\Work\BKS\AILS-CVRP-main\AILS-CVRP-main
bash scripts/build_jar.sh
```

### Run Single-Thread (Baseline)
```bash
java -jar AILSII.jar <instance> <parameters_single.txt>
```

### Run Multi-Start (2 workers)
```bash
java -jar AILSII.jar <instance> <parameters_multistart_2.txt>
```

### Run Multi-Start (48 workers, optimized)
```bash
java -Xmx64G \
     -XX:+UseG1GC \
     -XX:MaxGCPauseMillis=200 \
     -XX:+UseNUMA \
     -XX:+UseLargePages \
     -jar AILSII.jar <instance> <parameters_multistart_48.txt>
```

### Monitor CPU Usage
```bash
# Windows
taskmgr

# Linux
htop
```

### Memory Profiling
```bash
# Enable GC logging
java -Xlog:gc*:file=gc.log -jar AILSII.jar ...
```

---

## Sign-Off Checklist

Before declaring Multi-Start AILS production-ready:

- [ ] All Phase 1 checks passed (code verification)
- [ ] All Phase 2 checks passed (backward compatibility)
- [ ] All Phase 3 checks passed (basic multi-start)
- [ ] All Phase 4 checks passed (restart logic)
- [ ] All Phase 5 checks passed (notifications)
- [ ] All Phase 6 checks passed (edge cases)
- [ ] All Phase 7 checks passed (scalability)
- [ ] All Phase 8 checks passed (configuration)
- [ ] All Phase 9 checks passed (integration)
- [ ] All Phase 10 checks passed (output)
- [ ] All critical invariants verified
- [ ] Performance benchmarks meet expectations
- [ ] Documentation complete and accurate

**Ready for Production:** ☐ Yes ☐ No

**Notes:**
```
[Space for test results, issues found, performance numbers, etc.]
```
