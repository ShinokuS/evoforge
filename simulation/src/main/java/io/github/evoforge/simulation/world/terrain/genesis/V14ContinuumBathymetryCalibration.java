package io.github.evoforge.simulation.world.terrain.genesis;

import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.terrain.field.TerrainElevationField;

/**
 * Area-independent operating values for regional V14 bathymetry execution.
 *
 * <p>This intentionally mirrors the scalar formulas of {@link V14BathymetryCalibration} without
 * retaining a finite-raster {@code int area}. Exact historical sources keep their old calibration;
 * production Continuum only needs world extents and scalar slope/depth limits.</p>
 */
public record V14ContinuumBathymetryCalibration(
        long width,
        long height,
        long floorSubunits,
        long maximumCardinalFallSubunits,
        long worldDepthCapSubunits,
        int coastalContextRadiusCells,
        long coastalMinimumFallSubunits,
        long coastalMaximumFallSubunits) {
    private static final int PPM = 1_000_000;

    public V14ContinuumBathymetryCalibration {
        if (width <= 0L || height <= 0L || floorSubunits >= 0L || maximumCardinalFallSubunits <= 0L) {
            throw new IllegalArgumentException("Continuum bathymetry dimensions/vertical limits are invalid");
        }
        long verticalCapacity = Math.negateExact(floorSubunits);
        if (worldDepthCapSubunits <= 0L || worldDepthCapSubunits > verticalCapacity) {
            throw new IllegalArgumentException("worldDepthCapSubunits must fit negative-Z capacity");
        }
        if (coastalContextRadiusCells <= 0
                || coastalMinimumFallSubunits < 0L
                || coastalMaximumFallSubunits <= 0L
                || coastalMinimumFallSubunits > coastalMaximumFallSubunits
                || coastalMaximumFallSubunits >= TerrainElevationField.SUBUNITS_PER_CELL / 2L) {
            throw new IllegalArgumentException("Continuum bathymetry coastal limits are invalid");
        }
    }

    public static V14ContinuumBathymetryCalibration compile(
            ContinuumWorldDomain domain,
            int minimumZCells,
            V14BathymetryRecipe recipe) {
        if (domain == null || recipe == null) {
            throw new IllegalArgumentException("Continuum bathymetry calibration inputs must not be null");
        }
        if (minimumZCells >= 0) {
            throw new IllegalArgumentException("bathymetry requires bounds below sea level");
        }
        long limitingHorizontalSpan = Math.min(domain.width(), domain.height());
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
                limitingHorizontalSpan * recipe.worldSlopeRadiusUtilizationPpm()
                        / (2L * PPM));
        long slopeSupportedDepth = Math.multiplyExact(usableRadiusCells, maximumCardinalFall)
                * 1_000L
                / recipe.profileGradientBoundMilli();
        long worldDepthCap = Math.min(verticalCapacity, Math.max(1L, slopeSupportedDepth));

        long requestedCoastalContext = Math.max(
                1L,
                limitingHorizontalSpan * recipe.coastalContextScalePpm() / PPM);
        int coastalContextRadius = Math.max(
                recipe.coastalContextMinimumCells(),
                Math.min(
                        recipe.coastalContextMaximumCells(),
                        Math.toIntExact(Math.min(Integer.MAX_VALUE, requestedCoastalContext))));
        long coastalMinimumFall = TerrainElevationField.SUBUNITS_PER_CELL
                * (long) recipe.coastalMinimumFallPpm()
                / PPM;
        long coastalMaximumFall = Math.max(
                1L,
                TerrainElevationField.SUBUNITS_PER_CELL
                        * (long) recipe.coastalMaximumFallPpm()
                        / PPM);

        return new V14ContinuumBathymetryCalibration(
                domain.width(),
                domain.height(),
                floorSubunits,
                maximumCardinalFall,
                worldDepthCap,
                coastalContextRadius,
                coastalMinimumFall,
                coastalMaximumFall);
    }
}
