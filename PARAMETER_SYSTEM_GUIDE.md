# Parameter Loading System Guide

## ✅ Implementation Complete

The parameter loading system with **3-level priority** and **source tracking** is now fully operational!

---

## 🎯 **Priority System**

Parameters are loaded in this order (each level overrides the previous):

```
1. Default values (hardcoded) ← Lowest priority
2. parameters.txt file         ← Medium priority
3. Command-line arguments      ← Highest priority (wins!)
```

---

## 📋 **Quick Usage Examples**

### **Example 1: Use Only Defaults**
```bash
java -jar AILSII.jar -file X-n101-k25.vrp -best 27591 -limit 60 -stoppingCriterion Time
```
**Output:**
```
dMin: 15 (default)
dMax: 30 (default)
gamma: 30 (default)
sisr.maxStringLength: 15.0 (default)
```

---

### **Example 2: Use parameters.txt**

**Create/edit `parameters.txt`:**
```txt
dMin=10
dMax=40
sisr.maxStringLength=20.0
sisr.blinkRate=0.02
```

**Run:**
```bash
java -jar AILSII.jar -file X-n101-k25.vrp -best 27591 -limit 60 -stoppingCriterion Time
```

**Output:**
```
Loading parameters from: parameters.txt
dMin: 10 (parameters.txt)
dMax: 40 (parameters.txt)
gamma: 30 (default)
sisr.maxStringLength: 20.0 (parameters.txt)
sisr.blinkRate: 0.020 (parameters.txt)
```

---

### **Example 3: CLI Overrides Everything**

**With same `parameters.txt` as above:**
```bash
java -jar AILSII.jar -file X-n101-k25.vrp -best 27591 -limit 60 -dMin 5 -varphi 50
```

**Output:**
```
Loading parameters from: parameters.txt
dMin: 5 (CLI)           ← CLI wins!
dMax: 40 (parameters.txt)
varphi: 50 (CLI)        ← CLI wins!
gamma: 30 (default)
sisr.maxStringLength: 20.0 (parameters.txt)
```

---

## 📝 **parameters.txt Format**

### **File Location**
Place in the same directory where you run the JAR:
```
E:\Work\BKS\AILS-CVRP-main\AILS-CVRP-main\parameters.txt
```

### **Format**
```txt
# Lines starting with # are comments
# Format: parameterName=value

# Core parameters
dMin=15
dMax=30
gamma=30
varphi=40

# SISR parameters (use dot notation)
sisr.maxStringLength=15.0
sisr.splitRate=0.5
sisr.splitDepth=0.3
sisr.blinkRate=0.01
```

### **Supported Parameters**

#### **Core Algorithm Parameters**
| Parameter | Type | Default | Example |
|-----------|------|---------|---------|
| `dMin` | int | 15 | `dMin=10` |
| `dMax` | int | 30 | `dMax=40` |
| `gamma` | int | 30 | `gamma=25` |
| `varphi` | int | 40 | `varphi=50` |
| `etaMin` | double | 0.01 | `etaMin=0.005` |
| `etaMax` | double | 1.0 | `etaMax=0.9` |
| `epsilon` | double | 0.01 | `epsilon=0.001` |
| `knnLimit` | int | 100 | `knnLimit=150` |

#### **SISR Parameters**
| Parameter | Type | Default | Example |
|-----------|------|---------|---------|
| `sisr.maxStringLength` | double | 15.0 | `sisr.maxStringLength=20.0` |
| `sisr.splitRate` | double | 0.5 | `sisr.splitRate=0.6` |
| `sisr.splitDepth` | double | 0.3 | `sisr.splitDepth=0.4` |
| `sisr.blinkRate` | double | 0.01 | `sisr.blinkRate=0.02` |

---

## 🖥️ **New CLI Parameters**

You can now override these via command-line:

```bash
java -jar AILSII.jar \
  -file data/Vrp_Set_X/X-n101-k25.vrp \
  -best 27591 \
  -limit 60 \
  -stoppingCriterion Time \
  -dMin 10 \
  -dMax 40 \
  -gamma 25 \
  -varphi 50 \
  -etaMin 0.005 \
  -etaMax 0.95 \
  -epsilon 0.001 \
  -knnLimit 150
```

**Note:** SISR parameters can only be set via `parameters.txt` (not CLI yet)

---

## 🔍 **Source Tracking**

Every parameter now shows where it came from:

```
Config
stoppingCriterionType: Time (CLI)
etaMax: 1.000 (default)
etaMin: 0.010 (default)
gamma: 25 (parameters.txt)
dMin: 10 (parameters.txt)
dMax: 40 (CLI)
varphi: 50 (CLI)
epsilon: 0.010 (default)
perturbation: [Sequential, Concentric, SISR]
insertionHeuristics: [Distance, Cost]
limitKnn: 100 (default)
SISR: SISRConfig[maxStringLength=20.0 (parameters.txt), splitRate=0.50 (default), splitDepth=0.30 (default), blinkRate=0.020 (parameters.txt)]
```

This makes it **crystal clear** where each value came from:
- `(default)` - Using hardcoded default
- `(parameters.txt)` - Loaded from file
- `(CLI)` - Overridden by command-line

---

## 📊 **Common Configurations**

### **Small Instances (< 100 customers)**

**parameters.txt:**
```txt
dMin=10
dMax=25
varphi=30
sisr.maxStringLength=12.0
```

### **Large Instances (> 500 customers)**

**parameters.txt:**
```txt
dMin=20
dMax=40
varphi=60
sisr.maxStringLength=20.0
```

### **More Diversification**

**parameters.txt:**
```txt
dMin=20
dMax=50
sisr.blinkRate=0.02
sisr.splitRate=0.6
```

### **More Intensification**

**parameters.txt:**
```txt
dMin=5
dMax=15
varphi=80
sisr.blinkRate=0.005
```

---

## 🛠️ **How It Works**

### **Loading Sequence**

1. **Initialize defaults** (from Config.java and SISRConfig.java)
2. **Check for parameters.txt** in current directory
3. **If found:** Read file and override defaults
4. **Parse CLI arguments** and override file values
5. **Display config** with source tracking

### **File Reading**

- Automatically loads `parameters.txt` from current directory
- If file doesn't exist: silently continues with defaults (no error)
- Supports comments with `#`
- Ignores empty lines and whitespace
- Validates parameter names and values
- Prints warnings for unknown parameters or invalid values

### **Error Handling**

```
Warning: Unknown parameter 'invalidParam' in parameters.txt
Warning: Invalid value for parameter 'dMin': abc
```

---

## 🎨 **Implementation Details**

### **Files Modified**

| File | Changes | Purpose |
|------|---------|---------|
| `InputParameters.java` | +170 lines | File reading, source tracking |
| `Config.java` | +18 lines | Source-aware toString() |
| `SISRConfig.java` | +48 lines | Getters, setters, source tracking |

### **Key Methods**

**InputParameters.java:**
- `initializeParameterSources()` - Initialize tracking
- `readParametersFile()` - Read parameters.txt
- `applyParameter()` - Apply parameter with source
- `getDouble()`, `getInt()` - Parse helpers

**Config.java:**
- `toString(HashMap<String, String> sources)` - Display with sources

**SISRConfig.java:**
- `getMaxStringLength()`, `setMaxStringLength()` - Accessors
- `toString(HashMap<String, String> sources)` - Display with sources

---

## 📦 **Files Created**

1. **parameters.txt** - Example configuration file with all parameters
2. **PARAMETER_SYSTEM_GUIDE.md** (this file) - Complete usage guide

---

## ✨ **Benefits**

1. **Easy experimentation** - Just edit `parameters.txt`, no recompilation!
2. **Clear traceability** - Always know where values came from
3. **Flexible workflow** - Use file for base config, CLI for quick tweaks
4. **Safe defaults** - Missing file or parameters won't cause errors
5. **Self-documenting** - Source tracking shows exact configuration used

---

## 🧪 **Testing Workflow**

### **Test different configurations:**

```bash
# 1. Create baseline
cp parameters.txt parameters_baseline.txt

# 2. Create test config
cat > parameters.txt << EOF
dMin=10
dMax=40
sisr.maxStringLength=20.0
EOF

# 3. Run test
java -jar AILSII.jar -file X-n101-k25.vrp -best 27591 -limit 300 -stoppingCriterion Time

# 4. Try quick CLI tweak
java -jar AILSII.jar -file X-n101-k25.vrp -best 27591 -limit 300 -dMin 15

# 5. Restore baseline
mv parameters_baseline.txt parameters.txt
```

---

## 📖 **Example Output**

```
Loading parameters from: parameters.txt
File: data/Vrp_Set_X/X-n101-k25.vrp
Rounded: true
limit: 60.0
Best: 27591.0
LimitTime: 60.0
Config
stoppingCriterionType: Time (CLI)
etaMax: 1.000 (default)
etaMin: 0.010 (default)
gamma: 25 (parameters.txt)
dMin: 10 (parameters.txt)
dMax: 40 (parameters.txt)
varphi: 50 (CLI)
epsilon: 0.010 (default)
perturbation: [Sequential, Concentric, SISR]
insertionHeuristics: [Distance, Cost]
limitKnn: 100 (default)
SISR: SISRConfig[maxStringLength=20.0 (parameters.txt), splitRate=0.50 (default), splitDepth=0.30 (default), blinkRate=0.020 (parameters.txt)]
```

---

## 💡 **Tips**

1. **Version control** your `parameters.txt` for reproducibility
2. **Use comments** to document why you chose specific values
3. **Start with defaults**, then tune incrementally
4. **Use CLI** for quick one-off experiments
5. **Check source tracking** to verify your configuration

---

## 🔄 **Migration from Old System**

**Before (hardcoded in Config.java):**
```java
this.dMin = 15;
this.dMax = 30;
// Rebuild required for every change!
```

**After (flexible):**
```txt
# parameters.txt
dMin=15
dMax=30
# No rebuild needed!
```

---

**Created:** 2025-11-20
**Implementation:** ~170 LOC across 3 files
**Status:** ✅ **Production Ready**
