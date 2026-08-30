package io.github.evoforge.simulation.world.terrain.genesis;

/** Exact implementation constants of the manually accepted balanced V12 terrain recipe. */
public record V12TerrainRecipe(
        int minimumCoherentScale,
        int minimumFragmentScale,
        int fragmentScaleDivisor,
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
        int rollingDetailSpacingDenominator,
        int coastTransitionCells,
        int coastBaseHeightPpm,
        int coastInteriorHeightPpm,
        int coastMinimumReliefGatePpm,
        int upliftWeightPpm,
        int landformWeightPpm,
        int ridgeWeightPpm,
        int rollingWeightPpm,
        int rollingPrimaryWeightPpm,
        int negativeReliefCompressionPpm,
        int featureJitterPpm,
        int featureMinimumRadiusPpm,
        int featureRadiusRangePpm,
        int featureMinimumMagnitudePpm,
        int featureMagnitudeRangePpm,
        int featureNeighborhoodRadius,
        int featureBalanceBlockSize,
        int minimumStepPpm,
        int maximumStepPpm,
        int relaxationPasses,
        int minimumWarpScale,
        int warpScaleMultiplier,
        int warpAmplitudeDivisor,
        int ridgeCrestThresholdPpm) {

    public static V12TerrainRecipe balanced() {
        return new V12TerrainRecipe(
                4, 2, 4,
                20, 64,
                52, 2, 1,
                34, 3, 2,
                16, 1, 2,
                10, 1, 3,
                12, 70_000, 230_000, 250_000,
                220_000, 340_000, 300_000, 240_000, 760_000, 650_000,
                260_000, 650_000, 270_000, 550_000, 450_000, 1, 2,
                180_000, 600_000, 4,
                8, 2, 6, 500_000);
    }

    public int rollingDetailWeightPpm() {
        return 1_000_000 - rollingPrimaryWeightPpm;
    }
}
