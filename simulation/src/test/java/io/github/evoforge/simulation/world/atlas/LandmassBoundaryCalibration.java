package io.github.evoforge.simulation.world.atlas;

/** Exact finite-world safety values; this contract deliberately owns no coastline shape. */
public record LandmassBoundaryCalibration(
        int minimumOceanMarginCells,
        int maximumLandCells) {

    public LandmassBoundaryCalibration {
        if (minimumOceanMarginCells < 0) {
            throw new IllegalArgumentException("minimum ocean margin must be non-negative");
        }
        if (maximumLandCells < 0) {
            throw new IllegalArgumentException("maximum land cells must be non-negative");
        }
    }
}
