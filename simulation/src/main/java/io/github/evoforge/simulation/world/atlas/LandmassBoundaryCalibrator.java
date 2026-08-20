package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.spatial.WorldBounds;

/** Resolves finite-world ocean safety independently from actual continent geometry. */
@FunctionalInterface
public interface LandmassBoundaryCalibrator {
    LandmassBoundaryCalibration calibrate(
            WorldGenesis genesis,
            V12LandformCalibration terrain,
            LandmassBoundaryRecipe recipe);

    static LandmassBoundaryCalibrator standard() {
        return StandardLandmassBoundaryCalibrator.INSTANCE;
    }
}

final class StandardLandmassBoundaryCalibrator implements LandmassBoundaryCalibrator {
    static final StandardLandmassBoundaryCalibrator INSTANCE = new StandardLandmassBoundaryCalibrator();
    private static final int PPM = NormalizedValue.SCALE;

    private StandardLandmassBoundaryCalibrator() {
    }

    @Override
    public LandmassBoundaryCalibration calibrate(
            WorldGenesis genesis,
            V12LandformCalibration terrain,
            LandmassBoundaryRecipe recipe) {
        if (genesis == null || terrain == null || recipe == null) {
            throw new IllegalArgumentException("landmass boundary calibration inputs must not be null");
        }
        WorldBounds bounds = genesis.spec().bounds();
        int width = Math.toIntExact((long) bounds.maxX() - bounds.minX() + 1L);
        int height = Math.toIntExact((long) bounds.maxY() - bounds.minY() + 1L);
        if (width != terrain.width() || height != terrain.height()) {
            throw new IllegalArgumentException("terrain calibration must match world bounds");
        }
        int limitingSpan = Math.min(width, height);
        if (limitingSpan < 5) {
            throw new IllegalArgumentException("ocean-bounded landmass generation needs at least a 5-cell span");
        }

        int maximumMargin = Math.max(1, (limitingSpan - 4) / 2);
        int margin = Math.min(maximumMargin, recipe.boundary().minimumOceanMarginCells());

        LandmassBoundaryRecipe.CoveragePolicy coverage = recipe.coverage();
        long saturationDenominator = (long) limitingSpan + coverage.halfSaturationCells();
        int maximumLandPpm = coverage.baseMaximumLandPpm()
                + Math.toIntExact((long) coverage.maximumLandRangePpm()
                        * limitingSpan / saturationDenominator);
        int requestedMaximumLandCells = Math.toIntExact(
                ((long) terrain.area() * maximumLandPpm + PPM / 2L) / PPM);
        int candidateWidth = Math.max(0, width - margin * 2);
        int candidateHeight = Math.max(0, height - margin * 2);
        int candidateCells = Math.multiplyExact(candidateWidth, candidateHeight);

        return new LandmassBoundaryCalibration(
                margin,
                Math.min(requestedMaximumLandCells, candidateCells));
    }
}
