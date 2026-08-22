package io.github.evoforge.visualizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.kernel.scheduling.HandlerRegistry;
import io.github.evoforge.simulation.kernel.scheduling.Scheduler;
import io.github.evoforge.simulation.kernel.time.SimulationClock;
import io.github.evoforge.simulation.kernel.scheduling.SimulationStepper;
import org.junit.jupiter.api.Test;

final class VisualizerTimeControllerTest {

    @Test
    void startsPausedAndDoesNotTieTicksToRenderCalls() {
        Fixture fixture = fixture(0.25f);

        assertFalse(fixture.controller().running());
        assertEquals(0, fixture.controller().update(10f));
        assertEquals(0, fixture.clock().tick());
    }

    @Test
    void singleStepAdvancesExactlyOneProductionTick() {
        Fixture fixture = fixture(0.25f);

        fixture.controller().stepOnce();

        assertEquals(1, fixture.clock().tick());
    }

    @Test
    void runningModeUsesAccumulatedRealTime() {
        Fixture fixture = fixture(0.25f);
        fixture.controller().setRunning(true);

        assertEquals(0, fixture.controller().update(0.125f));
        assertEquals(1, fixture.controller().update(0.125f));
        assertEquals(1, fixture.clock().tick());

        assertEquals(3, fixture.controller().update(0.75f));
        assertEquals(4, fixture.clock().tick());
    }

    @Test
    void pausingClearsPartialRealTimeCarry() {
        Fixture fixture = fixture(0.25f);
        fixture.controller().setRunning(true);
        fixture.controller().update(0.125f);

        fixture.controller().setRunning(false);
        fixture.controller().setRunning(true);

        assertEquals(0, fixture.controller().update(0.125f));
        assertEquals(0, fixture.clock().tick());
    }

    @Test
    void singleStepIsRejectedWhileRunning() {
        Fixture fixture = fixture(0.25f);
        fixture.controller().setRunning(true);

        assertTrue(fixture.controller().running());
        assertThrows(
                IllegalStateException.class,
                fixture.controller()::stepOnce);
    }

    private static Fixture fixture(
            float secondsPerTick) {

        HandlerRegistry handlers = new HandlerRegistry();
        Scheduler scheduler = new Scheduler(handlers);
        SimulationClock clock = new SimulationClock();
        SimulationStepper stepper = new SimulationStepper(
                clock,
                scheduler);

        return new Fixture(
                clock,
                new VisualizerTimeController(
                        stepper,
                        secondsPerTick));
    }

    private record Fixture(
            SimulationClock clock,
            VisualizerTimeController controller) {
    }
}
