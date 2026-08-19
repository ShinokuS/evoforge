package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.definition.NormalizedValue;

/**
 * Immutable model choices for the V13 structural mountain stage.
 *
 * <p>V13 deliberately starts from the same visual law as the accepted V12 hills: one broad smooth
 * landform with a soft edge. Mountain character comes from much larger scale, strong anisotropic
 * elongation and additional vertical headroom rather than from narrow ridge walls or repeated
 * high-frequency peak modulation.</p>
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
        int minimumLongAxisWidthPpm,
        int maximumLongAxisWidthPpm,
        int coastalTransitionCells,
        int shorelineUpliftPpm,
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
        requirePositive(minimumLongAxisWidthPpm, "minimumLongAxisWidthPpm");
        if (maximumLongAxisWidthPpm < minimumLongAxisWidthPpm) {
            throw new IllegalArgumentException("maximumLongAxisWidthPpm must be >= minimumLongAxisWidthPpm");
        }
        requirePositive(coastalTransitionCells, "coastalTransitionCells");
        requireNormalized(shorelineUpliftPpm, "shorelineUpliftPpm");
        requireNormalized(plateauCorePpm, "plateauCorePpm");
        requireNormalized(centerJitterPpm, "centerJitterPpm");
        requireNormalized(widthVariationPpm, "widthVariationPpm");
        requireNormalized(heightVariationPpm, "heightVariationPpm");
        requirePositive(minimumSharpnessMilli, "minimumSharpnessMilli");
        if (maximumSharpnessMilli < minimumSharpnessMilli) {
            throw new IllegalArgumentException("maximumSharpnessMilli must be >= minimumSharpnessMilli");
        }
    }

    /**
     * Broad, ramp-friendly mountain recipe. Even sharp mountains remain hill-like at their base;
     * sharpness changes the upper profile instead of permitting near-vertical walls.
     */
    public static MountainRecipe balanced() {
        return new MountainRecipe(
                12,
                24,
                72,
                3,
                1,
                80_000,
                450_000,
                140_000,
                420_000,
                1_200_000,
                2_400_000,
                24,
                180_000,
                220_000,
                160_000,
                120_000,
                120_000,
                850,
                1_350);
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
