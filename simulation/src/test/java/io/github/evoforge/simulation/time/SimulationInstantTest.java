package io.github.evoforge.simulation.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class SimulationInstantTest {

    @Test
    void crossesEraBoundaryWithoutLosingTicks() {
        SimulationInstant start = new SimulationInstant(7L, SimulationInstant.TICKS_PER_ERA - 3L);
        SimulationInstant end = start.plusTicks(10L);

        assertEquals(new SimulationInstant(8L, 7L), end);
        assertEquals(10L, start.ticksUntilExact(end));
    }

    @Test
    void longHorizonClockCanAdvanceBeyondOneSignedLongTimeline() {
        LongHorizonClock clock = new LongHorizonClock();
        for (int i = 0; i < 16; i++) {
            clock.advanceBy(Long.MAX_VALUE);
        }

        assertTrue(clock.now().era() > 0L);
        assertTrue(clock.now().compareTo(SimulationInstant.fromTicks(Long.MAX_VALUE)) > 0);
    }

    @Test
    void clockCannotMoveBackwards() {
        LongHorizonClock clock = new LongHorizonClock(new SimulationInstant(5L, 10L));
        assertThrows(IllegalArgumentException.class,
                () -> clock.jumpTo(new SimulationInstant(4L, 10L)));
    }
}
