package io.github.evoforge.simulation.world.genesis;

import io.github.evoforge.simulation.world.mechanics.geometry.CellVolumeRate;

/** Requested long-term hydrologic climate forcing before world generation begins. */
public record HydroClimateSpec(
        CellVolumeRate precipitationSupply,
        CellVolumeRate evaporativeDemand) {

    public static final HydroClimateSpec UNFORCED = new HydroClimateSpec(
            CellVolumeRate.ZERO,
            CellVolumeRate.ZERO);

    public HydroClimateSpec {
        if (precipitationSupply == null) {
            throw new IllegalArgumentException("precipitationSupply must not be null");
        }
        if (evaporativeDemand == null) {
            throw new IllegalArgumentException("evaporativeDemand must not be null");
        }
    }

    public static HydroClimateSpec of(
            CellVolumeRate precipitationSupply,
            CellVolumeRate evaporativeDemand) {
        return new HydroClimateSpec(precipitationSupply, evaporativeDemand);
    }
}
