package io.github.evoforge.simulation.mechanics.hydrology;

import io.github.evoforge.simulation.world.space.measurement.CellVolume;
import io.github.evoforge.simulation.world.space.measurement.CellVolumeRate;

/** Uniform periodic placeholder evaporation configured at runtime composition. */
public record EvaporationSchedule(
        int amountPerColumn,
        long intervalTicks) {

    public EvaporationSchedule {
        CellVolume.requireValid(amountPerColumn);
        if (amountPerColumn == CellVolume.EMPTY) {
            throw new IllegalArgumentException(
                    "evaporation amountPerColumn must be positive");
        }
        if (intervalTicks <= 0L) {
            throw new IllegalArgumentException(
                    "evaporation intervalTicks must be positive");
        }
    }

    /** Exact long-run potential Water removal represented by this operational pulse schedule. */
    public CellVolumeRate meanRatePerColumn() {
        return CellVolumeRate.of(amountPerColumn, intervalTicks);
    }
}
