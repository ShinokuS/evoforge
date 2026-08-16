package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.spatial.WorldBounds;

/** Immutable generated surface-water initial conditions and shoreline relation by XY column. */
public interface SurfaceHydrologyField {
    WorldBounds bounds();

    /** Initial finite free-Water volume to place directly above the generated terrain surface. */
    int initialWaterVolumeAt(int x, int y);

    /** Whether this dry column is directly adjacent to generated initial surface Water. */
    boolean isShoreline(int x, int y);

    default boolean isInitiallyWet(int x, int y) {
        return initialWaterVolumeAt(x, y) > 0;
    }

    default boolean contains(int x, int y) {
        WorldBounds bounds = bounds();
        return x >= bounds.minX() && x <= bounds.maxX()
                && y >= bounds.minY() && y <= bounds.maxY();
    }
}
