package io.github.evoforge.simulation.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class SleepingProcessSchedulerTest {

    @Test
    void reschedulingSameProcessReplacesOldWakeInsteadOfGrowingHistory() {
        SleepingProcessScheduler scheduler = new SleepingProcessScheduler();
        SimulationInstant evaluated = SimulationInstant.ZERO;

        for (int i = 0; i < 100_000; i++) {
            scheduler.sleepUntil(7L, evaluated, SimulationInstant.fromTicks(1_000_000L + i), WakeReason.scheduled());
        }

        assertEquals(1, scheduler.sleepingCount());
        assertEquals(1, scheduler.queuedEntryCount());
        assertEquals(100_000L, scheduler.metrics().schedules());
        assertEquals(99_999L, scheduler.metrics().replacements());
    }

    @Test
    void hugeTimeJumpReturnsOneElapsedIntervalNotEveryMissingTick() {
        SleepingProcessScheduler scheduler = new SleepingProcessScheduler();
        SimulationInstant from = SimulationInstant.fromTicks(100L);
        SimulationInstant wakeAt = from.plusTicks(50L);
        scheduler.sleepUntil(42L, from, wakeAt, WakeReason.scheduled());

        LongHorizonClock clock = new LongHorizonClock(from);
        clock.advanceBy(1_000_000_000_000_000L);
        List<ScheduledWake> due = scheduler.drainDue(clock.now());

        assertEquals(1, due.size());
        ScheduledWake wake = due.getFirst();
        assertEquals(from, wake.lastEvaluatedAt());
        assertEquals(0, scheduler.sleepingCount());
        assertEquals(0, scheduler.queuedEntryCount());

        AtomicInteger transitionCalls = new AtomicInteger();
        ElapsedTimeTransition<Long> transition = (state, start, end) -> {
            transitionCalls.incrementAndGet();
            return state + start.ticksUntilExact(end);
        };
        long result = transition.advance(0L, wake.lastEvaluatedAt(), clock.now());

        assertEquals(1, transitionCalls.get());
        assertEquals(1_000_000_000_000_000L, result);
    }

    @Test
    void cancelPhysicallyRemovesCurrentWake() {
        SleepingProcessScheduler scheduler = new SleepingProcessScheduler();
        scheduler.sleepUntil(1L, SimulationInstant.ZERO, SimulationInstant.fromTicks(100L), WakeReason.externalChange());

        scheduler.cancel(1L);

        assertEquals(0, scheduler.sleepingCount());
        assertEquals(0, scheduler.queuedEntryCount());
        assertNull(scheduler.nextWake());
    }
}
