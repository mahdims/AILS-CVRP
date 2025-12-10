package SearchMethod;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import Perturbation.InsertionHeuristic;
import Perturbation.PerturbationType;

public class InputParameters
{

	private String file="";
	private boolean rounded=true;
	private double limit=Double.MAX_VALUE;
	private double best=0;
	private String solutionDir="";
	private Config config =new Config();
	private HashMap<String, String> parameterSources = new HashMap<>();
	
	public void readingInput(String[] args)
	{
		// Initialize parameter sources with defaults
		initializeParameterSources();

		// Step 1: Read parameters.txt file (if exists)
		readParametersFile("parameters.txt");

		// Step 2: Parse command-line arguments (overrides file and defaults)
		try
		{
			for (int i = 0; i < args.length-1; i+=2)
			{
				switch(args[i])
				{
					case "-file": file=getAddress(args[i+1]);break;
					case "-rounded": rounded=getRound(args[i+1]);break;
					case "-limit": limit=getLimit(args[i+1]);break;
					case "-best": best=getBest(args[i+1]);break;
					case "-solutionDir": solutionDir=args[i+1];break;
					case "-stoppingCriterion":
						config.setStoppingCriterionType(getStoppingCriterion(args[i+1]));
						parameterSources.put("stoppingCriterion", "CLI");
						break;
					case "-dMax":
						config.setDMax(getDMax(args[i+1]));
						parameterSources.put("dMax", "CLI");
						break;
					case "-dMin":
						config.setDMin(getDMin(args[i+1]));
						parameterSources.put("dMin", "CLI");
						break;
					case "-gamma":
						config.setGamma(getGamma(args[i+1]));
						parameterSources.put("gamma", "CLI");
						break;
					case "-varphi":
						config.setVarphi(getVarphi(args[i+1]));
						parameterSources.put("varphi", "CLI");
						break;
					case "-etaMin":
						config.setEtaMin(getDouble(args[i+1], "etaMin"));
						parameterSources.put("etaMin", "CLI");
						break;
					case "-etaMax":
						config.setEtaMax(getDouble(args[i+1], "etaMax"));
						parameterSources.put("etaMax", "CLI");
						break;
					case "-epsilon":
						config.setEpsilon(getDouble(args[i+1], "epsilon"));
						parameterSources.put("epsilon", "CLI");
						break;
					case "-knnLimit":
						config.setKnnLimit(getInt(args[i+1], "knnLimit"));
						parameterSources.put("knnLimit", "CLI");
						break;
					case "-fleetMinimizationRate":
						config.setFleetMinimizationRate(getDouble(args[i+1], "fleetMinimizationRate"));
						parameterSources.put("fleetMinimizationRate", "CLI");
						break;
					case "-fleetMinimizationMaxIter":
						config.setFleetMinimizationMaxIter(getInt(args[i+1], "fleetMinimizationMaxIter"));
						parameterSources.put("fleetMinimizationMaxIter", "CLI");
						break;
				}
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}

		System.out.println("File: "+file);
		System.out.println("Rounded: "+rounded);
		System.out.println("limit: "+limit);
		System.out.println("Best: "+best);
		System.out.println("LimitTime: "+limit);
		System.out.println(config.toString(parameterSources));
	}
	
	
	public String getAddress(String text)
	{
		try 
		{
			File file=new File(text);
			if(file.exists()&&!file.isDirectory())
				return text;
			else
				System.err.println("The -file parameter must contain the address of a valid file.");
		} 
		catch (Exception e) {
			e.printStackTrace();
		}
		return "";	
	}
	
	public boolean getRound(String text)
	{
		rounded=true;
		try 
		{
			if(text.equals("false")||text.equals("true"))
				rounded=Boolean.valueOf(text);
			else
				System.err.println("The -rounded parameter must have the values false or true.");
		} 
		catch (Exception e) {
			e.printStackTrace();
		}
		return rounded;
	}
	
	public double getLimit(String text)
	{
		try 
		{
			limit=Double.valueOf(text);
		} 
		catch (java.lang.NumberFormatException e) {
			System.err.println("The -limit parameter must contain a valid real value.");
		}
		return limit;
	}
	
	public double getBest(String text)
	{
		try 
		{
			best=Double.valueOf(text);
		} 
		catch (java.lang.NumberFormatException e) {
			System.err.println("The -best parameter must contain a valid real value.");
		}
		return best;
	}
	
	public int getVarphi(String text)
	{
		int varphi=40;
		try 
		{
			varphi=Integer.valueOf(text);
		} 
		catch (java.lang.NumberFormatException e) {
			System.err.println("The -varphi parameter must contain a valid integer value.");
		}
		return varphi;
	}
	
	public int getGamma(String text)
	{
		int gamma=30;
		try 
		{
			gamma=Integer.valueOf(text);
		} 
		catch (java.lang.NumberFormatException e) {
			System.err.println("The -gamma parameter must contain a valid integer value.");
		}
		return gamma;
	}
	
	public int getDMax(String text)
	{
		int dMax=30;
		try 
		{
			dMax=Integer.valueOf(text);
		} 
		catch (java.lang.NumberFormatException e) {
			System.err.println("The -dMax parameter must contain a valid integer value.");
		}
		return dMax;
	}
	
	public int getDMin(String text)
	{
		int dMin=15;
		try 
		{
			dMin=Integer.valueOf(text);
		} 
		catch (java.lang.NumberFormatException e) {
			System.err.println("The -dMin parameter must contain a valid integer value.");
		}
		return dMin;
	}
	
	public StoppingCriterionType getStoppingCriterion(String text)
	{
		StoppingCriterionType stoppingCriterion=StoppingCriterionType.Time;
		try 
		{
			stoppingCriterion=StoppingCriterionType.valueOf(text);
		} 
		catch (java.lang.IllegalArgumentException e) 
		{
			System.err.println("The -stoppingCriterion parameter must have the values "+Arrays.toString(StoppingCriterionType.values())+".");
		}
		return stoppingCriterion;
	}

	public String getFile() {
		return file;
	}

	public boolean isRounded() {
		return rounded;
	}

	public double getTimeLimit() {
		return limit;
	}

	public double getBest() {
		return best;
	}


	public Config getConfig() {
		return config;
	}

	public String getSolutionDir() {
		return solutionDir;
	}

	// ========== PARAMETER FILE READING AND SOURCE TRACKING ==========

	/**
	 * Initialize parameter sources with default values
	 */
	private void initializeParameterSources() {
		parameterSources.put("dMin", "default");
		parameterSources.put("dMax", "default");
		parameterSources.put("gamma", "default");
		parameterSources.put("varphi", "default");
		parameterSources.put("etaMin", "default");
		parameterSources.put("etaMax", "default");
		parameterSources.put("epsilon", "default");
		parameterSources.put("knnLimit", "default");
		parameterSources.put("stoppingCriterion", "default");
		parameterSources.put("perturbation", "default");
		parameterSources.put("insertionHeuristics", "default");
		parameterSources.put("sisr.maxStringLength", "default");
		parameterSources.put("sisr.splitRate", "default");
		parameterSources.put("sisr.splitDepth", "default");
		parameterSources.put("sisr.avgRemovedPercent", "default");
		parameterSources.put("sisr.blinkRate", "default");
		parameterSources.put("fleetMinimizationRate", "default");
		parameterSources.put("fleetMinimizationMaxIter", "default");
		parameterSources.put("eliteSetSize", "default");
		parameterSources.put("eliteSetBeta", "default");
		parameterSources.put("eliteSetMinDiversity", "default");
	}

	/**
	 * Read parameters from parameters.txt file (if exists)
	 * Format: parameterName=value (one per line, # for comments)
	 */
	private void readParametersFile(String filename) {
		File paramFile = new File(filename);
		if (!paramFile.exists()) {
			// File doesn't exist - silently continue with defaults
			return;
		}

		System.out.println("Loading parameters from: " + filename);

		try (BufferedReader reader = new BufferedReader(new FileReader(paramFile))) {
			String line;
			int lineNumber = 0;

			while ((line = reader.readLine()) != null) {
				lineNumber++;
				line = line.trim();

				// Skip empty lines and comments
				if (line.isEmpty() || line.startsWith("#")) {
					continue;
				}

				// Parse key=value
				String[] parts = line.split("=", 2);
				if (parts.length != 2) {
					System.err.println("Warning: Invalid format at line " + lineNumber + ": " + line);
					continue;
				}

				String key = parts[0].trim();
				String value = parts[1].trim();

				// Apply parameter
				applyParameter(key, value, "parameters.txt");
			}
		} catch (IOException e) {
			System.err.println("Warning: Error reading " + filename + ": " + e.getMessage());
		}
	}

	/**
	 * Apply a parameter from file or CLI
	 */
	private void applyParameter(String key, String value, String source) {
		try {
			switch (key) {
				// Core algorithm parameters
				case "dMin":
					config.setDMin(Integer.parseInt(value));
					parameterSources.put(key, source);
					break;
				case "dMax":
					config.setDMax(Integer.parseInt(value));
					parameterSources.put(key, source);
					break;
				case "gamma":
					config.setGamma(Integer.parseInt(value));
					parameterSources.put(key, source);
					break;
				case "varphi":
					config.setVarphi(Integer.parseInt(value));
					parameterSources.put(key, source);
					break;
				case "etaMin":
					config.setEtaMin(Double.parseDouble(value));
					parameterSources.put(key, source);
					break;
				case "etaMax":
					config.setEtaMax(Double.parseDouble(value));
					parameterSources.put(key, source);
					break;
				case "epsilon":
					config.setEpsilon(Double.parseDouble(value));
					parameterSources.put(key, source);
					break;
				case "knnLimit":
					config.setKnnLimit(Integer.parseInt(value));
					parameterSources.put(key, source);
					break;

				// SISR parameters
				case "sisr.maxStringLength":
					config.getSisrConfig().setMaxStringLength(Double.parseDouble(value));
					parameterSources.put(key, source);
					break;
				case "sisr.splitRate":
					config.getSisrConfig().setSplitRate(Double.parseDouble(value));
					parameterSources.put(key, source);
					break;
				case "sisr.splitDepth":
					config.getSisrConfig().setSplitDepth(Double.parseDouble(value));
					parameterSources.put(key, source);
					break;
				case "sisr.avgRemoved":
					config.getSisrConfig().setAvgRemoved(Double.parseDouble(value));
					parameterSources.put(key, source);
					break;
				case "sisr.blinkRate":
					config.getSisrConfig().setBlinkRate(Double.parseDouble(value));
					parameterSources.put(key, source);
					break;

				// Fleet minimization parameters
				case "fleetMinimizationRate":
					config.setFleetMinimizationRate(Double.parseDouble(value));
					parameterSources.put(key, source);
					break;
				case "fleetMinimizationMaxIter":
					config.setFleetMinimizationMaxIter(Integer.parseInt(value));
					parameterSources.put(key, source);
					break;

				// Elite Set parameters
				case "eliteSetSize":
					config.setEliteSetSize(Integer.parseInt(value));
					parameterSources.put(key, source);
					break;
				case "eliteSetBeta":
					config.setEliteSetBeta(Double.parseDouble(value));
					parameterSources.put(key, source);
					break;
				case "eliteSetMinDiversity":
					config.setEliteSetMinDiversity(Double.parseDouble(value));
					parameterSources.put(key, source);
					break;

				// Adaptive Operator Selection parameters
				case "aos.enabled":
					config.setAosEnabled(Boolean.parseBoolean(value));
					parameterSources.put(key, source);
					break;
				case "aos.segmentLength":
					config.setAosSegmentLength(Integer.parseInt(value));
					parameterSources.put(key, source);
					break;
				case "aos.reactionFactor":
					config.setAosReactionFactor(Double.parseDouble(value));
					parameterSources.put(key, source);
					break;
				case "aos.minProbability":
					config.setAosMinProbability(Double.parseDouble(value));
					parameterSources.put(key, source);
					break;
				case "aos.scoreGlobalBest":
					config.setAosScoreGlobalBest(Double.parseDouble(value));
					parameterSources.put(key, source);
					break;
				case "aos.scoreImproved":
					config.setAosScoreImproved(Double.parseDouble(value));
					parameterSources.put(key, source);
					break;
				case "aos.scoreAccepted":
					config.setAosScoreAccepted(Double.parseDouble(value));
					parameterSources.put(key, source);
					break;
				case "aos.scoreRejected":
					config.setAosScoreRejected(Double.parseDouble(value));
					parameterSources.put(key, source);
					break;

				// Perturbation operators (destroy methods)
				case "perturbation":
					parsePerturbationList(value, source);
					break;

				// Insertion heuristics (recreate methods)
				case "insertionHeuristics":
					parseInsertionHeuristicsList(value, source);
					break;

				default:
					System.err.println("Warning: Unknown parameter '" + key + "' in " + source);
			}
		} catch (NumberFormatException e) {
			System.err.println("Warning: Invalid value for parameter '" + key + "': " + value);
		}
	}

	/**
	 * Helper method to parse double values
	 */
	private double getDouble(String text, String paramName) {
		try {
			return Double.parseDouble(text);
		} catch (NumberFormatException e) {
			System.err.println("The -" + paramName + " parameter must contain a valid real value.");
			return 0.0;
		}
	}

	/**
	 * Helper method to parse integer values
	 */
	private int getInt(String text, String paramName) {
		try {
			return Integer.parseInt(text);
		} catch (NumberFormatException e) {
			System.err.println("The -" + paramName + " parameter must contain a valid integer value.");
			return 0;
		}
	}

	/**
	 * Parse comma-separated list of perturbation operators (destroy methods)
	 * Format: Sequential,Concentric,SISR
	 */
	private void parsePerturbationList(String value, String source) {
		String[] parts = value.split(",");
		List<PerturbationType> perturbList = new ArrayList<>();

		for (String part : parts) {
			String trimmed = part.trim();
			try {
				PerturbationType type = PerturbationType.valueOf(trimmed);
				perturbList.add(type);
			} catch (IllegalArgumentException e) {
				System.err.println("Warning: Unknown perturbation type '" + trimmed + "' in " + source);
				System.err.println("  Valid types: Sequential, Concentric, SISR");
			}
		}

		if (!perturbList.isEmpty()) {
			PerturbationType[] array = perturbList.toArray(new PerturbationType[0]);
			config.setPerturbation(array);
			parameterSources.put("perturbation", source);
		}
	}

	/**
	 * Parse comma-separated list of insertion heuristics (recreate methods)
	 * Format: Distance,Cost
	 */
	private void parseInsertionHeuristicsList(String value, String source) {
		String[] parts = value.split(",");
		List<InsertionHeuristic> heuristicList = new ArrayList<>();

		for (String part : parts) {
			String trimmed = part.trim();
			try {
				InsertionHeuristic heuristic = InsertionHeuristic.valueOf(trimmed);
				heuristicList.add(heuristic);
			} catch (IllegalArgumentException e) {
				System.err.println("Warning: Unknown insertion heuristic '" + trimmed + "' in " + source);
				System.err.println("  Valid heuristics: Distance, Cost");
			}
		}

		if (!heuristicList.isEmpty()) {
			InsertionHeuristic[] array = heuristicList.toArray(new InsertionHeuristic[0]);
			config.setInsertionHeuristics(array);
			parameterSources.put("insertionHeuristics", source);
		}
	}

}

