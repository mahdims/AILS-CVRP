package SearchMethod;

import java.text.DecimalFormat;
import java.util.Arrays;

import Perturbation.InsertionHeuristic;
import Perturbation.PerturbationType;
import Perturbation.SISRConfig;
import PathRelinking.PathRelinkingConfig;

public class Config implements Cloneable {
	DecimalFormat deci = new DecimalFormat("0.000");
	double etaMin, etaMax;
	int dMin, dMax;

	int gamma;
	PerturbationType perturbation[];
	InsertionHeuristic[] insertionHeuristics;
	// --------------------PR-------------------
	int varphi;
	double epsilon;
	int knnLimit;
	StoppingCriterionType stoppingCriterionType;
	SISRConfig sisrConfig;

	// --------------------Fleet Minimization-------------------
	double fleetMinimizationRate;
	int fleetMinimizationMaxIter;

	// --------------------Elite Set-------------------
	int eliteSetSize;
	double eliteSetBeta;
	double eliteSetMinDiversity;

	// --------------------Adaptive Operator Selection-------------------
	boolean aosEnabled;
	boolean aosDecoupled;  // Decoupled destroy-repair selection (recommended by Pisinger & Ropke 2019)
	int aosSegmentLength;
	double aosReactionFactor;
	double aosMinProbability;
	double aosScoreGlobalBest;
	double aosScoreImproved;
	double aosScoreAccepted;
	double aosScoreRejected;

	// --------------------Path Relinking-------------------
	PathRelinkingConfig prConfig;

	// --------------------Warm Start-------------------
	boolean warmStartEnabled;

	public Config() {
		// ----------------------------Main----------------------------
		this.stoppingCriterionType = StoppingCriterionType.Time;
		this.dMin = 15;
		this.dMax = 30;
		this.gamma = 30;
		this.knnLimit = 100;
		this.varphi = 40;

		this.epsilon = 0.01;
		this.etaMin = 0.01;
		this.etaMax = 1;

		this.perturbation = new PerturbationType[3];
		this.perturbation[0] = PerturbationType.Sequential;
		this.perturbation[1] = PerturbationType.Concentric;
		this.perturbation[2] = PerturbationType.SISR;

		this.insertionHeuristics = new InsertionHeuristic[2];
		insertionHeuristics[0] = InsertionHeuristic.Distance;
		insertionHeuristics[1] = InsertionHeuristic.Cost;

		this.sisrConfig = new SISRConfig();

		this.fleetMinimizationRate = 0.01;
		this.fleetMinimizationMaxIter = 100;

		this.eliteSetSize = 10;
		this.eliteSetBeta = 0.2;
		this.eliteSetMinDiversity = 0.15;

		this.aosEnabled = true;
		this.aosDecoupled = true;  // Decoupled destroy-repair selection (Pisinger & Ropke 2019)
		this.aosSegmentLength = 20;
		this.aosReactionFactor = 0.2;
		this.aosMinProbability = 0.05;
		this.aosScoreGlobalBest = 33.0;
		this.aosScoreImproved = 9.0;
		this.aosScoreAccepted = 13.0;
		this.aosScoreRejected = 0.0;

		this.prConfig = PathRelinkingConfig.aggressive();

		this.warmStartEnabled = false;
	}

	public Config clone() {
		try {
			return (Config) super.clone();
		} catch (CloneNotSupportedException e) {
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public String toString() {
		return "Config "
				+ "\nstoppingCriterionType: " + stoppingCriterionType
				+ "\netaMax: " + deci.format(etaMax)
				+ "\netaMin: " + deci.format(etaMin)
				+ "\ngamma: " + gamma
				+ "\ndMin: " + dMin
				+ "\ndMax: " + dMax
				+ "\nvarphi: " + varphi
				+ "\nepsilon: " + deci.format(epsilon)
				+ "\nperturbation: " + Arrays.toString(perturbation)
				+ "\ninsertionHeuristics: " + Arrays.toString(insertionHeuristics)
				+ "\nlimitKnn: " + knnLimit
				+ "\nSISR: " + sisrConfig
				+ "\nfleetMinimizationRate: " + deci.format(fleetMinimizationRate)
				+ "\nfleetMinimizationMaxIter: " + fleetMinimizationMaxIter
				+ "\neliteSetSize: " + eliteSetSize
				+ "\neliteSetBeta: " + deci.format(eliteSetBeta)
				+ "\neliteSetMinDiversity: " + deci.format(eliteSetMinDiversity)
				+ "\nwarmStartEnabled: " + warmStartEnabled;
	}

	/**
	 * toString with parameter source tracking
	 */
	public String toString(java.util.HashMap<String, String> sources) {
		return "Config "
				+ "\nstoppingCriterionType: " + stoppingCriterionType + " ("
				+ sources.getOrDefault("stoppingCriterion", "default") + ")"
				+ "\netaMax: " + deci.format(etaMax) + " (" + sources.getOrDefault("etaMax", "default") + ")"
				+ "\netaMin: " + deci.format(etaMin) + " (" + sources.getOrDefault("etaMin", "default") + ")"
				+ "\ngamma: " + gamma + " (" + sources.getOrDefault("gamma", "default") + ")"
				+ "\ndMin: " + dMin + " (" + sources.getOrDefault("dMin", "default") + ")"
				+ "\ndMax: " + dMax + " (" + sources.getOrDefault("dMax", "default") + ")"
				+ "\nvarphi: " + varphi + " (" + sources.getOrDefault("varphi", "default") + ")"
				+ "\nepsilon: " + deci.format(epsilon) + " (" + sources.getOrDefault("epsilon", "default") + ")"
				+ "\nperturbation: " + Arrays.toString(perturbation) + " ("
				+ sources.getOrDefault("perturbation", "default") + ")"
				+ "\ninsertionHeuristics: " + Arrays.toString(insertionHeuristics) + " ("
				+ sources.getOrDefault("insertionHeuristics", "default") + ")"
				+ "\nlimitKnn: " + knnLimit + " (" + sources.getOrDefault("knnLimit", "default") + ")"
				+ "\nSISR: " + sisrConfig.toString(sources)
				+ "\nfleetMinimizationRate: " + deci.format(fleetMinimizationRate) + " ("
				+ sources.getOrDefault("fleetMinimizationRate", "default") + ")"
				+ "\nfleetMinimizationMaxIter: " + fleetMinimizationMaxIter + " ("
				+ sources.getOrDefault("fleetMinimizationMaxIter", "default") + ")"
				+ "\neliteSetSize: " + eliteSetSize + " (" + sources.getOrDefault("eliteSetSize", "default") + ")"
				+ "\neliteSetBeta: " + deci.format(eliteSetBeta) + " ("
				+ sources.getOrDefault("eliteSetBeta", "default") + ")"
				+ "\neliteSetMinDiversity: " + deci.format(eliteSetMinDiversity) + " ("
				+ sources.getOrDefault("eliteSetMinDiversity", "default") + ")"
				+ "\naos.enabled: " + aosEnabled + " ("
				+ sources.getOrDefault("aos.enabled", "default") + ")"
				+ "\naos.segmentLength: " + aosSegmentLength + " ("
				+ sources.getOrDefault("aos.segmentLength", "default") + ")"
				+ "\naos.reactionFactor: " + deci.format(aosReactionFactor) + " ("
				+ sources.getOrDefault("aos.reactionFactor", "default") + ")"
				+ "\naos.minProbability: " + deci.format(aosMinProbability) + " ("
				+ sources.getOrDefault("aos.minProbability", "default") + ")"
				+ "\naos.scoreGlobalBest: " + deci.format(aosScoreGlobalBest) + " ("
				+ sources.getOrDefault("aos.scoreGlobalBest", "default") + ")"
				+ "\naos.scoreImproved: " + deci.format(aosScoreImproved) + " ("
				+ sources.getOrDefault("aos.scoreImproved", "default") + ")"
				+ "\naos.scoreAccepted: " + deci.format(aosScoreAccepted) + " ("
				+ sources.getOrDefault("aos.scoreAccepted", "default") + ")"
				+ "\naos.scoreRejected: " + deci.format(aosScoreRejected) + " ("
				+ sources.getOrDefault("aos.scoreRejected", "default") + ")"
				+ "\nwarmStartEnabled: " + warmStartEnabled + " ("
				+ sources.getOrDefault("warmStart", "default") + ")";
	}

	public DecimalFormat getDeci() {
		return deci;
	}

	public void setDeci(DecimalFormat deci) {
		this.deci = deci;
	}

	public double getEtaMin() {
		return etaMin;
	}

	public void setEtaMin(double etaMin) {
		this.etaMin = etaMin;
	}

	public double getEtaMax() {
		return etaMax;
	}

	public void setEtaMax(double etaMax) {
		this.etaMax = etaMax;
	}

	public int getDMin() {
		return dMin;
	}

	public void setDMin(int dMin) {
		this.dMin = dMin;
	}

	public int getDMax() {
		return dMax;
	}

	public void setDMax(int dMax) {
		this.dMax = dMax;
	}

	public int getGamma() {
		return gamma;
	}

	public void setGamma(int gamma) {
		this.gamma = gamma;
	}

	public PerturbationType[] getPerturbation() {
		return perturbation;
	}

	public void setPerturbation(PerturbationType[] perturbation) {
		this.perturbation = perturbation;
	}

	public InsertionHeuristic[] getInsertionHeuristics() {
		return insertionHeuristics;
	}

	public void setInsertionHeuristics(InsertionHeuristic[] insertionHeuristics) {
		this.insertionHeuristics = insertionHeuristics;
	}

	public int getVarphi() {
		return varphi;
	}

	public void setVarphi(int varphi) {
		if (knnLimit < varphi)
			knnLimit = varphi;

		this.varphi = varphi;
	}

	public double getEpsilon() {
		return epsilon;
	}

	public void setEpsilon(double epsilon) {
		this.epsilon = epsilon;
	}

	public int getKnnLimit() {
		return knnLimit;
	}

	public void setKnnLimit(int knnLimit) {
		this.knnLimit = knnLimit;
	}

	public StoppingCriterionType getStoppingCriterionType() {
		return stoppingCriterionType;
	}

	public void setStoppingCriterionType(StoppingCriterionType stoppingCriterionType) {
		this.stoppingCriterionType = stoppingCriterionType;
	}

	public SISRConfig getSisrConfig() {
		return sisrConfig;
	}

	public void setSisrConfig(SISRConfig sisrConfig) {
		this.sisrConfig = sisrConfig;
	}

	public double getFleetMinimizationRate() {
		return fleetMinimizationRate;
	}

	public void setFleetMinimizationRate(double fleetMinimizationRate) {
		this.fleetMinimizationRate = fleetMinimizationRate;
	}

	public int getFleetMinimizationMaxIter() {
		return fleetMinimizationMaxIter;
	}

	public void setFleetMinimizationMaxIter(int fleetMinimizationMaxIter) {
		this.fleetMinimizationMaxIter = fleetMinimizationMaxIter;
	}

	public int getEliteSetSize() {
		return eliteSetSize;
	}

	public void setEliteSetSize(int eliteSetSize) {
		this.eliteSetSize = eliteSetSize;
	}

	public double getEliteSetBeta() {
		return eliteSetBeta;
	}

	public void setEliteSetBeta(double eliteSetBeta) {
		this.eliteSetBeta = eliteSetBeta;
	}

	public double getEliteSetMinDiversity() {
		return eliteSetMinDiversity;
	}

	public void setEliteSetMinDiversity(double eliteSetMinDiversity) {
		this.eliteSetMinDiversity = eliteSetMinDiversity;
	}

	public boolean isAosEnabled() {
		return aosEnabled;
	}

	public void setAosEnabled(boolean aosEnabled) {
		this.aosEnabled = aosEnabled;
	}

	public int getAosSegmentLength() {
		return aosSegmentLength;
	}

	public void setAosSegmentLength(int aosSegmentLength) {
		this.aosSegmentLength = aosSegmentLength;
	}

	public double getAosReactionFactor() {
		return aosReactionFactor;
	}

	public void setAosReactionFactor(double aosReactionFactor) {
		this.aosReactionFactor = aosReactionFactor;
	}

	public double getAosMinProbability() {
		return aosMinProbability;
	}

	public void setAosMinProbability(double aosMinProbability) {
		this.aosMinProbability = aosMinProbability;
	}

	public double getAosScoreGlobalBest() {
		return aosScoreGlobalBest;
	}

	public void setAosScoreGlobalBest(double aosScoreGlobalBest) {
		this.aosScoreGlobalBest = aosScoreGlobalBest;
	}

	public double getAosScoreImproved() {
		return aosScoreImproved;
	}

	public void setAosScoreImproved(double aosScoreImproved) {
		this.aosScoreImproved = aosScoreImproved;
	}

	public double getAosScoreAccepted() {
		return aosScoreAccepted;
	}

	public void setAosScoreAccepted(double aosScoreAccepted) {
		this.aosScoreAccepted = aosScoreAccepted;
	}

	public double getAosScoreRejected() {
		return aosScoreRejected;
	}

	public void setAosScoreRejected(double aosScoreRejected) {
		this.aosScoreRejected = aosScoreRejected;
	}

	public boolean isAosDecoupled() {
		return aosDecoupled;
	}

	public void setAosDecoupled(boolean aosDecoupled) {
		this.aosDecoupled = aosDecoupled;
	}

	public PathRelinkingConfig getPrConfig() {
		return prConfig;
	}

	public void setPrConfig(PathRelinkingConfig prConfig) {
		this.prConfig = prConfig;
	}

	public boolean isWarmStartEnabled() {
		return warmStartEnabled;
	}

	public void setWarmStartEnabled(boolean warmStartEnabled) {
		this.warmStartEnabled = warmStartEnabled;
	}

}
