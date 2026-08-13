package io.github.evoforge.simulation.world.pathfinding;

public final class PathHeuristics {

    public static final PathHeuristic ZERO =
            (x, y, z, goalX, goalY, goalZ) -> 0L;

    private PathHeuristics() {
    }

    /**
     * Admissible for 26-neighbor movement when every edge costs at least the supplied value.
     */
    public static PathHeuristic chebyshev(
            long minimumEdgeCost) {

        if (minimumEdgeCost <= 0) {
            throw new IllegalArgumentException(
                    "minimumEdgeCost must be > 0");
        }

        return (x, y, z, goalX, goalY, goalZ) -> {
            long dx = Math.abs((long) goalX - x);
            long dy = Math.abs((long) goalY - y);
            long dz = Math.abs((long) goalZ - z);
            long steps = Math.max(dx, Math.max(dy, dz));
            return Math.multiplyExact(steps, minimumEdgeCost);
        };
    }
}
