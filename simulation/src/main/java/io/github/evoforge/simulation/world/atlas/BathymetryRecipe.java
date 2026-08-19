package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.definition.NormalizedValue;

/**
 * Immutable model choices for deterministic standing-water bathymetry.
 *
 * <p>The recipe owns versioned model constants only. World-specific operating limits belong to
 * {@link BathymetryCalibrator}; spatial synthesis belongs to {@link BathymetryElevationAlgorithm}.
 * The model deliberately knows nothing about Water, runtime Shapes or river carving.</p>
 */
public record BathymetryRecipe(
        int baseTerrainFloorCells,
        int maximumCardinalFallPpm,
        int worldSlopeRadiusUtilizationPpm,
        int profileGradientBoundMilli) {

    private static final int PPM = NormalizedValue.SCALE;

    public BathymetryRecipe {
        if (baseTerrainFloorCells <= 0) {
            throw new IllegalArgumentException("baseTerrainFloorCells must be positive");
        }
        requirePositiveNormalized(maximumCardinalFallPpm, "maximumCardinalFallPpm");
        requirePositiveNormalized(worldSlopeRadiusUtilizationPpm, "worldSlopeRadiusUtilizationPpm");
        if (profileGradientBoundMilli <= 0) {
            throw new IllegalArgumentException("profileGradientBoundMilli must be positive");
        }
    }

    /**
     * Balanced first bathymetry model: broad readable underwater slopes with enough world-scale
     * headroom for deep seas while keeping small enclosed water bodies naturally shallow.
     */
    public static BathymetryRecipe balanced() {
        return new BathymetryRecipe(
                1,
                420_000,
                900_000,
                1_875);
    }

    private static void requirePositiveNormalized(int value, String name) {
        if (value <= 0 || value > PPM) {
            throw new IllegalArgumentException(name + " must be in [1, 1_000_000]");
        }
    }
}
