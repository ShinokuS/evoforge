package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.spatial.WorldBounds;

/** Resolves basin scale from realized dry-terrain area, independently of Fragmentation. */
@FunctionalInterface
public interface InlandBasinMorphologyCalibrator {
    InlandBasinMorphologyCalibration calibrate(
            ElevationField baseElevation,
            InlandBasinMorphologyRecipe recipe);

    static InlandBasinMorphologyCalibrator standard() {
        return StandardInlandBasinMorphologyCalibrator.INSTANCE;
    }
}

final class StandardInlandBasinMorphologyCalibrator implements InlandBasinMorphologyCalibrator {
    static final StandardInlandBasinMorphologyCalibrator INSTANCE =
            new StandardInlandBasinMorphologyCalibrator();
    private static final int PPM = NormalizedValue.SCALE;

    private StandardInlandBasinMorphologyCalibrator() {
    }

    @Override
    public InlandBasinMorphologyCalibration calibrate(
            ElevationField baseElevation,
            InlandBasinMorphologyRecipe recipe) {
        if (baseElevation == null || recipe == null) {
            throw new IllegalArgumentException("inland basin calibration inputs must not be null");
        }

        WorldBounds bounds = baseElevation.bounds();
        long landCells = 0L;
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                if (baseElevation.elevationSubunitsAt(x, y) >= 0L) landCells++;
            }
        }
        if (landCells == 0L) {
            return new InlandBasinMorphologyCalibration(
                    recipe.minimumRadiusCells(),
                    recipe.minimumRadiusCells(),
                    0,
                    recipe.minimumDepthSubunits(),
                    recipe.minimumDepthSubunits());
        }

        double landLinearScale = StrictMath.sqrt(landCells);
        int characteristicRadius = clamp(
                (int) StrictMath.round(
                        landLinearScale * recipe.targetRadiusLandLinearPpm() / PPM),
                recipe.minimumRadiusCells(),
                recipe.maximumRadiusCells());
        int minimumRadius = clamp(
                (int) StrictMath.round(
                        characteristicRadius * recipe.minimumRadiusScalePpm() / (double) PPM),
                recipe.minimumRadiusCells(),
                recipe.maximumRadiusCells());
        int maximumRadius = clamp(
                (int) StrictMath.round(
                        characteristicRadius * recipe.maximumRadiusScalePpm() / (double) PPM),
                minimumRadius,
                recipe.maximumRadiusCells());

        long denominator = Math.max(
                1L,
                (long) characteristicRadius * characteristicRadius
                        * recipe.landAreaPerBasinRadiusSquared());
        int targetBasins = Math.min(
                recipe.maximumBasinCount(),
                Math.max(1, Math.toIntExact((landCells + denominator - 1L) / denominator)));

        long characteristicDepth = Math.round(
                characteristicRadius
                        * recipe.depthPerRadiusPpm()
                        / (double) PPM
                        * ElevationField.SUBUNITS_PER_CELL);
        characteristicDepth = clamp(
                characteristicDepth,
                recipe.minimumDepthSubunits(),
                recipe.maximumDepthSubunits());
        long minimumDepth = clamp(
                characteristicDepth * 3L / 4L,
                recipe.minimumDepthSubunits(),
                recipe.maximumDepthSubunits());
        long maximumDepth = clamp(
                characteristicDepth * 5L / 4L,
                minimumDepth,
                recipe.maximumDepthSubunits());

        return new InlandBasinMorphologyCalibration(
                minimumRadius,
                maximumRadius,
                targetBasins,
                minimumDepth,
                maximumDepth);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static long clamp(long value, long minimum, long maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
