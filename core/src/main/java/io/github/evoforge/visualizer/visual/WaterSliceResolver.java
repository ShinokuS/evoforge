package io.github.evoforge.visualizer.visual;

import io.github.evoforge.simulation.world.landscape.water.WaterLookup;
import io.github.evoforge.simulation.world.mechanics.geometry.CellSpace;
import io.github.evoforge.simulation.world.mechanics.geometry.GeometryLookup;
import io.github.evoforge.simulation.world.mechanics.geometry.Shape;

/**
 * Resolves the nearest Water layer visible through the selected horizontal cut.
 *
 * <p>The authoritative Water state remains XYZ. This helper owns no cached or
 * mutable liquid state; it only applies presentation cutaway rules to current
 * Water + Geometry facts.</p>
 */
public final class WaterSliceResolver {

    public static final int NO_WATER = Integer.MIN_VALUE;

    private final WaterLookup water;
    private final GeometryLookup geometry;

    public WaterSliceResolver(
            WaterLookup water,
            GeometryLookup geometry) {

        if (water == null || geometry == null) {
            throw new IllegalArgumentException(
                    "water slice dependencies must not be null");
        }
        this.water = water;
        this.geometry = geometry;
    }

    /**
     * Returns the first wet cell at or below {@code selectedStandingZ} that is
     * visible through open/partial cell space, or {@link #NO_WATER}.
     */
    public int resolve(
            int x,
            int y,
            int selectedStandingZ,
            int maxLowerDepth) {

        if (maxLowerDepth < 0) {
            throw new IllegalArgumentException(
                    "maxLowerDepth must not be negative");
        }

        for (int depth = 0; depth <= maxLowerDepth; depth++) {
            int z = safeSubtract(selectedStandingZ, depth);
            Shape shape = geometry.find(x, y, z);
            int capacity = CellSpace.capacity(shape);

            if (water.amount(x, y, z) > 0 && capacity > 0) {
                return z;
            }

            // A completely solid cell closes the vertical cutaway. Water below
            // it must not leak visually through terrain/roof geometry.
            if (capacity == 0) {
                return NO_WATER;
            }
        }

        return NO_WATER;
    }

    private static int safeSubtract(
            int value,
            int delta) {

        long result = (long) value - delta;
        return result < Integer.MIN_VALUE
                ? Integer.MIN_VALUE
                : (int) result;
    }
}
