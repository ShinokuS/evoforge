package io.github.evoforge.simulation.world.climate;

import io.github.evoforge.simulation.world.mechanics.geometry.CellVolumeRate;
import io.github.evoforge.simulation.world.spatial.WorldBounds;

/**
 * Durable long-term climate facts by global XY column.
 *
 * <p>This is generated world state, not tick weather and not an instruction to run atmospheric
 * forcing. Water normals are durable climate facts from which a runtime atmospheric model may
 * later derive actual rain and evaporation; temperature is a physical climate fact for later
 * soil/ecology/biome consumers.</p>
 */
public interface ClimateNormalsField {
    WorldBounds bounds();

    ClimateTemperature meanTemperatureAt(int x, int y);

    CellVolumeRate precipitationNormalAt(int x, int y);

    CellVolumeRate evaporativeDemandNormalAt(int x, int y);

    /** @deprecated Supply is a runtime-forcing concept. Use {@link #precipitationNormalAt(int, int)}. */
    @Deprecated(forRemoval = true)
    default CellVolumeRate precipitationSupplyAt(int x, int y) {
        return precipitationNormalAt(x, y);
    }

    /** @deprecated Use {@link #evaporativeDemandNormalAt(int, int)} for the durable climate fact. */
    @Deprecated(forRemoval = true)
    default CellVolumeRate evaporativeDemandAt(int x, int y) {
        return evaporativeDemandNormalAt(x, y);
    }

    default boolean contains(int x, int y) {
        WorldBounds bounds = bounds();
        return x >= bounds.minX() && x <= bounds.maxX()
                && y >= bounds.minY() && y <= bounds.maxY();
    }
}
