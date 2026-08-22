package io.github.evoforge.simulation.world.space.measurement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;

final class CellVolumeRateTest {

    @Test
    void rateIsStoredAsReducedExactFraction() {
        assertEquals(
                new CellVolumeRate(1L, 4L),
                CellVolumeRate.of(250_000L, 1_000_000L));
        assertEquals(
                new CellVolumeRate(3L, 2L),
                CellVolumeRate.of(9L, 6L));
    }

    @Test
    void eventRateReducesBeforeMultiplication() {
        CellVolumeRate rate = CellVolumeRate.ofEvents(
                1_000_000L,
                Long.MAX_VALUE,
                Long.MAX_VALUE);

        assertEquals(new CellVolumeRate(1_000_000L, 1L), rate);
    }

    @Test
    void zeroEventRateUsesCanonicalZero() {
        assertSame(CellVolumeRate.ZERO, CellVolumeRate.ofEvents(5L, 0L, 10L));
        assertEquals(CellVolumeRate.ZERO, CellVolumeRate.of(0L, 999L));
    }

    @Test
    void perTickRealizationIsExactWithoutStoredCarry() {
        CellVolumeRate rate = CellVolumeRate.of(5L, 3L);

        assertEquals(1L, rate.volumeDueAtTick(1L));
        assertEquals(2L, rate.volumeDueAtTick(2L));
        assertEquals(2L, rate.volumeDueAtTick(3L));
        assertEquals(1L, rate.volumeDueAtTick(4L));

        long total = 0L;
        for (long tick = 1L; tick <= 300L; tick++) {
            total = Math.addExact(total, rate.volumeDueAtTick(tick));
        }
        assertEquals(500L, total);
    }

    @Test
    void perTickRealizationHandlesLargeProductsExactly() {
        CellVolumeRate rate = CellVolumeRate.of(
                Long.MAX_VALUE - 17L,
                Long.MAX_VALUE - 3L);
        long tick = Long.MAX_VALUE - 9L;

        BigInteger numerator = BigInteger.valueOf(rate.volumeUnitsNumerator());
        BigInteger denominator = BigInteger.valueOf(rate.tickDenominator());
        BigInteger current = numerator
                .multiply(BigInteger.valueOf(tick))
                .divide(denominator);
        BigInteger previous = numerator
                .multiply(BigInteger.valueOf(tick - 1L))
                .divide(denominator);

        assertEquals(
                current.subtract(previous).longValueExact(),
                rate.volumeDueAtTick(tick));
    }

    @Test
    void tickZeroHasNoElapsedRateInterval() {
        assertThrows(IllegalArgumentException.class,
                () -> CellVolumeRate.of(1L, 2L).volumeDueAtTick(0L));
    }

    @Test
    void invalidDimensionsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> CellVolumeRate.of(-1L, 1L));
        assertThrows(IllegalArgumentException.class,
                () -> CellVolumeRate.of(1L, 0L));
        assertThrows(IllegalArgumentException.class,
                () -> CellVolumeRate.ofEvents(1L, -1L, 2L));
        assertThrows(IllegalArgumentException.class,
                () -> CellVolumeRate.ofEvents(1L, 1L, 0L));
    }
}
