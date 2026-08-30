package io.github.evoforge.simulation.world.terrain.genesis;

/** Exact historical V14 deterministic standing-water bathymetry policy. */
public record V14BathymetryRecipe(
        int baseTerrainFloorCells,
        int maximumCardinalFallPpm,
        int worldSlopeRadiusUtilizationPpm,
        int profileGradientBoundMilli,
        int coastalContextScalePpm,
        int coastalContextMinimumCells,
        int coastalContextMaximumCells,
        int coastalMinimumFallPpm,
        int coastalMaximumFallPpm,
        int coastalReliefFullScalePpm,
        V14BathymetryInteriorRecipe interiorStructure) {

    private static final int PPM = 1_000_000;

    public V14BathymetryRecipe {
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
        if (interiorStructure == null) {
            throw new IllegalArgumentException("interiorStructure must not be null");
        }
    }

    public static V14BathymetryRecipe balanced() {
        return new V14BathymetryRecipe(
                1,
                420_000,
                900_000,
                1_875,
                45_000,
                6,
                18,
                20_000,
                460_000,
                500_000,
                V14BathymetryInteriorRecipe.balanced());
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
