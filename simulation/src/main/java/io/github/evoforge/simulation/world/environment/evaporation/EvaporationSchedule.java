package io.github.evoforge.simulation.world.environment.evaporation;

import io.github.evoforge.simulation.world.mechanics.geometry.CellVolume;

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
}
