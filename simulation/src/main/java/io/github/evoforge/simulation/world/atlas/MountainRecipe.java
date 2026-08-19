package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.definition.NormalizedValue;

/**
 * Immutable model choices for the V13 structural mountain stage.
 *
 * <p>Semantic authoring stays in {@code MountainIntent}; this recipe owns implementation policy so
 * later mountain revisions can replace the model without scattering tuned literals through the
 * spatial algorithm.</p>
 */
public record MountainRecipe(
        int baseTerrainCeilingCells,
        int minimumHalfWidthCells,
        int maximumHalfWidthCells,
        int candidateSpacingNumerator,
        int candidateSpacingDenominator,
        int minimumHeightHeadroomPpm,
        int maximumHeightHeadroomPpm,
        int minimumAllowedRisePpm,
        int maximumAllowedRisePpm,
        int minimumRidgeHalfLengthWidthPpm,
        int maximumRidgeHalfLengthWidthPpm,
        int peakSpacingWidthPpm,
        int saddleFloorPpm,
        int branchLengthWidthPpm,
        int maximumBranches,
        int foothillWidthPpm,
        int foothillWeightPpm,
        int plateauCorePpm,
        int centerJitterPpm,
        int widthVariationPpm,
        int heightVariationPpm,
        int minimumSharpnessMilli,
        int maximumSharpnessMilli) {

    private static final int PPM = NormalizedValue.SCALE;

    public MountainRecipe {
        requirePositive(baseTerrainCeilingCells, "baseTerrainCeilingCells");
        requirePositive(minimumHalfWidthCells, "minimumHalfWidthCells");
        if (maximumHalfWidthCells < minimumHalfWidthCells) {
            throw new IllegalArgumentException("maximumHalfWidthCells must be >= minimumHalfWidthCells");
        }
        requirePositive(candidateSpacingNumerator, "candidateSpacingNumerator");
        requirePositive(candidateSpacingDenominator, "candidateSpacingDenominator");
        requireNormalized(minimumHeightHeadroomPpm, "minimumHeightHeadroomPpm");
        requireNormalized(maximumHeightHeadroomPpm, "maximumHeightHeadroomPpm");
        if (maximumHeightHeadroomPpm < minimumHeightHeadroomPpm) {
            throw new IllegalArgumentException("maximumHeightHeadroomPpm must be >= minimumHeightHeadroomPpm");
        }
        requirePositive(minimumAllowedRisePpm, "minimumAllowedRisePpm");
        if (maximumAllowedRisePpm < minimumAllowedRisePpm) {
            throw new IllegalArgumentException("maximumAllowedRisePpm must be >= minimumAllowedRisePpm");
        }
        requireNonNegative(minimumRidgeHalfLengthWidthPpm, "minimumRidgeHalfLengthWidthPpm");
        if (maximumRidgeHalfLengthWidthPpm < minimumRidgeHalfLengthWidthPpm) {
            throw new IllegalArgumentException(
                    "maximumRidgeHalfLengthWidthPpm must be >= minimumRidgeHalfLengthWidthPpm");
        }
        requirePositive(peakSpacingWidthPpm, "peakSpacingWidthPpm");
        requireNormalized(saddleFloorPpm, "saddleFloorPpm");
        requirePositive(branchLengthWidthPpm, "branchLengthWidthPpm");
        if (maximumBranches < 0 || maximumBranches > 4) {
            throw new IllegalArgumentException("maximumBranches must be in [0, 4]");
        }
        requirePositive(foothillWidthPpm, "foothillWidthPpm");
        if (foothillWidthPpm < PPM) {
            throw new IllegalArgumentException("foothillWidthPpm must be >= 1.0");
        }
        requireNormalized(foothillWeightPpm, "foothillWeightPpm");
        requireNormalized(plateauCorePpm, "plateauCorePpm");
        requireNormalized(centerJitterPpm, "centerJitterPpm");
        requireNormalized(widthVariationPpm, "widthVariationPpm");
        requireNormalized(heightVariationPpm, "heightVariationPpm");
        requirePositive(minimumSharpnessMilli, "minimumSharpnessMilli");
        if (maximumSharpnessMilli < minimumSharpnessMilli) {
            throw new IllegalArgumentException("maximumSharpnessMilli must be >= minimumSharpnessMilli");
        }
    }

    /** First structural-mountain recipe, intentionally independent from the accepted V12 recipe. */
    public static MountainRecipe balanced() {
        return new MountainRecipe(
                12,
                12,
                54,
                5,
                2,
                120_000,
                800_000,
                300_000,
                1_800_000,
                200_000,
                3_500_000,
                900_000,
                580_000,
                1_500_000,
                2,
                1_850_000,
                250_000,
                250_000,
                220_000,
                220_000,
                180_000,
                800,
                3_200);
    }

    private static void requirePositive(int value, String name) {
        if (value <= 0) throw new IllegalArgumentException(name + " must be positive");
    }

    private static void requireNonNegative(int value, String name) {
        if (value < 0) throw new IllegalArgumentException(name + " must be non-negative");
    }

    private static void requireNormalized(int value, String name) {
        if (value < 0 || value > PPM) {
            throw new IllegalArgumentException(name + " must be in [0, 1_000_000]");
        }
    }
}
