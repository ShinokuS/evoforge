package io.github.evoforge.simulation.world.geometry;

public final class ShapeTraversalFactor {

    public static final int NONE = 0;
    public static final int SCALE = 1000;
    public static final int NEUTRAL = SCALE;

    private ShapeTraversalFactor() {
    }

    public static int requirePositive(
            int factor) {

        if (factor <= NONE) {
            throw new IllegalArgumentException(
                    "traversal factor must be > 0");
        }

        return factor;
    }
}
