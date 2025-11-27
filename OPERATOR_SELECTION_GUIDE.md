# Operator Selection Guide

## ✅ **New Feature: Configurable Destroy and Recreate Operators**

You can now select which **perturbation operators** (destroy methods) and **insertion heuristics** (recreate methods) to use via `parameters.txt`!

---

## 🎯 **Quick Examples**

### **Example 1: Use Only SISR**
```txt
# parameters.txt
perturbation=SISR
insertionHeuristics=Distance
```

**Result:**
```
perturbation: [SISR] (parameters.txt)
insertionHeuristics: [Distance] (parameters.txt)
Perturbation usage: Sequential=0 Concentric=0 SISR=1329 (total=1329 iterations)
```

---

### **Example 2: Sequential and SISR Only**
```txt
# parameters.txt
perturbation=Sequential,SISR
insertionHeuristics=Distance,Cost
```

**Result:**
```
perturbation: [Sequential, SISR] (parameters.txt)
Perturbation usage: Sequential=650 Concentric=0 SISR=679 (total=1329 iterations)
```

---

### **Example 3: All Three Operators (Default)**
```txt
# parameters.txt
perturbation=Sequential,Concentric,SISR
insertionHeuristics=Distance,Cost
```

**Result:**
```
perturbation: [Sequential, Concentric, SISR] (parameters.txt)
Perturbation usage: Sequential=442 Concentric=447 SISR=440 (total=1329 iterations)
```

---

## 📝 **Parameter Format**

### **In parameters.txt:**

```txt
# Perturbation operators (destroy methods)
# Available options: Sequential, Concentric, SISR
# Format: comma-separated list (no spaces around commas)
perturbation=Sequential,Concentric,SISR

# Insertion heuristics (recreate methods)
# Available options: Distance, Cost
# Format: comma-separated list (no spaces around commas)
insertionHeuristics=Distance,Cost
```

---

## 🔍 **Available Options**

### **Perturbation Operators (Destroy Methods)**

| Operator | Description |
|----------|-------------|
| `Sequential` | Sequential string removal |
| `Concentric` | Concentric removal around seed customer |
| `SISR` | Slack Induction by String Removal (new!) |

**Format:** Comma-separated list, e.g., `Sequential,SISR` or just `SISR`

---

### **Insertion Heuristics (Recreate Methods)**

| Heuristic | Description |
|-----------|-------------|
| `Distance` | Distance-based insertion cost |
| `Cost` | Full cost-based insertion |

**Format:** Comma-separated list, e.g., `Distance,Cost` or just `Distance`

---

## 🧪 **Testing Different Configurations**

### **Test 1: Baseline (Sequential + Concentric)**
```txt
# parameters.txt
perturbation=Sequential,Concentric
```
```bash
java -jar AILSII.jar -file X-n101-k25.vrp -best 27591 -limit 60 -stoppingCriterion Time
# Record performance
```

---

### **Test 2: SISR Only**
```txt
# parameters.txt
perturbation=SISR
```
```bash
java -jar AILSII.jar -file X-n101-k25.vrp -best 27591 -limit 60 -stoppingCriterion Time
# Compare with baseline
```

---

### **Test 3: All Three (Hybrid)**
```txt
# parameters.txt
perturbation=Sequential,Concentric,SISR
```
```bash
java -jar AILSII.jar -file X-n101-k25.vrp -best 27591 -limit 60 -stoppingCriterion Time
# Check if hybrid is better
```

---

## ⚠️ **Important Notes**

1. **Case-sensitive:** Use exact names: `Sequential`, `SISR` (not `sequential`, `sisr`)
2. **No spaces:** Use `Sequential,SISR` (not `Sequential, SISR`)
3. **At least one:** Must specify at least one operator/heuristic
4. **Unknown operators:** Will print warning and skip

---

## 🚨 **Error Handling**

### **Invalid Operator Name**
```txt
perturbation=Sequential,InvalidOp,SISR
```
**Output:**
```
Warning: Unknown perturbation type 'InvalidOp' in parameters.txt
  Valid types: Sequential, Concentric, SISR
perturbation: [Sequential, SISR] (parameters.txt)
```

### **Invalid Heuristic Name**
```txt
insertionHeuristics=Distance,InvalidHeur
```
**Output:**
```
Warning: Unknown insertion heuristic 'InvalidHeur' in parameters.txt
  Valid heuristics: Distance, Cost
insertionHeuristics: [Distance] (parameters.txt)
```

---

## 📊 **Comparison Study Template**

Create multiple configuration files to test systematically:

### **config_baseline.txt**
```txt
perturbation=Sequential,Concentric
insertionHeuristics=Distance,Cost
```

### **config_sisr_only.txt**
```txt
perturbation=SISR
insertionHeuristics=Distance,Cost
```

### **config_hybrid.txt**
```txt
perturbation=Sequential,Concentric,SISR
insertionHeuristics=Distance,Cost
```

### **Run Comparison:**
```bash
# Test 1: Baseline
cp config_baseline.txt parameters.txt
java -jar AILSII.jar -file X-n101-k25.vrp -best 27591 -limit 300 -stoppingCriterion Time > results_baseline.txt

# Test 2: SISR only
cp config_sisr_only.txt parameters.txt
java -jar AILSII.jar -file X-n101-k25.vrp -best 27591 -limit 300 -stoppingCriterion Time > results_sisr.txt

# Test 3: Hybrid
cp config_hybrid.txt parameters.txt
java -jar AILSII.jar -file X-n101-k25.vrp -best 27591 -limit 300 -stoppingCriterion Time > results_hybrid.txt

# Compare results
grep "Perturbation usage" results_*.txt
grep "solution quality.*gap: 0.0000%" results_*.txt
```

---

## 💡 **Recommendations**

### **For Exploratory Analysis:**
```txt
perturbation=Sequential,Concentric,SISR
insertionHeuristics=Distance,Cost
```
**Benefit:** Maximum diversity in search

---

### **For Focused SISR Study:**
```txt
perturbation=SISR
insertionHeuristics=Distance
```
**Benefit:** Isolate SISR performance

---

### **For Fast Baseline:**
```txt
perturbation=Sequential
insertionHeuristics=Distance
```
**Benefit:** Simplest configuration

---

## 🔄 **Dynamic Experimentation**

No recompilation needed! Just edit `parameters.txt` and run:

```bash
# Edit parameters.txt
vim parameters.txt  # or nano, notepad, etc.

# Run immediately
java -jar AILSII.jar -file X-n101-k25.vrp -best 27591 -limit 60 -stoppingCriterion Time

# Check which operators were used
# Look for "Perturbation usage: ..." in output
```

---

## 📈 **Expected Operator Distribution**

When using multiple operators, they're selected **uniformly at random**:

### **All Three Operators:**
```
perturbation=Sequential,Concentric,SISR
Expected: ~33% each
Actual: Sequential=442 Concentric=447 SISR=440 ✓
```

### **Two Operators:**
```
perturbation=Sequential,SISR
Expected: ~50% each
Actual: Sequential=650 SISR=679 ✓
```

---

## ✨ **Benefits**

1. **Easy experimentation** - No code changes needed
2. **Ablation studies** - Test individual operators
3. **Comparative analysis** - Baseline vs SISR vs Hybrid
4. **Publication ready** - Document exact operator mix
5. **Source tracking** - Always know which config was used

---

## 📚 **Related Documentation**

- **[PARAMETER_SYSTEM_GUIDE.md](PARAMETER_SYSTEM_GUIDE.md)** - Full parameter system
- **[TUNABLE_PARAMETERS.md](TUNABLE_PARAMETERS.md)** - All tunable parameters
- **[parameters.txt](parameters.txt)** - Configuration file

---

**Created:** 2025-11-20
**Feature:** Configurable Destroy/Recreate Operators
**Implementation:** ~60 LOC in InputParameters.java
