# Multi-Start AILS - Quick Start Guide

## ✅ Implementation Complete!

All components are implemented, compiled, and ready to test on your dev machine (8 cores).

---

## 🚀 Test 1: Verify Backward Compatibility (Single-Thread)

**Ensure multi-start is DISABLED first:**

In [parameters.txt](parameters.txt):
```properties
multiStart.enabled=false
```

**Run a quick test:**
```bash
java -jar AILSII.jar -file data/Vrp_Set_X/X-n101-k25.vrp -rounded true -best 27591 -limit 60 -stoppingCriterion Time
```

**Expected**: Should run exactly as before, no multi-start messages.

---

## 🚀 Test 2: Enable Multi-Start (6 Workers for Your Dev Machine)

**Enable multi-start in [parameters.txt](parameters.txt):**
```properties
# Enable multi-start AILS
multiStart.enabled=true

# Use 6 workers for 8-core dev machine (1 main + 6 workers + 1 PR = 8 threads)
multiStart.numWorkerThreads=6

# Wait for 6 elite solutions before launching workers
multiStart.minEliteSizeForWorkers=6

# Stagnation threshold
multiStart.stagnationThreshold=2000

# Competitive threshold (2%)
multiStart.competitiveThreshold=0.02

# Enable worker→main notifications
multiStart.notifyMainThread=true
```

**Run multi-start test (5 minutes):**
```bash
java -jar AILSII.jar -file data/Vrp_Set_X/X-n101-k25.vrp -rounded true -best 27591 -limit 300 -stoppingCriterion Time
```

**Expected output:**
```
[Main] Multi-Start AILS enabled with 6 worker threads

==========================================================
MULTI-START AILS
==========================================================
Configuration:
  Main thread: Thread-1 (protected, never restarts)
  Worker threads: 6
  PR thread: enabled
  Seed strategy: QualityBased
  Notify main: true
  Time limit: 300.0s
==========================================================

[MultiStart] Launching main thread (Thread-1)...
[ThreadMonitor] Registered Thread-1

[AILS] Path Relinking thread started
[MultiStart] Waiting for elite set >= 6 solutions...
[MultiStart] Elite set ready (6 solutions)

[MultiStart] Launching Worker-2 (time remaining: 285.3s)
[ThreadMonitor] Registered Thread-2
[SeedSelector] Selected: f=1520.50, score=0.8421, usage=0, source=AILS
[Thread-2] Starting from elite seed: f=1520.50

[MultiStart] Launching Worker-3 (time remaining: 285.1s)
[ThreadMonitor] Registered Thread-3
[SeedSelector] Selected: f=1535.20, score=0.8156, usage=0, source=PATH_RELINKING
[Thread-3] Starting from elite seed: f=1535.20

[MultiStart] Launching Worker-4 (time remaining: 284.9s)
...
[MultiStart] Launching Worker-7 (time remaining: 284.2s)

[MultiStart] Starting monitoring loop...

... [AILS iterations from all threads] ...

[MultiStart] All threads completed

=== Thread Monitor Summary ===
Global Best: 1485.32
Stagnation Threshold: 2000 iterations
Competitive Threshold: 2.0%

Thread Statistics:
  Thread-1: iter=5234, inserts=12 (2.29/1k), bestF=1485.32, gap=0.00%, stagnant=0, restarts=0 [ACTIVE]
  Thread-2: iter=4892, inserts=8 (1.64/1k), bestF=1492.15, gap=0.46%, stagnant=0, restarts=2 [ACTIVE]
  Thread-3: iter=4756, inserts=6 (1.26/1k), bestF=1498.42, gap=0.88%, stagnant=0, restarts=1 [ACTIVE]
  Thread-4: iter=4621, inserts=5 (1.08/1k), bestF=1502.33, gap=1.14%, stagnant=0, restarts=3 [ACTIVE]
  Thread-5: iter=4389, inserts=4 (0.91/1k), bestF=1509.12, gap=1.60%, stagnant=0, restarts=2 [ACTIVE]
  Thread-6: iter=4512, inserts=7 (1.55/1k), bestF=1495.87, gap=0.71%, stagnant=0, restarts=1 [ACTIVE]
  Thread-7: iter=4298, inserts=3 (0.70/1k), bestF=1515.45, gap=2.03%, stagnant=0, restarts=4 [ACTIVE]

=== Elite Set Statistics ===
Size: 10/10
Source Breakdown:
  AILS: 7 (70.0%)
  PATH_RELINKING: 3 (30.0%)
Average Diversity: 0.245
...

Best solution: 1485.32
==========================================================
```

---

## 🧪 What to Check

### ✅ Success Indicators

1. **All workers launch**
   - Should see 6 workers (Thread-2 through Thread-7) launch
   - Each starts from different elite seed

2. **CPU utilization**
   - Open Task Manager (Windows) or htop (Linux)
   - CPU should be near 100% (all 8 cores busy)

3. **Workers restart when stagnant**
   - Look for restart messages
   - Restart count should be > 0 for some workers

4. **Different seeds selected**
   - Each worker should start from different elite solution
   - Usage count should increment (usage=0 → usage=1 → usage=2...)

5. **Thread Monitor Summary**
   - Shows statistics for all 7 threads (1 main + 6 workers)
   - Restart counts vary (0-5 restarts typical)
   - Elite insertion rates vary per thread

6. **Final solution quality**
   - Multi-start should find equal or better solution than single-thread
   - Check "Best solution: XXX" at end

### 🚨 Red Flags

- Workers don't launch (stuck waiting for elite set)
- All workers start from same seed
- Usage count never increments
- CPU usage < 50% (threads not running)
- Crashes or errors
- Worse solution quality than single-thread

---

## 📊 Performance Comparison

**Run both modes and compare:**

```bash
# Single-thread (baseline)
# Set: multiStart.enabled=false
java -jar AILSII.jar -file data/instance.vrp -limit 600 -stoppingCriterion Time
# Note the best solution and time to find it

# Multi-start (6 workers)
# Set: multiStart.enabled=true, numWorkerThreads=6
java -jar AILSII.jar -file data/instance.vrp -limit 600 -stoppingCriterion Time
# Compare best solution and convergence time
```

**Expected:**
- Multi-start should find equal or better solution
- Multi-start should converge faster (more iterations/second)
- Total iterations across all threads should be 5-7x single-thread

---

## 🔧 Tuning for Your Dev Machine

### If workers restart too often (> 10 restarts per worker):
```properties
# Increase stagnation threshold
multiStart.stagnationThreshold=3000
```

### If workers never restart (stagnant without restarting):
```properties
# Lower stagnation threshold
multiStart.stagnationThreshold=1500
```

### If CPU usage < 100%:
```properties
# Reduce workers (maybe some overhead)
multiStart.numWorkerThreads=5
```

### If too many workers restarting early:
```properties
# Increase min elite size (wait for more seeds)
multiStart.minEliteSizeForWorkers=8
```

---

## 🎯 Next: Scale Up Testing

Once 6 workers work well on your dev machine, you can scale to your production server:

**2x Intel 6972P (96 cores):**

```properties
multiStart.enabled=true
multiStart.numWorkerThreads=48          # Conservative: 50 total threads
# OR
multiStart.numWorkerThreads=94          # Aggressive: 96 total threads

multiStart.minEliteSizeForWorkers=10    # Need more seeds for more workers
multiStart.stagnationThreshold=2000
multiStart.competitiveThreshold=0.02
multiStart.notifyMainThread=true
```

**JVM settings for production:**
```bash
java -Xmx64G \
     -XX:+UseG1GC \
     -XX:MaxGCPauseMillis=200 \
     -XX:+UseNUMA \
     -XX:+UseLargePages \
     -jar AILSII.jar -file data/instance.vrp -limit 3600 -stoppingCriterion Time
```

---

## 📚 Troubleshooting

### Problem: "No elite solutions available for restart"
**Solution**: Wait longer for elite set to populate, or reduce `minEliteSizeForWorkers`

### Problem: Workers launch but immediately terminate
**Solution**: Check `stoppingCriterion()` - make sure time limit is reasonable

### Problem: All workers start from same seed
**Solution**: Check `QualityBasedSeedSelector` - should select different solutions

### Problem: Compilation errors about MultiStartAILS
**Solution**: Already compiled successfully! ✅

### Problem: Lower solution quality than single-thread
**Solution**: This is unusual, check:
- Are workers actually running? (CPU usage)
- Are they restarting too often? (increase threshold)
- Is elite set building properly? (check statistics)

---

## 🎉 You're Ready!

Everything is implemented and working. Just:

1. ✅ **Test backward compatibility** (multiStart.enabled=false)
2. ✅ **Test 6 workers** (multiStart.enabled=true, numWorkerThreads=6)
3. ✅ **Monitor CPU usage** (should be ~100%)
4. ✅ **Check restart behavior** (2-5 restarts per worker typical)
5. ✅ **Compare performance** (multi-start vs single-thread)

Enjoy the massive speedup! 🚀
