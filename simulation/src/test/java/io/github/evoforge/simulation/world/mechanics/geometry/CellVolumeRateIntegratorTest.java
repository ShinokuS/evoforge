package io.github.evoforge.simulation.world.mechanics.geometry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class CellVolumeRateIntegratorTest {

    @Test
    void constantRateMatchesTickAnchoredAnalyticalSequence() {
        CellVolumeRate rate = CellVolumeRate.of(2L, 3L);
        CellVolumeRateIntegrator integrator = new CellVolumeRateIntegrator();

        for (long tick = 1L; tick <= 24L; tick++) {
            assertEquals(rate.volumeDueAtTick(tick), integrator.advance(rate));
        }
        assertFalse(integrator.hasFractionalCarry());
    }

    @Test
    void changingRatesPreserveFractionalMassAcrossInactiveIntervals() {
        CellVolumeRateIntegrator integrator = new CellVolumeRateIntegrator();

        assertEquals(0L, integrator.advance(CellVolumeRate.of(1L, 2L)));
        assertTrue(integrator.hasFractionalCarry());
        assertEquals(0L, integrator.advance(CellVolumeRate.ZERO));
        assertTrue(integrator.hasFractionalCarry());
        assertEquals(1L, integrator.advance(CellVolumeRate.of(1L, 2L)));
        assertFalse(integrator.hasFractionalCarry());
    }
}
