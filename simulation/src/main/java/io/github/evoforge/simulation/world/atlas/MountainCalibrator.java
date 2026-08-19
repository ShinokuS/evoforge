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

        // World-size vertical law: a mountain can only climb as high as the narrower horizontal span
        // can support at the calibrated geometric slope. The 0.85 radius utilization leaves room for
        // foothills instead of requiring the summit to sit exactly at a world boundary.
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
        long typicalUplift = worldHeightCap * heightFractionPpm / PPM;

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

        // A tall mountain is automatically broad even when authored Scale is small. This coupling
        // deliberately uses only elevation geometry; it is not a promise about any concrete Shape.
        long riseSteps = typicalUplift == 0L
                ? 0L
                : (typicalUplift + maximumCardinalRise - 1L) / maximumCardinalRise;
        int slopeCoupledHalfWidth = Math.toIntExact(Math.max(
                1L,
                riseSteps * recipe.slopeWidthCouplingPpm() / PPM));
        int typicalHalfWidth = Math.max(authoredHalfWidth, slopeCoupledHalfWidth);

        int chaininessPpm = intent.chaininess().partsPerMillion();
        int longAxisWidthPpm = interpolate(
                recipe.minimumLongAxisWidthPpm(),
                recipe.maximumLongAxisWidthPpm(),
                chaininessPpm);
        int typicalLongAxis = Math.max(
                typicalHalfWidth,
                Math.toIntExact((long) typicalHalfWidth * longAxisWidthPpm / PPM));

        int baseSpacing = Math.max(
                1,
                typicalHalfWidth * recipe.candidateSpacingNumerator()
                        / recipe.candidateSpacingDenominator());
        int candidateSpacing = Math.max(baseSpacing, typicalLongAxis);

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
        int coastalTransitionCells = coastalRise == 0L
                ? recipe.minimumCoastalTransitionCells()
                : Math.max(
                        recipe.minimumCoastalTransitionCells(),
                        Math.toIntExact((coastalRise + maximumCardinalRise - 1L)
                                / maximumCardinalRise));

        return new MountainCalibration(
                width,
                height,
                area,
                candidateSpacing,
                intent.abundance().partsPerMillion(),
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

    private static int interpolate(int minimum, int maximum, int coordinatePpm) {
        return minimum + (int) ((long) (maximum - minimum) * coordinatePpm / PPM);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
