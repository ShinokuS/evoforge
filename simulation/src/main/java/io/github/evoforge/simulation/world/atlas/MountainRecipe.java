package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.definition.NormalizedValue;

/**
 * Immutable model choices for the V13 structural mountain stage.
 *
 * <p>The model deliberately knows nothing about concrete runtime Shapes. Its readability contract
 * is geometric: each mountain is one bounded hill profile whose cardinal rise is calibrated before
 * the later generic surface fitter chooses whatever geometry represents that surface well.</p>
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
        int minimumCoastalTransitionCells,
        int shorelineUpliftPpm,
        int maximumShorelineUpliftCells,
        int plateauCorePpm,
        int centerJitterPpm,
        int widthVariationPpm,
        int heightVariationPpm,
        int minimumSharpnessMilli,
        int maximumSharpnessMilli) {

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
        requirePositive(minimumCoastalTransitionCells, "minimumCoastalTransitionCells");
        requireNormalized(shorelineUpliftPpm, "shorelineUpliftPpm");
        requirePositive(maximumShorelineUpliftCells, "maximumShorelineUpliftCells");
        requireNormalized(plateauCorePpm, "plateauCorePpm");
        requireNormalized(centerJitterPpm, "centerJitterPpm");
        requireNormalized(widthVariationPpm, "widthVariationPpm");
        requireNormalized(heightVariationPpm, "heightVariationPpm");
        requirePositive(minimumSharpnessMilli, "minimumSharpnessMilli");
        if (maximumSharpnessMilli < minimumSharpnessMilli) {
            throw new IllegalArgumentException("maximumSharpnessMilli must be >= minimumSharpnessMilli");
        }
    }

    /** Balanced structural-mountain defaults. Abundance owns coverage; scale owns structure size. */
    public static MountainRecipe balanced() {
        return new MountainRecipe(
                12,
                8,
                180,
                40_000,
                180_000,
                3,
                2,
                0,
                1_000_000,
                220_000,
                380_000,
                850_000,
                650_000,
                1_100_000,
                2_050_000,
                12,
                0,
                3,
                220_000,
                220_000,
                220_000,
                120_000,
                850,
                1_250);
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
