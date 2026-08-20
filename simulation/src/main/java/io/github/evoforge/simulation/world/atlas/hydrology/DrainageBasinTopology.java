package io.github.evoforge.simulation.world.atlas.hydrology;

import io.github.evoforge.simulation.world.spatial.WorldBounds;

/** Immutable closed-depression topology derived from final terrain elevation. */
public interface DrainageBasinTopology {
    int NO_BASIN = -1;

    WorldBounds bounds();

    int basinCount();

    /** Returns {@link #NO_BASIN} where the terrain drains without depression filling. */
    int basinIdAt(int x, int y);

    DrainageBasin basin(int id);

    default boolean isBasinAt(int x, int y) {
        return basinIdAt(x, y) != NO_BASIN;
    }

    default boolean contains(int x, int y) {
        WorldBounds bounds = bounds();
        return x >= bounds.minX() && x <= bounds.maxX()
                && y >= bounds.minY() && y <= bounds.maxY();
    }
}
