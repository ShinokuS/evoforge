package io.github.evoforge.simulation.world.climate;

import io.github.evoforge.simulation.world.atlas.HydroClimateField;
import io.github.evoforge.simulation.world.mechanics.geometry.CellVolumeRate;
import io.github.evoforge.simulation.world.spatial.WorldBounds;

/**
 * Narrow runtime-facing hydrologic projection of authoritative climate normals.
 *
 * <p>This view owns no generated state. It deliberately translates durable long-term normals into
 * the existing per-tick forcing protocol only when runtime composition chooses to install it. A
 * future WeatherState may replace this direct projection without changing ClimateNormals ownership.
 * </p>
 */
public final class ClimateHydroForcingView implements HydroClimateField {
    private final ClimateNormalsField climate;

    public ClimateHydroForcingView(ClimateNormalsField climate) {
        if (climate == null) {
            throw new IllegalArgumentException("climate normals must not be null");
        }
        this.climate = climate;
    }

    @Override
    public WorldBounds bounds() {
        return climate.bounds();
    }

    @Override
    public CellVolumeRate precipitationSupplyAt(int x, int y) {
        return climate.precipitationNormalAt(x, y);
    }

    @Override
    public CellVolumeRate evaporativeDemandAt(int x, int y) {
        return climate.evaporativeDemandNormalAt(x, y);
    }
}
