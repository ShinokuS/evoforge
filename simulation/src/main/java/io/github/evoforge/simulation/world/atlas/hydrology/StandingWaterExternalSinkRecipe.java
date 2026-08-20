package io.github.evoforge.simulation.world.atlas.hydrology;

import io.github.evoforge.simulation.definition.NormalizedValue;

/**
 * Scale policy for deciding whether edge-connected water acts as an external drainage sink.
 *
 * <p>The role requires three independent signals: enough total water area, a sufficiently broad
 * uninterrupted opening on one world side, and enough interior clearance to reject long narrow
 * edge channels. Area and boundary-opening thresholds scale sublinearly with world span so large
 * worlds do not require a fixed fraction of the entire map.</p>
 */
public record StandingWaterExternalSinkRecipe(
        int areaThresholdCapPpm,
        int areaRootScaleMilli,
        int minimumWorldClearancePpm,
        int boundaryRunRootScaleMilli,
        int minimumAreaCells,
        int minimumClearanceCells,
        int minimumBoundaryRunCells) {

    private static final int MILLI_SCALE = 1_000;

    public StandingWaterExternalSinkRecipe {
        int scale = NormalizedValue.SCALE;
        if (areaThresholdCapPpm < 0 || areaThresholdCapPpm > scale) {
            throw new IllegalArgumentException("external-sink area threshold cap must be normalized ppm");
        }
        if (minimumWorldClearancePpm < 0 || minimumWorldClearancePpm > scale) {
            throw new IllegalArgumentException("external-sink clearance fraction must be normalized ppm");
        }
        if (areaRootScaleMilli <= 0 || boundaryRunRootScaleMilli <= 0) {
            throw new IllegalArgumentException("external-sink root scales must be positive milli-units");
        }
        if (minimumAreaCells <= 0 || minimumClearanceCells <= 0 || minimumBoundaryRunCells <= 0) {
            throw new IllegalArgumentException("external-sink absolute minima must be positive");
        }
        if (areaRootScaleMilli > 100 * MILLI_SCALE
                || boundaryRunRootScaleMilli > 100 * MILLI_SCALE) {
            throw new IllegalArgumentException("external-sink root scales are implausibly large");
        }
    }

    public static StandingWaterExternalSinkRecipe balanced() {
        return new StandingWaterExternalSinkRecipe(
                300_000,
                5_500,
                15_000,
                4_500,
                16,
                2,
                4);
    }
}
