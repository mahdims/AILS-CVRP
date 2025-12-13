package SearchMethod;

import java.lang.reflect.InvocationTargetException;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Random;

import Auxiliary.Distance;
import Data.Instance;
import DiversityControl.DistAdjustment;
import DiversityControl.OmegaAdjustment;
import DiversityControl.AcceptanceCriterion;
import DiversityControl.IdealDist;
import EliteSet.EliteSet;
import Improvement.LocalSearch;
import Improvement.IntraLocalSearch;
import Improvement.FeasibilityPhase;
import Perturbation.InsertionHeuristic;
import Perturbation.Perturbation;
import Perturbation.SISR;
import Perturbation.CriticalRemoval;
import Solution.Solution;
import Solution.Node;
import PathRelinking.PathRelinkingThread;

public class AILSII {
	// ----------Problema------------
	Solution solution, referenceSolution, bestSolution;

	Instance instance;
	Distance pairwiseDistance;
	double bestF = Double.MAX_VALUE;
	double executionMaximumLimit;
	double optimal;

	// ----------caculoLimiar------------
	int numIterUpdate;

	// ----------Metricas------------
	int iterator, iteratorMF;
	long first, ini;
	double timeAF, totalTime, time;
	long lastHeartbeatTime; // Track time of last heartbeat log (for 10-min intervals)

	Random rand = new Random();

	HashMap<String, OmegaAdjustment> omegaSetup = new HashMap<String, OmegaAdjustment>();
	HashMap<String, Integer> perturbationUsageCount = new HashMap<String, Integer>();

	// Operator success tracking for effectiveness analysis
	HashMap<String, Integer> operatorSuccessCount = new HashMap<String, Integer>();
	String lastOperatorUsed = null;
	double lastSolutionF = Double.MAX_VALUE;

	double distanceLS;

	Perturbation[] pertubOperators;
	Perturbation selectedPerturbation;

	FeasibilityPhase feasibilityOperator;
	ConstructSolution constructSolution;

	// SISR operator dedicated for fleet minimization
	SISR sisrForFleetMin;

	// Elite Set for maintaining diverse high-quality solutions
	EliteSet eliteSet;

	// History tracking for CriticalRemoval operator
	// Tracks how many times each customer has been removed during search
	private int[] customerRemovalCounts;

	// Adaptive Operator Selection (Decoupled destroy-repair)
	private DecoupledAOS daos;

	// Path Relinking thread (runs in parallel)
	PathRelinkingThread prThread;
	Thread prThreadHandle;

	// PR->AILS communication: volatile flag for thread-safe signaling
	private volatile boolean prFoundBetter = false;
	// Temporary storage for PR solution (to avoid race condition)
	private Solution prPendingSolution;
	private double prPendingF;

	LocalSearch localSearch;

	InsertionHeuristic insertionHeuristic;
	IntraLocalSearch intraLocalSearch;
	AcceptanceCriterion acceptanceCriterion;
	// ----------Mare------------
	DistAdjustment distAdjustment;
	// ---------Print----------
	boolean print = true;
	IdealDist idealDist;

	double epsilon;
	DecimalFormat deci = new DecimalFormat("0.0000");
	StoppingCriterionType stoppingCriterionType;

	public AILSII(Instance instance, InputParameters reader) {
		this.instance = instance;
		Config config = reader.getConfig();
		this.optimal = reader.getBest();
		this.executionMaximumLimit = reader.getTimeLimit();

		this.epsilon = config.getEpsilon();
		this.stoppingCriterionType = config.getStoppingCriterionType();
		this.idealDist = new IdealDist();
		this.solution = new Solution(instance, config);
		this.referenceSolution = new Solution(instance, config);
		this.bestSolution = new Solution(instance, config);
		this.numIterUpdate = config.getGamma();

		this.pairwiseDistance = new Distance();

		this.pertubOperators = new Perturbation[config.getPerturbation().length];

		this.distAdjustment = new DistAdjustment(idealDist, config, executionMaximumLimit);

		this.intraLocalSearch = new IntraLocalSearch(instance, config);

		this.localSearch = new LocalSearch(instance, config, intraLocalSearch);

		this.feasibilityOperator = new FeasibilityPhase(instance, config, intraLocalSearch);

		this.constructSolution = new ConstructSolution(instance, config);

		OmegaAdjustment newOmegaAdjustment;
		for (int i = 0; i < config.getPerturbation().length; i++) {
			newOmegaAdjustment = new OmegaAdjustment(config.getPerturbation()[i], config, instance.getSize(),
					idealDist);
			omegaSetup.put(config.getPerturbation()[i] + "", newOmegaAdjustment);
			perturbationUsageCount.put(config.getPerturbation()[i] + "", 0);
			operatorSuccessCount.put(config.getPerturbation()[i] + "", 0);
		}

		this.acceptanceCriterion = new AcceptanceCriterion(instance, config, executionMaximumLimit);

		// Create dedicated SISR operator for fleet minimization
		this.sisrForFleetMin = new SISR(instance, config, omegaSetup, intraLocalSearch, this);

		// Initialize Elite Set from config
		this.eliteSet = new EliteSet(
				config.getEliteSetSize(),
				config.getEliteSetBeta(),
				config.getEliteSetMinDiversity(),
				instance,
				config);

		// Initialize history tracking for CriticalRemoval
		// Array size = number of customers + 1 (index 0 is depot, not tracked)
		this.customerRemovalCounts = new int[instance.getSize() + 1];

		// Initialize PR->AILS communication buffers
		this.prPendingSolution = new Solution(instance, config);
		this.prPendingF = Double.MAX_VALUE;

		// Initialize Path Relinking thread
		if (config.getPrConfig().isEnabled()) {
			IntraLocalSearch prIntraLS = new IntraLocalSearch(instance, config);
			this.prThread = new PathRelinkingThread(
					eliteSet,
					instance,
					config,
					config.getPrConfig(),
					prIntraLS,
					this // Pass AILSII reference for communication
			);
		}

		try {
			for (int i = 0; i < pertubOperators.length; i++) {
				// Convert PerturbationType to String
				String operatorName = config.getPerturbation()[i] + "";

				// Special handling for new operators that need extra parameters
				if (operatorName.equals("CriticalRemoval")) {
					// CriticalRemoval needs ailsInstance for removal tracking
					this.pertubOperators[i] = new CriticalRemoval(
							instance, config, omegaSetup, intraLocalSearch, this);
				} else {
					// Standard operators via reflection (now with ailsInstance parameter)
					this.pertubOperators[i] = (Perturbation) Class.forName("Perturbation." + operatorName)
							.getConstructor(Instance.class, Config.class, HashMap.class,
									IntraLocalSearch.class, SearchMethod.AILSII.class)
							.newInstance(instance, config, omegaSetup, intraLocalSearch, this);
				}
			}

		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
				| InvocationTargetException | NoSuchMethodException | SecurityException
				| ClassNotFoundException e) {
			e.printStackTrace();
		}

		// Initialize Adaptive Operator Selection (Decoupled destroy-repair)
		if (config.isAosEnabled()) {
			// Destroy operator names (perturbation operators)
			String[] destroyOperatorNames = new String[config.getPerturbation().length];
			for (int i = 0; i < config.getPerturbation().length; i++) {
				destroyOperatorNames[i] = config.getPerturbation()[i].toString();
			}

			// Repair operator names (insertion heuristics)
			String[] repairOperatorNames = new String[config.getInsertionHeuristics().length];
			for (int i = 0; i < config.getInsertionHeuristics().length; i++) {
				repairOperatorNames[i] = config.getInsertionHeuristics()[i].toString();
			}

			// Create decoupled AOS (tracks destroy and repair independently)
			this.daos = new DecoupledAOS(destroyOperatorNames, repairOperatorNames, rand, config);
		}

	}

	/**
	 * Record customer removals for CriticalRemoval tracking.
	 * Called by perturbation operators (except CriticalRemoval itself) to track
	 * which customers are frequently being removed during the search.
	 *
	 * Applies periodic decay (95% retention) every 1000 iterations to gradually
	 * forget old removal patterns and adapt to current search behavior.
	 *
	 * @param removedCustomers Array of customers that were removed
	 * @param count            Number of customers in the array to process
	 */
	public void recordRemovals(Node[] removedCustomers, int count) {
		// Increment removal count for each removed customer
		for (int i = 0; i < count; i++) {
			customerRemovalCounts[removedCustomers[i].name]++;
		}

		// Periodic decay every 1000 iterations to prevent unbounded growth
		// and to give more weight to recent removal patterns
		if (iterator % 1000 == 0 && iterator > 0) {
			for (int j = 1; j < customerRemovalCounts.length; j++) {
				// Decay by 5% (retain 95%), rounding to nearest integer
				customerRemovalCounts[j] = (int) (customerRemovalCounts[j] * 0.95 + 0.5);
			}
		}
	}

	/**
	 * Get customer removal counts array.
	 * Used by CriticalRemoval operator to identify frequently removed customers.
	 *
	 * @return Array where index i contains removal count for customer i
	 */
	public int[] getCustomerRemovalCounts() {
		return customerRemovalCounts;
	}

	public void search() {
		iterator = 0;
		first = System.currentTimeMillis();
		lastHeartbeatTime = first; // Initialize heartbeat timer

		// Try warm start if enabled, otherwise use constructive heuristic
		boolean warmStartLoaded = false;
		if (referenceSolution.config.isWarmStartEnabled()) {
			warmStartLoaded = SolutionLoader.loadSolution(referenceSolution, instance, "warm_start");
		}

		if (!warmStartLoaded) {
			// Fallback to original constructive method
			referenceSolution.numRoutes = instance.getMinNumberRoutes();
			constructSolution.construct(referenceSolution);
		}

		// Logging for warm start analysis
		if (warmStartLoaded) {
			System.out.println("=== Warm Start Solution Analysis ===");
			System.out.println("After loading:");
			System.out.println("  Cost: " + referenceSolution.f);
			System.out.println("  Routes: " + referenceSolution.numRoutes);
			System.out.println("  Infeasibility: " + referenceSolution.infeasibility());
			System.out.println("  Is feasible: " + referenceSolution.feasible());
		}

		feasibilityOperator.makeFeasible(referenceSolution);

		if (warmStartLoaded) {
			System.out.println("After feasibility phase:");
			System.out.println("  Cost: " + referenceSolution.f);
			System.out.println("  Infeasibility: " + referenceSolution.infeasibility());
			System.out.println("  Is feasible: " + referenceSolution.feasible());
		}

		localSearch.localSearch(referenceSolution, true);

		if (warmStartLoaded) {
			System.out.println("After local search:");
			System.out.println("  Cost: " + referenceSolution.f);
			System.out.println("  Routes: " + referenceSolution.numRoutes);
			System.out.println("====================================");
		}

		bestSolution.clone(referenceSolution);
		bestF = referenceSolution.f;

		// Apply fleet minimization on initial solution
		Config config = referenceSolution.config;
		if (config.getFleetMinimizationRate() > 0) {
			applyFleetMinimization();
		}

		// Start Path Relinking thread if enabled
		if (config.getPrConfig().isEnabled() && prThread != null) {
			// Pass global time limit to PR thread so it can stop independently
			prThread.setGlobalTimeLimit(first, executionMaximumLimit);

			prThreadHandle = new Thread(prThread, "PathRelinkingThread");
			prThreadHandle.setDaemon(true);
			prThreadHandle.start();
			System.out.println("[AILS] Path Relinking thread started");
		}

		while (!stoppingCriterion()) {
			iterator++;

			// Process pending PR injection (deferred update pattern for thread safety)
			// This must happen at the start of iteration before we use referenceSolution
			if (prFoundBetter) {
				// Update best solution and reference solution from PR pending buffer
				bestF = prPendingF;
				bestSolution.clone(prPendingSolution);
				referenceSolution.clone(prPendingSolution);
				iteratorMF = iterator;
				timeAF = (double) (System.currentTimeMillis() - first) / 1000;

				// Reset heartbeat timer on improvement from PR
				lastHeartbeatTime = System.currentTimeMillis();

				// Reset omega for all perturbations to ideal distance
				for (OmegaAdjustment omegaAdj : omegaSetup.values()) {
					omegaAdj.setActualOmega(idealDist.idealDist);
				}

				// Reset AOS scores (segment invalidated by trajectory change)
				if (daos != null) {
					daos.getDestroyAOS().resetScores();
					daos.getRepairAOS().resetScores();
				}

				// Log the PR injection
				if (print) {
					System.out.println("[PR->AILS] New best from PR: " + bestF
							+ " gap: " + deci.format(getGap()) + "%"
							+ " K: " + prPendingSolution.numRoutes
							+ " iteration: " + iterator
							+ " time: " + timeAF
							+ " [Omega + AOS RESET]");
				}

				// Clear flag for next PR injection
				prFoundBetter = false;
			}

			// Apply fleet minimization probabilistically in first alpha% of run
			if (config.getFleetMinimizationRate() > 0 && isInFirstaPrecent()) {
				// Probabilistic application: 0.5% chance per iteration
				if (rand.nextDouble() < config.getFleetMinimizationRate()) {
					applyFleetMinimization();
				}
			}

			solution.clone(referenceSolution);

			// Select operators using Decoupled AOS (if enabled) or uniform random
			String perturbName;
			String repairName = null;
			InsertionHeuristic selectedRepair = null;

			if (daos != null) {
				// Decoupled AOS: Select destroy and repair operators independently
				perturbName = daos.selectDestroyOperator();
				repairName = daos.selectRepairOperator();

				// Find the destroy operator object by name
				for (Perturbation op : pertubOperators) {
					if (op.getPerturbationType().toString().equals(perturbName)) {
						selectedPerturbation = op;
						break;
					}
				}

				// Find the repair operator (insertion heuristic) by name
				for (InsertionHeuristic ih : InsertionHeuristic.values()) {
					if (ih.toString().equals(repairName)) {
						selectedRepair = ih;
						break;
					}
				}

				// Set the selected repair operator for the perturbation
				selectedPerturbation.setSelectedInsertionHeuristic(selectedRepair);
			} else {
				// Fallback: Uniform random selection (original behavior)
				selectedPerturbation = pertubOperators[rand.nextInt(pertubOperators.length)];
				perturbName = selectedPerturbation.getPerturbationType() + "";
				// Let perturbation randomly select its own repair operator
			}

			perturbationUsageCount.put(perturbName, perturbationUsageCount.get(perturbName) + 1);

			// Track last operators for success rate analysis
			lastOperatorUsed = perturbName;

			selectedPerturbation.applyPerturbation(solution);
			feasibilityOperator.makeFeasible(solution);
			localSearch.localSearch(solution, true);
			distanceLS = pairwiseDistance.pairwiseSolutionDistance(solution, referenceSolution);

			evaluateSolution();
			distAdjustment.distAdjustment();

			selectedPerturbation.getChosenOmega().setDistance(distanceLS);// update

			// Determine acceptance and track outcome for AOS
			boolean accepted = acceptanceCriterion.acceptSolution(solution);
			if (accepted) {
				referenceSolution.clone(solution);

				// Try to insert accepted solution into elite set for diversity
				// This helps build a diverse elite set beyond just global best solutions
				eliteSet.tryInsert(solution, solution.f);
			}

			// Provide feedback to Decoupled AOS based on outcome
			// Note: PR injections are handled at loop start, so we always record outcomes here
			if (daos != null) {
				int outcomeType;
				double bestFBeforeIteration = lastSolutionF; // F before this operator was applied

				if (solution.f < bestF) {
					// New global best found
					outcomeType = 1;
				} else if (solution.f < bestFBeforeIteration) {
					// Improved solution (better than previous reference)
					outcomeType = 2;
				} else if (accepted) {
					// Accepted solution (by SA criterion)
					outcomeType = 3;
				} else {
					// Rejected solution
					outcomeType = 0;
				}

				// Record outcome for BOTH destroy and repair operators (decoupled)
				daos.recordOutcome(perturbName, repairName, outcomeType);

				// Log operator probabilities at segment boundaries
				if (iterator % 2000 == 0) {
					daos.printStats(iterator);
				}
			}

			// Update lastSolutionF for next iteration
			lastSolutionF = referenceSolution.f;

			// Heartbeat logging: show progress every 10 minutes when no improvements
			long currentTime = System.currentTimeMillis();
			long timeSinceLastHeartbeat = currentTime - lastHeartbeatTime;
			if (timeSinceLastHeartbeat >= 600000) { // 10 minutes = 600,000 ms
				double elapsed = (currentTime - first) / 1000.0;
				double timeSinceImprovement = elapsed - timeAF;
				System.out.println("[AILS-Heartbeat] time:" + deci.format(elapsed) + "s"
						+ " iter:" + iterator
						+ " | bestF:" + bestF
						+ " gap:" + deci.format(getGap()) + "%"
						+ " noImpr:" + deci.format(timeSinceImprovement) + "s"
						+ " eta:" + deci.format(acceptanceCriterion.getEta())
						+ " omega:" + deci.format(selectedPerturbation.omega));
				lastHeartbeatTime = currentTime;
			}
		}

		totalTime = (double) (System.currentTimeMillis() - first) / 1000;

		// Stop Path Relinking thread
		if (prThread != null) {
			System.out.println("[AILS] Stopping Path Relinking thread...");
			prThread.stop();

			try {
				prThreadHandle.join(5000); // Wait up to 5 seconds
				System.out.println("[AILS] Path Relinking thread stopped");
			} catch (InterruptedException e) {
				System.err.println("[AILS] Path Relinking thread did not terminate cleanly");
			}
		}

		printPerturbationUsageSummary();
	}

	public void printPerturbationUsageSummary() {
		StringBuilder summary = new StringBuilder("\nPerturbation usage: ");
		for (String name : perturbationUsageCount.keySet()) {
			summary.append(name).append("=").append(perturbationUsageCount.get(name)).append(" ");
		}
		summary.append("(total=").append(iterator).append(" iterations)");
		System.out.println(summary.toString());

		// Print detailed success rate analysis
		System.out.println("\n==================================================");
		System.out.println("    Operator Effectiveness Analysis              ");
		System.out.println("==================================================");
		System.out.printf("%-25s %8s %10s %10s%n", "Operator", "Uses", "Improv.", "Rate");
		System.out.println("--------------------------------------------------");

		int totalUses = 0;
		int totalImprovements = 0;

		for (String name : perturbationUsageCount.keySet()) {
			int uses = perturbationUsageCount.get(name);
			int improvements = operatorSuccessCount.getOrDefault(name, 0);
			double rate = uses > 0 ? 100.0 * improvements / uses : 0.0;

			totalUses += uses;
			totalImprovements += improvements;

			System.out.printf("%-25s %8d %10d %9.2f%%%n",
					name, uses, improvements, rate);
		}

		System.out.println("--------------------------------------------------");
		double overallRate = totalUses > 0 ? 100.0 * totalImprovements / totalUses : 0.0;
		System.out.printf("%-25s %8d %10d %9.2f%%%n",
				"TOTAL", totalUses, totalImprovements, overallRate);
		System.out.println("==================================================\n");
	}

	public void evaluateSolution() {
		if ((solution.f - bestF) < -epsilon) {
			bestF = solution.f;

			bestSolution.clone(solution);
			iteratorMF = iterator;
			timeAF = (double) (System.currentTimeMillis() - first) / 1000;

			// Track which operator led to this improvement
			if (lastOperatorUsed != null) {
				operatorSuccessCount.put(lastOperatorUsed,
						operatorSuccessCount.get(lastOperatorUsed) + 1);
			}

			// Reset heartbeat timer on improvement
			lastHeartbeatTime = System.currentTimeMillis();

			// Try to insert into elite set (pass bestSolution which is already cloned)
			boolean inserted = eliteSet.tryInsert(bestSolution, bestSolution.f);

			if (print) {
				System.out.println("[AILS] time:" + deci.format(timeAF) + "s"
						+ " iter:" + iterator
						+ " | f:" + bestF
						+ " gap:" + deci.format(getGap()) + "%"
						+ " K:" + solution.numRoutes
						+ " eta:" + deci.format(acceptanceCriterion.getEta())
						+ " omega:" + deci.format(selectedPerturbation.omega)
						+ (inserted ? " [Elite+" + eliteSet.size() + "]" : ""));
			}

			// Update elite set iteration for monitoring
			eliteSet.updateIteration(iterator);
		}
	}

	private boolean stoppingCriterion() {
		switch (stoppingCriterionType) {
			case Iteration:
				if (bestF <= optimal || executionMaximumLimit <= iterator)
					return true;
				break;

			case Time:
				if (bestF <= optimal || executionMaximumLimit < (System.currentTimeMillis() - first) / 1000)
					return true;
				break;
		}
		return false;
	}

	/**
	 * Check if we're in the first alpha% of the run
	 * 
	 * @return true if in first alpha% based on iteration or time
	 */
	private boolean isInFirstaPrecent() {
		float firstaPrecent = 0.15f;

		switch (stoppingCriterionType) {
			case Iteration:
				return iterator <= executionMaximumLimit * firstaPrecent;

			case Time:
				double elapsedTime = (System.currentTimeMillis() - first) / 1000.0;
				return elapsedTime <= executionMaximumLimit * firstaPrecent;
		}
		return false;
	}

	public static void main(String[] args) {
		InputParameters reader = new InputParameters();
		reader.readingInput(args);

		Instance instance = new Instance(reader);

		AILSII ailsII = new AILSII(instance, reader);

		ailsII.search();

		// Save solution to file if solution directory is specified
		String solutionDir = reader.getSolutionDir();
		if (solutionDir != null && !solutionDir.isEmpty()) {
			try {
				// Extract instance name from file path
				String fileName = reader.getFile();
				String instanceName = new java.io.File(fileName).getName();
				instanceName = instanceName.replace(".vrp", "");

				// Create solution file path
				String solutionFile = solutionDir + java.io.File.separator + instanceName + ".sol";

				// Save the best solution
				ailsII.getBestSolution().printSolution(solutionFile);

				System.out.println("Solution saved to: " + solutionFile);
			} catch (Exception e) {
				System.err.println("Error saving solution: " + e.getMessage());
				e.printStackTrace();
			}
		}
	}

	public Solution getBestSolution() {
		return bestSolution;
	}

	public EliteSet getEliteSet() {
		return eliteSet;
	}

	public double getBestF() {
		return bestF;
	}

	public double getGap() {
		return 100 * ((bestF - optimal) / optimal);
	}

	public boolean isPrint() {
		return print;
	}

	public void setPrint(boolean print) {
		this.print = print;
	}

	public Solution getSolution() {
		return solution;
	}

	public int getIterator() {
		return iterator;
	}

	public String printOmegas() {
		String str = "";
		for (int i = 0; i < pertubOperators.length; i++) {
			str += "\n" + omegaSetup.get(this.pertubOperators[i].perturbationType + "" + referenceSolution.numRoutes);
		}
		return str;
	}

	public Perturbation[] getPertubOperators() {
		return pertubOperators;
	}

	public double getTotalTime() {
		return totalTime;
	}

	public double getTimePerIteration() {
		return totalTime / iterator;
	}

	public double getTimeAF() {
		return timeAF;
	}

	public int getIteratorMF() {
		return iteratorMF;
	}

	public double getConvergenceIteration() {
		return (double) iteratorMF / iterator;
	}

	public double convergenceTime() {
		return (double) timeAF / totalTime;
	}

	/**
	 * Called by PR thread when it finds a solution better than global best
	 * Thread-safe method for PR->AILS communication
	 *
	 * IMPORTANT: Uses deferred update pattern to avoid race conditions
	 * - PR thread stores solution in pending buffer and sets flag
	 * - Main AILS thread processes the update at a safe point (between iterations)
	 *
	 * @param prSolution Solution found by PR
	 * @param prF        Objective value of PR solution
	 */
	public synchronized void notifyPRBetterSolution(Solution prSolution, double prF) {
		if (prF < bestF - epsilon) {
			// Store PR solution in pending buffer (avoid race condition with main thread)
			prPendingSolution.clone(prSolution);
			prPendingF = prF;

			// Set flag to signal main thread to process the update
			// Main thread will update referenceSolution at a safe point
			prFoundBetter = true;

			// Note: Detailed logging moved to main thread where actual update happens
		}
	}

	/**
	 * Apply fleet minimization to try to reduce the number of routes
	 * Matches C++ implementation with local search and acceptance criteria
	 */
	private void applyFleetMinimization() {
		Config config = referenceSolution.config;
		int maxIter = config.getFleetMinimizationMaxIter();

		int initialRoutes = referenceSolution.numRoutes;

		// Apply fleet minimization using dedicated SISR operator
		Solution fleetMinResult = referenceSolution.fleetMinimisationSISR(
				sisrForFleetMin,
				maxIter);

		// Run local search if fewer vehicles (C++ implementation)
		if (fleetMinResult.numRoutes < referenceSolution.numRoutes) {
			localSearch.localSearch(fleetMinResult, true);
		} else {
			// No improvement - silently skip (no log spam)
			return;
		}

		// NECESSARY CONDITION: Must reduce routes to be accepted
		// Use adaptive threshold only if routes are reduced
		boolean accepted = (fleetMinResult.numRoutes < initialRoutes) &&
				acceptanceCriterion.acceptSolution(fleetMinResult);

		if (accepted) {
			// Update reference solution
			referenceSolution.clone(fleetMinResult);

			// Update best solution if improved
			if ((fleetMinResult.f - bestF) < -epsilon) {
				bestF = fleetMinResult.f;
				bestSolution.clone(fleetMinResult);
				iteratorMF = iterator;
				timeAF = (double) (System.currentTimeMillis() - first) / 1000;
			}
		}

		// One-line output
		if (print) {
			double currentTime = (double) (System.currentTimeMillis() - first) / 1000;
			String routeChange = initialRoutes + "->" + fleetMinResult.numRoutes;
			String acceptStatus = accepted ? "accepted" : "rejected";
			System.out.println("[FleetMin] time:" + deci.format(currentTime) + "s"
					+ " iter:" + iterator
					+ " | routes:" + routeChange
					+ " status:" + acceptStatus
					+ " f:" + deci.format(fleetMinResult.f));
		}
	}

}
