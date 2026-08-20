package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.definition.NormalizedValue;

/** Policy for shaping the finite V14 land domain inside a guaranteed external ocean. */
public record LandmassBoundaryRecipe(
        BoundaryPolicy boundary,
        CoveragePolicy coverage,
        ShapePolicy shape) {

    private static final int PPM = NormalizedValue.SCALE;

    public LandmassBoundaryRecipe {
        if (boundary == null || coverage == null || shape == null) {
            throw new IllegalArgumentException("landmass boundary recipe sections must not be null");
        }
    }

    /** Balanced oceanic-domain policy for natural continents rather than an inset rectangle. */
    public static LandmassBoundaryRecipe balanced() {
        return new LandmassBoundaryRecipe(
                new BoundaryPolicy(
                        5,
                        500),
                new CoveragePolicy(
                        520_000,
                        240_000,
                        256),
                new ShapePolicy(
                        12,
                        600_000,
                        333_333,
                        350_000,
                        400_000,
                        450_000,
                        150_000,
                        120_000,
                        450_000));
    }

    /** Hard world-edge safety margin. Organic shape selection happens farther inside this guard. */
    public record BoundaryPolicy(
            int minimumOceanMarginCells,
            int marginRootScaleMilli) {
        public BoundaryPolicy {
            if (minimumOceanMarginCells <= 0 || marginRootScaleMilli <= 0) {
                throw new IllegalArgumentException("landmass boundary policy values must be positive");
            }
        }
    }

    /**
     * Maximum terrestrial domain as a bounded scale law. Even 100% authored land keeps a real
     * ocean instead of asymptotically filling the rectangular world.
     */
    public record CoveragePolicy(
            int baseMaximumLandPpm,
            int maximumLandRangePpm,
            int halfSaturationCells) {
        public CoveragePolicy {
            requirePpm(baseMaximumLandPpm, "baseMaximumLandPpm");
            requirePpm(maximumLandRangePpm, "maximumLandRangePpm");
            if ((long) baseMaximumLandPpm + maximumLandRangePpm > PPM) {
                throw new IllegalArgumentException("maximum land coverage scale must remain normalized");
            }
            if (halfSaturationCells <= 0) {
                throw new IllegalArgumentException("halfSaturationCells must be positive");
            }
        }
    }

    /** Broad deterministic scalar field used to author continent, island and peninsula silhouettes. */
    public record ShapePolicy(
            int minimumMacroScaleCells,
            int macroCoherentScalePpm,
            int maximumMacroWorldFractionPpm,
            int detailMacroScalePpm,
            int centerWeightPpm,
            int macroWeightPpm,
            int detailWeightPpm,
            int fragmentationDetailShiftPpm,
            int domainInfluencePpm) {
        public ShapePolicy {
            if (minimumMacroScaleCells <= 0) {
                throw new IllegalArgumentException("minimumMacroScaleCells must be positive");
            }
            requirePpm(macroCoherentScalePpm, "macroCoherentScalePpm");
            requirePpm(maximumMacroWorldFractionPpm, "maximumMacroWorldFractionPpm");
            requirePpm(detailMacroScalePpm, "detailMacroScalePpm");
            requirePpm(centerWeightPpm, "centerWeightPpm");
            requirePpm(macroWeightPpm, "macroWeightPpm");
            requirePpm(detailWeightPpm, "detailWeightPpm");
            requirePpm(fragmentationDetailShiftPpm, "fragmentationDetailShiftPpm");
            requirePpm(domainInfluencePpm, "domainInfluencePpm");
            if ((long) centerWeightPpm + macroWeightPpm + detailWeightPpm != PPM) {
                throw new IllegalArgumentException("base landmass-domain shape weights must sum to 1.0");
            }
            if (fragmentationDetailShiftPpm > centerWeightPpm) {
                throw new IllegalArgumentException("fragmentation detail shift must come from center weight");
            }
        }
    }

    private static void requirePpm(int value, String label) {
        if (value < 0 || value > PPM) {
            throw new IllegalArgumentException(label + " must be in [0, 1_000_000]");
        }
    }
}
