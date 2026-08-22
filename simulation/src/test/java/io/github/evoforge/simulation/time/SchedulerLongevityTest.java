package io.github.evoforge.simulation.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class SchedulerLongevityTest {

    @Test
    void repeatedScheduleCancelKeepsQueueAndHandleSlotsBounded() {
        HandlerRegistry handlers = new HandlerRegistry();
        HandlerId handler = handlers.register(processId -> {});
        Scheduler scheduler = new Scheduler(handlers);

        for (int i = 0; i < 100_000; i++) {
            TaskHandle handle = scheduler.schedule(Long.MAX_VALUE, handler, 1L);
            assertTrue(scheduler.cancel(handle));
        }

        assertEquals(0, scheduler.size());
        assertEquals(0, scheduler.queuedEntryCount());
        assertEquals(1, scheduler.allocatedHandleSlotCount());
    }

    @Test
    void staleHandleCannotCancelLaterTaskThatReusedItsSlot() {
        HandlerRegistry handlers = new HandlerRegistry();
        HandlerId handler = handlers.register(processId -> {});
        Scheduler scheduler = new Scheduler(handlers);

        TaskHandle old = scheduler.schedule(10L, handler, 1L);
        assertTrue(scheduler.cancel(old));

        TaskHandle replacement = scheduler.schedule(20L, handler, 2L);
        assertEquals(old.asLong(), replacement.asLong());
        assertFalse(old.equals(replacement));
        assertFalse(scheduler.cancel(old));
        assertEquals(1, scheduler.size());

        assertTrue(scheduler.cancel(replacement));
    }

    @Test
    void sameTimeOrderStillFollowsSchedulingOrderAfterSlotsAreReused() {
        List<Long> handled = new ArrayList<>();
        HandlerRegistry handlers = new HandlerRegistry();
        HandlerId handler = handlers.register(handled::add);
        Scheduler scheduler = new Scheduler(handlers);

        TaskHandle firstOld = scheduler.schedule(100L, handler, 100L);
        TaskHandle secondOld = scheduler.schedule(100L, handler, 200L);
        scheduler.cancel(secondOld);
        scheduler.cancel(firstOld);

        // Free slots are now intentionally in reverse numeric order.
        scheduler.schedule(10L, handler, 1L);
        scheduler.schedule(10L, handler, 2L);
        scheduler.dispatchDue(10L);

        assertEquals(List.of(1L, 2L), handled);
    }
}
