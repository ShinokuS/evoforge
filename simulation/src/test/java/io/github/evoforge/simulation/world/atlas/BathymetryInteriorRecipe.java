package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.definition.NormalizedValue;

/**
 * Versioned policy for broad deep-water seafloor structures.
 *
 * <p>The policy deliberately describes only large interior morphology. Shoreline and coastal
 * descent remain owned by the accepted {@link BathymetryMorphologyAlgorithm}. Structural relief is
 * permitted only when a water body has enough depth and horizontal room to support several broad
 * features without touching the accepted coastal band.</p>
 */
public record BathymetryInteriorRecipe(
        int minimumDepthCells,
        int minimumCoreRadiusCells,
        int minimumCoreCount,
        int maximumCoreCount,
        int coreRadiusUtilizationPpm,
        int structuralSlopeUtilizationPpm,
        int basinStrengthPpm,
        int highStrengthPpm) {

    private static final int PPM = NormalizedValue.SCALE;

    public BathymetryInteriorRecipe {
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

    public static BathymetryInteriorRecipe balanced() {
        return new BathymetryInteriorRecipe(
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
