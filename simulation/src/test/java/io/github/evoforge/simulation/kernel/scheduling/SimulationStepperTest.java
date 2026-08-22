package io.github.evoforge.simulation.kernel.scheduling;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import io.github.evoforge.simulation.kernel.time.SimulationClock;

final class SimulationStepperTest {

    @Test
    void advancesClockBeforeDispatchingDueWork() {
        List<Long> handled = new ArrayList<>();
        HandlerRegistry handlers = new HandlerRegistry();
        HandlerId handlerId = handlers.register(handled::add);
        Scheduler scheduler = new Scheduler(handlers);
        SimulationClock clock = new SimulationClock();
        SimulationStepper stepper = new SimulationStepper(
                clock,
                scheduler);

        scheduler.schedule(
                1,
                handlerId,
                42);

        assertEquals(0, clock.tick());
        assertEquals(List.of(), handled);

        stepper.advance();

        assertEquals(1, clock.tick());
        assertEquals(List.of(42L), handled);
    }
}
