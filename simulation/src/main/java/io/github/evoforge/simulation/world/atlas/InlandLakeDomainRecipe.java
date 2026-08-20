package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.definition.NormalizedValue;

/** Versioned model choices for placing Z=0 inland-water domains inside existing continental lowlands. */
public record InlandLakeDomainRecipe(
        int targetDryLandCoveragePpm,
        int maximumInteriorOccupancyPpm,
        int maximumSourceElevationPpm,
        int minimumInteriorClearanceCells,
        int interiorClearanceWorldDivisor,
        int minimumSmoothingRadiusCells,
        int smoothingWorldDivisor,
        int maximumSmoothingRadiusCells,
        int minimumComponentSpanCells,
        int componentSpanWorldDivisor,
        int maximumLakeBodies) {

    private static final int PPM = NormalizedValue.SCALE;

    public InlandLakeDomainRecipe {
        requireNormalized(targetDryLandCoveragePpm, "targetDryLandCoveragePpm");
        requireNormalized(maximumInteriorOccupancyPpm, "maximumInteriorOccupancyPpm");
        requireNormalized(maximumSourceElevationPpm, "maximumSourceElevationPpm");
        requirePositive(minimumInteriorClearanceCells, "minimumInteriorClearanceCells");
        requirePositive(interiorClearanceWorldDivisor, "interiorClearanceWorldDivisor");
        requirePositive(minimumSmoothingRadiusCells, "minimumSmoothingRadiusCells");
        requirePositive(smoothingWorldDivisor, "smoothingWorldDivisor");
        if (maximumSmoothingRadiusCells < minimumSmoothingRadiusCells) {
            throw new IllegalArgumentException("maximumSmoothingRadiusCells must be >= minimumSmoothingRadiusCells");
        }
        requirePositive(minimumComponentSpanCells, "minimumComponentSpanCells");
        requirePositive(componentSpanWorldDivisor, "componentSpanWorldDivisor");
        requirePositive(maximumLakeBodies, "maximumLakeBodies");
    }

    public static InlandLakeDomainRecipe balanced() {
        return new InlandLakeDomainRecipe(
                15_000,
                140_000,
                280_000,
                8,
                50,
                3,
                90,
                24,
                8,
                60,
                6);
    }

    private static void requirePositive(int value, String name) {
        if (value <= 0) throw new IllegalArgumentException(name + " must be positive");
    }

    private static void requireNormalized(int value, String name) {
        if (value < 0 || value > PPM) {
            throw new IllegalArgumentException(name + " must be in [0, 1_000_000]");
        }
    }
}
