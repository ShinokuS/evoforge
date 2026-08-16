package io.github.evoforge.simulation.world.mechanics.geometry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
