package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.spatial.WorldBounds;

/** Immutable read contract for generated surface elevation by global world column. */
public interface ElevationField {
    long SUBUNITS_PER_CELL = 1_000_000L;

    WorldBounds bounds();

    /** Discrete surface-cell Z used by terrain materialization. */
    int elevationAt(int x, int y);

    /**
     * Precise generated elevation used by macro world facts such as drainage.
     *
     * <p>The default preserves compatibility for substitute algorithms that only
     * author discrete cell elevation. Precision-aware algorithms override it.
     */
    default long elevationSubunitsAt(int x, int y) {
        return Math.multiplyExact((long) elevationAt(x, y), SUBUNITS_PER_CELL);
    }

    default boolean contains(int x, int y) {
        WorldBounds bounds = bounds();
        return x >= bounds.minX() && x <= bounds.maxX()
                && y >= bounds.minY() && y <= bounds.maxY();
    }
}
