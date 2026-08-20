package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.spatial.WorldBounds;

/** Resolves world scale and authored landmass character into exact V14 ocean-domain parameters. */
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
    private static final double MILLI = 1_000d;

    private StandardLandmassBoundaryCalibrator() {
    }

    @Override
    public LandmassBoundaryCalibration calibrate(
            WorldGenesis genesis,
            V12LandformCalibration terrain,
            LandmassBoundaryRecipe recipe) {
        if (genesis == null) throw new IllegalArgumentException("genesis must not be null");
        if (terrain == null) throw new IllegalArgumentException("terrain calibration must not be null");
        if (recipe == null) throw new IllegalArgumentException("landmass boundary recipe must not be null");

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

        LandmassBoundaryRecipe.BoundaryPolicy boundary = recipe.boundary();
        int maximumMargin = Math.max(1, (limitingSpan - 4) / 2);
        int scaledMargin = ceilFinite(
                (boundary.marginRootScaleMilli() / MILLI) * StrictMath.sqrt(limitingSpan),
                "ocean margin");
        int margin = Math.min(
                maximumMargin,
                Math.max(boundary.minimumOceanMarginCells(), scaledMargin));

        LandmassBoundaryRecipe.CoveragePolicy coverage = recipe.coverage();
        long saturationDenominator = (long) limitingSpan + coverage.halfSaturationCells();
        int maximumLandPpm = coverage.baseMaximumLandPpm()
                + Math.toIntExact((long) coverage.maximumLandRangePpm()
                        * limitingSpan / saturationDenominator);
        int requestedMaximumLandCells = Math.toIntExact(
                ((long) terrain.area() * maximumLandPpm + PPM / 2L) / PPM);
        int candidateWidth = Math.max(0, width - margin * 2);
        int candidateHeight = Math.max(0, height - margin * 2);
        int hardMarginCandidateCells = Math.multiplyExact(candidateWidth, candidateHeight);
        int maximumLandCells = Math.min(requestedMaximumLandCells, hardMarginCandidateCells);

        LandmassBoundaryRecipe.ShapePolicy shape = recipe.shape();
        int macroScale = Math.max(
                shape.minimumMacroScaleCells(),
                Math.toIntExact(Math.max(
                        1L,
                        (long) terrain.coherentLandmassScale() * shape.macroCoherentScalePpm() / PPM)));
        int maximumMacroScale = Math.max(
                shape.minimumMacroScaleCells(),
                Math.toIntExact(Math.max(
                        1L,
                        (long) limitingSpan * shape.maximumMacroWorldFractionPpm() / PPM)));
        macroScale = Math.min(macroScale, maximumMacroScale);
        int detailScale = Math.max(
                4,
                Math.toIntExact(Math.max(
                        1L,
                        (long) macroScale * shape.detailMacroScalePpm() / PPM)));

        int detailShift = Math.toIntExact(
                (long) terrain.fragmentationPpm() * shape.fragmentationDetailShiftPpm() / PPM);
        int centerWeight = shape.centerWeightPpm() - detailShift;
        int detailWeight = shape.detailWeightPpm() + detailShift;

        return new LandmassBoundaryCalibration(
                margin,
                maximumLandCells,
                macroScale,
                detailScale,
                centerWeight,
                shape.macroWeightPpm(),
                detailWeight,
                shape.domainInfluencePpm());
    }

    private static int ceilFinite(double value, String label) {
        if (!Double.isFinite(value) || value <= 0d || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(label + " must be finite and positive");
        }
        return (int) StrictMath.ceil(value);
    }
}
