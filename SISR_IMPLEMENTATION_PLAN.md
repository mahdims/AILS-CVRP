# SISR Operator Implementation Plan for AILS-CVRP
**Version 2.0 - Updated with Existing Infrastructure Reuse**

---

## Document Control
- **Created**: 2025-11-20
- **Last Updated**: 2025-11-20
- **Status**: Ready for Implementation
- **Phases Completed**: 0/6

---

## I. Executive Summary

This plan details the implementation of **SISR (Slack Induction by String Removals)** ruin and recreate operators based on Christiaens & Vanden Berghe (2020). The implementation will:

✓ **Reuse existing infrastructure**: KNN structure, omega parameter
✓ **Minimize modifications**: Only 3 new files + small changes to 2 existing files
✓ **Maintain compatibility**: Zero impact on existing operators
✓ **Faithful to C++ implementation**: All algorithms and equations preserved

### Key Design Principles
1. **Use `omega` parameter** (not separate avgRemoved)
2. **Reuse existing `knn[][]`** structure (not create sisrAdjacency)
3. **Override `addCandidates()`** for SISR-specific recreation
4. **Minimal changes** to existing codebase

---

## II. Critical Updates from Original Plan

### ✓ **UPDATE 1: Use Existing Omega Parameter**
**Original Plan**: Create separate `avgRemoved` parameter in SISRConfig
**Updated Plan**: Use `omega` from base `Perturbation` class

**Rationale**: In AILS, `omega` controls perturbation strength (nodes removed). SISR's `avgRemoved` (c̄ in paper) serves the identical purpose. Using `omega` maintains consistency and leverages existing adaptive mechanism.

**Implementation**:
```java
// In SISR.java calculateSISRParameters():
// OLD: double avgRemoved = sisrConfig.avgRemoved;
// NEW: double avgRemoved = omega;  // Use inherited omega from Perturbation
```

### ✓ **UPDATE 2: Reuse Existing KNN Structure**
**Original Plan**: Create new `sisrAdjacency[][]` structure in Instance
**Updated Plan**: Use existing `knn[][]` and `Node.knn[]`

**Rationale**:
- KNN already exists in Instance.java (lines 15, 157, 194-195)
- Each Node has `knn[]` array containing nearest neighbor indices
- Concentric operator uses `reference.knn[]` directly (line 28)
- SISR adjacency list is conceptually identical to KNN
- No need to duplicate the same data structure

**Evidence from Codebase**:
```java
// Instance.java (line 157, 194-195)
knn = new int[size][knnLimit];
for (int j = 0; j < knnLimit; j++)
    knn[i][j] = neighKnn[j].name;

// Node.java (line 15, 39)
public int knn[];
this.knn = instance.getKnn()[name];

// Concentric.java (line 28) - ALREADY USING KNN
for (int i = 0; i < omega && i < reference.knn.length; i++) {
    if(reference.knn[i] != 0) {
        node = solution[reference.knn[i]-1];
        // ... process node
    }
}
```

**Implementation**:
```java
// In SISR.java applySISRRuin():
// Access seed customer's nearest neighbors
Node seedNode = solution[seedCustomer - 1];
int[] adjList = seedNode.knn;  // Use existing KNN!

for (int i = 0; i < adjList.length; i++) {
    int neighbor = adjList[i];
    // ... process neighbor
}
```

### ✓ **UPDATE 3: Simplified SISRConfig**
**Original**: avgRemoved, maxStringLength, splitRate, splitDepth, blinkRate, adjacencyLimit
**Updated**: maxStringLength, splitRate, splitDepth, blinkRate

**Removed Parameters**:
- ~~`avgRemoved`~~ → Use `omega` from Perturbation
- ~~`adjacencyLimit`~~ → Use existing `knnLimit` from Config

---

## III. Architecture Overview

### Current AILS Design Pattern
```
Perturbation (abstract base)
├── omega                    // Adaptive perturbation strength
├── knn structure            // Granular neighborhood (via Instance)
├── applyPerturbation()      // Ruin phase (overridden by subclasses)
└── addCandidates()          // Recreate phase (overridden by subclasses)

Sequential extends Perturbation
├── Removes consecutive nodes
└── Uses inherited addCandidates() (KNN-based insertion)

Concentric extends Perturbation
├── Removes nearest neighbors using knn[]
└── Uses inherited addCandidates() (KNN-based insertion)
```

### SISR Integration
```
SISR extends Perturbation
├── Uses omega for removal count
├── Uses knn[] for neighbor lists
├── Overrides applyPerturbation() → SISR ruin logic
└── Overrides addCandidates() → SISR recreate logic
```

---

## IV. File Structure Plan

### **New Files to Create** (3 files)

#### 1. **`src/Perturbation/SISR.java`**
**Purpose**: Main SISR operator class
**Size**: ~700 LOC
**Extends**: Perturbation
**Key Methods**:
```
RUIN PHASE (Algorithm 2 from paper):
- applyPerturbation()            // Override: main entry point
- applySISRRuin()                // Lines 1-10: ruin algorithm
- calculateSISRParameters()      // Equations 5-7: ell_s_max, k_s
- selectSeedCustomer()           // Line 3: random seed selection
- processRouteRemoval()          // Lines 6-10: process single route
- regularStringRemoval()         // Figure 3: consecutive removal
- splitStringRemoval()           // Figure 4: removal with preservation
- calculateStringLength()        // Equations 8-9: l_t calculation
- executeRemovalPlan()           // Line 9: perform removals

RECREATE PHASE (Algorithm 3 from paper):
- addCandidates()                // Override: main entry point
- applySISRRecreate()            // Lines 1-14: recreate algorithm
- selectRecreateOrder()          // Line 2: weighted strategy selection
- sortAbsentCustomers()          // Line 2: sort by strategy
- findBestInsertionPosition()    // Lines 4-9: best position with blinks
- insertCustomerAtPosition()     // Lines 13-14: perform insertion
- createNewRouteWithCustomer()   // Lines 10-12: new route creation

HELPER METHODS:
- resetBuffers()
- findCustomerPositionInRoute()
- addSegmentToRemovalPlan()
```

#### 2. **`src/Perturbation/SISRRecreateOrder.java`**
**Purpose**: Enum for ordering strategies
**Size**: ~15 LOC
```java
package Perturbation;

public enum SISRRecreateOrder {
    RANDOM(0),   // Weight: 4/11 (36.4%)
    DEMAND(1),   // Weight: 4/11 (36.4%)
    FAR(2),      // Weight: 2/11 (18.2%)
    CLOSE(3);    // Weight: 1/11 (9.1%)

    final int type;

    SISRRecreateOrder(int type) {
        this.type = type;
    }
}
```

#### 3. **`src/Perturbation/SISRConfig.java`**
**Purpose**: SISR-specific configuration
**Size**: ~40 LOC
```java
package Perturbation;

public class SISRConfig {
    // String removal parameters
    public double maxStringLength;    // Lmax in paper (Eq. 5)
    public double splitRate;          // Probability of split removal
    public double splitDepth;         // β: split depth parameter

    // Insertion parameter
    public double blinkRate;          // γ: position skip probability

    public SISRConfig() {
        // Default values (instance-independent)
        this.maxStringLength = 15.0;
        this.splitRate = 0.5;          // 50% split probability
        this.splitDepth = 0.3;         // β = 0.3
        this.blinkRate = 0.01;         // γ = 0.01 (1% blink)
    }

    @Override
    public String toString() {
        return String.format(
            "SISRConfig[maxStringLength=%.1f, splitRate=%.2f, " +
            "splitDepth=%.2f, blinkRate=%.3f]",
            maxStringLength, splitRate, splitDepth, blinkRate
        );
    }
}
```

### **Files to Modify** (2 files - minimal changes)

#### 4. **`src/Perturbation/PerturbationType.java`**
**Changes**: Add SISR enum value
**Lines Modified**: 1 line added
```java
package Perturbation;

public enum PerturbationType {
    Sequential(0),
    Concentric(1),
    SISR(2);        // ← ADD THIS LINE

    final int type;

    PerturbationType(int type) {
        this.type = type;
    }
}
```

#### 5. **`src/SearchMethod/Config.java`**
**Changes**: Add SISRConfig field and methods
**Lines Modified**: ~20 lines added
```java
public class Config implements Cloneable {
    // ... existing fields ...

    // ========== ADD NEW FIELD ==========
    SISRConfig sisrConfig;

    public Config() {
        // ... existing initialization ...

        // ========== ADD INITIALIZATION ==========
        this.sisrConfig = new SISRConfig();
    }

    // ========== ADD GETTER/SETTER ==========
    public SISRConfig getSisrConfig() {
        return sisrConfig;
    }

    public void setSisrConfig(SISRConfig sisrConfig) {
        this.sisrConfig = sisrConfig;
    }

    @Override
    public String toString() {
        return "Config ..."
            // ... existing fields ...
            + "\nSISR: " + sisrConfig;  // ← ADD THIS LINE
    }
}
```

### **Files NOT Modified** (Zero Changes)
✓ `Instance.java` - No new adjacency structure needed
✓ `Node.java` - Already has knn[]
✓ `Route.java` - No changes needed
✓ `Solution.java` - No changes needed
✓ `AILSII.java` - No initialization needed (KNN already built)
✓ `Perturbation.java` - Base class unchanged
✓ `Sequential.java` - Unchanged
✓ `Concentric.java` - Unchanged

---

## V. Detailed Implementation Steps

### **PHASE 1: Foundation Setup** (Estimated: 30 minutes)
**Goal**: Create supporting enums and configuration
**Risk Level**: ⬜ Very Low
**No Compilation Errors Expected**

#### Tasks:
- [ ] 1.1: Create `SISRRecreateOrder.java` enum
- [ ] 1.2: Create `SISRConfig.java` configuration class
- [ ] 1.3: Update `PerturbationType.java` (add SISR(2))
- [ ] 1.4: Update `Config.java` (add sisrConfig field + getter/setter)
- [ ] 1.5: Test compilation

#### Verification:
```bash
# Should compile without errors
javac src/Perturbation/SISRRecreateOrder.java
javac src/Perturbation/SISRConfig.java
javac src/Perturbation/PerturbationType.java
javac src/SearchMethod/Config.java
```

#### Success Criteria:
- ✓ All files compile
- ✓ Config.toString() includes SISR info
- ✓ PerturbationType.SISR accessible

---

### **PHASE 2: SISR Skeleton** (Estimated: 30 minutes)
**Goal**: Create SISR.java with constructor and basic structure
**Risk Level**: ⬜ Very Low
**Basic Framework Only**

#### Tasks:
- [ ] 2.1: Create `SISR.java` file with package and imports
- [ ] 2.2: Create inner classes (SISRParams, InsertionPosition, RemovalPlan)
- [ ] 2.3: Add SISR-specific fields (sisrAbsent, removalPlan, etc.)
- [ ] 2.4: Implement constructor
- [ ] 2.5: Add method stubs (empty implementations)
- [ ] 2.6: Test compilation

#### Code Template:
```java
package Perturbation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import Data.Instance;
import DiversityControl.OmegaAdjustment;
import Improvement.IntraLocalSearch;
import SearchMethod.Config;
import Solution.Node;
import Solution.Route;
import Solution.Solution;

public class SISR extends Perturbation {

    // SISR-specific fields
    private SISRConfig sisrConfig;
    private List<Node> sisrAbsent;           // Set A in paper
    private List<Node> sisrInsertionStack;   // Insertion tracking
    private List<RemovalPlan> removalPlan;   // Temporary planning

    // Inner class: Removal planning
    private static class RemovalPlan {
        int position;
        Node node;

        RemovalPlan(int position, Node node) {
            this.position = position;
            this.node = node;
        }
    }

    // Inner class: SISR parameters (Equations 5-7)
    private static class SISRParams {
        double ell_s_max;  // Max string length
        int k_s;           // Number of routes to ruin
    }

    // Inner class: Insertion position
    private static class InsertionPosition {
        int routeIdx;
        int position;
        double cost;
        boolean valid;

        InsertionPosition() {
            this.cost = Double.MAX_VALUE;
            this.valid = false;
        }
    }

    // Constructor
    public SISR(Instance instance, Config config,
                HashMap<String, OmegaAdjustment> omegaSetup,
                IntraLocalSearch intraLocalSearch) {
        super(instance, config, omegaSetup, intraLocalSearch);
        this.perturbationType = PerturbationType.SISR;
        this.sisrConfig = config.getSisrConfig();
        this.sisrAbsent = new ArrayList<>();
        this.sisrInsertionStack = new ArrayList<>();
        this.removalPlan = new ArrayList<>();
    }

    // Override: Ruin phase entry point
    @Override
    public void applyPerturbation(Solution s) {
        setSolution(s);
        resetBuffers();
        applySISRRuin();
        assignSolution(s);
    }

    // Override: Recreate phase entry point
    @Override
    public void addCandidates() {
        applySISRRecreate();
    }

    // Method stubs (to be implemented in Phases 3-4)
    private void resetBuffers() { /* TODO Phase 3 */ }
    private void applySISRRuin() { /* TODO Phase 3 */ }
    private void applySISRRecreate() { /* TODO Phase 4 */ }
}
```

#### Verification:
```bash
javac src/Perturbation/SISR.java
```

#### Success Criteria:
- ✓ SISR.java compiles
- ✓ Constructor accessible
- ✓ Can instantiate (even with empty methods)

---

### **PHASE 3: SISR Ruin Implementation** (Estimated: 3-4 hours)
**Goal**: Implement complete ruin phase (Algorithm 2 from paper)
**Risk Level**: 🟨 Medium-High
**Complex Logic with Multiple Helper Methods**

#### Tasks:
- [ ] 3.1: Implement `resetBuffers()`
- [ ] 3.2: Implement `calculateSISRParameters()` (Equations 5-7)
- [ ] 3.3: Implement `selectSeedCustomer()` (Line 3)
- [ ] 3.4: Implement `applySISRRuin()` main loop (Lines 4-10)
- [ ] 3.5: Implement `processRouteRemoval()` (Lines 6-10)
- [ ] 3.6: Implement `calculateStringLength()` (Equations 8-9)
- [ ] 3.7: Implement `regularStringRemoval()` (Figure 3)
- [ ] 3.8: Implement `splitStringRemoval()` (Figure 4)
- [ ] 3.9: Implement `findCustomerPositionInRoute()` helper
- [ ] 3.10: Implement `addSegmentToRemovalPlan()` helper
- [ ] 3.11: Implement `executeRemovalPlan()`
- [ ] 3.12: Test ruin phase

#### Key Implementation Details:

##### 3.2: Calculate SISR Parameters (Equations 5-7)
```java
private SISRParams calculateSISRParameters() {
    SISRParams params = new SISRParams();

    // Count non-empty routes and their cardinalities
    double sumCardinality = 0.0;
    int numNonEmpty = 0;

    for (int i = 0; i < numRoutes; i++) {
        int routeSize = routes[i].numElements - 1; // Exclude depot
        if (routeSize > 0) {
            sumCardinality += routeSize;
            numNonEmpty++;
        }
    }

    if (numNonEmpty == 0) {
        params.ell_s_max = 0;
        params.k_s = 0;
        return params;
    }

    // ===== EQUATION 5: ell_s_max = min{Lmax, avg_cardinality} =====
    double avgCardinality = sumCardinality / numNonEmpty;
    params.ell_s_max = Math.min(sisrConfig.maxStringLength, avgCardinality);

    // ===== EQUATION 6: k_s_max = floor(4*c̄/(1+ell_s_max)) - 1 =====
    // NOTE: Use omega instead of separate avgRemoved!
    double k_s_max = Math.floor(4.0 * omega / (1.0 + params.ell_s_max)) - 1;

    // ===== EQUATION 7: k_s = floor(U(1, k_s_max+1)) =====
    if (k_s_max < 1) {
        params.k_s = 1;
    } else {
        params.k_s = (int) Math.floor(rand.nextDouble() * k_s_max + 1.0);
    }

    params.k_s = Math.max(1, Math.min(params.k_s, numNonEmpty));

    return params;
}
```

##### 3.3: Select Seed Customer (Line 3)
```java
private int selectSeedCustomer() {
    List<Integer> servedCustomers = new ArrayList<>();

    // Collect all customers currently in routes
    for (int i = 0; i < solution.length; i++) {
        if (solution[i].nodeBelong && solution[i].name != 0) {
            servedCustomers.add(solution[i].name);
        }
    }

    if (servedCustomers.isEmpty()) {
        return -1;
    }

    // Random selection
    return servedCustomers.get(rand.nextInt(servedCustomers.size()));
}
```

##### 3.4: Main Ruin Loop (Lines 4-10)
```java
private void applySISRRuin() {
    // Lines 1-2: Calculate parameters
    SISRParams params = calculateSISRParameters();
    if (params.k_s == 0) return;

    // Line 3: Select seed customer
    int seedCustomer = selectSeedCustomer();
    if (seedCustomer < 0) return;

    // Line 4: Initialize ruined routes set R
    Set<Integer> ruinedRoutes = new HashSet<>();
    int routesProcessed = 0;

    // Line 5: for c ∈ adj(c_s^seed) and |R| < k_s do
    // *** USE EXISTING KNN STRUCTURE ***
    if (seedCustomer > 0 && seedCustomer <= solution.length) {
        Node seedNode = solution[seedCustomer - 1];
        int[] adjList = seedNode.knn;  // ← USE EXISTING KNN!

        for (int i = 0; i < adjList.length; i++) {
            // Check termination: |R| < k_s
            if (routesProcessed >= params.k_s) break;

            int neighbor = adjList[i];

            // Line 6: if c ∉ A and t ∉ R then
            if (neighbor == 0) continue;  // Skip depot
            if (neighbor > solution.length) continue;

            Node neighborNode = solution[neighbor - 1];
            if (!neighborNode.nodeBelong) continue;  // c ∈ A (already removed)

            Route neighborRoute = neighborNode.route;
            if (ruinedRoutes.contains(neighborRoute.nameRoute)) continue; // t ∈ R

            // Lines 7-10: Process this route
            if (processRouteRemoval(neighborRoute, neighbor, params, ruinedRoutes)) {
                routesProcessed++;
            }
        }
    }
}
```

##### 3.7: Regular String Removal (Figure 3)
```java
private void regularStringRemoval(Route route, int stringLength, int closestCus) {
    // Find position of closestCus in route
    int closestCusPos = findCustomerPositionInRoute(route, closestCus);

    if (closestCusPos < 0) {
        // Fallback: use middle of route
        int routeSize = route.numElements - 1;
        closestCusPos = Math.max(0, routeSize / 2);
    }

    // Calculate valid start positions that include closestCusPos
    // The string [startPos, startPos+stringLength-1] must contain closestCusPos
    int routeSize = route.numElements - 1;
    int minStart = Math.max(0, closestCusPos - stringLength + 1);
    int maxStart = Math.min(closestCusPos, routeSize - stringLength);

    if (minStart > maxStart) {
        // Edge case: adjust to valid range
        minStart = Math.max(0, routeSize - stringLength);
        maxStart = minStart;
    }

    // Randomly select start position
    int startPos = minStart;
    if (maxStart > minStart) {
        startPos = minStart + rand.nextInt(maxStart - minStart + 1);
    }

    // Add segment to removal plan
    addSegmentToRemovalPlan(route, startPos, stringLength);
}
```

##### 3.8: Split String Removal (Figure 4)
```java
private void splitStringRemoval(Route route, int stringLength, int closestCus) {
    int routeSize = route.numElements - 1;

    // Step 1: Determine m (number of preserved customers)
    // m_max = |t| - l (route size minus string length)
    int m_max = routeSize - stringLength;
    if (m_max < 0) m_max = 0;

    int m = 1;
    // CORRECTED LOGIC: Continue while m < m_max AND U(0,1) ≤ β
    while (m < m_max && rand.nextDouble() <= sisrConfig.splitDepth) {
        m++;
    }

    // Step 2: Find closestCus position
    int closestCusPos = findCustomerPositionInRoute(route, closestCus);
    if (closestCusPos < 0) {
        closestCusPos = routeSize / 2;
    }

    // Step 3: Select l+m window that includes closestCusPos
    int windowSize = Math.min(stringLength + m, routeSize);
    int minWindowStart = Math.max(0, closestCusPos - windowSize + 1);
    int maxWindowStart = Math.min(closestCusPos, routeSize - windowSize);

    if (minWindowStart > maxWindowStart) {
        minWindowStart = 0;
        maxWindowStart = Math.max(0, routeSize - windowSize);
    }

    int windowStart = minWindowStart;
    if (maxWindowStart > minWindowStart) {
        windowStart = minWindowStart + rand.nextInt(maxWindowStart - minWindowStart + 1);
    }
    int windowEnd = windowStart + windowSize - 1;

    // Step 4: Randomly place m preserved customers within window
    int minPreserveStart = windowStart;
    int maxPreserveStart = windowEnd - m + 1;

    int preserveStart = minPreserveStart;
    if (maxPreserveStart > minPreserveStart) {
        preserveStart = minPreserveStart + rand.nextInt(maxPreserveStart - minPreserveStart + 1);
    }
    int preserveEnd = preserveStart + m - 1;

    // Step 5: Remove customers outside preserved substring
    // Remove BEFORE preserved substring
    if (preserveStart > windowStart) {
        addSegmentToRemovalPlan(route, windowStart, preserveStart - windowStart);
    }

    // Remove AFTER preserved substring
    if (preserveEnd < windowEnd) {
        addSegmentToRemovalPlan(route, preserveEnd + 1, windowEnd - preserveEnd);
    }
}
```

##### 3.11: Execute Removal Plan
```java
private int executeRemovalPlan(Route route) {
    if (removalPlan.isEmpty()) return 0;

    // Sort by position (descending) to avoid index shifting
    Collections.sort(removalPlan, (a, b) -> Integer.compare(b.position, a.position));

    // Remove duplicates
    Set<Node> seen = new HashSet<>();
    removalPlan.removeIf(rp -> !seen.add(rp.node));

    // Execute removals
    int removedCount = 0;
    for (RemovalPlan rp : removalPlan) {
        if (rp.node != null && rp.node.nodeBelong) {
            // Store old neighbors (for potential use)
            rp.node.prevOld = rp.node.prev;
            rp.node.nextOld = rp.node.next;

            // Remove from route
            f += route.remove(rp.node);

            // Add to absent list
            sisrAbsent.add(rp.node);
            removedCount++;
        }
    }

    return removedCount;
}
```

#### Verification:
```java
// Test in main():
SISR sisr = new SISR(instance, config, omegaSetup, intraLocalSearch);
Solution testSolution = /* ... create test solution ... */;
sisr.applyPerturbation(testSolution);

// Verify:
// - Some customers removed (sisrAbsent not empty)
// - Routes modified
// - No crashes
System.out.println("Removed " + sisrAbsent.size() + " customers");
```

#### Success Criteria:
- ✓ Ruin phase removes 15-30% of customers
- ✓ Regular/split string removal work correctly
- ✓ Parameters calculated per equations 5-9
- ✓ No exceptions or crashes

---

### **PHASE 4: SISR Recreate Implementation** (Estimated: 3-4 hours)
**Goal**: Implement complete recreate phase (Algorithm 3 from paper)
**Risk Level**: 🟨 Medium-High
**Complex Insertion Logic with Blink Rate**

#### Tasks:
- [ ] 4.1: Implement `applySISRRecreate()` main loop (Lines 1-14)
- [ ] 4.2: Implement `selectRecreateOrder()` (Line 2, weighted)
- [ ] 4.3: Implement `sortAbsentCustomers()` (Line 2, 4 strategies)
- [ ] 4.4: Implement `findBestInsertionPosition()` (Lines 4-9)
- [ ] 4.5: Implement `insertCustomerAtPosition()` (Lines 13-14)
- [ ] 4.6: Implement `createNewRouteWithCustomer()` (Lines 10-12)
- [ ] 4.7: Test recreate phase
- [ ] 4.8: Test complete ruin + recreate cycle

#### Key Implementation Details:

##### 4.1: Main Recreate Loop (Algorithm 3)
```java
private void applySISRRecreate() {
    if (sisrAbsent.isEmpty()) return;

    // Copy to working list
    List<Node> pending = new ArrayList<>(sisrAbsent);
    sisrAbsent.clear();
    sisrInsertionStack.clear();

    // Step 1: Select ordering strategy (Line 2)
    SISRRecreateOrder selectedOrder = selectRecreateOrder();

    // Step 2: Sort customers by selected ordering (Line 2)
    sortAbsentCustomers(pending, selectedOrder);

    // Step 3: Insert each customer (Lines 3-14)
    for (Node customer : pending) {
        // Lines 4-9: Find best insertion position
        InsertionPosition bestPos = findBestInsertionPosition(customer);

        // Lines 10-12: Create new route if no valid position
        if (!bestPos.valid && numRoutes < instance.getMaxNumberRoutes()) {
            createNewRouteWithCustomer(customer);
            continue;
        }

        // Lines 13-14: Insert at best position
        if (!insertCustomerAtPosition(customer, bestPos)) {
            // Failed to insert - keep in absent list
            sisrAbsent.add(customer);
        }
    }
}
```

##### 4.2: Select Recreate Order (Weighted Selection)
```java
// Weights: Random=4, Demand=4, Far=2, Close=1 (total=11)
private SISRRecreateOrder selectRecreateOrder() {
    int r = rand.nextInt(11) + 1;  // 1 to 11

    if (r <= 4) {
        return SISRRecreateOrder.RANDOM;      // 4/11 = 36.4%
    } else if (r <= 8) {
        return SISRRecreateOrder.DEMAND;      // 4/11 = 36.4%
    } else if (r <= 10) {
        return SISRRecreateOrder.FAR;         // 2/11 = 18.2%
    } else {
        return SISRRecreateOrder.CLOSE;       // 1/11 = 9.1%
    }
}
```

##### 4.3: Sort Absent Customers (4 Strategies)
```java
private void sortAbsentCustomers(List<Node> customers, SISRRecreateOrder order) {
    switch (order) {
        case RANDOM:
            Collections.shuffle(customers, rand);
            break;

        case DEMAND:
            customers.sort((a, b) -> {
                // Largest demand first
                if (a.demand == b.demand) {
                    return Integer.compare(a.name, b.name);
                }
                return Integer.compare(b.demand, a.demand);
            });
            break;

        case FAR:
            customers.sort((a, b) -> {
                // Farthest from depot first
                double distA = instance.dist(0, a.name);
                double distB = instance.dist(0, b.name);
                if (Math.abs(distA - distB) < 0.001) {
                    return Integer.compare(a.name, b.name);
                }
                return Double.compare(distB, distA);
            });
            break;

        case CLOSE:
            customers.sort((a, b) -> {
                // Closest to depot first
                double distA = instance.dist(0, a.name);
                double distB = instance.dist(0, b.name);
                if (Math.abs(distA - distB) < 0.001) {
                    return Integer.compare(a.name, b.name);
                }
                return Double.compare(distA, distB);
            });
            break;
    }
}
```

##### 4.4: Find Best Insertion Position (with Blink Rate)
```java
private InsertionPosition findBestInsertionPosition(Node customer) {
    InsertionPosition best = new InsertionPosition();

    // Random route order (Line 5: "in random order")
    List<Integer> routeOrder = new ArrayList<>();
    for (int i = 0; i < numRoutes; i++) {
        routeOrder.add(i);
    }
    Collections.shuffle(routeOrder, rand);

    // Try each route
    for (int routeIdx : routeOrder) {
        Route route = routes[routeIdx];

        // Check capacity (Line 5: "which can serve c")
        if (route.totalDemand + customer.demand > instance.getCapacity()) {
            continue;
        }

        // Try all positions in route (Line 6)
        Node current = route.first;
        int position = 0;

        do {
            // *** BLINK RATE (Line 7) ***
            // "With probability γ, the position is skipped"
            if (rand.nextDouble() < sisrConfig.blinkRate) {
                current = current.next;
                position++;
                continue;  // Skip this position
            }

            // Calculate insertion cost (Line 8)
            double deltaCost = instance.dist(current.name, customer.name) +
                              instance.dist(customer.name, current.next.name) -
                              instance.dist(current.name, current.next.name);

            // Update best (Lines 8-9)
            if (deltaCost < best.cost) {
                best.cost = deltaCost;
                best.routeIdx = routeIdx;
                best.position = position;
                best.valid = true;
            }

            current = current.next;
            position++;
        } while (current != route.first);
    }

    return best;
}
```

##### 4.5: Insert Customer at Position
```java
private boolean insertCustomerAtPosition(Node customer, InsertionPosition insertPos) {
    if (!insertPos.valid) return false;

    Route route = routes[insertPos.routeIdx];

    // Navigate to insertion point
    Node insertAfter = route.first;
    for (int i = 0; i < insertPos.position; i++) {
        insertAfter = insertAfter.next;
        if (insertAfter == route.first) break;  // Safety check
    }

    // Insert customer (Line 13-14)
    f += route.addAfter(customer, insertAfter);
    sisrInsertionStack.add(customer);

    return true;
}
```

##### 4.6: Create New Route (Lines 10-12)
```java
private void createNewRouteWithCustomer(Node customer) {
    // Find depot node
    Node depot = solution[0];  // Depot is always first

    // Create new route
    Route newRoute = new Route(instance, config, depot, numRoutes);

    // Add customer to new route
    newRoute.addAfter(customer, newRoute.first);

    // Add route to solution
    routes[numRoutes] = newRoute;
    numRoutes++;

    // Track insertion
    sisrInsertionStack.add(customer);

    // Update cost (simplified - will be recalculated by feasibility operator)
    f += newRoute.fRoute;
}
```

#### Verification:
```java
// Test complete cycle:
SISR sisr = new SISR(instance, config, omegaSetup, intraLocalSearch);
Solution testSolution = /* ... create test solution ... */;

// Ruin
int originalCustomers = countServedCustomers(testSolution);
sisr.applyPerturbation(testSolution);
int afterRuin = countServedCustomers(testSolution);

System.out.println("Original: " + originalCustomers);
System.out.println("After ruin: " + afterRuin);
System.out.println("Removed: " + (originalCustomers - afterRuin));

// Recreate (done automatically in applyPerturbation)
// Verify all customers reinserted
int afterRecreate = countServedCustomers(testSolution);
System.out.println("After recreate: " + afterRecreate);

assert afterRecreate == originalCustomers : "All customers should be reinserted!";
```

#### Success Criteria:
- ✓ All removed customers reinserted
- ✓ Four ordering strategies work correctly
- ✓ Blink rate randomization functional
- ✓ Capacity constraints respected
- ✓ Solution feasible after recreate

---

### **PHASE 5: Integration Testing** (Estimated: 1-2 hours)
**Goal**: Integrate SISR into AILSII and test with existing algorithm
**Risk Level**: 🟨 Medium
**Ensure Compatibility with Framework**

#### Tasks:
- [ ] 5.1: Test SISR instantiation in AILSII
- [ ] 5.2: Test operator selection (Sequential + Concentric + SISR)
- [ ] 5.3: Run full AILSII with SISR only
- [ ] 5.4: Run full AILSII with mixed operators
- [ ] 5.5: Verify omega adaptation works with SISR
- [ ] 5.6: Verify no memory leaks or crashes

#### Test Configuration:
```java
// Test 1: SISR only
Config config1 = new Config();
config1.setPerturbation(new PerturbationType[] {
    PerturbationType.SISR
});

// Test 2: Mixed operators
Config config2 = new Config();
config2.setPerturbation(new PerturbationType[] {
    PerturbationType.Sequential,
    PerturbationType.Concentric,
    PerturbationType.SISR
});

// Test 3: Custom SISR parameters
SISRConfig sisrConfig = config2.getSisrConfig();
sisrConfig.maxStringLength = 20.0;
sisrConfig.splitRate = 0.6;
sisrConfig.splitDepth = 0.4;
sisrConfig.blinkRate = 0.02;
config2.setSisrConfig(sisrConfig);
```

#### Verification Checklist:
- [ ] SISR operator instantiated correctly
- [ ] Random selection includes SISR (check logs)
- [ ] Omega adaptation updates for SISR
- [ ] Solution quality improves over iterations
- [ ] No crashes or exceptions
- [ ] Memory usage stable

#### Success Criteria:
- ✓ Algorithm runs to completion
- ✓ SISR selected proportionally (1/3 if 3 operators)
- ✓ Solution quality competitive with Sequential/Concentric
- ✓ No errors in logs

---

### **PHASE 6: Validation and Benchmarking** (Estimated: 2-3 hours)
**Goal**: Validate correctness and compare performance
**Risk Level**: ⬜ Low
**Final Testing and Documentation**

#### Tasks:
- [ ] 6.1: Run on small benchmark instance (verify correctness)
- [ ] 6.2: Run on medium benchmark instance (compare operators)
- [ ] 6.3: Run on large benchmark instance (test scalability)
- [ ] 6.4: Compare solution quality (SISR vs Sequential vs Concentric)
- [ ] 6.5: Analyze operator selection statistics
- [ ] 6.6: Document results and parameter sensitivity
- [ ] 6.7: Create usage examples

#### Benchmark Tests:
```
Small:  E-n22-k4.vrp   (22 customers, 4 vehicles)
Medium: E-n51-k5.vrp   (51 customers, 5 vehicles)
Large:  E-n101-k8.vrp  (101 customers, 8 vehicles)
```

#### Metrics to Track:
- Best solution cost
- Average solution cost
- Convergence speed
- Operator selection frequency
- Average omega value for SISR
- Average removed customers per iteration
- Computation time

#### Success Criteria:
- ✓ SISR finds competitive solutions
- ✓ No degradation in overall algorithm performance
- ✓ Results reproducible
- ✓ Parameters well-calibrated

---

## VI. Implementation Summary

### Statistics
| Metric | Value |
|--------|-------|
| **New Files** | 3 |
| **Modified Files** | 2 |
| **Unchanged Files** | 10+ |
| **Total LOC Added** | ~750 |
| **Estimated Time** | 10-14 hours |
| **Risk Level** | Medium |

### File Change Summary
```
NEW FILES:
  + src/Perturbation/SISR.java                    (~700 LOC)
  + src/Perturbation/SISRRecreateOrder.java       (~15 LOC)
  + src/Perturbation/SISRConfig.java              (~40 LOC)

MODIFIED FILES:
  ✎ src/Perturbation/PerturbationType.java        (+1 line)
  ✎ src/SearchMethod/Config.java                  (+20 lines)

UNCHANGED FILES:
  ✓ src/Data/Instance.java                        (0 changes)
  ✓ src/Solution/Node.java                        (0 changes)
  ✓ src/Solution/Route.java                       (0 changes)
  ✓ src/Solution/Solution.java                    (0 changes)
  ✓ src/Perturbation/Perturbation.java            (0 changes)
  ✓ src/Perturbation/Sequential.java              (0 changes)
  ✓ src/Perturbation/Concentric.java              (0 changes)
  ✓ src/SearchMethod/AILSII.java                  (0 changes)
  ✓ src/DiversityControl/*                        (0 changes)
  ✓ src/Improvement/*                             (0 changes)
```

---

## VII. Key Design Decisions (Updated)

### ✓ **1. Reuse Omega Parameter**
**Decision**: Use `omega` from base Perturbation instead of separate `avgRemoved`
**Rationale**: Maintains consistency, leverages adaptive mechanism
**Implementation**: `double avgRemoved = omega;` in calculateSISRParameters()

### ✓ **2. Reuse KNN Structure**
**Decision**: Use existing `node.knn[]` instead of creating `sisrAdjacency[][]`
**Rationale**: Avoids duplication, proven structure, already initialized
**Implementation**: `int[] adjList = seedNode.knn;`
**Evidence**: Concentric already uses this pattern (line 28)

### ✓ **3. Override addCandidates()**
**Decision**: SISR overrides recreate method for specialized logic
**Rationale**: Clean separation, maintains framework compatibility
**Implementation**: Custom insertion with blink rate and 4 ordering strategies

### ✓ **4. Faithful to Paper**
**Decision**: Implement all equations (5-9) and algorithms (2-3) exactly as specified
**Rationale**: Ensures correctness, reproducible results
**Implementation**: All formulas preserved with comments

### ✓ **5. Inner Classes for Data Structures**
**Decision**: Use inner classes (SISRParams, InsertionPosition, RemovalPlan)
**Rationale**: Encapsulation, no pollution of global namespace
**Implementation**: Private static classes within SISR.java

---

## VIII. Testing Strategy

### Unit Tests (Per Phase)
**Phase 3 - Ruin Tests**:
- [ ] Test parameter calculation (equations 5-7)
- [ ] Test seed customer selection (valid customer, in route)
- [ ] Test regular string removal (includes closest customer)
- [ ] Test split string removal (preserves interior substring)
- [ ] Test removal plan execution (correct order, no duplicates)

**Phase 4 - Recreate Tests**:
- [ ] Test ordering selection (verify 4:4:2:1 weights)
- [ ] Test sorting strategies (Random, Demand, Far, Close)
- [ ] Test blink rate (positions skipped with γ probability)
- [ ] Test best insertion (lowest cost selected)
- [ ] Test new route creation (when needed)

### Integration Tests (Phase 5)
- [ ] Test with SISR only configuration
- [ ] Test with mixed operators (Sequential + Concentric + SISR)
- [ ] Test omega adaptation (updates correctly)
- [ ] Test multiple iterations (no cumulative errors)
- [ ] Test on different instance sizes

### Validation Tests (Phase 6)
- [ ] Small instance (E-n22-k4): Verify correctness
- [ ] Medium instance (E-n51-k5): Compare quality
- [ ] Large instance (E-n101-k8): Test scalability
- [ ] Parameter sensitivity: Test different configurations

---

## IX. Risk Mitigation

| Risk | Level | Mitigation |
|------|-------|------------|
| Complex ruin logic | 🟨 High | Implement incrementally, test each method |
| Split string edge cases | 🟨 Medium | Add bounds checking, fallback logic |
| Insertion failures | 🟨 Medium | Track uninserted customers, create new routes |
| KNN structure mismatch | 🟨 Medium | Verify KNN indices (1-based vs 0-based) |
| Omega too large | 🟨 Medium | Cap omega at feasible values |
| Memory usage | 🟦 Low | Reuse lists, clear buffers |
| Integration issues | 🟦 Low | Extensive testing with existing framework |

---

## X. Configuration Examples

### Default Configuration
```java
Config config = new Config();
// SISR already initialized with defaults
// maxStringLength = 15.0
// splitRate = 0.5
// splitDepth = 0.3
// blinkRate = 0.01
```

### Custom SISR Configuration
```java
Config config = new Config();

// Enable SISR operator
config.setPerturbation(new PerturbationType[] {
    PerturbationType.Sequential,
    PerturbationType.Concentric,
    PerturbationType.SISR
});

// Customize SISR parameters
SISRConfig sisrConfig = config.getSisrConfig();
sisrConfig.maxStringLength = 20.0;    // Longer strings
sisrConfig.splitRate = 0.6;            // More split removals
sisrConfig.splitDepth = 0.4;           // Deeper splits
sisrConfig.blinkRate = 0.02;           // More randomization
config.setSisrConfig(sisrConfig);
```

### SISR-Only Configuration (for testing)
```java
Config config = new Config();
config.setPerturbation(new PerturbationType[] {
    PerturbationType.SISR
});

// omega will be adapted automatically (0.01 * size initially)
```

---

## XI. Expected Outcomes

### Functionality
- ✓ SISR fully integrated with AILSII framework
- ✓ Ruin phase removes 15-35% of customers (omega-dependent)
- ✓ Recreate phase reinsters all customers
- ✓ Four ordering strategies provide diversification
- ✓ Blink rate adds controlled randomization

### Performance
- ✓ Computational overhead similar to Sequential/Concentric
- ✓ Solution quality competitive or better
- ✓ Convergence speed maintained
- ✓ Memory usage acceptable

### Code Quality
- ✓ Clean implementation following existing patterns
- ✓ Well-commented with paper references
- ✓ Minimal modifications to existing code
- ✓ Backward compatible (existing configs work unchanged)

---

## XII. Phase Completion Checklist

Use this checklist to track progress:

```
PHASE 1: Foundation Setup
[ ] 1.1: SISRRecreateOrder.java created
[ ] 1.2: SISRConfig.java created
[ ] 1.3: PerturbationType.java updated
[ ] 1.4: Config.java updated
[ ] 1.5: Compilation successful
Status: ⬜ Not Started | 🟨 In Progress | ✅ Complete

PHASE 2: SISR Skeleton
[ ] 2.1: SISR.java created with package/imports
[ ] 2.2: Inner classes defined
[ ] 2.3: Fields added
[ ] 2.4: Constructor implemented
[ ] 2.5: Method stubs added
[ ] 2.6: Compilation successful
Status: ⬜ Not Started | 🟨 In Progress | ✅ Complete

PHASE 3: SISR Ruin
[ ] 3.1: resetBuffers()
[ ] 3.2: calculateSISRParameters()
[ ] 3.3: selectSeedCustomer()
[ ] 3.4: applySISRRuin()
[ ] 3.5: processRouteRemoval()
[ ] 3.6: calculateStringLength()
[ ] 3.7: regularStringRemoval()
[ ] 3.8: splitStringRemoval()
[ ] 3.9: findCustomerPositionInRoute()
[ ] 3.10: addSegmentToRemovalPlan()
[ ] 3.11: executeRemovalPlan()
[ ] 3.12: Ruin phase tested
Status: ⬜ Not Started | 🟨 In Progress | ✅ Complete

PHASE 4: SISR Recreate
[ ] 4.1: applySISRRecreate()
[ ] 4.2: selectRecreateOrder()
[ ] 4.3: sortAbsentCustomers()
[ ] 4.4: findBestInsertionPosition()
[ ] 4.5: insertCustomerAtPosition()
[ ] 4.6: createNewRouteWithCustomer()
[ ] 4.7: Recreate phase tested
[ ] 4.8: Complete cycle tested
Status: ⬜ Not Started | 🟨 In Progress | ✅ Complete

PHASE 5: Integration Testing
[ ] 5.1: SISR instantiation tested
[ ] 5.2: Operator selection tested
[ ] 5.3: SISR-only run successful
[ ] 5.4: Mixed operators run successful
[ ] 5.5: Omega adaptation verified
[ ] 5.6: No memory leaks/crashes
Status: ⬜ Not Started | 🟨 In Progress | ✅ Complete

PHASE 6: Validation
[ ] 6.1: Small instance test
[ ] 6.2: Medium instance test
[ ] 6.3: Large instance test
[ ] 6.4: Quality comparison
[ ] 6.5: Statistics analysis
[ ] 6.6: Documentation complete
[ ] 6.7: Usage examples created
Status: ⬜ Not Started | 🟨 In Progress | ✅ Complete
```

---

## XIII. References

### Paper
Christiaens, J., & Vanden Berghe, G. (2020). Slack Induction by String Removals for Vehicle Routing Problems. *Transportation Science*, 54(2), 417-433.

### Key Algorithms
- **Algorithm 2** (Page 7): SISR Ruin Method
- **Algorithm 3** (Page 8): Greedy Insertion with Blinks
- **Equations 5-9** (Page 7): Parameter Calculations
- **Figure 3** (Page 7): Regular String Removal
- **Figure 4** (Page 7): Split String Removal

### Codebase References
- `Perturbation.java`: Base class pattern
- `Concentric.java` (line 28): KNN usage example
- `Instance.java` (lines 157, 194-195): KNN structure
- `Node.java` (line 15, 39): Node.knn[] field

---

## XIV. Notes and Observations

### Important Implementation Notes
1. **KNN Structure**: Uses 1-based indexing (knn[i] contains node names starting from 1, with 0 representing depot)
2. **Omega Range**: Dynamically adapted between omegaMin=1 and omegaMax=size-2
3. **Route Structure**: Circular linked list with depot as first/last node
4. **Cost Calculation**: Delta costs (addition - removal) accumulated in `f`

### Potential Issues and Solutions
| Issue | Solution |
|-------|----------|
| KNN array bounds | Check `neighbor <= solution.length` |
| Empty routes | Check `route.numElements > 1` |
| Split with m=0 | Default m=1, check m_max>=0 |
| No valid insertion | Create new route (if vehicles available) |
| Omega too small | Ensure k_s >= 1 |

---

## XV. Future Enhancements (Post-Implementation)

### Optional Improvements
- [ ] Add logging/statistics for SISR operator
- [ ] Parameter auto-tuning based on instance characteristics
- [ ] Adaptive weights for recreate ordering strategies
- [ ] Parallel removal of routes (currently sequential)
- [ ] Alternative adjacency metrics (not just distance)

### Performance Optimizations
- [ ] Cache insertion cost calculations
- [ ] Early termination for insertion search
- [ ] Precompute route capacities
- [ ] Optimize removal plan execution

---

**END OF IMPLEMENTATION PLAN**

*This document should be referenced at the beginning of each phase to ensure consistency and completeness.*
