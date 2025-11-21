# AILS-II Tunable Parameters Guide

## Overview
This document lists all parameters that can be tuned in the AILS-II algorithm with SISR operator.

---

## 📋 **Command-Line Parameters**

### **Required Parameters**

| Parameter | Type | Description | Example |
|-----------|------|-------------|---------|
| `-file` | String | Path to VRP instance file (.vrp) | `-file data/Vrp_Set_X/X-n101-k25.vrp` |
| `-best` | Double | Best known solution (for gap calculation) | `-best 27591` |
| `-limit` | Double | Time limit (seconds) or iteration limit | `-limit 60` |

### **Optional Parameters**

| Parameter | Type | Default | Description | Example |
|-----------|------|---------|-------------|---------|
| `-rounded` | Boolean | `true` | Use rounded distances | `-rounded true` |
| `-stoppingCriterion` | Enum | `Time` | Stop by Time or Iteration | `-stoppingCriterion Time` |
| `-solutionDir` | String | `""` | Directory to save solution files | `-solutionDir results/` |

### **Algorithm Parameters (Command-Line)**

| Parameter | Type | Default | Description | Range | Example |
|-----------|------|---------|-------------|-------|---------|
| `-dMin` | Integer | `15` | Minimum perturbation strength | 1-50 | `-dMin 10` |
| `-dMax` | Integer | `30` | Maximum perturbation strength | 15-100 | `-dMax 40` |
| `-gamma` | Integer | `30` | Update frequency for omega adaptation | 10-100 | `-gamma 25` |
| `-varphi` | Integer | `40` | Number of closest neighbors for insertion | 10-100 | `-varphi 50` |

**Example Command:**
```bash
java -jar AILSII.jar \
  -file data/Vrp_Set_X/X-n101-k25.vrp \
  -rounded true \
  -best 27591 \
  -limit 120 \
  -stoppingCriterion Time \
  -dMin 10 \
  -dMax 40 \
  -gamma 25 \
  -varphi 50
```

---

## 🔧 **Config.java Parameters (Hardcoded)**

### **Core Algorithm Parameters**

| Parameter | Type | Default | Location | Description |
|-----------|------|---------|----------|-------------|
| `etaMin` | Double | `0.01` | Line 38 | Minimum acceptance threshold (MARE) |
| `etaMax` | Double | `1.0` | Line 39 | Maximum acceptance threshold (MARE) |
| `epsilon` | Double | `0.01` | Line 37 | Precision for solution comparison |
| `knnLimit` | Integer | `100` | Line 33 | KNN structure size limit |

**File:** [src/SearchMethod/Config.java](src/SearchMethod/Config.java)

### **Perturbation Operator Selection**

| Parameter | Type | Default | Location | Description |
|-----------|------|---------|----------|-------------|
| `perturbation[]` | Array | `[Sequential, Concentric, SISR]` | Lines 41-44 | Which operators to use |

**To modify:**
```java
// Use only SISR
this.perturbation = new PerturbationType[1];
this.perturbation[0] = PerturbationType.SISR;

// Use Sequential and SISR only
this.perturbation = new PerturbationType[2];
this.perturbation[0] = PerturbationType.Sequential;
this.perturbation[1] = PerturbationType.SISR;
```

### **Insertion Heuristic Selection**

| Parameter | Type | Default | Location | Description |
|-----------|------|---------|----------|-------------|
| `insertionHeuristics[]` | Array | `[Distance, Cost]` | Lines 46-48 | Insertion cost metrics |

**Options:** `Distance`, `Cost`

---

## 🎯 **SISR-Specific Parameters**

### **SISRConfig.java Parameters (Hardcoded)**

| Parameter | Type | Default | Location | Description | Range |
|-----------|------|---------|----------|-------------|-------|
| `maxStringLength` | Double | `15.0` | Line 25 | Maximum string length (Lmax) | 5-50 |
| `splitRate` | Double | `0.5` | Line 26 | Probability of split string removal | 0.0-1.0 |
| `splitDepth` | Double | `0.3` | Line 27 | Split depth parameter (β) | 0.1-0.5 |
| `blinkRate` | Double | `0.01` | Line 28 | Position skip probability (γ) | 0.0-0.2 |

**File:** [src/Perturbation/SISRConfig.java](src/Perturbation/SISRConfig.java)

**To modify:**
```java
public SISRConfig() {
    this.maxStringLength = 20.0;   // Increase for longer strings
    this.splitRate = 0.6;           // More split removals
    this.splitDepth = 0.4;          // Deeper splits
    this.blinkRate = 0.02;          // More position skipping
}
```

### **SISR Recreate Order Probabilities**

**File:** [src/Perturbation/SISR.java:509-521](src/Perturbation/SISR.java#L509-L521)

| Order Strategy | Default Probability | Description |
|----------------|---------------------|-------------|
| `RANDOM` | 36.4% (4/11) | Random order |
| `DEMAND` | 36.4% (4/11) | By demand (descending) |
| `FAR` | 18.2% (2/11) | By distance from depot (descending) |
| `CLOSE` | 9.1% (1/11) | By distance from depot (ascending) |

**To modify weights:**
```java
private SISRRecreateOrder selectRecreateOrder() {
    int r = rand.nextInt(10) + 1;  // Change total weight

    if (r <= 5) return SISRRecreateOrder.RANDOM;      // 50%
    else if (r <= 7) return SISRRecreateOrder.DEMAND;  // 20%
    else if (r <= 9) return SISRRecreateOrder.FAR;     // 20%
    else return SISRRecreateOrder.CLOSE;              // 10%
}
```

---

## 📊 **Parameter Tuning Recommendations**

### **Quick Parameter Tuning Guide**

#### **1. Perturbation Strength (dMin, dMax)**
- **Small instances (< 100 customers):** dMin=10, dMax=25
- **Medium instances (100-500):** dMin=15, dMax=30 (default)
- **Large instances (> 500):** dMin=20, dMax=40

#### **2. SISR String Length (maxStringLength)**
- **Tight capacity:** 10-15 (default: 15)
- **Loose capacity:** 15-25
- **Very large instances:** 20-30

#### **3. SISR Blink Rate (blinkRate)**
- **More diversification:** 0.02-0.05
- **Balanced (default):** 0.01
- **More intensification:** 0.005-0.01

#### **4. SISR Split Rate (splitRate)**
- **More split removals:** 0.6-0.8
- **Balanced (default):** 0.5
- **More regular removals:** 0.3-0.4

#### **5. Neighbor Limit (varphi)**
- **Fast search:** 20-30
- **Balanced (default):** 40
- **High-quality search:** 50-80

---

## 🧪 **Parameter Tuning Experiments**

### **Experiment 1: Test SISR String Length**

```bash
# Test different maxStringLength values
# Modify SISRConfig.java line 25 before each test

# Test 1: Short strings
maxStringLength = 10.0
bash scripts/build_jar.sh
java -jar AILSII.jar -file data/Vrp_Set_X/X-n101-k25.vrp -best 27591 -limit 60 -stoppingCriterion Time

# Test 2: Default
maxStringLength = 15.0
bash scripts/build_jar.sh
java -jar AILSII.jar -file data/Vrp_Set_X/X-n101-k25.vrp -best 27591 -limit 60 -stoppingCriterion Time

# Test 3: Long strings
maxStringLength = 25.0
bash scripts/build_jar.sh
java -jar AILSII.jar -file data/Vrp_Set_X/X-n101-k25.vrp -best 27591 -limit 60 -stoppingCriterion Time
```

### **Experiment 2: Test Perturbation Strength**

```bash
# No rebuild needed - use command-line parameters

# Test 1: Low strength
java -jar AILSII.jar -file data/Vrp_Set_X/X-n101-k25.vrp -best 27591 -limit 60 -dMin 5 -dMax 15 -stoppingCriterion Time

# Test 2: Default
java -jar AILSII.jar -file data/Vrp_Set_X/X-n101-k25.vrp -best 27591 -limit 60 -dMin 15 -dMax 30 -stoppingCriterion Time

# Test 3: High strength
java -jar AILSII.jar -file data/Vrp_Set_X/X-n101-k25.vrp -best 27591 -limit 60 -dMin 20 -dMax 50 -stoppingCriterion Time
```

### **Experiment 3: Test Operator Mix**

```java
// Modify Config.java lines 41-44

// Test 1: Sequential only
this.perturbation = new PerturbationType[1];
this.perturbation[0] = PerturbationType.Sequential;

// Test 2: SISR only
this.perturbation = new PerturbationType[1];
this.perturbation[0] = PerturbationType.SISR;

// Test 3: All three (default)
this.perturbation = new PerturbationType[3];
this.perturbation[0] = PerturbationType.Sequential;
this.perturbation[1] = PerturbationType.Concentric;
this.perturbation[2] = PerturbationType.SISR;
```

---

## 📝 **Parameter Summary Table**

### **By Modification Method**

| Parameter | Command-Line | Config.java | SISRConfig.java | Default |
|-----------|:------------:|:-----------:|:---------------:|---------|
| `dMin` | ✅ | ✅ | - | 15 |
| `dMax` | ✅ | ✅ | - | 30 |
| `gamma` | ✅ | ✅ | - | 30 |
| `varphi` | ✅ | ✅ | - | 40 |
| `etaMin` | - | ✅ | - | 0.01 |
| `etaMax` | - | ✅ | - | 1.0 |
| `epsilon` | - | ✅ | - | 0.01 |
| `knnLimit` | - | ✅ | - | 100 |
| `perturbation[]` | - | ✅ | - | [Seq, Con, SISR] |
| `insertionHeuristics[]` | - | ✅ | - | [Dist, Cost] |
| `maxStringLength` | - | - | ✅ | 15.0 |
| `splitRate` | - | - | ✅ | 0.5 |
| `splitDepth` | - | - | ✅ | 0.3 |
| `blinkRate` | - | - | ✅ | 0.01 |

---

## 🚀 **Quick Reference Commands**

### **Baseline Run (Default Parameters)**
```bash
java -jar AILSII.jar -file data/Vrp_Set_X/X-n101-k25.vrp -rounded true -best 27591 -limit 60 -stoppingCriterion Time
```

### **High Diversification**
```bash
java -jar AILSII.jar -file data/Vrp_Set_X/X-n101-k25.vrp -rounded true -best 27591 -limit 60 -dMin 20 -dMax 50 -stoppingCriterion Time
```

### **High Intensification**
```bash
java -jar AILSII.jar -file data/Vrp_Set_X/X-n101-k25.vrp -rounded true -best 27591 -limit 60 -dMin 5 -dMax 15 -varphi 80 -stoppingCriterion Time
```

### **Large Instance Settings**
```bash
java -jar AILSII.jar -file data/Vrp_Set_X/X-n1001-k43.vrp -rounded true -best 72355 -limit 300 -dMin 30 -dMax 60 -varphi 60 -stoppingCriterion Time
```

---

## 💡 **Tips**

1. **Always rebuild after modifying .java files:**
   ```bash
   bash scripts/build_jar.sh
   ```

2. **Test on small instances first** before running full benchmarks

3. **Track parameter changes** in a spreadsheet for systematic tuning

4. **Use perturbation usage output** to verify operator balance

5. **Compare results statistically** (run 10+ times per configuration)

---

**Last Updated:** 2025-11-20
**Total Tunable Parameters:** 14 core + 4 SISR-specific = **18 parameters**
