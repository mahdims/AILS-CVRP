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
import Solution.Solution;
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

	double distanceLS;

	Perturbation[] pertubOperators;
	Perturbation selectedPerturbation;

	FeasibilityPhase feasibilityOperator;
	ConstructSolution constructSolution;

	// SISR operator dedicated for fleet minimization
	SISR sisrForFleetMin;

	// Elite Set for maintaining diverse high-quality solutions
	EliteSet eliteSet;

	// Path Relinking thread (runs in parallel)
	PathRelinkingThread prThread;
	Thread prThreadHandle;

	// PR->AILS communication: volatile flag for thread-safe signaling
	private volatile boolean prFoundBetter = false;

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
		}

		this.acceptanceCriterion = new AcceptanceCriterion(instance, config, executionMaximumLimit);

		// Create dedicated SISR operator for fleet minimization
		this.sisrForFleetMin = new SISR(instance, config, omegaSetup, intraLocalSearch);

		// Initialize Elite Set from config
		this.eliteSet = new EliteSet(
				config.getEliteSetSize(),
				config.getEliteSetBeta(),
				config.getEliteSetMinDiversity(),
				instance,
				config);

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
				this.pertubOperators[i] = (Perturbation) Class.forName("Perturbation." + config.getPerturbation()[i])
						.getConstructor(Instance.class, Config.class, HashMap.class, IntraLocalSearch.class)
						.newInstance(instance, config, omegaSetup, intraLocalSearch);
			}

		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
				| InvocationTargetException | NoSuchMethodException | SecurityException
				| ClassNotFoundException e) {
			e.printStackTrace();
		}

	}

	public void search() {
		iterator = 0;
		first = System.currentTimeMillis();
		lastHeartbeatTime = first; // Initialize heartbeat timer
		referenceSolution.numRoutes = instance.getMinNumberRoutes();
		constructSolution.construct(referenceSolution);

		feasibilityOperator.makeFeasible(referenceSolution);
		localSearch.localSearch(referenceSolution, true);
		bestSolution.clone(referenceSolution);

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

			// Apply fleet minimization probabilistically in first alpha% of run
			if (config.getFleetMinimizationRate() > 0 && isInFirstaPrecent()) {
				// Probabilistic application: 0.5% chance per iteration
				if (rand.nextDouble() < config.getFleetMinimizationRate()) {
					applyFleetMinimization();
				}
			}

			solution.clone(referenceSolution);

			selectedPerturbation = pertubOperators[rand.nextInt(pertubOperators.length)];
			String perturbName = selectedPerturbation.getPerturbationType() + "";
			perturbationUsageCount.put(perturbName, perturbationUsageCount.get(perturbName) + 1);
			selectedPerturbation.applyPerturbation(solution);
			feasibilityOperator.makeFeasible(solution);
			localSearch.localSearch(solution, true);
			distanceLS = pairwiseDistance.pairwiseSolutionDistance(solution, referenceSolution);

			evaluateSolution();
			distAdjustment.distAdjustment();

			selectedPerturbation.getChosenOmega().setDistance(distanceLS);// update

			if (acceptanceCriterion.acceptSolution(solution))
				referenceSolution.clone(solution);

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
	}

	public void evaluateSolution() {
		if ((solution.f - bestF) < -epsilon) {
			bestF = solution.f;

			bestSolution.clone(solution);
			iteratorMF = iterator;
			timeAF = (double) (System.currentTimeMillis() - first) / 1000;

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
	 * @param prSolution Solution found by PR
	 * @param prF        Objective value of PR solution
	 */
	public synchronized void notifyPRBetterSolution(Solution prSolution, double prF) {
		if (prF < bestF - epsilon) {
			// Update best solution
			bestF = prF;
			bestSolution.clone(prSolution);
			referenceSolution.clone(prSolution);
			iteratorMF = iterator;
			timeAF = (double) (System.currentTimeMillis() - first) / 1000;

			// Reset heartbeat timer on improvement from PR
			lastHeartbeatTime = System.currentTimeMillis();

			// Reset omega for all perturbations to ideal distance
			for (OmegaAdjustment omegaAdj : omegaSetup.values()) {
				omegaAdj.setActualOmega(idealDist.idealDist);
			}

			// Set flag for main thread
			prFoundBetter = true;

			if (print) {
				System.out.println("[PR->AILS] New best from PR: " + bestF
						+ " gap: " + deci.format(getGap()) + "%"
						+ " K: " + prSolution.numRoutes
						+ " iteration: " + iterator
						+ " time: " + timeAF
						+ " [Omega RESET]");
			}
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
