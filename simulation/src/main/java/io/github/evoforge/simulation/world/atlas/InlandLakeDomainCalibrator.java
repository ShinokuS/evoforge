package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.spatial.WorldBounds;

/** Resolves world scale and available dry terrain into exact lake-domain operating values. */
@FunctionalInterface
public interface InlandLakeDomainCalibrator {
    InlandLakeDomainCalibration calibrate(
            WorldGenesis genesis,
            ElevationField continentalBase,
            InlandLakeDomainRecipe recipe);

    static InlandLakeDomainCalibrator standard() {
        return StandardInlandLakeDomainCalibrator.INSTANCE;
    }
}

final class StandardInlandLakeDomainCalibrator implements InlandLakeDomainCalibrator {
    static final StandardInlandLakeDomainCalibrator INSTANCE = new StandardInlandLakeDomainCalibrator();
    private static final int PPM = NormalizedValue.SCALE;

    private StandardInlandLakeDomainCalibrator() {
    }

    @Override
    public InlandLakeDomainCalibration calibrate(
            WorldGenesis genesis,
            ElevationField continentalBase,
            InlandLakeDomainRecipe recipe) {
        if (genesis == null || continentalBase == null || recipe == null) {
            throw new IllegalArgumentException("inland lake calibration inputs must not be null");
        }
        WorldBounds bounds = genesis.spec().bounds();
        if (!sameHorizontalBounds(bounds, continentalBase.bounds())) {
            throw new IllegalArgumentException("continental base must match lake-generation horizontal bounds");
        }

        int width = Math.toIntExact((long) bounds.maxX() - bounds.minX() + 1L);
        int height = Math.toIntExact((long) bounds.maxY() - bounds.minY() + 1L);
        int area = Math.toIntExact(Math.multiplyExact((long) width, height));
        int dryLandCells = 0;
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                if (continentalBase.elevationSubunitsAt(x, y) > ElevationGenerationStage.SEA_LEVEL_SUBUNITS) {
                    dryLandCells++;
                }
            }
        }

        int limitingSpan = Math.min(width, height);
        int targetLakeCells = Math.toIntExact(
                (long) dryLandCells * recipe.targetDryLandCoveragePpm() / PPM);
        int clearance = Math.max(
                recipe.minimumInteriorClearanceCells(),
                limitingSpan / recipe.interiorClearanceWorldDivisor());
        int smoothingRadius = clamp(
                limitingSpan / recipe.smoothingWorldDivisor(),
                recipe.minimumSmoothingRadiusCells(),
                recipe.maximumSmoothingRadiusCells());
        int minimumSpan = Math.max(
                recipe.minimumComponentSpanCells(),
                limitingSpan / recipe.componentSpanWorldDivisor());
        int minimumComponentCells = Math.max(4, minimumSpan * minimumSpan / 2);
        int maximumLakeBodies = Math.min(
                recipe.maximumLakeBodies(),
                Math.max(1, targetLakeCells / Math.max(1, minimumComponentCells)));

        long positiveAmplitude = Math.multiplyExact(
                (long) Math.max(1, continentalBase.bounds().maxZ()),
                ElevationField.SUBUNITS_PER_CELL);
        long maximumSourceElevation = Math.max(
                1L,
                positiveAmplitude * recipe.maximumSourceElevationPpm() / PPM);

        return new InlandLakeDomainCalibration(
                width,
                height,
                area,
                dryLandCells,
                targetLakeCells,
                clearance,
                smoothingRadius,
                minimumComponentCells,
                minimumSpan,
                maximumLakeBodies,
                maximumSourceElevation);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static boolean sameHorizontalBounds(WorldBounds first, WorldBounds second) {
        return first.minX() == second.minX()
                && first.maxX() == second.maxX()
                && first.minY() == second.minY()
                && first.maxY() == second.maxY();
    }
}
