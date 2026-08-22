package io.github.evoforge.simulation.world.navigation.traversal;

public record SurfaceTraversalCost(long units) {

    public static final long NEUTRAL_UNITS = 1000;

    public SurfaceTraversalCost {
        if (units <= 0) {
            throw new IllegalArgumentException(
                    "surface traversal cost must be > 0");
        }
    }

    public static SurfaceTraversalCost of(
            long units) {

        return new SurfaceTraversalCost(units);
    }

    public static SurfaceTraversalCost neutral() {
        return new SurfaceTraversalCost(NEUTRAL_UNITS);
    }
}
