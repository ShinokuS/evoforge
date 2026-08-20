package io.github.evoforge.simulation.world.atlas.hydrology;

import io.github.evoforge.simulation.world.spatial.WorldBounds;

/** Immutable generated inland-lake membership with per-body water-surface elevation. */
public interface InlandLakeTopology {
    int NO_LAKE = -1;

    WorldBounds bounds();

    int lakeCount();

    int lakeIdAt(int x, int y);

    InlandLake lake(int id);

    default boolean isLakeAt(int x, int y) {
        return lakeIdAt(x, y) != NO_LAKE;
    }

    default long surfaceElevationSubunitsAt(int x, int y) {
        int lakeId = lakeIdAt(x, y);
        if (lakeId == NO_LAKE) {
            throw new IllegalArgumentException("coordinate is not generated inland water");
        }
        return lake(lakeId).surfaceElevationSubunits();
    }

    default boolean contains(int x, int y) {
        WorldBounds bounds = bounds();
        return x >= bounds.minX() && x <= bounds.maxX()
                && y >= bounds.minY() && y <= bounds.maxY();
    }
}
