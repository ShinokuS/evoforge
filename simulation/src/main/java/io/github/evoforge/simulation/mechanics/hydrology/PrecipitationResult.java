package io.github.evoforge.simulation.mechanics.hydrology;

import io.github.evoforge.simulation.world.space.measurement.CellVolume;

/** Exact accounting for one precipitation input applied to one terrain surface. */
public record PrecipitationResult(
        int input,
        int infiltrated,
        int surfaceWater,
        int unplaced) {

    public PrecipitationResult {
        CellVolume.requireValid(input);
        CellVolume.requireValid(infiltrated);
        CellVolume.requireValid(surfaceWater);
        CellVolume.requireValid(unplaced);

        if ((long) infiltrated + surfaceWater + unplaced != input) {
            throw new IllegalArgumentException(
                    "precipitation result must conserve its input");
        }
    }
}
