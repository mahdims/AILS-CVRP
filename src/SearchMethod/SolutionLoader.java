package SearchMethod;

import java.io.File;
import Data.Instance;
import Solution.Solution;
import Solution.Route;
import Solution.Node;

/**
 * Utility class for loading initial solutions from .sol files (warm start)
 */
public class SolutionLoader {

	/**
	 * Attempts to load an initial solution from a .sol file in the warm_start folder.
	 *
	 * Path construction:
	 * - Instance file: data/Vrp_Set_X/X-n101-k25.vrp
	 * - Solution file: warm_start/Vrp_Set_X/X-n101-k25.sol
	 *
	 * @param solution The Solution object to populate
	 * @param instance The Instance object containing problem information
	 * @param warmStartFolder The base folder for warm start files (typically "warm_start")
	 * @return true if solution was successfully loaded, false otherwise
	 */
	public static boolean loadSolution(Solution solution, Instance instance, String warmStartFolder) {
		try {
			// Get the instance file path (e.g., "data/Vrp_Set_X/X-n101-k25.vrp")
			String instancePath = instance.getName();

			// Extract the relevant parts from the path
			// We need to transform: data/Vrp_Set_X/X-n101-k25.vrp
			// into: warm_start/Vrp_Set_X/X-n101-k25.sol

			// Find the last path separator
			int lastSeparator = Math.max(instancePath.lastIndexOf('/'), instancePath.lastIndexOf('\\'));
			String filename = instancePath.substring(lastSeparator + 1);

			// Find the second-to-last path separator to get the dataset folder
			String pathBeforeFilename = instancePath.substring(0, lastSeparator);
			int secondLastSeparator = Math.max(pathBeforeFilename.lastIndexOf('/'),
											   pathBeforeFilename.lastIndexOf('\\'));
			String datasetFolder = pathBeforeFilename.substring(secondLastSeparator + 1);

			// Replace .vrp extension with .sol
			String solFilename = filename.replace(".vrp", ".sol");

			// Construct the warm start solution file path
			String solutionPath = warmStartFolder + File.separator + datasetFolder +
								 File.separator + solFilename;

			// Check if the file exists
			File solFile = new File(solutionPath);
			if (!solFile.exists()) {
				System.out.println("Warm start: Solution file not found at " + solutionPath);
				System.out.println("Falling back to constructive heuristic.");
				return false;
			}

			// Load the solution using the existing uploadSolution1 method
			System.out.println("Warm start: Loading solution from " + solutionPath);
			solution.uploadSolution1(solutionPath);

			// Set the number of routes based on what was loaded
			solution.numRoutes = countNonEmptyRoutes(solution);

			// First, mark all nodes as not belonging to any route
			Node[] solutionNodes = solution.getSolution();
			for (int i = 0; i < solutionNodes.length; i++) {
				solutionNodes[i].nodeBelong = false;
				solutionNodes[i].route = null;
			}

			// Now set proper route references for nodes that ARE in routes
			for (int r = 0; r < solution.numRoutes; r++) {
				Route route = solution.routes[r];
				Node current = route.first.next;
				while (current != null && current != route.first) {
					if (current.name > 0 && current.name <= solutionNodes.length) {
						// Set the node's route reference
						current.route = route;
						current.nodeBelong = true;
						// Also mark in the solution array
						solutionNodes[current.name - 1].nodeBelong = true;
						solutionNodes[current.name - 1].route = route;
					}
					current = current.next;
				}
			}

			System.out.println("Warm start: Successfully loaded solution with " + solution.numRoutes +
							   " routes, cost: " + solution.f);

			return true;

		} catch (Exception e) {
			System.err.println("Warm start: Error loading solution file: " + e.getMessage());
			System.out.println("Falling back to constructive heuristic.");
			return false;
		}
	}

	/**
	 * Count the number of non-empty routes in the solution
	 */
	private static int countNonEmptyRoutes(Solution solution) {
		int count = 0;
		for (int i = 0; i < solution.routes.length; i++) {
			// A route is non-empty if it has more than just the depot
			if (solution.routes[i].numElements > 1) {
				count++;
			}
		}
		return count;
	}
}
