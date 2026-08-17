package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.mechanics.geometry.CellVolumeRate;
import io.github.evoforge.simulation.world.spatial.WorldBounds;

/**
 * Narrow runtime atmospheric-water forcing contract by global XY column.
 *
 * <p>This interface is not an independent generated climate owner. Generated worlds project their
 * authoritative ClimateNormalsField into this contract so the existing rain and evaporation
 * systems consume only the dimensions they currently understand.</p>
 */
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
