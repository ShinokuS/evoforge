package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.definition.NormalizedValue;

/** Policy for finite V14 external-ocean boundary and maximum terrestrial capacity only. */
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
                new BoundaryPolicy(1),
                new CoveragePolicy(
                        420_000,
                        180_000,
                        256));
    }

    /**
     * Number of outer raster cells that must remain part of the external ocean. This is only a
     * finite-world topology guarantee; it must never be used as a rectangular coastline falloff.
     */
    public record BoundaryPolicy(int guaranteedOceanEdgeCells) {
        public BoundaryPolicy {
            if (guaranteedOceanEdgeCells <= 0) {
                throw new IllegalArgumentException("guaranteed ocean edge cells must be positive");
            }
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
}
