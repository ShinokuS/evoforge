package io.github.evoforge.simulation.world.space.measurement;

import java.math.BigInteger;

/**
 * Stateful exact integrator for a CellVolumeRate that may change between simulation ticks.
 *
 * <p>Unlike {@link CellVolumeRate#volumeDueAtTick(long)}, which is analytically anchored to a
 * constant rate from tick zero, this integrator preserves the exact fractional carry while the
 * current rate changes over time. This is required for eventful weather and other variable physical
 * fluxes.</p>
 */
public final class CellVolumeRateIntegrator {
    private BigInteger remainderNumerator = BigInteger.ZERO;
    private BigInteger remainderDenominator = BigInteger.ONE;

    /** Adds exactly one tick at the supplied current rate and returns whole CellVolume units due. */
    public long advance(CellVolumeRate rate) {
        if (rate == null) {
            throw new IllegalArgumentException("cell-volume rate must not be null");
        }

        BigInteger rateNumerator = BigInteger.valueOf(rate.volumeUnitsNumerator());
        BigInteger rateDenominator = BigInteger.valueOf(rate.tickDenominator());
        BigInteger totalNumerator = remainderNumerator
                .multiply(rateDenominator)
                .add(rateNumerator.multiply(remainderDenominator));
        BigInteger totalDenominator = remainderDenominator.multiply(rateDenominator);
        BigInteger[] division = totalNumerator.divideAndRemainder(totalDenominator);

        setRemainder(division[1], totalDenominator);
        return division[0].longValueExact();
    }

    public boolean hasFractionalCarry() {
        return remainderNumerator.signum() != 0;
    }

    private void setRemainder(BigInteger numerator, BigInteger denominator) {
        if (numerator.signum() == 0) {
            remainderNumerator = BigInteger.ZERO;
            remainderDenominator = BigInteger.ONE;
            return;
        }
        BigInteger divisor = numerator.gcd(denominator);
        remainderNumerator = numerator.divide(divisor);
        remainderDenominator = denominator.divide(divisor);
    }
}
