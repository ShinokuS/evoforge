package io.github.evoforge.simulation.world.terrain.genesis;

/** Exact accepted V15 depth policy for already-authored inland Z=0 standing-water bodies. */
public record V15InlandLakeBathymetryRecipe(
        int minimumSignificantRadiusCells,
        int minimumSignificantDepthCells,
        int maximumDepthCells,
        int radiusDepthNumerator,
        int radiusDepthDenominator) {
    public V15InlandLakeBathymetryRecipe {
        if (minimumSignificantRadiusCells <= 0
                || minimumSignificantDepthCells <= 0
                || maximumDepthCells < minimumSignificantDepthCells
                || radiusDepthNumerator <= 0
                || radiusDepthDenominator <= 0) {
            throw new IllegalArgumentException(
                    "inland lake bathymetry policy values must be positive and ordered");
        }
    }

    public static V15InlandLakeBathymetryRecipe balanced() {
        return new V15InlandLakeBathymetryRecipe(10, 5, 24, 1, 2);
    }
}
