package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.spatial.WorldBounds;

/** Resolves world bounds into exact operating limits for standing-water bathymetry. */
@FunctionalInterface
public interface BathymetryCalibrator {

    BathymetryCalibration calibrate(WorldGenesis genesis, BathymetryRecipe recipe);

    static BathymetryCalibrator standard() {
        return StandardBathymetryCalibrator.INSTANCE;
    }
}

final class StandardBathymetryCalibrator implements BathymetryCalibrator {
    static final StandardBathymetryCalibrator INSTANCE = new StandardBathymetryCalibrator();
    private static final int PPM = NormalizedValue.SCALE;

    private StandardBathymetryCalibrator() {
    }

    @Override
    public BathymetryCalibration calibrate(WorldGenesis genesis, BathymetryRecipe recipe) {
        if (genesis == null) throw new IllegalArgumentException("genesis must not be null");
        if (recipe == null) throw new IllegalArgumentException("bathymetry recipe must not be null");

        WorldBounds bounds = genesis.spec().bounds();
        if (bounds.minZ() >= 0) {
            throw new IllegalArgumentException("bathymetry requires world bounds below sea level z=0");
        }

        int width = Math.toIntExact((long) bounds.maxX() - bounds.minX() + 1L);
        int height = Math.toIntExact((long) bounds.maxY() - bounds.minY() + 1L);
        int area = Math.toIntExact(Math.multiplyExact((long) width, height));
        int limitingHorizontalSpan = Math.min(width, height);

        long floorSubunits = Math.multiplyExact(
                (long) bounds.minZ(), ElevationField.SUBUNITS_PER_CELL);
        long verticalCapacity = Math.negateExact(floorSubunits);
        long maximumCardinalFall = Math.max(
                1L,
                ElevationField.SUBUNITS_PER_CELL
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

        return new BathymetryCalibration(
                width,
                height,
                area,
                floorSubunits,
                maximumCardinalFall,
                worldDepthCap);
    }
}
