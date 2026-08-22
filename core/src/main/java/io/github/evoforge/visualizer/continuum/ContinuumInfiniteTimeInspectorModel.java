package io.github.evoforge.visualizer.continuum;

import io.github.evoforge.simulation.time.CompactingStateBuffer;
import io.github.evoforge.simulation.time.ElapsedTimeTransition;
import io.github.evoforge.simulation.time.LongHorizonClock;
import io.github.evoforge.simulation.time.ScheduledWake;
import io.github.evoforge.simulation.time.SimulationInstant;
import io.github.evoforge.simulation.time.SleepingProcessScheduler;
import io.github.evoforge.simulation.time.WakeReason;
import java.util.ArrayList;
import java.util.List;

/** Plain Stage 2 proof model: old world age does not imply old-history storage or tick replay. */
public final class ContinuumInfiniteTimeInspectorModel {

    public static final long HUGE_JUMP_TICKS = 1_000_000_000_000_000L;
    public static final int PROCESS_COUNT = 6;
    public static final int HISTORY_TAIL_LIMIT = 64;

    private LongHorizonClock clock = new LongHorizonClock();
    private final SleepingProcessScheduler sleeping = new SleepingProcessScheduler();
    private CompactingStateBuffer<Long, Long> stateHistory = new CompactingStateBuffer<>(0L, Long::sum, HISTORY_TAIL_LIMIT);
    private final ArrayList<ProcessRow> rows = new ArrayList<>();

    private long lastJumpTicks;
    private int lastWakeOperations;
    private long totalWakeOperations;
    private long syntheticState;
    private int schedulerChurnQueueEntries;
    private int schedulerChurnHandleSlots;

    public ContinuumInfiniteTimeInspectorModel() {
        setYoungWorld();
    }

    public void setYoungWorld() {
        rebuildAt(SimulationInstant.fromTicks(10L));
    }

    public void setAncientWorld() {
        rebuildAt(new SimulationInstant(1_000_000L, 10L));
    }

    public void jumpHugeInterval() {
        clock.advanceBy(HUGE_JUMP_TICKS);
        List<ScheduledWake> due = sleeping.drainDue(clock.now());
        lastJumpTicks = HUGE_JUMP_TICKS;
        lastWakeOperations = due.size();

        ElapsedTimeTransition<Long> transition = (state, start, end) -> state + 1L;
        for (ScheduledWake wake : due) {
            syntheticState = transition.advance(syntheticState, wake.lastEvaluatedAt(), clock.now());
            totalWakeOperations++;
            markWoken(wake.processId());
        }
    }

    public void compactMillionChanges() {
        stateHistory = new CompactingStateBuffer<>(0L, Long::sum, HISTORY_TAIL_LIMIT);
        for (int i = 0; i < 1_000_000; i++) {
            stateHistory.append(1L);
        }
    }

    public SimulationInstant now() {
        return clock.now();
    }

    public List<ProcessRow> processRows() {
        return List.copyOf(rows);
    }

    public int sleepingProcesses() {
        return sleeping.sleepingCount();
    }

    public int queuedWakeEntries() {
        return sleeping.queuedEntryCount();
    }

    public long lastJumpTicks() {
        return lastJumpTicks;
    }

    public int lastWakeOperations() {
        return lastWakeOperations;
    }

    public long totalWakeOperations() {
        return totalWakeOperations;
    }

    public long compactedCurrentState() {
        return stateHistory.currentState();
    }

    public int retainedHistoryEntries() {
        return stateHistory.tailSize();
    }

    public long compactions() {
        return stateHistory.compactions();
    }

    public int schedulerChurnQueueEntries() {
        return schedulerChurnQueueEntries;
    }

    public int schedulerChurnHandleSlots() {
        return schedulerChurnHandleSlots;
    }

    private void rebuildAt(SimulationInstant age) {
        clearSleepingScheduler();
        clock = new LongHorizonClock(age);
        rows.clear();
        lastJumpTicks = 0L;
        lastWakeOperations = 0;
        totalWakeOperations = 0L;
        syntheticState = 0L;
        stateHistory = new CompactingStateBuffer<>(0L, Long::sum, HISTORY_TAIL_LIMIT);

        for (int i = 0; i < PROCESS_COUNT; i++) {
            long processId = i + 1L;
            SimulationInstant wakeAt = age.plusTicks(100L + i * 100L);
            sleeping.sleepUntil(processId, age, wakeAt, WakeReason.scheduled());
            rows.add(new ProcessRow(processId, wakeAt, true));
        }
        runGenericSchedulerChurnProof();
    }

    private void clearSleepingScheduler() {
        for (ProcessRow row : rows) {
            sleeping.cancel(row.processId());
        }
    }

    private void markWoken(long processId) {
        for (int i = 0; i < rows.size(); i++) {
            ProcessRow row = rows.get(i);
            if (row.processId() == processId) {
                rows.set(i, new ProcessRow(processId, row.wakeAt(), false));
                return;
            }
        }
    }

    private void runGenericSchedulerChurnProof() {
        var handlers = new io.github.evoforge.simulation.time.HandlerRegistry();
        var handler = handlers.register(processId -> {});
        var scheduler = new io.github.evoforge.simulation.time.Scheduler(handlers);
        for (int i = 0; i < 10_000; i++) {
            var handle = scheduler.schedule(Long.MAX_VALUE, handler, 1L);
            scheduler.cancel(handle);
        }
        schedulerChurnQueueEntries = scheduler.queuedEntryCount();
        schedulerChurnHandleSlots = scheduler.allocatedHandleSlotCount();
    }

    public record ProcessRow(long processId, SimulationInstant wakeAt, boolean sleeping) {
    }
}
