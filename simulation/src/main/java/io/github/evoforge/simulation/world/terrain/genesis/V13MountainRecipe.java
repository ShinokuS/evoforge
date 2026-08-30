package io.github.evoforge.simulation.world.terrain.genesis;

/** Exact V13 structural-mountain model constants from the accepted historical generator. */
public record V13MountainRecipe(
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
        int maximumSharpnessMilli,
        int maximumAbundanceCoveragePpm,
        int profileGradientBoundMilli,
        int plateauProfileGradientBoundMilli) {

    private static final int PPM = 1_000_000;

    public V13MountainRecipe {
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
        requireNormalized(maximumAbundanceCoveragePpm, "maximumAbundanceCoveragePpm");
        requirePositive(profileGradientBoundMilli, "profileGradientBoundMilli");
        if (plateauProfileGradientBoundMilli < profileGradientBoundMilli) {
            throw new IllegalArgumentException(
                    "plateauProfileGradientBoundMilli must be >= profileGradientBoundMilli");
        }
    }

    public double profileGradientBound(boolean plateau) {
        return (plateau ? plateauProfileGradientBoundMilli : profileGradientBoundMilli) / 1_000.0;
    }

    public static V13MountainRecipe balanced() {
        return new V13MountainRecipe(
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
                1_250,
                750_000,
                1_300,
                1_600);
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
