# SISR Quick Start Guide

## 🚀 Fast Track to Testing SISR

### Step 1: Enable SISR (30 seconds)

Edit [src/SearchMethod/Config.java:41-43](src/SearchMethod/Config.java#L41-L43):

```java
// Change from:
this.perturbation=new PerturbationType[2];
this.perturbation[0]=PerturbationType.Sequential;
this.perturbation[1]=PerturbationType.Concentric;

// To (add SISR):
this.perturbation=new PerturbationType[3];
this.perturbation[0]=PerturbationType.Sequential;
this.perturbation[1]=PerturbationType.Concentric;
this.perturbation[2]=PerturbationType.SISR;  // ← Add this line
```

### Step 2: Build (1-2 minutes)

```bash
cd "E:\Work\BKS\AILS-CVRP-main\AILS-CVRP-main"
bash scripts/build_jar.sh
```

Look for: **"Compilation successful!"** and **"JAR file created successfully"**

### Step 3: Test Run (1 minute)

```bash
java -jar AILSII.jar \
  -file data/Vrp_Set_X/X-n101-k25.vrp \
  -rounded true \
  -best 27591 \
  -limit 60 \
  -stoppingCriterion Time
```

### ✅ Success Indicators
- No errors or exceptions
- Algorithm completes within 60 seconds
- Output shows solution quality (distance)
- Config output includes SISR configuration

---

## 📝 Alternative: Test SISR Alone

For focused testing, use **only** SISR:

```java
// In Config.java:
this.perturbation=new PerturbationType[1];
this.perturbation[0]=PerturbationType.SISR;  // Only SISR
```

Then rebuild and run the same command.

---

## 🧪 Run Unit Tests

```bash
# If Maven is set up:
mvn test -Dtest=TestSISR

# View test results in console
```

---

## 🐛 Troubleshooting

| Problem | Solution |
|---------|----------|
| `java: command not found` | Install Java JDK |
| `Compilation failed` | Check error messages, verify SISR.java syntax |
| `ClassNotFoundException: SISR` | Rebuild JAR after modifying Config.java |
| Algorithm crashes | Check console output, review SISR.java line causing error |

---

## 📊 Compare Performance

### Baseline (without SISR)
```bash
# Keep Config.java with only Sequential + Concentric
java -jar AILSII.jar -file data/Vrp_Set_X/X-n101-k25.vrp -rounded true -best 27591 -limit 300 -stoppingCriterion Time
# Record: Best solution found
```

### With SISR
```bash
# Add SISR to Config.java, rebuild
java -jar AILSII.jar -file data/Vrp_Set_X/X-n101-k25.vrp -rounded true -best 27591 -limit 300 -stoppingCriterion Time
# Record: Best solution found
```

Compare solution quality!

---

## 📚 Full Documentation

See [SISR_TESTING_GUIDE.md](SISR_TESTING_GUIDE.md) for comprehensive testing instructions.

---

## 💡 Key Files

- **Implementation:** [src/Perturbation/SISR.java](src/Perturbation/SISR.java)
- **Config:** [src/SearchMethod/Config.java](src/SearchMethod/Config.java)
- **Tests:** [src/Test/TestSISR.java](src/Test/TestSISR.java)
- **Build:** [scripts/build_jar.sh](scripts/build_jar.sh)

---

**Total Time: ~5 minutes from zero to first run**
