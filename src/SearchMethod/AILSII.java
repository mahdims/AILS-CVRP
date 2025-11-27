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
import Improvement.LocalSearch;
import Improvement.IntraLocalSearch;
import Improvement.FeasibilityPhase;
import Perturbation.InsertionHeuristic;
import Perturbation.Perturbation;
import Perturbation.SISR;
import Solution.Solution;

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

		while (!stoppingCriterion()) {
			iterator++;

			// Apply fleet minimization probabilistically in first 25% of run
			if (config.getFleetMinimizationRate() > 0 && isInFirstQuarter()) {
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
		}

		totalTime = (double) (System.currentTimeMillis() - first) / 1000;
		printPerturbationUsageSummary();
	}

	public void printPerturbationUsageSummary() {
		System.out.println("\nPerturbation usage: " +
				"Sequential=" + perturbationUsageCount.getOrDefault("Sequential", 0) +
				" Concentric=" + perturbationUsageCount.getOrDefault("Concentric", 0) +
				" SISR=" + perturbationUsageCount.getOrDefault("SISR", 0) +
				" (total=" + iterator + " iterations)");
	}

	public void evaluateSolution() {
		if ((solution.f - bestF) < -epsilon) {
			bestF = solution.f;

			bestSolution.clone(solution);
			iteratorMF = iterator;
			timeAF = (double) (System.currentTimeMillis() - first) / 1000;

			if (print) {
				System.out.println("solution quality: " + bestF
						+ " gap: " + deci.format(getGap()) + "%"
						+ " K: " + solution.numRoutes
						+ " iteration: " + iterator
						+ " eta: " + deci.format(acceptanceCriterion.getEta())
						+ " omega: " + deci.format(selectedPerturbation.omega)
						+ " time: " + timeAF);
			}
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
	 * Check if we're in the first quarter (25%) of the run
	 * 
	 * @return true if in first 25% based on iteration or time
	 */
	private boolean isInFirstQuarter() {
		switch (stoppingCriterionType) {
			case Iteration:
				return iterator <= executionMaximumLimit * 0.25;

			case Time:
				double elapsedTime = (System.currentTimeMillis() - first) / 1000.0;
				return elapsedTime <= executionMaximumLimit * 0.25;
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
			System.out.println("FleetMin(iter " + iterator + ")" + ": No improvement");
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
			String routeChange = initialRoutes + "->" + fleetMinResult.numRoutes;
			String acceptStatus = accepted ? "accepted" : "rejected";
			System.out.println("FleetMin(iter " + iterator + "): " + routeChange + " routes, " + acceptStatus + ", f="
					+ deci.format(fleetMinResult.f));
		}
	}

}
