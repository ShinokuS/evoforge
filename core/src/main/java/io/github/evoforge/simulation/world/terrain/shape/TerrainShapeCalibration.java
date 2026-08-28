package io.github.evoforge.simulation.world.terrain.shape;

import io.github.evoforge.simulation.world.atlas.ElevationField;

/** Explicit fit tolerances for compiling a precise generated surface into available Shape geometry. */
public record TerrainShapeCalibration(
        long maximumMeanEdgeErrorSubunits,
        long maximumReliefErrorSubunits,
        long minimumMeanErrorImprovementSubunits) {

    public TerrainShapeCalibration {
        if (maximumMeanEdgeErrorSubunits < 0L
                || maximumReliefErrorSubunits < 0L
                || minimumMeanErrorImprovementSubunits < 0L) {
            throw new IllegalArgumentException("terrain shape calibration values must be non-negative");
        }
    }

    public static TerrainShapeCalibration representative() {
        long cell = ElevationField.SUBUNITS_PER_CELL;
        return new TerrainShapeCalibration(
                cell * 18L / 100L,
                cell * 30L / 100L,
                cell * 8L / 100L);
    }
}
