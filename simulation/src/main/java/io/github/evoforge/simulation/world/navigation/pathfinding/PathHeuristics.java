package io.github.evoforge.simulation.world.navigation.pathfinding;

import io.github.evoforge.simulation.world.navigation.traversal.TransitionCostLowerBoundLookup;

public final class PathHeuristics {

    public static final PathHeuristic ZERO =
            (x, y, z, goalX, goalY, goalZ) -> 0L;

    private PathHeuristics() {
    }

    /** Admissible for 26-neighbor movement when every edge costs at least the supplied value. */
    public static PathHeuristic chebyshev(
            long minimumEdgeCost) {

        if (minimumEdgeCost <= 0) {
            throw new IllegalArgumentException(
                    "minimumEdgeCost must be > 0");
        }

        return chebyshev(() -> minimumEdgeCost);
    }

    /** Reads the current traversal-domain lower bound for every estimate. */
    public static PathHeuristic chebyshev(
            TransitionCostLowerBoundLookup lowerBound) {

        if (lowerBound == null) {
            throw new IllegalArgumentException(
                    "lowerBound must not be null");
        }

        return (x, y, z, goalX, goalY, goalZ) -> {
            long minimumEdgeCost = lowerBound.minimumEdgeCostUnits();
            if (minimumEdgeCost <= 0) {
                throw new IllegalStateException(
                        "transition lower bound must be > 0");
            }

            long dx = Math.abs((long) goalX - x);
            long dy = Math.abs((long) goalY - y);
            long dz = Math.abs((long) goalZ - z);
            long steps = Math.max(dx, Math.max(dy, dz));
            return Math.multiplyExact(steps, minimumEdgeCost);
        };
    }
}
