package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.genesis.WorldGenerationIntent;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.spatial.WorldBounds;

/** Resolves semantic world intent into exact V12 operating parameters. */
@FunctionalInterface
public interface V12LandformCalibrator {

    V12LandformCalibration calibrate(WorldGenesis genesis, V12LandformRecipe recipe);

    static V12LandformCalibrator standard() {
        return StandardV12LandformCalibrator.INSTANCE;
    }
}

final class StandardV12LandformCalibrator implements V12LandformCalibrator {
    static final StandardV12LandformCalibrator INSTANCE = new StandardV12LandformCalibrator();
    private static final int PPM = NormalizedValue.SCALE;

    private StandardV12LandformCalibrator() {
    }

    @Override
    public V12LandformCalibration calibrate(WorldGenesis genesis, V12LandformRecipe recipe) {
        if (genesis == null) throw new IllegalArgumentException("genesis must not be null");
        if (recipe == null) throw new IllegalArgumentException("recipe must not be null");

        WorldBounds bounds = genesis.spec().bounds();
        if (bounds.minZ() >= 0 || bounds.maxZ() <= 0) {
            throw new IllegalArgumentException(
                    "ocean-first generation requires world bounds below and above sea level z=0");
        }

        int width = Math.toIntExact((long) bounds.maxX() - bounds.minX() + 1L);
        int height = Math.toIntExact((long) bounds.maxY() - bounds.minY() + 1L);
        int area = DenseElevationField.cellCount(bounds);
        WorldGenerationIntent intent = genesis.generationIntent();

        V12LandformRecipe.LandmassPolicy landmass = recipe.landmass();
        int maxDimension = Math.max(width, height);
        int maximumCoherentScale = Math.max(landmass.minimumCoherentScale(), maxDimension);
        int coherentScale = interpolate(
                landmass.minimumCoherentScale(),
                maximumCoherentScale,
                intent.landmassScale().partsPerMillion());
        int fragmentedScale = Math.max(
                landmass.minimumFragmentScale(),
                coherentScale / landmass.fragmentScaleDivisor());

        V12LandformRecipe.ScalePolicy scales = recipe.scales();
        int landformSpacing = interpolate(
                scales.minimumLandformSpacing(),
                scales.maximumLandformSpacing(),
                intent.landformScale().partsPerMillion());
        int upliftScale = resolvedScale(
                scales.minimumUpliftScale(),
                landformSpacing,
                scales.upliftSpacingNumerator(),
                scales.upliftSpacingDenominator());
        int ridgeScale = resolvedScale(
                scales.minimumRidgeScale(),
                landformSpacing,
                scales.ridgeSpacingNumerator(),
                scales.ridgeSpacingDenominator());
        int rollingScale = resolvedScale(
                scales.minimumRollingScale(),
                landformSpacing,
                scales.rollingSpacingNumerator(),
                scales.rollingSpacingDenominator());
        int rollingDetailScale = resolvedScale(
                scales.minimumRollingDetailScale(),
                landformSpacing,
                scales.rollingDetailSpacingNumerator(),
                scales.rollingDetailSpacingDenominator());

        int ruggednessPpm = intent.ruggedness().partsPerMillion();
        V12LandformRecipe.SlopePolicy slopes = recipe.slopes();
        int stepPpm = slopes.minimumStepPpm()
                + (int) ((long) ruggednessPpm
                        * (slopes.maximumStepPpm() - slopes.minimumStepPpm()) / PPM);
        long maximumStep = Math.max(
                1L,
                (long) ElevationField.SUBUNITS_PER_CELL * stepPpm / PPM);

        int landCount = Math.toIntExact(
                ((long) area * intent.landCoverage().partsPerMillion() + PPM / 2L) / PPM);

        return new V12LandformCalibration(
                width,
                height,
                area,
                landCount,
                coherentScale,
                fragmentedScale,
                intent.fragmentation().partsPerMillion(),
                landformSpacing,
                upliftScale,
                ridgeScale,
                rollingScale,
                rollingDetailScale,
                intent.relief().partsPerMillion(),
                intent.localRelief().partsPerMillion(),
                ruggednessPpm,
                maximumStep);
    }

    private static int interpolate(int minimum, int maximum, int coordinatePpm) {
        return minimum + (int) ((long) (maximum - minimum) * coordinatePpm / PPM);
    }

    private static int resolvedScale(
            int minimum,
            int spacing,
            int numerator,
            int denominator) {
        return Math.max(minimum, spacing * numerator / denominator);
    }
}
