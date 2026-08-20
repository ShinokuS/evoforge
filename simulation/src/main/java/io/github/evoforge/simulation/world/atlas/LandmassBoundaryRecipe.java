package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.definition.NormalizedValue;

/** Policy for finite V14 ocean clearance and maximum terrestrial capacity only. */
public record LandmassBoundaryRecipe(
        BoundaryPolicy boundary,
        CoveragePolicy coverage) {

    private static final int PPM = NormalizedValue.SCALE;

    public LandmassBoundaryRecipe {
        if (boundary == null || coverage == null) {
            throw new IllegalArgumentException("landmass boundary recipe sections must not be null");
        }
    }

    public static LandmassBoundaryRecipe balanced() {
        return new LandmassBoundaryRecipe(
                new BoundaryPolicy(
                        5,
                        550_000,
                        96),
                new CoveragePolicy(
                        420_000,
                        180_000,
                        256));
    }

    /**
     * Minimum ocean clearance scales with sqrt(world span): enough visual sea around large maps
     * without consuming a fixed percentage of enormous worlds. This is a placement/safety fact,
     * never a rectangular coastline falloff.
     */
    public record BoundaryPolicy(
            int minimumOceanMarginCells,
            int marginSqrtScalePpm,
            int maximumOceanMarginCells) {
        public BoundaryPolicy {
            if (minimumOceanMarginCells <= 0 || maximumOceanMarginCells < minimumOceanMarginCells) {
                throw new IllegalArgumentException("ocean margin bounds must be positive and ordered");
            }
            requirePositivePpm(marginSqrtScalePpm, "marginSqrtScalePpm");
        }
    }

    /** Maximum terrestrial support as a bounded scale law; ordinary lower Land remains exact. */
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

    private static void requirePpm(int value, String label) {
        if (value < 0 || value > PPM) {
            throw new IllegalArgumentException(label + " must be in [0, 1_000_000]");
        }
    }

    private static void requirePositivePpm(int value, String label) {
        if (value <= 0) {
            throw new IllegalArgumentException(label + " must be positive");
        }
    }
}
