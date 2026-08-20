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
        int profileGradientBoundMilli,
        int coastalContextScalePpm,
        int coastalContextMinimumCells,
        int coastalContextMaximumCells,
        int coastalMinimumFallPpm,
        int coastalMaximumFallPpm,
        int coastalReliefFullScalePpm) {

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
        requirePositiveNormalized(coastalContextScalePpm, "coastalContextScalePpm");
        if (coastalContextMinimumCells <= 0
                || coastalContextMaximumCells < coastalContextMinimumCells) {
            throw new IllegalArgumentException("coastal context cell limits must be positive and ordered");
        }
        requireNonNegativeNormalized(coastalMinimumFallPpm, "coastalMinimumFallPpm");
        requirePositiveNormalized(coastalMaximumFallPpm, "coastalMaximumFallPpm");
        if (coastalMinimumFallPpm > coastalMaximumFallPpm) {
            throw new IllegalArgumentException("coastal fall limits must be ordered");
        }
        if (coastalMaximumFallPpm >= PPM / 2) {
            throw new IllegalArgumentException("coastalMaximumFallPpm must stay below half a cell per step");
        }
        requirePositiveNormalized(coastalReliefFullScalePpm, "coastalReliefFullScalePpm");
    }

    /**
     * Balanced bathymetry model: the accepted smooth bowl remains the universal base profile.
     * Ocean-connected coastlines may descend faster only when broad land morphology causally
     * supports it. Coastal fall remains below half a Z cell per cardinal step so readable terrain
     * bands stay broader than the one-cell noise rejected during visual development.
     */
    public static BathymetryRecipe balanced() {
        return new BathymetryRecipe(
                1,
                420_000,
                900_000,
                1_875,
                45_000,
                6,
                18,
                20_000,
                460_000,
                500_000);
    }

    private static void requirePositiveNormalized(int value, String name) {
        if (value <= 0 || value > PPM) {
            throw new IllegalArgumentException(name + " must be in [1, 1_000_000]");
        }
    }

    private static void requireNonNegativeNormalized(int value, String name) {
        if (value < 0 || value > PPM) {
            throw new IllegalArgumentException(name + " must be in [0, 1_000_000]");
        }
    }
}
