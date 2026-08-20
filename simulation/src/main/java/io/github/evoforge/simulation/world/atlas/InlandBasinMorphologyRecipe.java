package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.definition.NormalizedValue;

/** Scale law and bounded morphology policy for broad continental lowlands. */
public record InlandBasinMorphologyRecipe(
        int targetRadiusLandLinearPpm,
        int minimumRadiusCells,
        int maximumRadiusCells,
        int minimumRadiusScalePpm,
        int maximumRadiusScalePpm,
        int landAreaPerBasinRadiusSquared,
        int maximumBasinCount,
        int depthPerRadiusPpm,
        long minimumDepthSubunits,
        long maximumDepthSubunits) {

    private static final int PPM = NormalizedValue.SCALE;

    public InlandBasinMorphologyRecipe {
        requirePositivePpm(targetRadiusLandLinearPpm, "targetRadiusLandLinearPpm");
        if (minimumRadiusCells < 2 || maximumRadiusCells < minimumRadiusCells) {
            throw new IllegalArgumentException("inland basin radius policy is invalid");
        }
        requirePositivePpm(minimumRadiusScalePpm, "minimumRadiusScalePpm");
        requirePositivePpm(maximumRadiusScalePpm, "maximumRadiusScalePpm");
        if (maximumRadiusScalePpm < minimumRadiusScalePpm) {
            throw new IllegalArgumentException("maximum basin radius scale must be >= minimum scale");
        }
        if (landAreaPerBasinRadiusSquared <= 0 || maximumBasinCount <= 0) {
            throw new IllegalArgumentException("inland basin abundance policy must be positive");
        }
        requirePositivePpm(depthPerRadiusPpm, "depthPerRadiusPpm");
        if (minimumDepthSubunits <= 0L || maximumDepthSubunits < minimumDepthSubunits) {
            throw new IllegalArgumentException("inland basin depth policy is invalid");
        }
    }

    public static InlandBasinMorphologyRecipe balanced() {
        return new InlandBasinMorphologyRecipe(
                55_000,
                8,
                96,
                720_000,
                1_350_000,
                48,
                10,
                180_000,
                ElevationField.SUBUNITS_PER_CELL * 2L,
                ElevationField.SUBUNITS_PER_CELL * 14L);
    }

    private static void requirePositivePpm(int value, String label) {
        if (value <= 0 || value > PPM * 2L) {
            throw new IllegalArgumentException(label + " must be positive and bounded");
        }
    }
}
