package io.github.evoforge.simulation.world.terrain.genesis;

import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.terrain.field.TerrainElevationField;

/** Exact world-space operating limits used by historical V14 bathymetry. */
public record V14BathymetryCalibration(
        int width,
        int height,
        int area,
        long floorSubunits,
        long maximumCardinalFallSubunits,
        long worldDepthCapSubunits,
        int coastalContextRadiusCells,
        long coastalMinimumFallSubunits,
        long coastalMaximumFallSubunits) {

    private static final int PPM = 1_000_000;

    public V14BathymetryCalibration {
        if (width <= 0 || height <= 0 || area <= 0 || area != Math.multiplyExact(width, height)) {
            throw new IllegalArgumentException("bathymetry dimensions and area must be positive and consistent");
        }
        if (floorSubunits >= 0L || maximumCardinalFallSubunits <= 0L) {
            throw new IllegalArgumentException("bathymetry vertical limits are invalid");
        }
        long verticalCapacity = Math.negateExact(floorSubunits);
        if (worldDepthCapSubunits <= 0L || worldDepthCapSubunits > verticalCapacity) {
            throw new IllegalArgumentException("worldDepthCapSubunits must fit available negative-Z capacity");
        }
        if (coastalContextRadiusCells <= 0
                || coastalMinimumFallSubunits < 0L
                || coastalMaximumFallSubunits <= 0L
                || coastalMinimumFallSubunits > coastalMaximumFallSubunits
                || coastalMaximumFallSubunits >= TerrainElevationField.SUBUNITS_PER_CELL / 2L) {
            throw new IllegalArgumentException("bathymetry coastal limits are invalid");
        }
    }

    public static V14BathymetryCalibration compile(
            ContinuumWorldDomain domain,
            int minimumZCells,
            V14BathymetryRecipe recipe) {
        if (domain == null || recipe == null) {
            throw new IllegalArgumentException("bathymetry calibration inputs must not be null");
        }
        if (minimumZCells >= 0) {
            throw new IllegalArgumentException("bathymetry requires bounds below sea level");
        }
        int width = Math.toIntExact(domain.width());
        int height = Math.toIntExact(domain.height());
        int area = Math.toIntExact(Math.multiplyExact(domain.width(), domain.height()));
        int limitingHorizontalSpan = Math.min(width, height);

        long floorSubunits = Math.multiplyExact(
                (long) minimumZCells,
                TerrainElevationField.SUBUNITS_PER_CELL);
        long verticalCapacity = Math.negateExact(floorSubunits);
        long maximumCardinalFall = Math.max(
                1L,
                TerrainElevationField.SUBUNITS_PER_CELL
                        * (long) recipe.maximumCardinalFallPpm()
                        / PPM);
        long usableRadiusCells = Math.max(
                1L,
                (long) limitingHorizontalSpan * recipe.worldSlopeRadiusUtilizationPpm()
                        / (2L * PPM));
        long slopeSupportedDepth = Math.multiplyExact(usableRadiusCells, maximumCardinalFall)
                * 1_000L
                / recipe.profileGradientBoundMilli();
        long worldDepthCap = Math.min(verticalCapacity, Math.max(1L, slopeSupportedDepth));

        int requestedCoastalContext = Math.max(
                1,
                (int) ((long) limitingHorizontalSpan * recipe.coastalContextScalePpm() / PPM));
        int coastalContextRadius = Math.max(
                recipe.coastalContextMinimumCells(),
                Math.min(recipe.coastalContextMaximumCells(), requestedCoastalContext));
        long coastalMinimumFall = TerrainElevationField.SUBUNITS_PER_CELL
                * (long) recipe.coastalMinimumFallPpm()
                / PPM;
        long coastalMaximumFall = Math.max(
                1L,
                TerrainElevationField.SUBUNITS_PER_CELL
                        * (long) recipe.coastalMaximumFallPpm()
                        / PPM);

        return new V14BathymetryCalibration(
                width,
                height,
                area,
                floorSubunits,
                maximumCardinalFall,
                worldDepthCap,
                coastalContextRadius,
                coastalMinimumFall,
                coastalMaximumFall);
    }
}
