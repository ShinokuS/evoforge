package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.definition.NormalizedValue;

/**
 * Immutable algorithm recipe for the accepted V12 base-terrain implementation.
 *
 * <p>The recipe owns model constants that describe how V12 interprets calibrated world policy.
 * Human-authored {@code WorldGenerationIntent} never contains these implementation details. A
 * future V13 may use a completely different recipe while still implementing the same
 * {@link ElevationGenerator} contract.</p>
 */
public record V12LandformRecipe(
        LandmassPolicy landmass,
        ScalePolicy scales,
        CoastProfile coast,
        ReliefMix relief,
        FeatureKernel features,
        SlopePolicy slopes,
        NoisePolicy noise) {

    private static final int PPM = NormalizedValue.SCALE;

    public V12LandformRecipe {
        if (landmass == null || scales == null || coast == null || relief == null
                || features == null || slopes == null || noise == null) {
            throw new IllegalArgumentException("V12 landform recipe sections must not be null");
        }
    }

    /** Exact recipe that reproduces the manually accepted V12 terrain character. */
    public static V12LandformRecipe balanced() {
        return new V12LandformRecipe(
                new LandmassPolicy(4, 2, 4),
                new ScalePolicy(
                        20,
                        64,
                        52,
                        2,
                        1,
                        34,
                        3,
                        2,
                        16,
                        1,
                        2,
                        10,
                        1,
                        3),
                new CoastProfile(12, 70_000, 230_000, 250_000),
                new ReliefMix(220_000, 340_000, 300_000, 240_000, 760_000, 650_000),
                new FeatureKernel(260_000, 650_000, 270_000, 550_000, 450_000, 1, 2),
                new SlopePolicy(180_000, 600_000, 4),
                new NoisePolicy(8, 2, 6, 500_000));
    }

    public record LandmassPolicy(
            int minimumCoherentScale,
            int minimumFragmentScale,
            int fragmentScaleDivisor) {
        public LandmassPolicy {
            requirePositive(minimumCoherentScale, "minimumCoherentScale");
            requirePositive(minimumFragmentScale, "minimumFragmentScale");
            requirePositive(fragmentScaleDivisor, "fragmentScaleDivisor");
        }
    }

    public record ScalePolicy(
            int minimumLandformSpacing,
            int maximumLandformSpacing,
            int minimumUpliftScale,
            int upliftSpacingNumerator,
            int upliftSpacingDenominator,
            int minimumRidgeScale,
            int ridgeSpacingNumerator,
            int ridgeSpacingDenominator,
            int minimumRollingScale,
            int rollingSpacingNumerator,
            int rollingSpacingDenominator,
            int minimumRollingDetailScale,
            int rollingDetailSpacingNumerator,
            int rollingDetailSpacingDenominator) {
        public ScalePolicy {
            requirePositive(minimumLandformSpacing, "minimumLandformSpacing");
            if (maximumLandformSpacing < minimumLandformSpacing) {
                throw new IllegalArgumentException(
                        "maximumLandformSpacing must be >= minimumLandformSpacing");
            }
            requirePositive(minimumUpliftScale, "minimumUpliftScale");
            requirePositive(upliftSpacingNumerator, "upliftSpacingNumerator");
            requirePositive(upliftSpacingDenominator, "upliftSpacingDenominator");
            requirePositive(minimumRidgeScale, "minimumRidgeScale");
            requirePositive(ridgeSpacingNumerator, "ridgeSpacingNumerator");
            requirePositive(ridgeSpacingDenominator, "ridgeSpacingDenominator");
            requirePositive(minimumRollingScale, "minimumRollingScale");
            requirePositive(rollingSpacingNumerator, "rollingSpacingNumerator");
            requirePositive(rollingSpacingDenominator, "rollingSpacingDenominator");
            requirePositive(minimumRollingDetailScale, "minimumRollingDetailScale");
            requirePositive(rollingDetailSpacingNumerator, "rollingDetailSpacingNumerator");
            requirePositive(rollingDetailSpacingDenominator, "rollingDetailSpacingDenominator");
        }
    }

    public record CoastProfile(
            int transitionCells,
            int baseHeightPpm,
            int interiorHeightPpm,
            int minimumReliefGatePpm) {
        public CoastProfile {
            requirePositive(transitionCells, "transitionCells");
            requirePpm(baseHeightPpm, "baseHeightPpm");
            requirePpm(interiorHeightPpm, "interiorHeightPpm");
            requirePpm(minimumReliefGatePpm, "minimumReliefGatePpm");
        }
    }

    public record ReliefMix(
            int upliftWeightPpm,
            int landformWeightPpm,
            int ridgeWeightPpm,
            int rollingWeightPpm,
            int rollingPrimaryWeightPpm,
            int negativeReliefCompressionPpm) {
        public ReliefMix {
            requirePpm(upliftWeightPpm, "upliftWeightPpm");
            requirePpm(landformWeightPpm, "landformWeightPpm");
            requirePpm(ridgeWeightPpm, "ridgeWeightPpm");
            requirePpm(rollingWeightPpm, "rollingWeightPpm");
            requirePpm(rollingPrimaryWeightPpm, "rollingPrimaryWeightPpm");
            requirePpm(negativeReliefCompressionPpm, "negativeReliefCompressionPpm");
        }

        public int rollingDetailWeightPpm() {
            return PPM - rollingPrimaryWeightPpm;
        }
    }

    public record FeatureKernel(
            int jitterPpm,
            int minimumRadiusPpm,
            int radiusRangePpm,
            int minimumMagnitudePpm,
            int magnitudeRangePpm,
            int neighborhoodRadius,
            int balanceBlockSize) {
        public FeatureKernel {
            requirePpm(jitterPpm, "jitterPpm");
            requirePpm(minimumRadiusPpm, "minimumRadiusPpm");
            requirePpm(radiusRangePpm, "radiusRangePpm");
            requirePpm(minimumMagnitudePpm, "minimumMagnitudePpm");
            requirePpm(magnitudeRangePpm, "magnitudeRangePpm");
            if ((long) minimumRadiusPpm + radiusRangePpm > PPM) {
                throw new IllegalArgumentException("feature radius range must remain normalized");
            }
            if ((long) minimumMagnitudePpm + magnitudeRangePpm > PPM) {
                throw new IllegalArgumentException("feature magnitude range must remain normalized");
            }
            requirePositive(neighborhoodRadius, "neighborhoodRadius");
            requirePositive(balanceBlockSize, "balanceBlockSize");
        }
    }

    public record SlopePolicy(
            int minimumStepPpm,
            int maximumStepPpm,
            int relaxationPasses) {
        public SlopePolicy {
            requirePpm(minimumStepPpm, "minimumStepPpm");
            requirePpm(maximumStepPpm, "maximumStepPpm");
            if (maximumStepPpm < minimumStepPpm) {
                throw new IllegalArgumentException("maximumStepPpm must be >= minimumStepPpm");
            }
            requirePositive(relaxationPasses, "relaxationPasses");
        }
    }

    public record NoisePolicy(
            int minimumWarpScale,
            int warpScaleMultiplier,
            int warpAmplitudeDivisor,
            int ridgeCrestThresholdPpm) {
        public NoisePolicy {
            requirePositive(minimumWarpScale, "minimumWarpScale");
            requirePositive(warpScaleMultiplier, "warpScaleMultiplier");
            requirePositive(warpAmplitudeDivisor, "warpAmplitudeDivisor");
            requirePpm(ridgeCrestThresholdPpm, "ridgeCrestThresholdPpm");
            if (ridgeCrestThresholdPpm >= PPM) {
                throw new IllegalArgumentException("ridgeCrestThresholdPpm must be below 1.0");
            }
        }
    }

    private static void requirePositive(int value, String name) {
        if (value <= 0) throw new IllegalArgumentException(name + " must be positive");
    }

    private static void requirePpm(int value, String name) {
        if (value < 0 || value > PPM) {
            throw new IllegalArgumentException(name + " must be in [0, 1_000_000]");
        }
    }
}
