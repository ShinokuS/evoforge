package io.github.evoforge.simulation.world.climate;

import io.github.evoforge.simulation.world.mechanics.geometry.CellVolumeRate;
import io.github.evoforge.simulation.world.spatial.WorldBounds;

/**
 * Durable long-term climate facts by global XY column.
 *
 * <p>This is generated world state, not tick weather. Hydrologic rates describe atmospheric
 * normals from which runtime forcing may be projected; temperature is a physical climate fact for
 * later soil/ecology/biome consumers.</p>
 */
public interface ClimateNormalsField {
    WorldBounds bounds();

    ClimateTemperature meanTemperatureAt(int x, int y);

    CellVolumeRate precipitationSupplyAt(int x, int y);

    CellVolumeRate evaporativeDemandAt(int x, int y);

    default boolean contains(int x, int y) {
        WorldBounds bounds = bounds();
        return x >= bounds.minX() && x <= bounds.maxX()
                && y >= bounds.minY() && y <= bounds.maxY();
    }
}
