package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.spatial.WorldBounds;

/** Immutable read contract for generated surface elevation by global world column. */
public interface ElevationField {
    WorldBounds bounds();

    int elevationAt(int x, int y);

    default boolean contains(int x, int y) {
        WorldBounds bounds = bounds();
        return x >= bounds.minX() && x <= bounds.maxX()
                && y >= bounds.minY() && y <= bounds.maxY();
    }
}
