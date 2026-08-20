package io.github.evoforge.simulation.world.atlas;

/** Exact world-space operating limits consumed by compatible bathymetry algorithms. */
public record BathymetryCalibration(
        int width,
        int height,
        int area,
        long floorSubunits,
        long maximumCardinalFallSubunits,
        long worldDepthCapSubunits,
        int coastalContextRadiusCells,
        long coastalMinimumFallSubunits,
        long coastalMaximumFallSubunits) {

    public BathymetryCalibration {
        if (width <= 0 || height <= 0 || area <= 0) {
            throw new IllegalArgumentException("bathymetry dimensions and area must be positive");
        }
        if (area != Math.multiplyExact(width, height)) {
            throw new IllegalArgumentException("bathymetry area must equal width * height");
        }
        if (floorSubunits >= 0L) {
            throw new IllegalArgumentException("bathymetry floor must be below sea level");
        }
        if (maximumCardinalFallSubunits <= 0L) {
            throw new IllegalArgumentException("maximumCardinalFallSubunits must be positive");
        }
        long verticalCapacity = Math.negateExact(floorSubunits);
        if (worldDepthCapSubunits <= 0L || worldDepthCapSubunits > verticalCapacity) {
            throw new IllegalArgumentException("worldDepthCapSubunits must fit available negative-Z capacity");
        }
        if (coastalContextRadiusCells <= 0) {
            throw new IllegalArgumentException("coastalContextRadiusCells must be positive");
        }
        if (coastalMinimumFallSubunits < 0L
                || coastalMaximumFallSubunits <= 0L
                || coastalMinimumFallSubunits > coastalMaximumFallSubunits
                || coastalMaximumFallSubunits > ElevationField.SUBUNITS_PER_CELL) {
            throw new IllegalArgumentException("coastal fall limits must fit within one cell and be ordered");
        }
    }
}
