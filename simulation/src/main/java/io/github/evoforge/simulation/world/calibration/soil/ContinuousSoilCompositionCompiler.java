package io.github.evoforge.simulation.world.calibration.soil;

import io.github.evoforge.simulation.definition.NormalizedValue;

/**
 * Continuous default projection from normalized authored soil character to physical composition.
 *
 * <p>The mineral curve uses the quadratic Bernstein basis: coarse, intermediate and fine mineral
 * contributions vary smoothly over the whole authored fineness interval. There are no texture
 * categories or threshold branches. Organic matter is scaled separately by explicit model
 * calibration.</p>
 */
public final class ContinuousSoilCompositionCompiler implements SoilCompositionCompiler {
    private final SoilCompositionCalibration calibration;

    public ContinuousSoilCompositionCompiler(SoilCompositionCalibration calibration) {
        if (calibration == null) {
            throw new IllegalArgumentException("soil composition calibration must not be null");
        }
        this.calibration = calibration;
    }

    @Override
    public SoilCompositionProfile compile(SoilSemanticProfile semantic) {
        if (semantic == null) {
            throw new IllegalArgumentException("semantic soil profile must not be null");
        }

        long scale = NormalizedValue.SCALE;
        long fine = semantic.mineralFineness().partsPerMillion();
        long coarse = scale - fine;

        int sand = Math.toIntExact(roundDivide(coarse * coarse, scale));
        int clay = Math.toIntExact(roundDivide(fine * fine, scale));
        int silt = Math.subtractExact(SoilCompositionProfile.FRACTION_SCALE, sand + clay);
        int organicMatter = Math.toIntExact(roundDivide(
                (long) semantic.organicMatter().partsPerMillion()
                        * calibration.maximumOrganicMatterPartsPerMillion(),
                scale));

        return new SoilCompositionProfile(sand, silt, clay, organicMatter);
    }

    private static long roundDivide(long numerator, long denominator) {
        return Math.addExact(numerator, denominator / 2L) / denominator;
    }
}
