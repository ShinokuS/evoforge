package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.definition.NormalizedValue;

/** Stable policy for geometric V14 continent/island silhouettes. */
public record LandmassSilhouetteRecipe(
        BodyPolicy bodies,
        CoastPolicy coast,
        BlendPolicy blend) {

    private static final int PPM = NormalizedValue.SCALE;

    public LandmassSilhouetteRecipe {
        if (bodies == null || coast == null || blend == null) {
            throw new IllegalArgumentException("landmass silhouette recipe sections must not be null");
        }
    }

    public static LandmassSilhouetteRecipe balanced() {
        return new LandmassSilhouetteRecipe(
                new BodyPolicy(
                        1,
                        2,
                        6,
                        250_000,
                        170_000,
                        145_000,
                        190_000,
                        180_000,
                        300_000,
                        1_550_000,
                        2_500_000),
                new CoastPolicy(
                        6,
                        55_000,
                        75_000,
                        1_150_000,
                        1_550_000,
                        780_000,
                        120_000),
                new BlendPolicy(850_000));
    }

    /** Scale-aware placement of primary continental bodies and smaller satellite islands. */
    public record BodyPolicy(
            int minimumBodyCount,
            int additionalBodiesAtMinimumScale,
            int maximumSatelliteBodies,
            int singleBodyRadiusWorldPpm,
            int twoBodyRadiusWorldPpm,
            int manyBodyRadiusWorldPpm,
            int multiBodyAnchorOffsetWorldPpm,
            int satelliteMinimumRadiusPpm,
            int satelliteRadiusRangePpm,
            int satelliteMinimumReachPpm,
            int satelliteReachRangePpm) {
        public BodyPolicy {
            if (minimumBodyCount <= 0 || additionalBodiesAtMinimumScale < 0 || maximumSatelliteBodies < 0) {
                throw new IllegalArgumentException("landmass body counts must be valid");
            }
            requirePositivePpm(singleBodyRadiusWorldPpm, "singleBodyRadiusWorldPpm");
            requirePositivePpm(twoBodyRadiusWorldPpm, "twoBodyRadiusWorldPpm");
            requirePositivePpm(manyBodyRadiusWorldPpm, "manyBodyRadiusWorldPpm");
            requireNormalized(multiBodyAnchorOffsetWorldPpm, "multiBodyAnchorOffsetWorldPpm");
            requirePositivePpm(satelliteMinimumRadiusPpm, "satelliteMinimumRadiusPpm");
            requirePositivePpm(satelliteRadiusRangePpm, "satelliteRadiusRangePpm");
            requirePositivePpm(satelliteMinimumReachPpm, "satelliteMinimumReachPpm");
            requirePositivePpm(satelliteReachRangePpm, "satelliteReachRangePpm");
        }
    }

    /** Broad, non-grid-aligned contour deformation. No cell-scale noise belongs here. */
    public record CoastPolicy(
            int harmonicCount,
            int minimumIrregularityPpm,
            int fragmentationIrregularityRangePpm,
            int minimumAspectPpm,
            int maximumAspectPpm,
            int confinementStartPpm,
            int confinementStrengthPpm) {
        public CoastPolicy {
            if (harmonicCount < 2) {
                throw new IllegalArgumentException("landmass coast needs at least two broad harmonics");
            }
            requireNormalized(minimumIrregularityPpm, "minimumIrregularityPpm");
            requireNormalized(fragmentationIrregularityRangePpm, "fragmentationIrregularityRangePpm");
            requirePositivePpm(minimumAspectPpm, "minimumAspectPpm");
            requirePositivePpm(maximumAspectPpm, "maximumAspectPpm");
            if (maximumAspectPpm < minimumAspectPpm) {
                throw new IllegalArgumentException("maximum landmass aspect must be >= minimum aspect");
            }
            requireNormalized(confinementStartPpm, "confinementStartPpm");
            requireNormalized(confinementStrengthPpm, "confinementStrengthPpm");
        }
    }

    /** How strongly the geometric silhouette owns low-Land rank selection over legacy V12 noise. */
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
