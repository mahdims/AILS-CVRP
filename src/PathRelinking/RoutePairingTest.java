package PathRelinking;

import Data.Instance;
import SearchMethod.Config;
import SearchMethod.InputParameters;
import Solution.Solution;
import SearchMethod.ConstructSolution;
import Improvement.LocalSearch;
import Improvement.IntraLocalSearch;
import Improvement.FeasibilityPhase;

/**
 * Unit test for RoutePairing algorithm (Algorithm 5)
 *
 * Tests the route pairing functionality by:
 * 1. Creating two different solutions
 * 2. Pairing their routes
 * 3. Validating the pairing is a valid bijection
 * 4. Checking match quality
 */
public class RoutePairingTest {

    public static void main(String[] args) {
        System.out.println("=== RoutePairing Unit Test ===\n");

        // Test 1: Basic pairing test
        testBasicPairing();

        // Test 2: Validation test
        testPairingValidation();

        // Test 3: Same solution pairing (should be identity)
        testIdentityPairing();

        System.out.println("\n=== All Tests Completed ===");
    }

    /**
     * Test 1: Basic pairing functionality
     */
    private static void testBasicPairing() {
        System.out.println("Test 1: Basic Pairing");
        System.out.println("---------------------");

        try {
            // Create instance and config
            Instance instance = createTestInstance();
            Config config = new Config();

            // Create two different solutions
            Solution s1 = new Solution(instance, config);
            Solution s2 = new Solution(instance, config);

            // Construct solutions using different methods
            ConstructSolution constructor = new ConstructSolution(instance, config);
            constructor.construct(s1);
            constructor.construct(s2);

            // Apply local search to make them different
            IntraLocalSearch intraLS = new IntraLocalSearch(instance, config);
            LocalSearch ls = new LocalSearch(instance, config, intraLS);
            FeasibilityPhase feas = new FeasibilityPhase(instance, config, intraLS);

            feas.makeFeasible(s1);
            ls.localSearch(s1, true);

            feas.makeFeasible(s2);
            ls.localSearch(s2, true);

            // Ensure same number of routes
            while (s2.numRoutes < s1.numRoutes) {
                s2.numRoutes++;
            }
            while (s2.numRoutes > s1.numRoutes) {
                s2.removeEmptyRoutes();
            }

            System.out.println("Created two solutions:");
            System.out.println("  S1: " + s1.numRoutes + " routes, f=" + s1.f);
            System.out.println("  S2: " + s2.numRoutes + " routes, f=" + s2.f);

            // Test pairing
            RoutePairing pairing = new RoutePairing(instance);
            int[] phi = pairing.pairRoutes(s1, s2);

            // Print pairing
            pairing.printPairing(phi, s1, s2);

            // Validate pairing
            boolean valid = pairing.validatePairing(phi, s1.numRoutes);
            System.out.println("Pairing valid: " + (valid ? "[PASS] PASS" : "[FAIL] FAIL"));

            System.out.println();

        } catch (Exception e) {
            System.err.println("Test 1 FAILED: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Test 2: Validation of pairing function
     */
    private static void testPairingValidation() {
        System.out.println("Test 2: Pairing Validation");
        System.out.println("---------------------------");

        try {
            Instance instance = createTestInstance();
            RoutePairing pairing = new RoutePairing(instance);

            // Test valid pairing
            int[] validPhi = {0, 1, 2, 3};
            boolean valid1 = pairing.validatePairing(validPhi, 4);
            System.out.println("Valid pairing [0,1,2,3]: " +
                             (valid1 ? "[PASS] PASS" : "[FAIL] FAIL"));

            // Test valid permutation
            int[] validPerm = {2, 0, 3, 1};
            boolean valid2 = pairing.validatePairing(validPerm, 4);
            System.out.println("Valid permutation [2,0,3,1]: " +
                             (valid2 ? "[PASS] PASS" : "[FAIL] FAIL"));

            // Test invalid: duplicate
            int[] invalidDup = {0, 1, 1, 3};
            boolean invalid1 = pairing.validatePairing(invalidDup, 4);
            System.out.println("Invalid duplicate [0,1,1,3]: " +
                             (!invalid1 ? "[PASS] PASS" : "[FAIL] FAIL"));

            // Test invalid: out of bounds
            int[] invalidBounds = {0, 1, 2, 5};
            boolean invalid2 = pairing.validatePairing(invalidBounds, 4);
            System.out.println("Invalid bounds [0,1,2,5]: " +
                             (!invalid2 ? "[PASS] PASS" : "[FAIL] FAIL"));

            System.out.println();

        } catch (Exception e) {
            System.err.println("Test 2 FAILED: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Test 3: Pairing same solution should give identity function
     */
    private static void testIdentityPairing() {
        System.out.println("Test 3: Identity Pairing");
        System.out.println("------------------------");

        try {
            Instance instance = createTestInstance();
            Config config = new Config();

            // Create one solution
            Solution s = new Solution(instance, config);
            ConstructSolution constructor = new ConstructSolution(instance, config);
            constructor.construct(s);

            IntraLocalSearch intraLS = new IntraLocalSearch(instance, config);
            FeasibilityPhase feas = new FeasibilityPhase(instance, config, intraLS);
            feas.makeFeasible(s);

            // Pair with itself
            RoutePairing pairing = new RoutePairing(instance);
            int[] phi = pairing.pairRoutes(s, s);

            // Should be identity: phi[i] = i
            boolean isIdentity = true;
            for (int i = 0; i < phi.length; i++) {
                if (phi[i] != i) {
                    isIdentity = false;
                    System.out.println("  phi[" + i + "] = " + phi[i] + " (expected " + i + ")");
                }
            }

            System.out.println("Identity pairing: " +
                             (isIdentity ? "[PASS] PASS" : "[FAIL] FAIL"));

            if (isIdentity) {
                System.out.println("  All routes matched to themselves (100% overlap)");
            }

            System.out.println();

        } catch (Exception e) {
            System.err.println("Test 3 FAILED: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Create a test instance
     * Uses a small instance file if available, otherwise creates minimal instance
     */
    private static Instance createTestInstance() {
        try {
            // Try to load a small test instance
            String[] args = {
                "-f", "data/Vrp_Set_A/A-n32-k5.vrp",
                "-r", "true",
                "-t", "10",
                "-b", "784"
            };

            InputParameters reader = new InputParameters();
            reader.readingInput(args);
            return new Instance(reader);

        } catch (Exception e) {
            System.err.println("Could not load test instance: " + e.getMessage());
            System.err.println("Please ensure data/Vrp_Set_A/A-n32-k5.vrp exists");
            throw new RuntimeException("Test instance not available", e);
        }
    }

    /**
     * Run quick sanity check
     */
    public static boolean quickTest() {
        try {
            Instance instance = createTestInstance();
            Config config = new Config();

            Solution s1 = new Solution(instance, config);
            Solution s2 = new Solution(instance, config);

            ConstructSolution constructor = new ConstructSolution(instance, config);
            constructor.construct(s1);
            constructor.construct(s2);

            RoutePairing pairing = new RoutePairing(instance);
            int[] phi = pairing.pairRoutes(s1, s2);

            return pairing.validatePairing(phi, s1.numRoutes);

        } catch (Exception e) {
            return false;
        }
    }
}
