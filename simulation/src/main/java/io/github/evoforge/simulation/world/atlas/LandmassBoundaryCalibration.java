package io.github.evoforge.simulation.world.atlas;

/** Exact world-specific boundary-domain values consumed during landmass rank selection. */
public record LandmassBoundaryCalibration(
        int minimumOceanMarginCells,
        int transitionCells,
        int edgeVariationCells,
        int edgeNoiseScale,
        int maximumLandCells) {

    public LandmassBoundaryCalibration {
        if (minimumOceanMarginCells < 0 || transitionCells < 0 || edgeVariationCells < 0) {
            throw new IllegalArgumentException("landmass boundary distances must be non-negative");
        }
        if (edgeNoiseScale <= 0) {
            throw new IllegalArgumentException("landmass boundary noise scale must be positive");
        }
        if (maximumLandCells < 0) {
            throw new IllegalArgumentException("maximum land cells must be non-negative");
        }
        if (minimumOceanMarginCells == 0 && (transitionCells != 0 || edgeVariationCells != 0)) {
            throw new IllegalArgumentException("unconstrained landmass boundary cannot have transition policy");
        }
        if (edgeVariationCells > transitionCells) {
            throw new IllegalArgumentException("edge variation must fit the transition zone");
        }
    }

    static LandmassBoundaryCalibration unconstrained(int area) {
        if (area <= 0) throw new IllegalArgumentException("landmass area must be positive");
        return new LandmassBoundaryCalibration(0, 0, 0, 1, area);
    }

    public boolean oceanBounded() {
        return minimumOceanMarginCells > 0;
    }
}
