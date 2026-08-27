package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.definition.NormalizedValue;

/** Stable policy for compact-region V14 continent/island topology and coastline deformation. */
public record LandmassSilhouetteRecipe(
        ScaffoldPolicy scaffold,
        GrowthPolicy growth,
        CoastPolicy coast,
        CoastRelaxationPolicy relaxation,
        BlendPolicy blend) {

    private static final int PPM = NormalizedValue.SCALE;

    public LandmassSilhouetteRecipe {
        if (scaffold == null || growth == null || coast == null || relaxation == null || blend == null) {
            throw new IllegalArgumentException("landmass silhouette recipe sections must not be null");
        }
    }

    public static LandmassSilhouetteRecipe balanced() {
        return new LandmassSilhouetteRecipe(
                new ScaffoldPolicy(
                        4,
                        70_000,
                        220_000,
                        550_000,
                        380_000,
                        550_000),
                new GrowthPolicy(
                        8,
                        1_600_000,
                        1_050_000,
                        820_000,
                        650_000,
                        150_000,
                        120_000,
                        80_000),
                new CoastPolicy(
                        2_400_000,
                        750_000,
                        260_000,
                        80_000),
                new CoastRelaxationPolicy(
                        2,
                        160_000,
                        500_000,
                        90_000,
                        35_000,
                        450_000),
                new BlendPolicy(900_000));
    }

    /**
     * Jittered coarse reference scaffold. Continent scale controls spacing while fragmentation
     * compresses spacing so highly fragmented worlds have enough structural cells for islands.
     */
    public record ScaffoldPolicy(
            int minimumSpacingCells,
            int minimumSpacingWorldPpm,
            int maximumSpacingWorldPpm,
            int fragmentationSpacingCompressionPpm,
            int siteJitterPpm,
            int oceanSeedBandSpacingPpm) {
        public ScaffoldPolicy {
            if (minimumSpacingCells < 3) {
                throw new IllegalArgumentException("landmass scaffold spacing must be at least three cells");
            }
            requirePositivePpm(minimumSpacingWorldPpm, "minimumSpacingWorldPpm");
            requirePositivePpm(maximumSpacingWorldPpm, "maximumSpacingWorldPpm");
            if (maximumSpacingWorldPpm < minimumSpacingWorldPpm) {
                throw new IllegalArgumentException("maximum scaffold spacing must be >= minimum spacing");
            }
            requireNormalized(fragmentationSpacingCompressionPpm, "fragmentationSpacingCompressionPpm");
            requireNormalized(siteJitterPpm, "siteJitterPpm");
            requirePositivePpm(oceanSeedBandSpacingPpm, "oceanSeedBandSpacingPpm");
        }
    }

    /**
     * Multi-source compact growth policy. Fragmentation interpolates from one cohesive front toward
     * several separated fronts and lowers the maximum support fill so sea corridors survive.
     */
    public record GrowthPolicy(
            int maximumClusterCount,
            int cohesiveSupportExpansionPpm,
            int fragmentedSupportExpansionPpm,
            int cohesiveMaximumSupportPpm,
            int fragmentedMaximumSupportPpm,
            int growthRateVariationPpm,
            int directionalBiasPpm,
            int traversalNoisePpm) {
        public GrowthPolicy {
            if (maximumClusterCount < 1 || maximumClusterCount > 16) {
                throw new IllegalArgumentException("maximum land cluster count must be in [1, 16]");
            }
            requirePositivePpm(cohesiveSupportExpansionPpm, "cohesiveSupportExpansionPpm");
            requirePositivePpm(fragmentedSupportExpansionPpm, "fragmentedSupportExpansionPpm");
            requireNormalized(cohesiveMaximumSupportPpm, "cohesiveMaximumSupportPpm");
            requireNormalized(fragmentedMaximumSupportPpm, "fragmentedMaximumSupportPpm");
            requireNormalized(growthRateVariationPpm, "growthRateVariationPpm");
            requireNormalized(directionalBiasPpm, "directionalBiasPpm");
            requireNormalized(traversalNoisePpm, "traversalNoisePpm");
        }
    }

    /** Smooth domain-warped deformation of compact region boundaries; it never owns topology. */
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

    /**
     * Bounded near-coast relaxation. Weights describe one isotropic 3x3 stencil and must sum to one
     * million across the center, four cardinal neighbors and four diagonal neighbors. Maximum shift
     * is per pass and expressed as a fraction of one raster cell in signed-distance units.
     */
    public record CoastRelaxationPolicy(
            int passes,
            int bandWidthSpacingPpm,
            int selfWeightPpm,
            int orthogonalNeighborWeightPpm,
            int diagonalNeighborWeightPpm,
            int maximumShiftPpmOfCell) {
        public CoastRelaxationPolicy {
            if (passes < 0 || passes > 4) {
                throw new IllegalArgumentException("coast relaxation passes must be in [0, 4]");
            }
            requirePositivePpm(bandWidthSpacingPpm, "bandWidthSpacingPpm");
            requireNormalized(selfWeightPpm, "selfWeightPpm");
            requireNormalized(orthogonalNeighborWeightPpm, "orthogonalNeighborWeightPpm");
            requireNormalized(diagonalNeighborWeightPpm, "diagonalNeighborWeightPpm");
            requireNormalized(maximumShiftPpmOfCell, "maximumShiftPpmOfCell");
            long total = (long) selfWeightPpm
                    + 4L * orthogonalNeighborWeightPpm
                    + 4L * diagonalNeighborWeightPpm;
            if (total != PPM) {
                throw new IllegalArgumentException("coast relaxation stencil weights must sum to 1_000_000");
            }
        }
    }

    /** How strongly the compact continent field owns low-Land rank selection over V12 noise. */
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
