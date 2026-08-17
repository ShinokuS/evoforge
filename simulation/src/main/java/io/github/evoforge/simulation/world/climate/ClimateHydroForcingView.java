package io.github.evoforge.simulation.world.climate;

import io.github.evoforge.simulation.world.atlas.HydroClimateField;
import io.github.evoforge.simulation.world.mechanics.geometry.CellVolumeRate;
import io.github.evoforge.simulation.world.spatial.WorldBounds;

/**
 * Narrow runtime-facing hydrologic projection of authoritative climate normals.
 *
 * <p>This view owns no generated state. Rain and evaporation use it only to consume the water
 * forcing dimensions they understand today, while the full climate field remains the durable
 * generated fact.</p>
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
        return climate.precipitationSupplyAt(x, y);
    }

    @Override
    public CellVolumeRate evaporativeDemandAt(int x, int y) {
        return climate.evaporativeDemandAt(x, y);
    }
}
