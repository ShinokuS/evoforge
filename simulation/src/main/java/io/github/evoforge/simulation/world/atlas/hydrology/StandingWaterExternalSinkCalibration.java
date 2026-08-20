package io.github.evoforge.simulation.world.atlas.hydrology;

/** Exact world-scale thresholds used by external standing-water sink resolution. */
public record StandingWaterExternalSinkCalibration(
        int minimumBoundaryContactCells,
        int minimumClearanceCells) {

    public StandingWaterExternalSinkCalibration {
        if (minimumBoundaryContactCells <= 0 || minimumClearanceCells <= 0) {
            throw new IllegalArgumentException("external-sink calibration minima must be positive");
        }
    }
}
