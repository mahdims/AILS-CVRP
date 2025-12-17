package Perturbation;

import java.util.HashMap;
import java.util.Random;

import Data.Instance;
import DiversityControl.OmegaAdjustment;
import Improvement.IntraLocalSearch;
import SearchMethod.Config;
import Solution.Node;
import Solution.Route;
import Solution.Solution;

public abstract class Perturbation 
{
	protected Route routes[];
	protected int numRoutes;
	protected Node solution[];
	protected double f=0;
	protected Random rand=new Random();
	public double omega;
	OmegaAdjustment chosenOmega;
	Config config;
	protected Node candidates[];
	protected int countCandidates;

	InsertionHeuristic[]insertionHeuristics;
	public InsertionHeuristic selectedInsertionHeuristic;
	private InsertionHeuristic externallySetInsertionHeuristic = null;  // Set by DecoupledAOS

	Node node;
	
	public PerturbationType perturbationType;
	int size;
	HashMap<String, OmegaAdjustment> omegaSetup;
	
	double bestCost,bestDist;
	int numIterUpdate;
	int indexHeuristic;
	
	double cost,dist;
	double costPrev;
	int indexA,indexB;
	Node bestNode,aux;
	Instance instance;
	int limitAdj;

	IntraLocalSearch intraLocalSearch;

	// Reference to AILSII instance for history tracking
	protected SearchMethod.AILSII ailsInstance;

	// Regret-based insertion operator (for advanced repair heuristics)
	private RegretInsertion regretInsertion;

	public Perturbation(Instance instance,Config config,
	HashMap<String, OmegaAdjustment> omegaSetup, IntraLocalSearch intraLocalSearch,
	SearchMethod.AILSII ailsInstance)
	{
		this.config=config;
		this.instance=instance;
		this.insertionHeuristics=config.getInsertionHeuristics();
		this.size=instance.getSize()-1;
		this.candidates=new Node[size];
		this.omegaSetup=omegaSetup;
		this.numIterUpdate=config.getGamma();
		this.limitAdj=config.getVarphi();
		this.intraLocalSearch=intraLocalSearch;
		this.ailsInstance=ailsInstance;

		// Initialize RegretInsertion for regret-based repair heuristics
		this.regretInsertion = new RegretInsertion(instance, config.getKnnLimit());
	}
	
	public void setOrder()
	{
		Node aux;
		for (int i = 0; i < countCandidates; i++)
		{
			indexA=rand.nextInt(countCandidates);
			indexB=rand.nextInt(countCandidates);
			
			aux=candidates[indexA];
			candidates[indexA]=candidates[indexB];
			candidates[indexB]=aux;
		}
	}
	
	public void applyPerturbation(Solution s){}
	
	protected void setSolution(Solution s)
	{
		this.numRoutes=s.getNumRoutes();
		this.routes=s.routes;
		this.solution=s.getSolution();
		this.f=s.f;
		for (int i = 0; i < numRoutes; i++) 
		{
			routes[i].modified=false;
			routes[i].first.modified=false;
		}
		
		for (int i = 0; i < size; i++)
			solution[i].modified=false;

		// Use externally-set insertion heuristic (from DecoupledAOS) if available
		// Otherwise, select randomly (legacy behavior)
		if (externallySetInsertionHeuristic != null) {
			selectedInsertionHeuristic = externallySetInsertionHeuristic;
			externallySetInsertionHeuristic = null;  // Reset for next iteration
		} else {
			indexHeuristic=rand.nextInt(insertionHeuristics.length);
			selectedInsertionHeuristic=insertionHeuristics[indexHeuristic];
		}

		chosenOmega=omegaSetup.get(perturbationType+"");
		omega=chosenOmega.getActualOmega();
		omega=Math.min(omega, size);

		countCandidates=0;
	}
	
	protected void assignSolution(Solution s)
	{
		s.f=f;
		s.numRoutes=this.numRoutes;
	}

	/**
	 * Set insertion heuristic externally (used by DecoupledAOS)
	 * This overrides the random selection in setSolution()
	 *
	 * @param heuristic Insertion heuristic to use for next perturbation
	 */
	public void setSelectedInsertionHeuristic(InsertionHeuristic heuristic) {
		this.externallySetInsertionHeuristic = heuristic;
	}

	/**
	 * Get the currently selected insertion heuristic
	 *
	 * @return Currently selected insertion heuristic
	 */
	public InsertionHeuristic getSelectedInsertionHeuristic() {
		return selectedInsertionHeuristic;
	}

	/**
	 * Record removed candidates for history tracking.
	 * This method is called after customers are reinserted to track removal patterns.
	 *
	 * By default, this records all removed customers for CriticalRemoval tracking.
	 * CriticalRemoval overrides this method to prevent self-reinforcement
	 * (i.e., CriticalRemoval does not record its own removals).
	 */
	protected void recordCandidates() {
		// Only record if AILSII instance is available and there are candidates
		if (ailsInstance != null && countCandidates > 0) {
			ailsInstance.recordRemovals(candidates, countCandidates);
		}
	}

	protected Node getNode(Node no)
	{
		switch(selectedInsertionHeuristic)
		{
			case Distance: return getBestKNNNo2(no,1);
			case Cost: return getBestKNNNo2(no,limitAdj);
		}
		return null;
	}
	
	protected Node getBestKNNNo2(Node no,int limit)
	{
		bestCost=Double.MAX_VALUE;
		boolean flag=false;
		bestNode=null;
		
		int count=0;
		flag=false;
		for (int i = 0; i < no.knn.length&&count<limit; i++) 
		{
			if(no.knn[i]==0)
			{
				for (int j = 0; j < numRoutes; j++) 
				{
					aux=routes[j].first;
					flag=true;
					cost=instance.dist(aux.name,no.name)+instance.dist(no.name,aux.next.name)-instance.dist(aux.name,aux.next.name);
					if(cost<bestCost)
					{
						bestCost=cost;
						bestNode=aux;
					}
				}
				if(flag)
					count++;
			}
			else
			{
				aux=solution[no.knn[i]-1];
				if(aux.nodeBelong)
				{
					count++;
					cost=instance.dist(aux.name,no.name)+instance.dist(no.name,aux.next.name)-instance.dist(aux.name,aux.next.name);
					if(cost<bestCost)
					{
						bestCost=cost;
						bestNode=aux;
					}
				}
			}
		}
		
		if(bestNode==null)
		{
			for (int i = 0; i < solution.length; i++) 
			{
				aux=solution[i];
				if(aux.nodeBelong)
				{
					cost=instance.dist(aux.name,no.name)+instance.dist(no.name,aux.next.name)-instance.dist(aux.name,aux.next.name);
					if(cost<bestCost)
					{
						bestCost=cost;
						bestNode=aux;
					}
				}
			}
		}
		
		if(bestNode==null)
		{
			for (int i = 0; i < solution.length; i++) 
			{
				aux=solution[i];
				if(aux.nodeBelong)
				{
					cost=instance.dist(aux.name,no.name)+instance.dist(no.name,aux.next.name)-instance.dist(aux.name,aux.next.name);
					if(cost<bestCost)
					{
						bestCost=cost;
						bestNode=aux;
					}
				}
			}
		}
		
		cost=instance.dist(bestNode.name,no.name)+instance.dist(no.name,bestNode.next.name)-instance.dist(bestNode.name,bestNode.next.name);
		costPrev=instance.dist(bestNode.prev.name,no.name)+instance.dist(no.name,bestNode.name)-instance.dist(bestNode.prev.name,bestNode.name);
		if(cost<costPrev)
		{
			return bestNode;
		}
		else
		{
			return bestNode.prev;
		}
	}
	
	public void addCandidates()
	{
		// Check if using regret-based insertion heuristic
		if (selectedInsertionHeuristic != null && selectedInsertionHeuristic.isRegret()) {
			// Regret-based insertion: insert all candidates using regret heuristic
			double costDelta = regretInsertion.insertWithRegret(
				solution, routes, numRoutes,
				candidates, countCandidates,
				selectedInsertionHeuristic
			);
			f += costDelta;
		} else {
			// Greedy insertion: insert candidates one-by-one using getNode()
			for (int i = 0; i < countCandidates; i++)
			{
				node=candidates[i];
				bestNode=getNode(node);

				f+=bestNode.route.addAfter(node, bestNode);
			}
		}

		// Record removals for history tracking (after reinsertion)
		recordCandidates();
	}
	
	public int getIndexHeuristic() {
		return indexHeuristic;
	}

	public OmegaAdjustment getChosenOmega() {
		return chosenOmega;
	}
	
	public PerturbationType getPerturbationType() {
		return perturbationType;
	}
	
}