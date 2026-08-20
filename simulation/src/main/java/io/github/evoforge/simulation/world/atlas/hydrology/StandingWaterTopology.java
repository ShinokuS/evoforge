package io.github.evoforge.simulation.world.atlas.hydrology;

import io.github.evoforge.simulation.world.spatial.WorldBounds;

/** Immutable standing-water component topology over accepted generated elevation. */
public interface StandingWaterTopology {
    int NO_BODY = -1;

    WorldBounds bounds();

    int bodyCount();

    /** Returns {@link #NO_BODY} for land/non-standing-water columns. */
    int bodyIdAt(int x, int y);

    StandingWaterBody body(int id);

    default boolean isStandingWaterAt(int x, int y) {
        return bodyIdAt(x, y) != NO_BODY;
    }

    default boolean contains(int x, int y) {
        WorldBounds bounds = bounds();
        return x >= bounds.minX() && x <= bounds.maxX()
                && y >= bounds.minY() && y <= bounds.maxY();
    }
}
