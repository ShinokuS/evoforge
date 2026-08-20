package io.github.evoforge.simulation.world.atlas.hydrology;

import io.github.evoforge.simulation.definition.NormalizedValue;

/** Scale policy for deciding whether edge-connected water acts as an external drainage sink. */
public record StandingWaterExternalSinkRecipe(
        int minimumWorldAreaPpm,
        int minimumWorldClearancePpm,
        int minimumAreaCells,
        int minimumClearanceCells) {

    public StandingWaterExternalSinkRecipe {
        int scale = NormalizedValue.SCALE;
        if (minimumWorldAreaPpm < 0 || minimumWorldAreaPpm > scale) {
            throw new IllegalArgumentException("external-sink area fraction must be normalized ppm");
        }
        if (minimumWorldClearancePpm < 0 || minimumWorldClearancePpm > scale) {
            throw new IllegalArgumentException("external-sink clearance fraction must be normalized ppm");
        }
        if (minimumAreaCells <= 0 || minimumClearanceCells <= 0) {
            throw new IllegalArgumentException("external-sink absolute minima must be positive");
        }
    }

    public static StandingWaterExternalSinkRecipe balanced() {
        return new StandingWaterExternalSinkRecipe(
                5_000,
                15_000,
                16,
                2);
    }
}
