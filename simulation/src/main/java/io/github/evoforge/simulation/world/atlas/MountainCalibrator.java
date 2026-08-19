package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.genesis.MountainIntent;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.spatial.WorldBounds;

/** Resolves semantic mountain intent into exact cell-space operating values. */
@FunctionalInterface
public interface MountainCalibrator {

    MountainCalibration calibrate(WorldGenesis genesis, MountainRecipe recipe);

    static MountainCalibrator standard() {
        return StandardMountainCalibrator.INSTANCE;
    }
}

final class StandardMountainCalibrator implements MountainCalibrator {
    static final StandardMountainCalibrator INSTANCE = new StandardMountainCalibrator();
    private static final int PPM = NormalizedValue.SCALE;

    private StandardMountainCalibrator() {
    }

    @Override
    public MountainCalibration calibrate(WorldGenesis genesis, MountainRecipe recipe) {
        if (genesis == null) throw new IllegalArgumentException("genesis must not be null");
        if (recipe == null) throw new IllegalArgumentException("mountain recipe must not be null");

        WorldBounds bounds = genesis.spec().bounds();
        if (bounds.minZ() >= 0) {
            throw new IllegalArgumentException("V13 mountain generation expects ocean-capable bounds below z=0");
        }
        if (bounds.maxZ() <= recipe.baseTerrainCeilingCells()) {
            throw new IllegalArgumentException(
                    "V13 mountain generation needs positive headroom above the V12 base-terrain ceiling");
        }

        int width = Math.toIntExact((long) bounds.maxX() - bounds.minX() + 1L);
        int height = Math.toIntExact((long) bounds.maxY() - bounds.minY() + 1L);
        int area = Math.toIntExact(Math.multiplyExact((long) width, height));
        int limitingHorizontalSpan = Math.min(width, height);
        MountainIntent intent = genesis.generationIntent().mountains();

        long baseCeiling = Math.multiplyExact(
                (long) recipe.baseTerrainCeilingCells(), ElevationField.SUBUNITS_PER_CELL);
        long mountainCeiling = Math.multiplyExact(
                (long) bounds.maxZ(), ElevationField.SUBUNITS_PER_CELL);
        long availableHeadroom = mountainCeiling - baseCeiling;

        int allowedRisePpm = interpolate(
                recipe.minimumAllowedRisePpm(),
                recipe.maximumAllowedRisePpm(),
                intent.peakSharpness().partsPerMillion());
        long maximumCardinalRise = Math.max(
                1L,
                ElevationField.SUBUNITS_PER_CELL * (long) allowedRisePpm / PPM);

        long usableRadiusCells = Math.max(
                1L,
                (long) limitingHorizontalSpan * recipe.worldSlopeRadiusUtilizationPpm()
                        / (2L * PPM));
        long slopeSupportedHeight = Math.multiplyExact(usableRadiusCells, maximumCardinalRise);
        long worldHeightCap = Math.min(availableHeadroom, slopeSupportedHeight);

        int heightFractionPpm = interpolate(
                recipe.minimumHeightOfWorldSlopeCapPpm(),
                recipe.maximumHeightOfWorldSlopeCapPpm(),
                intent.height().partsPerMillion());
        long desiredUplift = worldHeightCap * heightFractionPpm / PPM;

        int worldMinimumHalfWidth = clamp(
                Math.toIntExact(Math.max(
                        1L,
                        (long) limitingHorizontalSpan * recipe.minimumHalfWidthWorldPpm() / PPM)),
                recipe.absoluteMinimumHalfWidthCells(),
                recipe.absoluteMaximumHalfWidthCells());
        int worldMaximumHalfWidth = clamp(
                Math.toIntExact(Math.max(
                        1L,
                        (long) limitingHorizontalSpan * recipe.maximumHalfWidthWorldPpm() / PPM)),
                worldMinimumHalfWidth,
                recipe.absoluteMaximumHalfWidthCells());
        int authoredHalfWidth = interpolate(
                worldMinimumHalfWidth,
                worldMaximumHalfWidth,
                intent.scale().partsPerMillion());

        // Height may broaden a source, but only within a bounded multiplier. If the authored scale
        // cannot carry the requested height at a readable slope, realized height is capped instead
        // of silently inflating the mountain until it occupies a continent.
        int maximumHeightCoupledWidth = Math.min(
                recipe.absoluteMaximumHalfWidthCells(),
                Math.toIntExact(Math.max(
                        authoredHalfWidth,
                        Math.round(authoredHalfWidth
                                * (1.0 + recipe.slopeWidthCouplingPpm() / (double) PPM)))));
        double profileGradientBound = recipe.profileGradientBound(false);
        int widthNeededForDesiredHeight = desiredUplift == 0L
                ? authoredHalfWidth
                : Math.toIntExact(Math.max(
                        1L,
                        (long) StrictMath.ceil(
                                desiredUplift * profileGradientBound
                                        / Math.max(1.0, maximumCardinalRise))));
        int typicalHalfWidth = clamp(
                widthNeededForDesiredHeight,
                authoredHalfWidth,
                maximumHeightCoupledWidth);

        long widthSupportedHeight = Math.max(
                0L,
                (long) StrictMath.floor(
                        typicalHalfWidth * maximumCardinalRise / profileGradientBound));
        long typicalUplift = Math.min(desiredUplift, widthSupportedHeight);

        int chaininessPpm = intent.chaininess().partsPerMillion();
        int longAxisWidthPpm = interpolate(
                recipe.minimumLongAxisWidthPpm(),
                recipe.maximumLongAxisWidthPpm(),
                chaininessPpm);
        int typicalLongAxis = Math.max(
                typicalHalfWidth,
                Math.toIntExact((long) typicalHalfWidth * longAxisWidthPpm / PPM));

        // Scale, not Height, owns the source lattice. Changing mountain height therefore preserves
        // the same deterministic candidate centers for a fixed seed/scale; only their realized
        // footprint and source count can change through the abundance budget.
        int candidateSpacing = Math.max(
                1,
                authoredHalfWidth * recipe.candidateSpacingNumerator()
                        / recipe.candidateSpacingDenominator());

        int targetCoveragePpm = Math.toIntExact(
                (long) intent.abundance().partsPerMillion()
                        * recipe.maximumAbundanceCoveragePpm()
                        / PPM);
        int candidateActivation = calibratedActivationPpm(
                targetCoveragePpm,
                typicalHalfWidth,
                typicalLongAxis,
                candidateSpacing);

        int sharpnessMilli = interpolate(
                recipe.minimumSharpnessMilli(),
                recipe.maximumSharpnessMilli(),
                intent.peakSharpness().partsPerMillion());
        boolean plateausEnabled = intent.plateausEnabled();
        int plateauProbability = plateausEnabled
                ? intent.plateauProbability().partsPerMillion()
                : 0;

        long shorelineUplift = Math.min(
                Math.multiplyExact(
                        (long) recipe.maximumShorelineUpliftCells(), ElevationField.SUBUNITS_PER_CELL),
                typicalUplift * recipe.shorelineUpliftPpm() / PPM);
        long coastalRise = Math.max(0L, typicalUplift - shorelineUplift);
        long riseSteps = coastalRise == 0L
                ? 0L
                : (coastalRise + maximumCardinalRise - 1L) / maximumCardinalRise;
        int coastalTransitionCells = Math.max(
                recipe.minimumCoastalTransitionCells(),
                Math.toIntExact(Math.max(0L, (riseSteps * 160L + 99L) / 100L)));

        return new MountainCalibration(
                width,
                height,
                area,
                candidateSpacing,
                candidateActivation,
                targetCoveragePpm,
                typicalHalfWidth,
                typicalLongAxis,
                typicalUplift,
                worldHeightCap,
                maximumCardinalRise,
                intent.peakSharpness().partsPerMillion(),
                sharpnessMilli,
                plateausEnabled,
                plateauProbability,
                coastalTransitionCells,
                shorelineUplift,
                baseCeiling,
                mountainCeiling);
    }

    private static int calibratedActivationPpm(
            int targetCoveragePpm,
            int halfWidth,
            int longAxis,
            int spacing) {
        if (targetCoveragePpm <= 0) return 0;
        double targetCoverage = targetCoveragePpm / (double) PPM;
        double expectedFootprint = StrictMath.PI * halfWidth * (double) longAxis;
        double latticeArea = Math.max(1.0, spacing * (double) spacing);
        double footprintLoad = Math.max(1.0e-6, expectedFootprint / latticeArea);
        double activation = -StrictMath.log(1.0 - targetCoverage) / footprintLoad;
        return (int) Math.round(Math.max(0.0, Math.min(1.0, activation)) * PPM);
    }

    private static int interpolate(int minimum, int maximum, int coordinatePpm) {
        return minimum + (int) ((long) (maximum - minimum) * coordinatePpm / PPM);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
