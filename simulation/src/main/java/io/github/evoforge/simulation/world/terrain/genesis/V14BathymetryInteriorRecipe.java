package io.github.evoforge.simulation.world.terrain.genesis;

/** Exact historical V14 policy for broad deep-water seafloor structures. */
public record V14BathymetryInteriorRecipe(
        int minimumDepthCells,
        int minimumCoreRadiusCells,
        int minimumCoreCount,
        int maximumCoreCount,
        int coreRadiusUtilizationPpm,
        int structuralSlopeUtilizationPpm,
        int basinStrengthPpm,
        int highStrengthPpm) {

    private static final int PPM = 1_000_000;

    public V14BathymetryInteriorRecipe {
        if (minimumDepthCells <= 0 || minimumCoreRadiusCells <= 0) {
            throw new IllegalArgumentException("deep bathymetry depth and radius limits must be positive");
        }
        if (minimumCoreCount < 3 || maximumCoreCount < minimumCoreCount) {
            throw new IllegalArgumentException("deep bathymetry core counts must allow several structures");
        }
        requirePositiveNormalized(coreRadiusUtilizationPpm, "coreRadiusUtilizationPpm");
        requirePositiveNormalized(structuralSlopeUtilizationPpm, "structuralSlopeUtilizationPpm");
        requirePositiveNormalized(basinStrengthPpm, "basinStrengthPpm");
        requirePositiveNormalized(highStrengthPpm, "highStrengthPpm");
        if (highStrengthPpm > basinStrengthPpm) {
            throw new IllegalArgumentException("deep bathymetry highs must not exceed basin strength");
        }
    }

    public static V14BathymetryInteriorRecipe balanced() {
        return new V14BathymetryInteriorRecipe(
                4,
                8,
                3,
                7,
                500_000,
                850_000,
                300_000,
                180_000);
    }

    private static void requirePositiveNormalized(int value, String name) {
        if (value <= 0 || value > PPM) {
            throw new IllegalArgumentException(name + " must be in [1, 1_000_000]");
        }
    }
}
