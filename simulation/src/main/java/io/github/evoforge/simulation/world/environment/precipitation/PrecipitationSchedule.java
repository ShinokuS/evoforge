package io.github.evoforge.simulation.world.environment.precipitation;

import io.github.evoforge.simulation.world.mechanics.geometry.CellVolume;

/** Uniform periodic precipitation source configured at runtime composition. */
public record PrecipitationSchedule(
        int amountPerColumn,
        long intervalTicks) {

    public PrecipitationSchedule {
        CellVolume.requireValid(amountPerColumn);
        if (amountPerColumn == CellVolume.EMPTY) {
            throw new IllegalArgumentException(
                    "precipitation amountPerColumn must be positive");
        }
        if (intervalTicks <= 0L) {
            throw new IllegalArgumentException(
                    "precipitation intervalTicks must be positive");
        }
    }
}
