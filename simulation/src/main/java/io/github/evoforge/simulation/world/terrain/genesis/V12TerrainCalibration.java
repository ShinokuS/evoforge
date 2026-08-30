package io.github.evoforge.simulation.world.terrain.genesis;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.terrain.definition.V15TerrainDefinition;

/** Exact V12 operating scales compiled from modern semantic definitions without allocating cells. */
public record V12TerrainCalibration(
        int width,
        int height,
        long area,
        long landCount,
        int coherentLandmassScale,
        int fragmentedLandmassScale,
        int fragmentationPpm,
        int landformSpacing,
        int upliftScale,
        int ridgeScale,
        int rollingScale,
        int rollingDetailScale,
        int reliefPpm,
        int localReliefPpm,
        int ruggednessPpm) {

    private static final int PPM = 1_000_000;

    public static V12TerrainCalibration compile(
            ContinuumWorldDomain domain,
            V15TerrainDefinition definition,
            V12TerrainRecipe recipe) {
        if (domain == null || definition == null || recipe == null) {
            throw new IllegalArgumentException("V12 calibration inputs must not be null");
        }
        int width = Math.toIntExact(domain.width());
        int height = Math.toIntExact(domain.height());
        long area = Math.multiplyExact(domain.width(), domain.height());

        int landmassScalePpm = ppm(definition.landmassScale());
        int fragmentationPpm = ppm(definition.fragmentation());
        int maxDimension = Math.max(width, height);
        int maximumCoherentScale = Math.max(recipe.minimumCoherentScale(), maxDimension);
        int coherentScale = interpolate(
                recipe.minimumCoherentScale(), maximumCoherentScale, landmassScalePpm);
        int fragmentedScale = Math.max(
                recipe.minimumFragmentScale(), coherentScale / recipe.fragmentScaleDivisor());

        int landformSpacing = interpolate(
                recipe.minimumLandformSpacing(),
                recipe.maximumLandformSpacing(),
                ppm(definition.landformScale()));
        int upliftScale = resolvedScale(
                recipe.minimumUpliftScale(),
                landformSpacing,
                recipe.upliftSpacingNumerator(),
                recipe.upliftSpacingDenominator());
        int ridgeScale = resolvedScale(
                recipe.minimumRidgeScale(),
                landformSpacing,
                recipe.ridgeSpacingNumerator(),
                recipe.ridgeSpacingDenominator());
        int rollingScale = resolvedScale(
                recipe.minimumRollingScale(),
                landformSpacing,
                recipe.rollingSpacingNumerator(),
                recipe.rollingSpacingDenominator());
        int rollingDetailScale = resolvedScale(
                recipe.minimumRollingDetailScale(),
                landformSpacing,
                recipe.rollingDetailSpacingNumerator(),
                recipe.rollingDetailSpacingDenominator());

        return new V12TerrainCalibration(
                width,
                height,
                area,
                scaledCount(area, ppm(definition.landCoverage())),
                coherentScale,
                fragmentedScale,
                fragmentationPpm,
                landformSpacing,
                upliftScale,
                ridgeScale,
                rollingScale,
                rollingDetailScale,
                ppm(definition.relief()),
                ppm(definition.localRelief()),
                ppm(definition.ruggedness()));
    }

    static int ppm(NormalizedValue value) {
        return Math.toIntExact(Math.round(value.value() * PPM));
    }

    private static int interpolate(int minimum, int maximum, int coordinatePpm) {
        return minimum + (int) ((long) (maximum - minimum) * coordinatePpm / PPM);
    }

    private static int resolvedScale(int minimum, int spacing, int numerator, int denominator) {
        return Math.max(minimum, Math.multiplyExact(spacing, numerator) / denominator);
    }

    private static long scaledCount(long area, int coordinatePpm) {
        long whole = area / PPM;
        long remainder = area % PPM;
        return Math.addExact(
                Math.multiplyExact(whole, coordinatePpm),
                (Math.multiplyExact(remainder, coordinatePpm) + PPM / 2L) / PPM);
    }
}
