package Perturbation;

/**
 * SISR Recreate Ordering Strategies
 *
 * Based on: Christiaens & Vanden Berghe (2020), Transportation Science
 * Algorithm 3, Line 2: "Set A is sorted by random, demand, far, and close
 * by weights equal to four, four, two, and one"
 *
 * Probabilistic weights:
 * - RANDOM: 4/11 (36.4%)
 * - DEMAND: 4/11 (36.4%)
 * - FAR:    2/11 (18.2%)
 * - CLOSE:  1/11 (9.1%)
 */
public enum SISRRecreateOrder {
    RANDOM(0),   // Random order
    DEMAND(1),   // Largest demand first
    FAR(2),      // Farthest from depot first
    CLOSE(3);    // Closest to depot first

    final int type;

    SISRRecreateOrder(int type) {
        this.type = type;
    }
}
