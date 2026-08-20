package io.github.evoforge.simulation.world.atlas;

/** Versioned depth policy for already-authored inland Z=0 standing-water bodies. */
public record InlandLakeBathymetryRecipe(
        int minimumSignificantRadiusCells,
        int minimumSignificantDepthCells,
        int maximumDepthCells,
        int radiusDepthNumerator,
        int radiusDepthDenominator) {

    public InlandLakeBathymetryRecipe {
        if (minimumSignificantRadiusCells <= 0
                || minimumSignificantDepthCells <= 0
                || maximumDepthCells < minimumSignificantDepthCells
                || radiusDepthNumerator <= 0
                || radiusDepthDenominator <= 0) {
            throw new IllegalArgumentException("inland lake bathymetry policy values must be positive and ordered");
        }
    }

    /**
     * Balanced lakes keep a shallow littoral edge but allow a broad inland body to develop a
     * materially deep core. The minimum significant depth is deliberately above the former 3-4 Z
     * puddle regime; larger bodies scale deeper with their geometric radius.
     */
    public static InlandLakeBathymetryRecipe balanced() {
        return new InlandLakeBathymetryRecipe(
                4,
                5,
                24,
                3,
                4);
    }
}
