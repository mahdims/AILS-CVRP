package Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import Data.Instance;
import Improvement.FeasibilityPhase;
import Improvement.IntraLocalSearch;
import SearchMethod.Config;
import SearchMethod.ConstructSolution;
import SearchMethod.InputParameters;
import Solution.Solution;
import Perturbation.SISR;
import Perturbation.PerturbationType;

/**
 * JUnit test class for SISR (Slack Induction by String Removal) operator
 *
 * Tests the implementation of the SISR perturbation operator including:
 * - Proper instantiation and configuration
 * - Ruin phase (customer removal)
 * - Recreate phase (customer reinsertion)
 * - Solution validity after perturbation
 * - Integration with existing framework
 */
public class TestSISR
{
	private Instance instance;
	private Config config;
	private Solution solution;

	@BeforeEach
	public void setUp()
	{
		// Set up test instance (small instance for quick tests)
		String[] args = {
			"-file", "data/Vrp_Set_X/X-n101-k25.vrp",
			"-rounded", "true",
			"-best", "27591",
			"-limit", "100",
			"-stoppingCriterion", "Time"
		};

		InputParameters reader = new InputParameters();
		reader.readingInput(args);

		this.instance = new Instance(reader);
		this.config = reader.getConfig();

		// Create a feasible solution
		this.solution = new Solution(instance, config);
		ConstructSolution constructSolution = new ConstructSolution(instance, config);
		constructSolution.construct(solution);

		IntraLocalSearch intraLocalSearch = new IntraLocalSearch(instance, config);
		FeasibilityPhase feasibilityOperator = new FeasibilityPhase(instance, config, intraLocalSearch);
		feasibilityOperator.makeFeasible(solution);
	}

	/**
	 * Test 1: Verify SISR can be instantiated correctly
	 */
	@Test
	public void testSISRInstantiation()
	{
		try {
			SISR sisrOperator = new SISR(instance, config);
			assertNotNull(sisrOperator, "SISR operator should be instantiated");
			assertEquals(PerturbationType.SISR, sisrOperator.getPerturbationType(),
				"Perturbation type should be SISR");
		} catch (Exception e) {
			e.printStackTrace();
			throw new AssertionError("SISR instantiation failed: " + e.getMessage());
		}
	}

	/**
	 * Test 2: Verify SISR configuration is properly initialized
	 */
	@Test
	public void testSISRConfiguration()
	{
		assertNotNull(config.getSisrConfig(), "SISR config should not be null");
		assertTrue(config.getSisrConfig().getBlinkRateMin() >= 0,
			"Blink rate min should be non-negative");
		assertTrue(config.getSisrConfig().getBlinkRateMax() <= 1.0,
			"Blink rate max should be <= 1.0");
		assertTrue(config.getSisrConfig().getBlinkRateMin() <= config.getSisrConfig().getBlinkRateMax(),
			"Blink rate min should be <= max");
	}

	/**
	 * Test 3: Verify SISR perturbation maintains solution size
	 * After ruin and recreate, all customers should still be in solution
	 */
	@Test
	public void testSISRMaintainsSolutionSize()
	{
		SISR sisrOperator = new SISR(instance, config);

		int originalSize = solution.getSize();
		double originalObjective = solution.f;

		// Perform perturbation
		sisrOperator.perturb(solution, 20, 0.5); // d=20, omega=0.5

		// Solution size should remain the same (all customers still assigned)
		assertEquals(originalSize, solution.getSize(),
			"Solution size should remain unchanged after SISR perturbation");

		// Objective may change (not testing value, just that it's computed)
		assertTrue(solution.f > 0, "Solution objective should be positive");
	}

	/**
	 * Test 4: Verify solution remains valid after SISR perturbation
	 */
	@Test
	public void testSISRMaintainsSolutionValidity()
	{
		SISR sisrOperator = new SISR(instance, config);

		// Perform perturbation
		sisrOperator.perturb(solution, 15, 0.5);

		// Check all routes start with depot
		for (int i = 0; i < solution.numRoutes; i++) {
			assertEquals(0, solution.routes[i].first.name,
				"Route " + i + " should start with depot (0)");
		}

		// Check total number of customers
		int totalCustomers = 0;
		for (int i = 0; i < solution.numRoutes; i++) {
			totalCustomers += solution.routes[i].getNumElements();
		}
		assertEquals(solution.numRoutes + solution.getSize(), totalCustomers,
			"Total customers in routes should match solution size + number of routes (depots)");
	}

	/**
	 * Test 5: Verify SISR with different perturbation strengths
	 */
	@Test
	public void testSISRWithDifferentStrengths()
	{
		SISR sisrOperator = new SISR(instance, config);

		// Test with small perturbation
		Solution sol1 = solution.clone();
		sisrOperator.perturb(sol1, 5, 0.3); // Small d, low omega
		assertEquals(solution.getSize(), sol1.getSize(),
			"Small perturbation should maintain solution size");

		// Test with medium perturbation
		Solution sol2 = solution.clone();
		sisrOperator.perturb(sol2, 15, 0.5); // Medium d, medium omega
		assertEquals(solution.getSize(), sol2.getSize(),
			"Medium perturbation should maintain solution size");

		// Test with large perturbation
		Solution sol3 = solution.clone();
		sisrOperator.perturb(sol3, 30, 0.8); // Large d, high omega
		assertEquals(solution.getSize(), sol3.getSize(),
			"Large perturbation should maintain solution size");
	}

	/**
	 * Test 6: Verify SISR randomization (blink rate varies)
	 * Run multiple times and check that solutions differ
	 */
	@Test
	public void testSISRRandomization()
	{
		SISR sisrOperator = new SISR(instance, config);

		// Perform perturbation multiple times
		Solution sol1 = solution.clone();
		Solution sol2 = solution.clone();
		Solution sol3 = solution.clone();

		sisrOperator.perturb(sol1, 20, 0.5);
		sisrOperator.perturb(sol2, 20, 0.5);
		sisrOperator.perturb(sol3, 20, 0.5);

		// At least one solution should be different (due to randomization)
		// We check objectives as a proxy for different solutions
		boolean hasDifference = (sol1.f != sol2.f) || (sol2.f != sol3.f) || (sol1.f != sol3.f);

		assertTrue(hasDifference,
			"SISR should produce different solutions due to randomization " +
			"(if this fails occasionally, it's due to random chance - run again)");
	}

	/**
	 * Test 7: Verify SISR works with edge cases
	 */
	@Test
	public void testSISREdgeCases()
	{
		SISR sisrOperator = new SISR(instance, config);

		// Test with d=0 (no perturbation)
		Solution sol1 = solution.clone();
		double beforeObjective = sol1.f;
		sisrOperator.perturb(sol1, 0, 0.5);
		// With d=0, solution might remain unchanged or change slightly due to recreate
		assertEquals(solution.getSize(), sol1.getSize(),
			"Solution size should remain same even with d=0");

		// Test with omega=0 (minimum learning)
		Solution sol2 = solution.clone();
		sisrOperator.perturb(sol2, 10, 0.0);
		assertEquals(solution.getSize(), sol2.getSize(),
			"Solution size should remain same with omega=0");

		// Test with omega=1 (maximum learning)
		Solution sol3 = solution.clone();
		sisrOperator.perturb(sol3, 10, 1.0);
		assertEquals(solution.getSize(), sol3.getSize(),
			"Solution size should remain same with omega=1");
	}

	/**
	 * Test 8: Integration test - SISR in perturbation array
	 * Verify SISR can be used alongside Sequential and Concentric
	 */
	@Test
	public void testSISRIntegration()
	{
		// Create config with SISR included
		Config testConfig = new Config();
		testConfig.setPerturbation(new PerturbationType[] {
			PerturbationType.Sequential,
			PerturbationType.Concentric,
			PerturbationType.SISR
		});

		// Verify configuration
		assertEquals(3, testConfig.getPerturbation().length,
			"Config should have 3 perturbation types");
		assertEquals(PerturbationType.SISR, testConfig.getPerturbation()[2],
			"Third perturbation type should be SISR");

		// Test that SISR operator can be instantiated via reflection
		// (simulating what AILSII.java does)
		try {
			Object operator = Class.forName("Perturbation." + PerturbationType.SISR)
				.getConstructor(Instance.class, Config.class)
				.newInstance(instance, testConfig);

			assertNotNull(operator, "SISR should be instantiable via reflection");
			assertTrue(operator instanceof SISR, "Instantiated object should be SISR type");
		} catch (Exception e) {
			e.printStackTrace();
			throw new AssertionError("SISR reflection instantiation failed: " + e.getMessage());
		}
	}

	/**
	 * Test 9: Performance sanity check
	 * Ensure SISR completes in reasonable time
	 */
	@Test
	public void testSISRPerformance()
	{
		SISR sisrOperator = new SISR(instance, config);

		long startTime = System.currentTimeMillis();

		// Perform 10 perturbations
		for (int i = 0; i < 10; i++) {
			Solution testSol = solution.clone();
			sisrOperator.perturb(testSol, 20, 0.5);
		}

		long endTime = System.currentTimeMillis();
		long duration = endTime - startTime;

		// 10 perturbations should complete in under 5 seconds (generous threshold)
		assertTrue(duration < 5000,
			"SISR should complete 10 perturbations in under 5 seconds (took " + duration + "ms)");
	}

	/**
	 * Test 10: Verify objective function consistency
	 * Solution objective should match sum of route objectives after SISR
	 */
	@Test
	public void testSISRObjectiveConsistency()
	{
		SISR sisrOperator = new SISR(instance, config);

		sisrOperator.perturb(solution, 20, 0.5);

		double routeSum = 0;
		for (int i = 0; i < solution.numRoutes; i++) {
			routeSum += solution.routes[i].F();
		}

		assertEquals(solution.f, routeSum, 0.01,
			"Solution objective should equal sum of route objectives");
	}
}
