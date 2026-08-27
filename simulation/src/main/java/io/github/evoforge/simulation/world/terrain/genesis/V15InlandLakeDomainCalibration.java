package io.github.evoforge.simulation.world.terrain.genesis;

import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.terrain.field.TerrainElevationField;

/** Exact operating values for the accepted V15 terrain-derived inland-lake domain selection. */
public record V15InlandLakeDomainCalibration(
        int width,
        int height,
        int area,
        int dryLandCells,
        int targetLakeCells,
        int minimumInteriorClearanceCells,
        int smoothingRadiusCells,
        int minimumComponentCells,
        int minimumComponentSpanCells,
        int maximumLakeBodies,
        long maximumSourceElevationSubunits) {
    private static final int PPM = 1_000_000;

    public V15InlandLakeDomainCalibration {
        if (width <= 0 || height <= 0 || area <= 0 || (long) width * height != area) {
            throw new IllegalArgumentException(
                    "lake calibration dimensions must be positive and consistent");
        }
        if (dryLandCells < 0 || dryLandCells > area) {
            throw new IllegalArgumentException("dryLandCells must fit world area");
        }
        if (targetLakeCells < 0 || targetLakeCells > dryLandCells) {
            throw new IllegalArgumentException("targetLakeCells must fit dry land");
        }
        if (minimumInteriorClearanceCells <= 0
                || smoothingRadiusCells <= 0
                || minimumComponentCells <= 0
                || minimumComponentSpanCells <= 0
                || maximumLakeBodies <= 0
                || maximumSourceElevationSubunits <= 0L) {
            throw new IllegalArgumentException("lake calibration operating values must be positive");
        }
    }

    public static V15InlandLakeDomainCalibration compile(
            ContinuumWorldDomain domain,
            int dryLandCells,
            int maximumZCells,
            V15InlandLakeDomainRecipe recipe) {
        if (domain == null || recipe == null) {
            throw new IllegalArgumentException("V15 lake calibration inputs must not be null");
        }
        int width = Math.toIntExact(domain.width());
        int height = Math.toIntExact(domain.height());
        int area = Math.multiplyExact(width, height);
        if (dryLandCells < 0 || dryLandCells > area) {
            throw new IllegalArgumentException("dryLandCells must fit the V15 Continuum domain");
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
                (long) Math.max(1, maximumZCells),
                TerrainElevationField.SUBUNITS_PER_CELL);
        long maximumSourceElevation = Math.max(
                1L,
                positiveAmplitude * recipe.maximumSourceElevationPpm() / PPM);

        return new V15InlandLakeDomainCalibration(
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
}
