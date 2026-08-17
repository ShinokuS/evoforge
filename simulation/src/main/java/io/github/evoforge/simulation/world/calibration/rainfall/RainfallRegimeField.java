package io.github.evoforge.simulation.world.calibration.rainfall;

import io.github.evoforge.simulation.world.spatial.WorldBounds;

/** Immutable preparation-time rainfall regime by global XY column. */
public interface RainfallRegimeField {
    WorldBounds bounds();
    RainfallRegime at(int x, int y);

    default boolean contains(int x, int y) {
        WorldBounds bounds = bounds();
        return x >= bounds.minX() && x <= bounds.maxX()
                && y >= bounds.minY() && y <= bounds.maxY();
    }
}
