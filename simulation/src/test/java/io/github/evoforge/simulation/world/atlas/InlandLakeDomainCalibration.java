package io.github.evoforge.simulation.world.atlas;

/** Exact operating values for terrain-derived inland-lake domain selection. */
public record InlandLakeDomainCalibration(
        int width,
        int height,
        int area,
        int dryLandCells,
        int targetLakeCells,
        int minimumInteriorClearanceCells,
        int smoothingRadiusCells,
        int minimumComponentCells,
        int minimumComponentSpanCells,
        int maximumLakeBodies,
        long maximumSourceElevationSubunits) {

    public InlandLakeDomainCalibration {
        if (width <= 0 || height <= 0 || area <= 0 || (long) width * height != area) {
            throw new IllegalArgumentException("lake calibration dimensions must be positive and consistent");
        }
        if (dryLandCells < 0 || dryLandCells > area) {
            throw new IllegalArgumentException("dryLandCells must fit world area");
        }
        if (targetLakeCells < 0 || targetLakeCells > dryLandCells) {
            throw new IllegalArgumentException("targetLakeCells must fit dry land");
        }
        if (minimumInteriorClearanceCells <= 0 || smoothingRadiusCells <= 0
                || minimumComponentCells <= 0 || minimumComponentSpanCells <= 0
                || maximumLakeBodies <= 0 || maximumSourceElevationSubunits <= 0L) {
            throw new IllegalArgumentException("lake calibration operating values must be positive");
        }
    }
}
