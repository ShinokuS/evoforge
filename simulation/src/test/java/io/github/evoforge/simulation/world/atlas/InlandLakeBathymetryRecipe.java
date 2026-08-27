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
     * Balanced lakes follow the accepted ocean-floor principle without lake-specific pits. Depth
     * comes only from shoreline distance: roughly two cardinal cells of inward room are required
     * for each full Z. Production lake-domain selection rejects bodies that cannot honestly supply
     * the ten-cell radius required for the five-Z minimum profile.
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
