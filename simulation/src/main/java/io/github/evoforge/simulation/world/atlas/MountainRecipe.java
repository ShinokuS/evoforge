package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.definition.NormalizedValue;

/**
 * Immutable model choices for the V13 structural mountain stage.
 *
 * <p>The model deliberately knows nothing about concrete runtime Shapes. Its readability contract
 * is geometric: mountain systems are born with a bounded source slope and later Shape fitting only
 * samples that already-valid surface.</p>
 */
public record MountainRecipe(
        int baseTerrainCeilingCells,
        int absoluteMinimumHalfWidthCells,
        int absoluteMaximumHalfWidthCells,
        int minimumHalfWidthWorldPpm,
        int maximumHalfWidthWorldPpm,
        int candidateSpacingNumerator,
        int candidateSpacingDenominator,
        int minimumHeightOfWorldSlopeCapPpm,
        int maximumHeightOfWorldSlopeCapPpm,
        int minimumAllowedRisePpm,
        int maximumAllowedRisePpm,
        int worldSlopeRadiusUtilizationPpm,
        int slopeWidthCouplingPpm,
        int minimumLongAxisWidthPpm,
        int maximumLongAxisWidthPpm,
        int plateauCorePpm,
        int centerJitterPpm,
        int widthVariationPpm,
        int heightVariationPpm) {

    private static final int PPM = NormalizedValue.SCALE;

    public MountainRecipe {
        requirePositive(baseTerrainCeilingCells, "baseTerrainCeilingCells");
        requirePositive(absoluteMinimumHalfWidthCells, "absoluteMinimumHalfWidthCells");
        if (absoluteMaximumHalfWidthCells < absoluteMinimumHalfWidthCells) {
            throw new IllegalArgumentException(
                    "absoluteMaximumHalfWidthCells must be >= absoluteMinimumHalfWidthCells");
        }
        requireNormalized(minimumHalfWidthWorldPpm, "minimumHalfWidthWorldPpm");
        requireNormalized(maximumHalfWidthWorldPpm, "maximumHalfWidthWorldPpm");
        if (maximumHalfWidthWorldPpm < minimumHalfWidthWorldPpm) {
            throw new IllegalArgumentException(
                    "maximumHalfWidthWorldPpm must be >= minimumHalfWidthWorldPpm");
        }
        requirePositive(candidateSpacingNumerator, "candidateSpacingNumerator");
        requirePositive(candidateSpacingDenominator, "candidateSpacingDenominator");
        requireNormalized(minimumHeightOfWorldSlopeCapPpm, "minimumHeightOfWorldSlopeCapPpm");
        requireNormalized(maximumHeightOfWorldSlopeCapPpm, "maximumHeightOfWorldSlopeCapPpm");
        if (maximumHeightOfWorldSlopeCapPpm < minimumHeightOfWorldSlopeCapPpm) {
            throw new IllegalArgumentException(
                    "maximumHeightOfWorldSlopeCapPpm must be >= minimumHeightOfWorldSlopeCapPpm");
        }
        requirePositive(minimumAllowedRisePpm, "minimumAllowedRisePpm");
        if (maximumAllowedRisePpm < minimumAllowedRisePpm || maximumAllowedRisePpm > PPM) {
            throw new IllegalArgumentException(
                    "maximumAllowedRisePpm must be in [minimumAllowedRisePpm, 1_000_000]");
        }
        requireNormalized(worldSlopeRadiusUtilizationPpm, "worldSlopeRadiusUtilizationPpm");
        requireNormalized(slopeWidthCouplingPpm, "slopeWidthCouplingPpm");
        requirePositive(minimumLongAxisWidthPpm, "minimumLongAxisWidthPpm");
        if (maximumLongAxisWidthPpm < minimumLongAxisWidthPpm) {
            throw new IllegalArgumentException("maximumLongAxisWidthPpm must be >= minimumLongAxisWidthPpm");
        }
        requireNormalized(plateauCorePpm, "plateauCorePpm");
        requireNormalized(centerJitterPpm, "centerJitterPpm");
        requireNormalized(widthVariationPpm, "widthVariationPpm");
        requireNormalized(heightVariationPpm, "heightVariationPpm");
    }

    /**
     * Direct bounded-slope mountain synthesis. Maximum sharpness rises by at most 0.235 vertical
     * cell per cardinal step, so even an oblique cut keeps roughly three cells per Z level.
     */
    public static MountainRecipe balanced() {
        return new MountainRecipe(
                12,
                8,
                160,
                40_000,
                180_000,
                2,
                1,
                0,
                1_000_000,
                200_000,
                235_000,
                920_000,
                1_000_000,
                1_150_000,
                2_350_000,
                220_000,
                140_000,
                100_000,
                100_000);
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
