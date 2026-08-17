package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.spatial.WorldBounds;

/**
 * Durable generated hydrographic structure by global XY column.
 *
 * <p>This field describes the generated channel network, not mutable runtime Water and not the
 * finite initial Water volume placed at tick zero. Runtime liquids remain authoritative after
 * materialization.</p>
 */
public interface HydrographyField {
    WorldBounds bounds();

    /** Whether the generated hydrographic model identifies this column as part of a channel. */
    boolean isChannelAt(int x, int y);

    default boolean contains(int x, int y) {
        WorldBounds bounds = bounds();
        return x >= bounds.minX() && x <= bounds.maxX()
                && y >= bounds.minY() && y <= bounds.maxY();
    }
}
