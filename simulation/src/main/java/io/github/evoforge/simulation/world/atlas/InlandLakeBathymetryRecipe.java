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
     * Balanced lakes follow the accepted ocean-floor principle without ocean deep-interior pits:
     * depth is supplied only by room available away from shoreline. About two cardinal cells of
     * inward room are required for each additional visible Z level, so depth bands remain broad.
     */
    public static InlandLakeBathymetryRecipe balanced() {
        return new InlandLakeBathymetryRecipe(
                10,
                5,
                24,
                1,
                2);
    }
}
