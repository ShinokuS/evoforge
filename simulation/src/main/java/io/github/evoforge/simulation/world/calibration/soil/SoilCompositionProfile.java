package io.github.evoforge.simulation.world.calibration.soil;

/**
 * Algorithm-independent physical composition of one mineral soil matrix.
 *
 * <p>Sand, silt and clay are dry-mineral mass fractions and must sum exactly to one. Organic
 * matter is a separate dry-mass fraction because pedotransfer algorithms commonly use it as an
 * additional predictor rather than as a fourth member of the mineral texture triangle.</p>
 */
public record SoilCompositionProfile(
        int sandPartsPerMillion,
        int siltPartsPerMillion,
        int clayPartsPerMillion,
        int organicMatterPartsPerMillion) {

    public static final int FRACTION_SCALE = 1_000_000;

    public SoilCompositionProfile {
        requireFraction(sandPartsPerMillion, "sand");
        requireFraction(siltPartsPerMillion, "silt");
        requireFraction(clayPartsPerMillion, "clay");
        requireFraction(organicMatterPartsPerMillion, "organic matter");
        long mineralTotal = (long) sandPartsPerMillion
                + siltPartsPerMillion
                + clayPartsPerMillion;
        if (mineralTotal != FRACTION_SCALE) {
            throw new IllegalArgumentException(
                    "sand + silt + clay must equal 1_000_000 parts per million: " + mineralTotal);
        }
    }

    public static SoilCompositionProfile ofPercent(
            int sandPercent,
            int siltPercent,
            int clayPercent,
            int organicMatterPercent) {
        return new SoilCompositionProfile(
                percentToPartsPerMillion(sandPercent, "sand"),
                percentToPartsPerMillion(siltPercent, "silt"),
                percentToPartsPerMillion(clayPercent, "clay"),
                percentToPartsPerMillion(organicMatterPercent, "organic matter"));
    }

    private static int percentToPartsPerMillion(int percent, String label) {
        if (percent < 0 || percent > 100) {
            throw new IllegalArgumentException(label + " percent must be within 0..100");
        }
        return Math.multiplyExact(percent, 10_000);
    }

    private static void requireFraction(int value, String label) {
        if (value < 0 || value > FRACTION_SCALE) {
            throw new IllegalArgumentException(
                    label + " fraction must be within 0..1_000_000 parts per million");
        }
    }
}
