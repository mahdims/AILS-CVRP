package Solution;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import Data.File;
import Data.Instance;
import Data.Point;
import Improvement.IntraLocalSearch;
import Perturbation.SISR;
import Perturbation.SISRConfig;
import SearchMethod.Config;

public class Solution
{
	private Point points[];
	Instance instance;
	public Config config;
	protected int size;
	Node solution[];

	protected int first;
	protected Node depot;
	int capacity;
	public Route routes[];
	public int numRoutes;
	protected int numRoutesMin;
	protected int numRoutesMax;
	public double f = 0;
	public int distance;
	double epsilon;
//	-----------Comparadores-----------

	IntraLocalSearch intraLocalSearch;

	// Fleet minimization: list of absent customer IDs (not currently in any route)
	private List<Integer> sisrAbsent;

	public Solution(Instance instance, Config config)
	{
		this.instance = instance;
		this.config = config;
		this.points = instance.getPoints();
		int depot = instance.getDepot();
		this.capacity = instance.getCapacity();
		this.size = instance.getSize() - 1;
		this.solution = new Node[size];
		this.numRoutesMin = instance.getMinNumberRoutes();
		this.numRoutes = numRoutesMin;
		this.numRoutesMax = instance.getMaxNumberRoutes();
		this.depot = new Node(points[depot], instance);
		this.epsilon = config.getEpsilon();
		this.sisrAbsent = new ArrayList<>();

		this.routes = new Route[numRoutesMax];

		for(int i = 0; i < routes.length; i++)
			routes[i] = new Route(instance, config, this.depot, i);

		int count = 0;
		for(int i = 0; i < (solution.length + 1); i++)
		{
			if(i != depot)
			{
				solution[count] = new Node(points[i], instance);
				count++;
			}
		}
	}

	public void clone(Solution reference)
	{
		this.numRoutes = reference.numRoutes;
		this.f = reference.f;

		// Clone sisrAbsent list
		this.sisrAbsent.clear();
		this.sisrAbsent.addAll(reference.sisrAbsent);

		for(int i = 0; i < routes.length; i++)
		{
			routes[i].nameRoute = i;
			reference.routes[i].nameRoute = i;
		}

		for(int i = 0; i < routes.length; i++)
		{
			routes[i].totalDemand = reference.routes[i].totalDemand;
			routes[i].fRoute = reference.routes[i].fRoute;
			routes[i].numElements = reference.routes[i].numElements;
			routes[i].modified = reference.routes[i].modified;

			if(reference.routes[i].first.prev == null)
				routes[i].first.prev = null;
			else if(reference.routes[i].first.prev.name == 0)
				routes[i].first.prev = routes[i].first;
			else
				routes[i].first.prev = solution[reference.routes[i].first.prev.name - 1];

			if(reference.routes[i].first.next == null)
				routes[i].first.next = null;
			else if(reference.routes[i].first.next.name == 0)
				routes[i].first.next = routes[i].first;
			else
				routes[i].first.next = solution[reference.routes[i].first.next.name - 1];
		}

		for(int i = 0; i < solution.length; i++)
		{
			solution[i].nodeBelong = reference.solution[i].nodeBelong;

			// Handle nodes that are not in any route (absent customers in fleet minimization)
			if (reference.solution[i].route != null)
			{
				solution[i].route = routes[reference.solution[i].route.nameRoute];

				if(reference.solution[i].prev.name == 0)
					solution[i].prev = routes[reference.solution[i].prev.route.nameRoute].first;
				else
					solution[i].prev = solution[reference.solution[i].prev.name - 1];

				if(reference.solution[i].next.name == 0)
					solution[i].next = routes[reference.solution[i].next.route.nameRoute].first;
				else
					solution[i].next = solution[reference.solution[i].next.name - 1];
			}
			else
			{
				// Node is not in any route (absent customer)
				solution[i].route = null;
				solution[i].prev = null;
				solution[i].next = null;
			}
		}
	}

	// ------------------------Visualizacao-------------------------

	public String toStringMeu()
	{
		String str = "size: " + size;
		str += "\n" + "depot: " + depot;
		str += "\nnumRoutes: " + numRoutes;
		str += "\ncapacity: " + capacity;

		str += "\nf: " + f;
//		System.out.println(str);
		for(int i = 0; i < numRoutes; i++)
		{
//			System.out.println(str);
			str += "\n" + routes[i];
		}

		return str;
	}

	@Override
	public String toString()
	{
		String str = "";
		for(int i = 0; i < numRoutes; i++)
		{
			str += routes[i].toString2() + "\n";
		}
		str += "Cost " + f + "\n";
		return str;
	}

	public int infeasibility()
	{
		int capViolation = 0;
		for(int i = 0; i < numRoutes; i++)
		{
			if(routes[i].availableCapacity() < 0)
				capViolation += routes[i].availableCapacity();
		}
		return capViolation;
	}

	public boolean checking(String local, boolean feasibility, boolean emptyRoute)
	{
		double f;
		double sumF = 0;
		int sumNumElements = 0;
		boolean erro = false;

		for(int i = 0; i < numRoutes; i++)
		{
			routes[i].findError();
			f = routes[i].F();
			sumF += f;
			sumNumElements += routes[i].numElements;

			if(Math.abs(f - routes[i].fRoute) > epsilon)
			{
				System.out.println("-------------------" + local + " ERROR-------------------" + "\n" + routes[i].toString() + "\nf esperado: " + f);
				erro = true;
			}

			if(emptyRoute && routes[i].first == routes[i].first.next)
			{
				System.out.println("-------------------" + local + " ERROR-------------------" + "Empty route: " + routes[i].toString());
				erro = true;
			}

			if(routes[i].first.name != 0)
			{
				System.out.println("-------------------" + local + " ERROR-------------------" + " Route initiating without depot: " + routes[i].toString());
				erro = true;
			}

			if(feasibility && !routes[i].isFeasible())
			{
				System.out.println("-------------------" + local + " ERROR-------------------" + "Infeasible route: " + routes[i].toString());
				erro = true;
			}

		}
		if(Math.abs(sumF - this.f) > epsilon)
		{
			erro = true;
			System.out.println("-------------------" + local + " Error total sum-------------------");
			System.out.println("Expected: " + sumF + " obtained: " + this.f);
			System.out.println(this.toStringMeu());
		}

		if((sumNumElements - numRoutes) != size)
		{
			erro = true;
			System.out.println("-------------------" + local + " ERROR quantity of Elements-------------------");
			System.out.println("Expected: " + size + " obtained : " + (sumNumElements - numRoutes));

			System.out.println(this);
		}
		return erro;
	}

	public boolean feasible()
	{
		for(int i = 0; i < numRoutes; i++)
		{
			if(routes[i].availableCapacity() < 0)
				return false;
		}
		return true;
	}

	public void removeEmptyRoutes()
	{
		for(int i = 0; i < numRoutes; i++)
		{
			if(routes[i].first == routes[i].first.next)
			{
				removeRoute(i);
				i--;
			}
		}
	}

	private void removeRoute(int index)
	{
		Route aux = routes[index];
		if(index != numRoutes - 1)
		{
			routes[index] = routes[numRoutes - 1];

			routes[numRoutes - 1] = aux;
		}
		numRoutes--;
	}

	public void uploadSolution(String name)
	{
		BufferedReader in;
		try
		{
			in = new BufferedReader(new FileReader(name));
			String str[] = null;
			String line;

			line = in.readLine();
			str = line.split(" ");

			for(int i = 0; i < 3; i++)
				in.readLine();

			int indexRoute = 0;
			line = in.readLine();
			str = line.split(" ");

			System.out.println("-------------- str.length: " + str.length);
			for(int i = 0; i < str.length; i++)
			{
				System.out.print(str[i] + "-");
			}
			System.out.println();

			do
			{
				routes[indexRoute].addNodeEndRoute(depot.clone());
				for(int i = 9; i < str.length - 1; i++)
				{
					System.out.println("add: " + solution[Integer.valueOf(str[i].trim()) - 1] + " na route: " + routes[indexRoute].nameRoute);
					f += routes[indexRoute].addNodeEndRoute(solution[Integer.valueOf(str[i]) - 1]);
				}
				indexRoute++;
				line = in.readLine();
				if(line != null)
					str = line.split(" ");
			}
			while(line != null);

		}
		catch(IOException e)
		{
			System.out.println("File read Error");
		}
	}

	public void uploadSolution1(String name)
	{
		BufferedReader in;
		try
		{
			in = new BufferedReader(new FileReader(name));
			String str[] = null;

			str = in.readLine().split(" ");
			int indexRoute = 0;
			while(!str[0].equals("Cost"))
			{
				for(int i = 2; i < str.length; i++)
				{
					f += routes[indexRoute].addNodeEndRoute(solution[Integer.valueOf(str[i]) - 1]);
				}
				indexRoute++;
				str = in.readLine().split(" ");
			}
		}
		catch(IOException e)
		{
			System.out.println("File read Error");
		}
	}

	public Route[] getRoutes()
	{
		return routes;
	}

	public int getNumRoutes()
	{
		return numRoutes;
	}

	public Node getDepot()
	{
		return depot;
	}

	public Node[] getSolution()
	{
		return solution;
	}

	public int getNumRoutesMax()
	{
		return numRoutesMax;
	}

	public void setNumRoutesMax(int numRoutesMax)
	{
		this.numRoutesMax = numRoutesMax;
	}

	public int getNumRoutesMin()
	{
		return numRoutesMin;
	}

	public void setNumRoutesMin(int numRoutesMin)
	{
		this.numRoutesMin = numRoutesMin;
	}

	public int getSize()
	{
		return size;
	}

	public void printSolution(String end)
	{
		File arq = new File(end);
		arq.write(this.toString());
		arq.close();
	}

	// ========== FLEET MINIMIZATION METHODS (Algorithm 4) ========== 

	/**
	 * Fleet minimization using SISR-based ruin-recreate
	 * Translated from C++ Algorithm 4: fleetMinimisationSISR
	 * Uses real SISR implementation from SISR.java
	 *
	 * @param sisrOperator SISR operator instance to use for ruin/recreate
	 * @param maxIterations Maximum iterations for fleet minimization
	 * @return Best solution with minimum routes
	 */
	public Solution fleetMinimisationSISR(SISR sisrOperator, int maxIterations)
	{
		int initialRoutes = numRoutes;

		// Line 2: s^best <- s
		Solution sBest = new Solution(instance, config);
		sBest.clone(this);
		int minRoutes = sBest.numRoutes;

		// Lines 3-4: Initialize absence counters to 0 for all customers
		for (int c = 0; c < size; c++)
		{
			solution[c].absenceCounter = 0;
		}

		// Line 5: while minimising tours do
		Solution s = new Solution(instance, config);
		s.clone(this);

		for (int iter = 0; iter < maxIterations; iter++)
		{
			// Line 6: s* <- SISRs-RUIN-RECREATE(s)
			Solution sStar = new Solution(instance, config);
			sStar.clone(s);

			// Apply SISR ruin using real SISR implementation
			sisrOperator.applyRuinOnly(sStar);

			// Apply SISR recreate if there are absent customers
			if (sisrOperator.hasAbsentCustomers())
			{
				sisrOperator.applyRecreateOnly(sStar);
			}

			// Get absent sets using real SISR implementation
			List<Integer> A = s.getSisrAbsent();
			List<Integer> AStar = sisrOperator.getAbsentCustomerIds();

			// Line 7: if |A*| < |A| or sumAbs(A*) < sumAbs(A) then
			int sumAbsA = s.calculateAbsentCustomersSumAbs(A);
			int sumAbsAStar = s.calculateAbsentCustomersSumAbs(AStar);

			if (AStar.size() < A.size() || sumAbsAStar < sumAbsA)
			{
				// Line 8: s <- s*
				s.clone(sStar);
			}

			// Line 9: if A* = empty set then (feasible solution - all customers are in routes)
			if (AStar.isEmpty())
			{
				// Line 10: Update s^best only if fewer routes or same routes with better fitness
				if (sStar.numRoutes < minRoutes ||
					(sStar.numRoutes == minRoutes && sStar.f < sBest.f))
				{
					sBest.clone(sStar);
					minRoutes = sBest.numRoutes;
				}

				// Line 11: remove t in T with the lowest sumAbs(t)
				// Only remove routes if we have more than the minimum
				if (s.numRoutes > s.numRoutesMin)
				{
					int routeToRemove = s.findRouteWithLowestSumAbs();
					if (routeToRemove >= 0)
					{
						s.removeRouteToAbsent(routeToRemove);
					}
					else
					{
						// No more routes to remove
						break;
					}
				}
				else
				{
					// Already at minimum routes, stop
					break;
				}
			}

			// Lines 12-13: for c in A* do: abs_c <- abs_c + 1
			for (int custId : AStar)
			{
				if (custId > 0 && custId <= size)
				{
					solution[custId - 1].absenceCounter++;
				}
			}
		}

		// Verify all customers are properly in routes by direct count
		int customersInRoutes = 0;
		for (int r = 0; r < sBest.numRoutes; r++)
		{
			Node current = sBest.routes[r].first.next;
			while (current != sBest.routes[r].first)
			{
				if (current.name > 0)
				{
					customersInRoutes++;
				}
				current = current.next;
			}
		}

		boolean allCustomersInRoutes = (customersInRoutes == size);

		// If not all customers are in routes, fleet minimization failed - return original
		if (!allCustomersInRoutes)
		{
			System.out.println("Fleet Minimization failed to produce valid solution (missing customers), keeping original");
			return this;
		}

		// Ensure sisrAbsent is clear
		sBest.sisrAbsent.clear();

		// Log summary only if routes were reduced
		if (sBest.numRoutes < initialRoutes)
		{
			System.out.println("Fleet Minimization: Reduced routes from " + initialRoutes + " to " + sBest.numRoutes);
		}

		return sBest;
	}

	/**
	 * Get list of absent customer IDs (SISR ruin phase)
	 */
	public List<Integer> getSisrAbsent()
	{
		return sisrAbsent;
	}

	/**
	 * Set list of absent customer IDs
	 */
	public void setSisrAbsent(List<Integer> absent)
	{
		this.sisrAbsent = absent;
	}

	/**
	 * Apply SISR ruin phase - remove customers from routes
	 * Simplified version that removes random customers from random routes
	 */
	public void applySISRRuin(SISRConfig sisrConfig)
	{
		sisrAbsent.clear();

		// Calculate number of customers to remove (based on avgRemovedPercent)
		int numToRemove = Math.max(1, (int)(size * sisrConfig.getAvgRemovedPercent()));

		// Select random customers to remove
		List<Integer> candidates = new ArrayList<>();
		for (int i = 0; i < size; i++)
		{
			if (solution[i].nodeBelong)
			{
				candidates.add(i);
			}
		}

		// Shuffle and remove
		java.util.Collections.shuffle(candidates);
		int removed = 0;

		for (int i = 0; i < candidates.size() && removed < numToRemove; i++)
		{
			int idx = candidates.get(i);
			Node node = solution[idx];

			if (node.nodeBelong && node.route != null)
			{
				// Remove from route
				f += node.route.remove(node);
				sisrAbsent.add(node.name); // Store customer ID, not Node
				removed++;
			}
		}
	}

	/**
	 * Apply SISR recreate phase - reinsert absent customers
	 */
	public void applySISRRecreate(SISRConfig sisrConfig)
	{
		if (sisrAbsent.isEmpty()) return;

		// Copy to working list
		List<Integer> pending = new ArrayList<>(sisrAbsent);
		sisrAbsent.clear();

		// Shuffle for random insertion order
		java.util.Collections.shuffle(pending);

		// Try to reinsert each customer
		for (Integer custId : pending)
		{
			if (custId <= 0 || custId > size) continue;

			Node customer = solution[custId - 1];
			double bestCost = Double.MAX_VALUE;
			Route bestRoute = null;
			Node bestInsertAfter = null;
			boolean foundFeasible = false;

			// First pass: try to find feasible insertion (respecting capacity)
			for (int r = 0; r < numRoutes; r++)
			{
				Route route = routes[r];

				// Check capacity
				if (route.totalDemand + customer.demand > capacity)
				{
					continue;
				}

				// Try all positions in route
				Node current = route.first;
				do
				{
					double deltaCost = instance.dist(current.name, customer.name) +
									  instance.dist(customer.name, current.next.name) -
									  instance.dist(current.name, current.next.name);

					if (deltaCost < bestCost)
					{
						bestCost = deltaCost;
						bestRoute = route;
						bestInsertAfter = current;
						foundFeasible = true;
					}

					current = current.next;
				} while (current != route.first);
			}

			// If no feasible position found, try creating new route
			if (!foundFeasible && numRoutes < numRoutesMax)
			{
				// Create new route with this customer
				Route newRoute = routes[numRoutes];
				newRoute.totalDemand = 0;
				newRoute.fRoute = 0;
				newRoute.numElements = 1; // depot only
				newRoute.first.next = newRoute.first;
				newRoute.first.prev = newRoute.first;

				f += newRoute.addAfter(customer, newRoute.first);
				numRoutes++;
				foundFeasible = true;
			}
			else if (foundFeasible && bestRoute != null)
			{
				// Insert at best position
				f += bestRoute.addAfter(customer, bestInsertAfter);
			}
			else
			{
				// No feasible position and can't create new route - add back to absent list
				sisrAbsent.add(custId);
			}
		}
	}

	/**
	 * Calculate sum of absence counters for given customers
	 */
	public int calculateAbsentCustomersSumAbs(List<Integer> absentIds)
	{
		int sum = 0;
		for (int custId : absentIds)
		{
			if (custId > 0 && custId <= size)
			{
				sum += solution[custId - 1].absenceCounter;
			}
		}
		return sum;
	}

	/**
	 * Find route with lowest sum of absence counters
	 * Returns route index, or -1 if no routes available
	 * Matches C++ implementation
	 */
	public int findRouteWithLowestSumAbs()
	{
		if (numRoutes == 0) return -1;

		int bestRouteIdx = -1;
		int lowestSum = Integer.MAX_VALUE;

		for (int r = 0; r < numRoutes; r++)
		{
			// Skip empty routes (numElements=1 means only depot)
			if (routes[r].numElements <= 1)
				continue;

			int sum = routes[r].calculateSumAbsenceCounters();
			if (sum < lowestSum)
			{
				lowestSum = sum;
				bestRouteIdx = r;
			}
		}

		return bestRouteIdx;
	}

	/**
	 * Remove a route and add all its customers to absent list
	 * Matches C++ implementation: adds customer IDs to sisrAbsent
	 */
	public void removeRouteToAbsent(int routeIndex)
	{
		if (routeIndex < 0 || routeIndex >= numRoutes) return;

		Route route = routes[routeIndex];

		// Collect all customer nodes from this route
		List<Node> customersToRemove = new ArrayList<>();
		Node current = route.first.next;
		while (current != route.first)
		{
			if (current.name > 0)
			{
				customersToRemove.add(current);
			}
			current = current.next;
		}

		// Properly remove each node from the route (sets nodeBelong=false, updates pointers)
		for (Node customer : customersToRemove)
		{
			f += route.remove(customer);
			sisrAbsent.add(customer.name); // Add customer ID to absent list
		}

		// Remove the now-empty route (equivalent to C++ routes.erase)
		removeRoute(routeIndex);

		// Recalculate fitness (equivalent to C++ cal_fitness())
		f = 0;
		for (int r = 0; r < numRoutes; r++)
		{
			f += routes[r].fRoute;
		}
	}

}