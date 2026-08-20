package io.github.evoforge.simulation.world.atlas;

/** Exact world-specific ocean-domain values consumed during V14 landmass rank selection. */
public record LandmassBoundaryCalibration(
        int minimumOceanMarginCells,
        int maximumLandCells,
        int macroScaleCells,
        int detailScaleCells,
        int centerWeightPpm,
        int macroWeightPpm,
        int detailWeightPpm,
        int domainInfluencePpm) {

    public LandmassBoundaryCalibration {
        if (minimumOceanMarginCells < 0) {
            throw new IllegalArgumentException("minimum ocean margin must be non-negative");
        }
        if (maximumLandCells < 0) {
            throw new IllegalArgumentException("maximum land cells must be non-negative");
        }
        if (macroScaleCells <= 0 || detailScaleCells <= 0) {
            throw new IllegalArgumentException("landmass-domain noise scales must be positive");
        }
        if (centerWeightPpm < 0
                || macroWeightPpm < 0
                || detailWeightPpm < 0
                || domainInfluencePpm < 0
                || domainInfluencePpm > 1_000_000) {
            throw new IllegalArgumentException("landmass-domain weights must be normalized");
        }
        if ((long) centerWeightPpm + macroWeightPpm + detailWeightPpm != 1_000_000L
                && minimumOceanMarginCells > 0) {
            throw new IllegalArgumentException("oceanic landmass-domain weights must sum to 1.0");
        }
    }

    static LandmassBoundaryCalibration unconstrained(int area) {
        if (area <= 0) throw new IllegalArgumentException("landmass area must be positive");
        return new LandmassBoundaryCalibration(
                0,
                area,
                1,
                1,
                0,
                0,
                0,
                0);
    }

    public boolean oceanBounded() {
        return minimumOceanMarginCells > 0;
    }
}
