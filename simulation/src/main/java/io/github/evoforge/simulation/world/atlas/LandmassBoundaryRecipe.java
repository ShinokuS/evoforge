package io.github.evoforge.simulation.world.atlas;

/** Scale law for reserving a genuine oceanic domain around a finite generated world. */
public record LandmassBoundaryRecipe(
        int minimumOceanMarginCells,
        int marginRootScaleMilli,
        int transitionMarginScaleMilli,
        int edgeVariationTransitionScaleMilli,
        int edgeNoiseMarginScaleMilli) {

    private static final int MILLI = 1_000;

    public LandmassBoundaryRecipe {
        if (minimumOceanMarginCells <= 0
                || marginRootScaleMilli <= 0
                || transitionMarginScaleMilli <= 0
                || edgeVariationTransitionScaleMilli < 0
                || edgeNoiseMarginScaleMilli <= 0) {
            throw new IllegalArgumentException("landmass boundary recipe values must be positive");
        }
        if (edgeVariationTransitionScaleMilli > MILLI) {
            throw new IllegalArgumentException("edge variation must not exceed the transition width");
        }
    }

    /**
     * Balanced ocean domain: sqrt(world span) hard margin, similarly broad transition, and broad
     * deterministic edge variation. A 64-cell span resolves to an eight-cell guaranteed margin.
     */
    public static LandmassBoundaryRecipe balanced() {
        return new LandmassBoundaryRecipe(
                5,
                1_000,
                1_000,
                500,
                3_000);
    }
}
