package io.github.evoforge.simulation.world.calibration.soil;

import io.github.evoforge.simulation.world.mechanics.measurement.WaterDepthRate;
import java.time.Duration;

/**
 * Saxton-Rawls (2006) pedotransfer implementation for mineral-soil hydraulic estimates.
 *
 * <p>The empirical algorithm is deliberately isolated behind {@link SoilHydraulicCalibrator}; its
 * coefficients are properties of this replaceable model, not authored world constants. Inputs are
 * physical sand/silt/clay texture and organic-matter fractions. Outputs are quantized only at the
 * preparation fact boundary: water contents to one part per million and saturated conductivity to
 * one micrometre per hour.</p>
 *
 * <p>Reference: K. E. Saxton & W. J. Rawls (2006), Soil Science Society of America Journal 70,
 * 1569-1578, DOI 10.2136/sssaj2005.0117. As with any pedotransfer function, these are statistical
 * estimates rather than measured properties of a particular soil.</p>
 */
public final class SaxtonRawls2006SoilHydraulicCalibrator implements SoilHydraulicCalibrator {
    private static final double FRACTION_SCALE = SoilCompositionProfile.FRACTION_SCALE;
    private static final double PPM_PER_PERCENT = 10_000d;
    private static final double PPM_OUTPUT_SCALE = SoilHydraulicProfile.FRACTION_SCALE;
    private static final double MICROMETERS_PER_MILLIMETER = 1_000d;

    @Override
    public SoilHydraulicProfile calibrate(SoilCompositionProfile composition) {
        if (composition == null) {
            throw new IllegalArgumentException("soil composition must not be null");
        }

        double sand = composition.sandPartsPerMillion() / FRACTION_SCALE;
        double clay = composition.clayPartsPerMillion() / FRACTION_SCALE;
        double organicMatterPercent = composition.organicMatterPartsPerMillion() / PPM_PER_PERCENT;

        double theta1500First = -0.024d * sand
                + 0.487d * clay
                + 0.006d * organicMatterPercent
                + 0.005d * sand * organicMatterPercent
                - 0.013d * clay * organicMatterPercent
                + 0.068d * sand * clay
                + 0.031d;
        double theta1500 = theta1500First
                + (0.14d * theta1500First - 0.02d);

        double theta33First = -0.251d * sand
                + 0.195d * clay
                + 0.011d * organicMatterPercent
                + 0.006d * sand * organicMatterPercent
                - 0.027d * clay * organicMatterPercent
                + 0.452d * sand * clay
                + 0.299d;
        double theta33 = theta33First
                + (1.283d * theta33First * theta33First
                - 0.374d * theta33First
                - 0.015d);

        double thetaSMinus33First = 0.278d * sand
                + 0.034d * clay
                + 0.022d * organicMatterPercent
                - 0.018d * sand * organicMatterPercent
                - 0.027d * clay * organicMatterPercent
                - 0.584d * sand * clay
                + 0.078d;
        double thetaSMinus33 = thetaSMinus33First
                + (0.636d * thetaSMinus33First - 0.107d);
        double thetaS = theta33 + thetaSMinus33 - 0.097d * sand + 0.043d;

        requirePhysicalWaterContents(theta1500, theta33, thetaS, composition);

        double b = (StrictMath.log(1500d) - StrictMath.log(33d))
                / (StrictMath.log(theta33) - StrictMath.log(theta1500));
        double lambda = 1d / b;
        double conductivityMillimetersPerHour = 1930d
                * StrictMath.pow(thetaS - theta33, 3d - lambda);
        if (!Double.isFinite(conductivityMillimetersPerHour)
                || conductivityMillimetersPerHour < 0d) {
            throw new IllegalArgumentException(
                    "Saxton-Rawls conductivity is not physical for composition: " + composition);
        }

        long conductivityMicrometersPerHour = quantizeNonNegative(
                conductivityMillimetersPerHour * MICROMETERS_PER_MILLIMETER,
                "saturated hydraulic conductivity");
        return new SoilHydraulicProfile(
                quantizeFraction(thetaS, "porosity"),
                quantizeFraction(theta33, "field capacity"),
                quantizeFraction(theta1500, "permanent wilting point"),
                WaterDepthRate.ofMicrometers(
                        conductivityMicrometersPerHour,
                        Duration.ofHours(1)));
    }

    private static void requirePhysicalWaterContents(
            double theta1500,
            double theta33,
            double thetaS,
            SoilCompositionProfile composition) {
        if (!Double.isFinite(theta1500)
                || !Double.isFinite(theta33)
                || !Double.isFinite(thetaS)
                || theta1500 <= 0d
                || theta33 < theta1500
                || thetaS < theta33
                || thetaS > 1d) {
            throw new IllegalArgumentException(
                    "Saxton-Rawls water contents are not physical for composition: " + composition);
        }
    }

    private static int quantizeFraction(double value, String label) {
        long partsPerMillion = Math.round(value * PPM_OUTPUT_SCALE);
        if (partsPerMillion < 0L || partsPerMillion > SoilHydraulicProfile.FRACTION_SCALE) {
            throw new IllegalArgumentException(label + " cannot be represented as a physical fraction");
        }
        return Math.toIntExact(partsPerMillion);
    }

    private static long quantizeNonNegative(double value, String label) {
        if (!Double.isFinite(value) || value < 0d || value > Long.MAX_VALUE) {
            throw new IllegalArgumentException(label + " cannot be represented");
        }
        return Math.round(value);
    }
}
