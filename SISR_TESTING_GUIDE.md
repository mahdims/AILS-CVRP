# SISR Testing Guide

## Overview
This guide provides comprehensive instructions for testing the newly implemented SISR (Slack Induction by String Removal) perturbation operator.

## Prerequisites

### 1. Install Java
Ensure Java JDK is installed and available in your PATH:
```bash
java -version
javac -version
```

If not installed, download from: https://www.oracle.com/java/technologies/downloads/

### 2. Verify Implementation Files
Ensure these files exist:
- `src/Perturbation/SISR.java` (661 lines)
- `src/Perturbation/SISRRecreateOrder.java` (27 lines)
- `src/Perturbation/SISRConfig.java` (45 lines)
- `src/Perturbation/PerturbationType.java` (modified - includes SISR)
- `src/SearchMethod/Config.java` (modified - includes SISRConfig)

## Compilation

### Step 1: Build the JAR File
```bash
cd "E:\Work\BKS\AILS-CVRP-main\AILS-CVRP-main"
bash scripts/build_jar.sh
```

**Expected Output:**
```
============================================================================
Building AILSII.jar
============================================================================
Found X Java source files
Compilation successful!
JAR file created successfully: AILSII.jar (XXX KB)
============================================================================
```

**If Compilation Fails:**
- Check error messages for syntax errors
- Verify all import statements are correct
- Ensure all files are in correct package directories

## Enabling SISR Operator

### Option 1: Modify Config.java (Permanent)
Edit [src/SearchMethod/Config.java:41-43](src/SearchMethod/Config.java#L41-L43):

**Current (2 operators):**
```java
this.perturbation=new PerturbationType[2];
this.perturbation[0]=PerturbationType.Sequential;
this.perturbation[1]=PerturbationType.Concentric;
```

**Modified (3 operators including SISR):**
```java
this.perturbation=new PerturbationType[3];
this.perturbation[0]=PerturbationType.Sequential;
this.perturbation[1]=PerturbationType.Concentric;
this.perturbation[2]=PerturbationType.SISR;
```

**Or use SISR only for testing:**
```java
this.perturbation=new PerturbationType[1];
this.perturbation[0]=PerturbationType.SISR;
```

After modifying, **rebuild the JAR**:
```bash
bash scripts/build_jar.sh
```

### Option 2: Command-Line Parameter (Future Enhancement)
Currently, perturbation types cannot be specified via command line. Consider adding this parameter to InputParameters.java for easier testing.

## Running Tests

### Test 1: Small Instance Quick Test
```bash
java -jar AILSII.jar \
  -file data/Vrp_Set_X/X-n101-k25.vrp \
  -rounded true \
  -best 27591 \
  -limit 60 \
  -stoppingCriterion Time
```

**Expected Behavior:**
- Algorithm runs for 60 seconds
- Uses SISR operator (if enabled in Config)
- Outputs solution quality and statistics
- Should complete without errors

### Test 2: Medium Instance Test
```bash
java -jar AILSII.jar \
  -file data/Vrp_Set_X/X-n106-k14.vrp \
  -rounded true \
  -best 26362 \
  -limit 120 \
  -stoppingCriterion Time
```

### Test 3: Large Instance Test (if available)
```bash
java -jar AILSII.jar \
  -file data/Vrp_Set_X/X-n1001-k43.vrp \
  -rounded true \
  -best 72355 \
  -limit 300 \
  -stoppingCriterion Time
```

## Unit Testing

### Run JUnit Tests
Create and run the SISR-specific test (see TestSISR.java):

```bash
# If using Maven (add pom.xml if needed)
mvn test -Dtest=TestSISR

# Or compile and run manually
javac -cp .:junit-platform-console-standalone.jar \
  -d build \
  src/Test/TestSISR.java

java -cp build:junit-platform-console-standalone.jar \
  org.junit.platform.console.ConsoleLauncher \
  --select-class Test.TestSISR
```

## Validation Checklist

### Phase 1: Compilation ✓
- [ ] All Java files compile without errors
- [ ] JAR file is created successfully
- [ ] No missing dependencies

### Phase 2: Integration Testing
- [ ] Algorithm runs without crashes
- [ ] SISR operator is instantiated correctly
- [ ] Config outputs show SISR configuration
- [ ] No NullPointerException or ClassNotFoundException

### Phase 3: Functional Testing
- [ ] Ruin phase removes customers correctly
- [ ] String removal respects cardinality constraints
- [ ] Recreate phase inserts all removed customers
- [ ] Solution remains valid after perturbation
- [ ] Blink rate randomization works (varies between runs)

### Phase 4: Performance Testing
- [ ] SISR completes in reasonable time
- [ ] Solution quality is competitive with Sequential/Concentric
- [ ] Omega parameter adapts properly
- [ ] Memory usage is acceptable

## Debugging

### Enable Verbose Output
Add debug prints to SISR.java for troubleshooting:

```java
// In ruin() method
System.out.println("SISR Ruin: removing " + d + " customers");
System.out.println("Blink rate: " + blinkRate);

// In recreate() method
System.out.println("SISR Recreate: inserting " + removalList.size() + " customers");
System.out.println("Order strategy: " + recreateOrderStrategy);
```

### Common Issues

#### Issue: ClassNotFoundException for SISR
**Cause:** JAR not rebuilt after adding SISR
**Solution:** Run `bash scripts/build_jar.sh` again

#### Issue: NullPointerException in SISR
**Cause:** KNN structure not initialized or omega parameter missing
**Solution:** Verify SISR constructor receives all required parameters

#### Issue: Solution becomes infeasible after SISR
**Cause:** Bug in recreate phase or insertion logic
**Solution:**
1. Add validation checks after each insertion
2. Verify all removed customers are reinserted
3. Check capacity constraints during insertion

#### Issue: SISR too slow
**Cause:** Inefficient string selection or insertion
**Solution:**
1. Profile with large instances
2. Check KNN query performance
3. Verify O(n²) algorithms aren't used unintentionally

## Performance Benchmarking

### Compare Operators
Run the same instance with different operator configurations:

**Test 1: Sequential Only**
```bash
# Modify Config.java to use only Sequential
bash scripts/build_jar.sh
java -jar AILSII.jar -file data/Vrp_Set_X/X-n101-k25.vrp -rounded true -best 27591 -limit 300 -stoppingCriterion Time
# Record: Best solution, time to best, final objective
```

**Test 2: SISR Only**
```bash
# Modify Config.java to use only SISR
bash scripts/build_jar.sh
java -jar AILSII.jar -file data/Vrp_Set_X/X-n101-k25.vrp -rounded true -best 27591 -limit 300 -stoppingCriterion Time
# Record: Best solution, time to best, final objective
```

**Test 3: All Three (Sequential + Concentric + SISR)**
```bash
# Modify Config.java to use all three operators
bash scripts/build_jar.sh
java -jar AILSII.jar -file data/Vrp_Set_X/X-n101-k25.vrp -rounded true -best 27591 -limit 300 -stoppingCriterion Time
# Record: Best solution, time to best, final objective
```

### Performance Metrics
Track these metrics for each configuration:
- **Solution Quality:** Final objective value vs. best known
- **Convergence Speed:** Time to reach best solution
- **Stability:** Standard deviation across 10 runs
- **Success Rate:** Percentage of runs reaching target quality

## Expected Results

Based on the SISR paper implementation:
- SISR should provide **complementary diversification** to Sequential/Concentric
- Solution quality should be **similar or better** when combined with existing operators
- Convergence may be **faster** on instances where string removal is effective
- No performance degradation on instances where SISR is less suitable

## Next Steps After Successful Testing

1. **Benchmark Suite:** Run on all Vrp_Set_X instances (101 instances)
2. **Statistical Analysis:** Compare with baseline using t-tests
3. **Parameter Tuning:** Optimize SISR-specific parameters if needed
4. **Publication Results:** Document improvements for paper/report
5. **Integration:** Consider making SISR default in production config

## Questions or Issues?

If you encounter problems:
1. Check the compilation output for specific error messages
2. Review SISR.java implementation against paper pseudocode
3. Verify all modifications to Config.java and PerturbationType.java
4. Test with small instances first before large benchmarks

## File Locations

- **Source Code:** `src/Perturbation/SISR.java`
- **Configuration:** `src/SearchMethod/Config.java`
- **Test Instances:** `data/Vrp_Set_X/*.vrp`
- **Build Script:** `scripts/build_jar.sh`
- **Test Class:** `src/Test/TestSISR.java` (to be created)

---
**Implementation Date:** November 2024
**Algorithm Reference:** SISR paper Section 3.4
**Lines of Code:** ~730 LOC across 3 files
