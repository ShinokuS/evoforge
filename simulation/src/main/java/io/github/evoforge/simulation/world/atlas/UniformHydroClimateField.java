package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.mechanics.geometry.CellVolumeRate;
import io.github.evoforge.simulation.world.spatial.WorldBounds;

final class UniformHydroClimateField implements HydroClimateField {
    private final WorldBounds bounds;
    private final CellVolumeRate precipitationSupply;
    private final CellVolumeRate evaporativeDemand;

    UniformHydroClimateField(
            WorldBounds bounds,
            CellVolumeRate precipitationSupply,
            CellVolumeRate evaporativeDemand) {
        if (bounds == null) {
            throw new IllegalArgumentException("bounds must not be null");
        }
        if (precipitationSupply == null || evaporativeDemand == null) {
            throw new IllegalArgumentException("hydro-climate rates must not be null");
        }
        this.bounds = bounds;
        this.precipitationSupply = precipitationSupply;
        this.evaporativeDemand = evaporativeDemand;
    }

    @Override
    public WorldBounds bounds() {
        return bounds;
    }

    @Override
    public CellVolumeRate precipitationSupplyAt(int x, int y) {
        requireInside(x, y);
        return precipitationSupply;
    }

    @Override
    public CellVolumeRate evaporativeDemandAt(int x, int y) {
        requireInside(x, y);
        return evaporativeDemand;
    }

    private void requireInside(int x, int y) {
        if (!contains(x, y)) {
            throw new IllegalArgumentException(
                    "position outside hydro-climate field: (" + x + ", " + y + ")");
        }
    }
}
