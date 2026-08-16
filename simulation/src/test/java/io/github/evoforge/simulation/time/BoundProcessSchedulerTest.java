package io.github.evoforge.simulation.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class BoundProcessSchedulerTest {

    @Test
    void schedulesRelativeToReadOnlySimulationTime() {
        List<Long> handled = new ArrayList<>();
        HandlerRegistry handlers = new HandlerRegistry();
        HandlerId handlerId = handlers.register(handled::add);
        Scheduler scheduler = new Scheduler(handlers);
        SimulationClock clock = new SimulationClock();
        BoundProcessScheduler bound = new BoundProcessScheduler(clock, scheduler, handlerId);

        clock.advance();
        clock.advance();
        bound.scheduleAfter(3, 7);

        scheduler.dispatchDue(4);
        assertEquals(List.of(), handled);
        scheduler.dispatchDue(5);
        assertEquals(List.of(7L), handled);
    }

    @Test
    void schedulesAtDeclaredAbsoluteTick() {
        List<Long> handled = new ArrayList<>();
        HandlerRegistry handlers = new HandlerRegistry();
        HandlerId handlerId = handlers.register(handled::add);
        Scheduler scheduler = new Scheduler(handlers);
        SimulationClock clock = new SimulationClock();
        BoundProcessScheduler bound = new BoundProcessScheduler(clock, scheduler, handlerId);

        clock.advance();
        clock.advance();
        bound.scheduleAt(7, 11);

        scheduler.dispatchDue(6);
        assertEquals(List.of(), handled);
        scheduler.dispatchDue(7);
        assertEquals(List.of(11L), handled);
    }

    @Test
    void rejectsNegativeDelayAndPastAbsoluteTick() {
        HandlerRegistry handlers = new HandlerRegistry();
        HandlerId handlerId = handlers.register(processId -> { });
        Scheduler scheduler = new Scheduler(handlers);
        SimulationClock clock = new SimulationClock();
        BoundProcessScheduler bound = new BoundProcessScheduler(clock, scheduler, handlerId);

        assertThrows(IllegalArgumentException.class, () -> bound.scheduleAfter(-1, 0));
        clock.advance();
        assertThrows(IllegalArgumentException.class, () -> bound.scheduleAt(0, 0));
    }
}
