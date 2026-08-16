package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.mechanics.geometry.CellVolumeRate;
import io.github.evoforge.simulation.world.spatial.WorldBounds;

/** Immutable long-term hydrologic climate normals by global XY world column. */
public interface HydroClimateField {
    WorldBounds bounds();

    CellVolumeRate precipitationSupplyAt(int x, int y);

    CellVolumeRate evaporativeDemandAt(int x, int y);

    default boolean contains(int x, int y) {
        WorldBounds bounds = bounds();
        return x >= bounds.minX() && x <= bounds.maxX()
                && y >= bounds.minY() && y <= bounds.maxY();
    }
}
