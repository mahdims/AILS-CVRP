# Multi-Start AILS - Known Risks & Mitigation Strategies

## Critical Design Decisions to Validate

### 1. Thread-Safe Elite Set Access

**Risk**: Elite set corruption with 48+ concurrent threads inserting solutions

**What Could Go Wrong:**
- Race condition: Two threads insert simultaneously, elite set size exceeds limit
- Deadlock: Thread holds lock while waiting for another operation
- Performance: Lock contention becomes bottleneck with many threads

**Current Mitigation:**
- EliteSet uses `ReentrantReadWriteLock` for thread safety
- `tryInsert()` method uses write lock
- `getAllEliteSolutionsThreadSafe()` uses read lock

**What to Verify:**
- [ ] Elite set size NEVER exceeds configured limit (check after every run)
- [ ] No deadlocks with 48 threads (run for 10+ minutes)
- [ ] Lock contention acceptable (measure time spent waiting for locks)

**How to Test:**
```java
// Add instrumentation to EliteSet.tryInsert():
long waitStart = System.nanoTime();
lock.writeLock().lock();
long waitTime = System.nanoTime() - waitStart;
if (waitTime > 1_000_000) { // > 1ms
    System.out.printf("[EliteSet] Long lock wait: %.2fms%n", waitTime / 1_000_000.0);
}
```

**Red Flags:**
- Elite set size = 11 when limit = 10
- Program hangs indefinitely
- Lock wait times > 100ms frequently

---

### 2. Solution Object Identity vs. Equality

**Risk**: Seed usage tracking breaks if Solution objects don't maintain identity

**What Could Go Wrong:**
- Worker restarts from "same" solution but usage count doesn't increment
- Solution cloning creates new objects → usage map doesn't recognize them
- All workers restart from same solution repeatedly

**Current Design:**
- `seedUsageCount` is `Map<Solution, Integer>` using object reference as key
- Solutions are cloned when inserted to elite set
- Usage count tracks the **elite set's Solution instance**

**Potential Issue:**
When we call `seedSelector.selectSeed(allElite, seedUsageCount)`, it returns `elite.solution`.
Then we increment `seedUsageCount.put(selected, ...)`.
But if `elite.solution` is later cloned or replaced, the map entry becomes orphaned.

**What to Verify:**
- [ ] Usage count increments correctly for each restart
- [ ] Same solution not selected twice in a row (unless all seeds used)
- [ ] Usage count matches expected value (num workers / num elite solutions)

**How to Test:**
```java
// Add logging in ThreadMonitor.getRestartSeed():
Solution selected = seedSelector.selectSeed(allElite, seedUsageCount);
int currentUsage = seedUsageCount.getOrDefault(selected, 0);
System.out.printf("[Debug] Selected solution identity: %d, current usage: %d%n",
                  System.identityHashCode(selected), currentUsage);
```

**Red Flags:**
- Worker-2 and Worker-3 both start from f=1500.0 with usage=0
- Usage count never increments beyond 1
- All restarts select same solution despite 10 solutions in elite set

**Potential Fix (if needed):**
Instead of `Map<Solution, Integer>`, use `Map<EliteSolution, Integer>` and track the wrapper.
Or: Add unique ID to Solution class and use `Map<UUID, Integer>`.

---

### 3. Time Synchronization Accuracy

**Risk**: Threads don't terminate at same time, causing uneven exploration

**What Could Go Wrong:**
- Workers launched late get very little time
- Some workers terminate early while others continue
- Total time exceeds configured limit

**Current Design:**
- Global start time set at beginning: `globalStartTime = System.currentTimeMillis()`
- Workers launched after elite set ready (may be delayed)
- Each worker gets: `timeRemaining = globalTimeLimit - (launchTime - globalStartTime) / 1000.0`

**What to Verify:**
- [ ] All threads terminate within ±5 seconds of globalTimeLimit
- [ ] Workers launched late get proportionally reduced time
- [ ] No thread runs beyond configured time limit

**How to Test:**
```java
// Add termination logging in each thread:
System.out.printf("[Thread-%d] Terminating after %.2fs (global elapsed: %.2fs)%n",
                  threadId,
                  (System.currentTimeMillis() - myStartTime) / 1000.0,
                  (System.currentTimeMillis() - globalStartTime) / 1000.0);
```

**Red Flags:**
- Worker-2 terminates at 300.1s, Worker-48 at 310.5s (10s skew!)
- Some workers get < 10s to run before time expires
- Total runtime exceeds globalTimeLimit by > 10s

---

### 4. Stagnation Detection False Positives

**Risk**: Good workers restart prematurely, wasting progress

**What Could Go Wrong:**
- Worker is intensifying (close to global best) but gets restarted
- Competitive threshold too low → all workers restart
- Stagnation threshold too low → workers restart before elite set stabilizes

**Current Design:**
- Dual criteria: (no insertions for N iterations) AND (gap > X%)
- Competitive threshold default: 0.02 (2%)
- Stagnation threshold default: 2000 iterations

**What to Verify:**
- [ ] Workers within 2% of global best are NOT restarted
- [ ] Workers far from global best but still inserting are NOT restarted
- [ ] Only truly stagnant workers restart

**How to Test:**
```java
// Add detailed logging in ThreadStats.isStagnant():
boolean criterion1 = iterationsSinceLastInsertion >= stagnationThreshold;
boolean criterion2 = gap > competitiveThreshold;
boolean result = criterion1 && criterion2;

System.out.printf("[Thread-%d] Stagnation check: iter=%d, gap=%.4f, restart=%s%n",
                  threadId, iterationsSinceLastInsertion, gap, result);
```

**Red Flags:**
- Worker with gap=0.015 (1.5%) gets restarted
- Worker with 1500 iterations but just inserted gets restarted
- All workers restart every 2000 iterations like clockwork

---

### 5. Main Thread Notification Overhead

**Risk**: Worker→Main notifications slow down main thread

**What Could Go Wrong:**
- Main thread spends too much time processing notifications
- Notification queue grows unbounded
- Synchronization overhead on `notifyBetterSolution()`

**Current Design:**
- Monitoring loop checks workers every 1 second
- If worker better than main, call `mainAILS.notifyBetterSolution()`
- Main thread checks pending solution at start of each iteration

**What to Verify:**
- [ ] Main thread iteration rate doesn't drop with notifications enabled
- [ ] Notification processing time < 1ms
- [ ] No lock contention on pending solution buffer

**How to Test:**
```java
// Compare main thread iteration rate:
// Run 1: notifyMainThread=false → record iterations/sec
// Run 2: notifyMainThread=true  → record iterations/sec
// Acceptable if Run2 >= 90% of Run1
```

**Red Flags:**
- Main thread: 100 iter/sec with notifications disabled, 50 iter/sec enabled
- Main thread spends 50% time waiting for locks
- Pending solution buffer overflows

---

### 6. Seed Exhaustion

**Risk**: More restarts than elite solutions → forced reuse → exploration suffers

**What Could Go Wrong:**
- Elite set size = 10, but 48 workers × 5 restarts = 240 seed requests
- Workers repeatedly restart from same 10 solutions
- No exploration benefit from restarts

**Current Design:**
- QualityBasedSeedSelector prefers unused (usage=0) solutions first
- When all used, selects least-used solution
- No upper limit on reuse

**What to Verify:**
- [ ] Elite set grows to configured size (10 solutions)
- [ ] Average usage count = (total restarts) / (elite set size)
- [ ] Usage distribution is relatively even

**How to Test:**
```java
// At end of run, print usage statistics:
for (Map.Entry<Solution, Integer> entry : seedUsageCount.entrySet()) {
    System.out.printf("Seed f=%.2f used %d times%n",
                      entry.getKey().f, entry.getValue());
}
// Check: max usage / min usage should be < 2 (relatively balanced)
```

**Red Flags:**
- One solution used 50 times, others used 0-2 times
- Elite set has 3 solutions but should have 10
- All workers restart from same solution despite diversity available

**Potential Fix (if needed):**
Increase elite set size to accommodate more workers:
```properties
eliteSet.size=20  # If numWorkerThreads=48
```

---

### 7. AILSII Constructor Ambiguity

**Risk**: Wrong constructor called, threads don't initialize correctly

**What Could Go Wrong:**
- Existing constructor `AILSII(Instance, Config)` called instead of new one
- ThreadMonitor remains null → monitoring code doesn't execute
- Workers run as independent single threads (not integrated)

**Current Design:**
- New constructor: `AILSII(Instance, Config, EliteSet, Solution, int, ThreadMonitor)`
- Existing constructor: `AILSII(Instance, Config)`
- New constructor should call existing one for base initialization

**What to Verify:**
- [ ] Main thread: threadMonitor != null, threadId = 1
- [ ] Worker threads: threadMonitor != null, threadId = 2+
- [ ] Single-thread mode: threadMonitor == null, threadId = 0 (or undefined)

**How to Test:**
```java
// Add assertion in AILSII multi-start constructor:
if (threadMonitor == null) {
    throw new IllegalArgumentException("ThreadMonitor cannot be null in multi-start mode");
}
```

**Red Flags:**
- Worker thread prints "threadMonitor is null" in monitoring hook
- Workers don't register with ThreadMonitor
- Restart logic never triggers because shouldRestart() returns false (null check)

---

### 8. Memory Leak from Thread Accumulation

**Risk**: Old workers don't fully terminate, memory leaks

**What Could Go Wrong:**
- Worker restarts 10 times → 10 AILSII instances in memory
- Old threads don't garbage collect
- Memory grows unbounded

**Current Design:**
- Old worker: `terminate()` → `shouldTerminate=true` → loop breaks → thread terminates
- Old thread: `join(2000)` waits up to 2 seconds
- Old references: overwritten in `workerAILS.set(i, newWorker)` and `workerThreads.set(i, newThread)`

**What to Verify:**
- [ ] Old workers fully terminate (thread state = TERMINATED)
- [ ] Memory usage stable across multiple restarts
- [ ] No memory leaks after 10+ restarts per worker

**How to Test:**
```bash
# Monitor JVM heap usage over time:
java -Xlog:gc*:file=gc.log -Xmx8G -jar AILSII.jar ...

# Check for memory leaks:
# 1. Heap usage should plateau after initial ramp-up
# 2. GC should keep heap below 50% after stabilization
# 3. No continuous upward trend
```

**Red Flags:**
- Heap usage grows from 2GB → 4GB → 6GB over 10 minutes
- Old threads still in RUNNABLE state after restart
- `workerAILS.size()` grows instead of staying constant

**Potential Fix (if needed):**
```java
// Before overwriting, explicitly null out old references:
AILSII oldWorker = workerAILS.get(i);
oldWorker.terminate();
oldThread.join(2000);
workerAILS.set(i, null);  // Release reference
workerThreads.set(i, null);
// Then create new worker...
```

---

### 9. Pathological Restart Loops

**Risk**: Worker restarts immediately after restart, wasting time

**What Could Go Wrong:**
- Worker restarts from poor seed
- Immediately becomes stagnant again (bad seed)
- Restarts again → bad seed → infinite loop
- All time spent restarting, no useful search

**Current Design:**
- No cooldown period after restart
- Worker can trigger stagnation check immediately after restart
- If seed is poor, worker might restart quickly

**What to Verify:**
- [ ] Worker doesn't restart within 100 iterations of previous restart
- [ ] Average iterations between restarts > stagnationThreshold / 2
- [ ] Restart rate stabilizes (not accelerating)

**How to Test:**
```java
// Track time between restarts in ThreadStats:
private long lastRestartIteration = 0;

public void recordRestart() {
    long currentIteration = totalIterations;
    long iterationsSinceRestart = currentIteration - lastRestartIteration;
    System.out.printf("[Thread-%d] Restart after %d iterations%n",
                      threadId, iterationsSinceRestart);
    lastRestartIteration = currentIteration;
    restartCount++;
}
```

**Red Flags:**
- Worker restarts every 50 iterations (threshold is 2000!)
- Worker-2 restarts 100 times in 300 seconds (every 3 seconds)
- Restart frequency increasing over time

**Potential Fix (if needed):**
Add cooldown period after restart:
```java
// In AILSII monitoring hook:
if (threadId > 1 &&
    threadMonitor.shouldRestart(threadId) &&
    iterator - lastRestartIteration > 500) {  // Cooldown: 500 iterations
    // Restart...
}
```

---

### 10. PR Thread Competition

**Risk**: PR thread conflicts with worker threads, causing issues

**What Could Go Wrong:**
- PR and workers both try to insert to elite set simultaneously
- PR notifies main thread at same time as worker
- PR thread exhausts time limit before workers finish

**Current Design:**
- PR thread shares same elite set (thread-safe)
- PR notifies main thread via `notifyPRBetterSolution()`
- Workers notify via `notifyBetterSolution()` (should be same mechanism)
- PR thread runs independently until stopped

**What to Verify:**
- [ ] PR and workers both contribute to elite set
- [ ] No conflicts between PR and worker notifications to main
- [ ] PR thread terminates when main thread terminates

**How to Test:**
```java
// Check elite set at end:
// Should see contributions from: AILS (main+workers) and PATH_RELINKING
// Verify both sources present in final elite set
```

**Red Flags:**
- Elite set has 10 solutions, all from AILS (PR inserted 0)
- PR thread still running after main thread terminated
- Main thread receives garbled notifications from PR+workers

---

## Pre-Flight Checklist

Before running multi-start in production:

### Code Review
- [ ] All monitoring hooks added to AILSII
- [ ] threadMonitor null checks in place
- [ ] Protected thread check in shouldRestart()
- [ ] Usage count increments in getRestartSeed()

### Configuration Validation
- [ ] Backward compatibility tested (enabled=false)
- [ ] Parameters in valid ranges
- [ ] Elite set size ≥ minEliteSizeForWorkers

### Thread Safety Audit
- [ ] EliteSet uses locks correctly
- [ ] seedUsageCount is ConcurrentHashMap
- [ ] threadStats is ConcurrentHashMap
- [ ] No shared mutable state without synchronization

### Performance Baseline
- [ ] Single-thread baseline established
- [ ] Target solution quality known
- [ ] Acceptable runtime measured

---

## Debugging Tools

### Enable Detailed Logging
```java
// In MultiStartAILS, add verbose flag:
private static final boolean VERBOSE = true;

if (VERBOSE) {
    System.out.printf("[Debug] Thread-%d: iter=%d, bestF=%.2f, gap=%.4f%n", ...);
}
```

### Thread Dump on Hang
```bash
# If program hangs, get thread dump:
jstack <pid> > thread_dump.txt
# Look for BLOCKED threads (deadlock indicator)
```

### Profile Lock Contention
```bash
# Use JProfiler or VisualVM to identify lock bottlenecks
java -agentlib:jdwp=... -jar AILSII.jar
```

### Monitor GC Pressure
```bash
# Check if GC is bottleneck:
java -Xlog:gc*:file=gc.log -jar AILSII.jar
# Look for long GC pauses (> 100ms)
```

---

## When to Abort Testing

If you observe any of these, STOP and fix before proceeding:

1. **Deadlock**: Program hangs indefinitely
2. **Crash**: JVM crash, OutOfMemoryError, StackOverflowError
3. **Corruption**: Elite set size exceeds limit, null pointer exceptions
4. **Incorrect Results**: Multi-start produces worse solution than single-thread
5. **Extreme Slowdown**: Multi-start slower than single-thread (should be faster!)

---

## Success Criteria

Multi-start is working correctly if:

1. **Correctness**
   - Backward compatibility: Single-thread mode identical to baseline
   - No crashes or errors after 30-minute run
   - Elite set integrity maintained

2. **Performance**
   - 48 workers → ~30-40x iteration throughput (some overhead expected)
   - Solution quality ≥ single-thread baseline
   - CPU utilization near 100%

3. **Behavior**
   - Main thread never restarts
   - Workers restart when stagnant (2-5 restarts typical)
   - Seed selection balanced (usage counts within 2x of each other)
   - All threads terminate within 5 seconds of time limit

4. **Monitoring**
   - Statistics accurate and consistent
   - Logs clear and informative
   - No warning/error messages

---

**Ready to proceed with implementation?** Review these risks and ensure mitigation strategies are in place.
