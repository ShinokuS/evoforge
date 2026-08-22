package io.github.evoforge.simulation.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.kernel.scheduling.CompactingStateBuffer;
import io.github.evoforge.simulation.kernel.time.ElapsedTimeTransition;
import io.github.evoforge.simulation.kernel.scheduling.HandlerId;
import io.github.evoforge.simulation.kernel.scheduling.HandlerRegistry;
import io.github.evoforge.simulation.kernel.scheduling.Scheduler;
import io.github.evoforge.simulation.kernel.time.SimulationInstant;
import io.github.evoforge.simulation.kernel.scheduling.SleepingProcessScheduler;
import io.github.evoforge.simulation.kernel.scheduling.TaskHandle;
import io.github.evoforge.simulation.kernel.scheduling.WakeReason;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

final class ContinuumTimeLongevityProfileTest {

    private static final int SLEEPING_PROCESSES = 100;
    private static final int RESCHEDULES = 100_000;
    private static final int DELTAS = 100_000;
    private static final int TAIL_LIMIT = 64;
    private static final long FAST_FORWARD_TICKS = 1_000_000_000_000_000L;
    private static final long MAX_ELAPSED_NANOS = Duration.ofSeconds(10).toNanos();

    @Test
    @Tag("scale-profile")
    void equivalentCurrentStateCostsDoNotGrowWithWorldAge() {
        List<SimulationInstant> ages = List.of(
                SimulationInstant.fromTicks(10L),
                new SimulationInstant(1_000_000L, 10L));

        ProfileResult baseline = null;
        for (SimulationInstant age : ages) {
            ProfileResult result = run(age);
            System.out.println(result.report());

            assertEquals(SLEEPING_PROCESSES, result.sleepingProcesses());
            assertEquals(SLEEPING_PROCESSES, result.sleepQueueEntries());
            assertEquals(0, result.genericPendingTasks());
            assertEquals(0, result.genericQueueEntries());
            assertEquals(1, result.genericHandleSlots());
            assertTrue(result.retainedDeltaTail() < TAIL_LIMIT);
            assertEquals(DELTAS, result.currentState());
            assertEquals(1, result.fastForwardCalls());
            assertTrue(result.elapsedNanos() < MAX_ELAPSED_NANOS);

            if (baseline == null) {
                baseline = result;
            } else {
                assertEquals(baseline.sleepingProcesses(), result.sleepingProcesses());
                assertEquals(baseline.sleepQueueEntries(), result.sleepQueueEntries());
                assertEquals(baseline.genericHandleSlots(), result.genericHandleSlots());
                assertEquals(baseline.retainedDeltaTail(), result.retainedDeltaTail());
                assertEquals(baseline.currentState(), result.currentState());
            }
        }
    }

    private static ProfileResult run(SimulationInstant age) {
        long started = System.nanoTime();

        SleepingProcessScheduler sleeping = new SleepingProcessScheduler();
        for (int process = 0; process < SLEEPING_PROCESSES; process++) {
            sleeping.sleepUntil(process, age, age.plusTicks(10_000L + process), WakeReason.scheduled());
        }
        for (int i = 0; i < RESCHEDULES; i++) {
            sleeping.sleepUntil(0L, age, age.plusTicks(20_000L + i), WakeReason.externalChange());
        }

        HandlerRegistry handlers = new HandlerRegistry();
        HandlerId handler = handlers.register(processId -> {});
        Scheduler scheduler = new Scheduler(handlers);
        for (int i = 0; i < RESCHEDULES; i++) {
            TaskHandle handle = scheduler.schedule(Long.MAX_VALUE, handler, 1L);
            scheduler.cancel(handle);
        }

        CompactingStateBuffer<Long, Long> buffer = new CompactingStateBuffer<>(0L, Long::sum, TAIL_LIMIT);
        for (int i = 0; i < DELTAS; i++) {
            buffer.append(1L);
        }

        AtomicInteger fastForwardCalls = new AtomicInteger();
        ElapsedTimeTransition<Long> transition = (state, from, to) -> {
            fastForwardCalls.incrementAndGet();
            return state + from.ticksUntilExact(to);
        };
        transition.advance(0L, age, age.plusTicks(FAST_FORWARD_TICKS));

        long elapsed = System.nanoTime() - started;
        return new ProfileResult(
                age,
                sleeping.sleepingCount(),
                sleeping.queuedEntryCount(),
                scheduler.size(),
                scheduler.queuedEntryCount(),
                scheduler.allocatedHandleSlotCount(),
                buffer.tailSize(),
                buffer.currentState(),
                fastForwardCalls.get(),
                elapsed);
    }

    private record ProfileResult(
            SimulationInstant age,
            int sleepingProcesses,
            int sleepQueueEntries,
            int genericPendingTasks,
            int genericQueueEntries,
            int genericHandleSlots,
            int retainedDeltaTail,
            long currentState,
            int fastForwardCalls,
            long elapsedNanos) {

        String report() {
            return "continuum-time-profile"
                    + " ageEra=" + age.era()
                    + " ageTick=" + age.tickWithinEra()
                    + " sleepingProcesses=" + sleepingProcesses
                    + " sleepQueueEntries=" + sleepQueueEntries
                    + " genericPendingTasks=" + genericPendingTasks
                    + " genericQueueEntries=" + genericQueueEntries
                    + " genericHandleSlots=" + genericHandleSlots
                    + " retainedDeltaTail=" + retainedDeltaTail
                    + " currentState=" + currentState
                    + " fastForwardCalls=" + fastForwardCalls
                    + " elapsedMs=" + String.format("%.3f", elapsedNanos / 1_000_000.0d);
        }
    }
}
