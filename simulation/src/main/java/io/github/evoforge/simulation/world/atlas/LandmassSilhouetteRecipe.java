package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.definition.NormalizedValue;

/** Stable policy for plate-scaffold V14 continent/island topology and coastline deformation. */
public record LandmassSilhouetteRecipe(
        PlatePolicy plates,
        CoastPolicy coast,
        BlendPolicy blend) {

    private static final int PPM = NormalizedValue.SCALE;

    public LandmassSilhouetteRecipe {
        if (plates == null || coast == null || blend == null) {
            throw new IllegalArgumentException("landmass silhouette recipe sections must not be null");
        }
    }

    public static LandmassSilhouetteRecipe balanced() {
        return new LandmassSilhouetteRecipe(
                new PlatePolicy(
                        4,
                        70_000,
                        220_000,
                        550_000,
                        5,
                        380_000,
                        550_000),
                new CoastPolicy(
                        2_400_000,
                        750_000,
                        320_000,
                        120_000),
                new BlendPolicy(900_000));
    }

    /**
     * Jittered control-mesh policy. Continent scale controls average plate spacing; fragmentation
     * compresses that spacing and removes graph correlation, producing more independent land/ocean
     * regions rather than merely roughening one continent.
     */
    public record PlatePolicy(
            int minimumSpacingCells,
            int minimumSpacingWorldPpm,
            int maximumSpacingWorldPpm,
            int fragmentationSpacingCompressionPpm,
            int maximumCorrelationPasses,
            int siteJitterPpm,
            int oceanSeedBandSpacingPpm) {
        public PlatePolicy {
            if (minimumSpacingCells < 3 || maximumCorrelationPasses < 0) {
                throw new IllegalArgumentException("plate scaffold scale must be valid");
            }
            requirePositivePpm(minimumSpacingWorldPpm, "minimumSpacingWorldPpm");
            requirePositivePpm(maximumSpacingWorldPpm, "maximumSpacingWorldPpm");
            if (maximumSpacingWorldPpm < minimumSpacingWorldPpm) {
                throw new IllegalArgumentException("maximum plate spacing must be >= minimum spacing");
            }
            requireNormalized(fragmentationSpacingCompressionPpm, "fragmentationSpacingCompressionPpm");
            requireNormalized(siteJitterPpm, "siteJitterPpm");
            requirePositivePpm(oceanSeedBandSpacingPpm, "oceanSeedBandSpacingPpm");
        }
    }

    /** Smooth domain-warped deformation of plate boundaries; topology remains owned by plates. */
    public record CoastPolicy(
            int warpScaleSpacingPpm,
            int detailScaleSpacingPpm,
            int warpAmplitudeSpacingPpm,
            int detailAmplitudeSpacingPpm) {
        public CoastPolicy {
            requirePositivePpm(warpScaleSpacingPpm, "warpScaleSpacingPpm");
            requirePositivePpm(detailScaleSpacingPpm, "detailScaleSpacingPpm");
            requireNormalized(warpAmplitudeSpacingPpm, "warpAmplitudeSpacingPpm");
            requireNormalized(detailAmplitudeSpacingPpm, "detailAmplitudeSpacingPpm");
        }
    }

    /** How strongly the plate-derived continent field owns low-Land rank selection over V12 noise. */
    public record BlendPolicy(int silhouetteInfluencePpm) {
        public BlendPolicy {
            requireNormalized(silhouetteInfluencePpm, "silhouetteInfluencePpm");
        }
    }

    private static void requireNormalized(int value, String label) {
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
