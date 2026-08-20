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
     * Balanced lakes use the same geometric principle as accepted ocean bathymetry: depth emerges
     * from room available away from shoreline. Unlike the ocean deep-interior layer, inland lakes do
     * not receive authored pits/basins. One Z of additional depth requires about two cells of inward
     * room, so visible depth bands stay broad instead of collapsing into one-cell terraces.
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
