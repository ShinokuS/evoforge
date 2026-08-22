package io.github.evoforge.simulation.world.atmosphere;

import java.math.BigInteger;
import java.time.Duration;

/**
 * Exact non-negative water-depth rate expressed as nanometres per nanosecond.
 *
 * <p>The representation is rational and canonical, so climatological precipitation and
 * evaporative-demand normals never require floating-point accumulation. This is a physical
 * surface-depth rate, independent from cell area and simulation tick duration.</p>
 */
public record WaterDepthRate(
        BigInteger depthNanometersNumerator,
        BigInteger durationNanosecondsDenominator) {

    private static final BigInteger NANOMETERS_PER_MICROMETER = BigInteger.valueOf(1_000L);
    private static final BigInteger NANOMETERS_PER_MILLIMETER = BigInteger.valueOf(1_000_000L);
    private static final BigInteger NANOS_PER_SECOND = BigInteger.valueOf(1_000_000_000L);

    public static final WaterDepthRate ZERO = new WaterDepthRate(BigInteger.ZERO, BigInteger.ONE);

    public WaterDepthRate {
        if (depthNanometersNumerator == null || durationNanosecondsDenominator == null) {
            throw new IllegalArgumentException("water-depth rate components must not be null");
        }
        if (depthNanometersNumerator.signum() < 0) {
            throw new IllegalArgumentException("water depth must be non-negative");
        }
        if (durationNanosecondsDenominator.signum() <= 0) {
            throw new IllegalArgumentException("water-depth duration must be positive");
        }
        if (depthNanometersNumerator.signum() == 0) {
            durationNanosecondsDenominator = BigInteger.ONE;
        } else {
            BigInteger divisor = depthNanometersNumerator.gcd(durationNanosecondsDenominator);
            depthNanometersNumerator = depthNanometersNumerator.divide(divisor);
            durationNanosecondsDenominator = durationNanosecondsDenominator.divide(divisor);
        }
    }

    public static WaterDepthRate ofNanometers(long nanometers, Duration duration) {
        return ofDepth(BigInteger.valueOf(nanometers), duration);
    }

    public static WaterDepthRate ofMicrometers(long micrometers, Duration duration) {
        return ofDepth(
                BigInteger.valueOf(micrometers).multiply(NANOMETERS_PER_MICROMETER),
                duration);
    }

    public static WaterDepthRate ofMillimeters(long millimeters, Duration duration) {
        return ofDepth(
                BigInteger.valueOf(millimeters).multiply(NANOMETERS_PER_MILLIMETER),
                duration);
    }

    private static WaterDepthRate ofDepth(BigInteger depthNanometers, Duration duration) {
        if (depthNanometers.signum() < 0) {
            throw new IllegalArgumentException("water depth must be non-negative");
        }
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("water-depth duration must be positive");
        }
        BigInteger durationNanoseconds = BigInteger.valueOf(duration.getSeconds())
                .multiply(NANOS_PER_SECOND)
                .add(BigInteger.valueOf(duration.getNano()));
        return new WaterDepthRate(depthNanometers, durationNanoseconds);
    }
}
