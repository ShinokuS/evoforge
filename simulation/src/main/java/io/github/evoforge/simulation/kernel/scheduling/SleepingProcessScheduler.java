package io.github.evoforge.simulation.kernel.scheduling;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;
import io.github.evoforge.simulation.kernel.time.SimulationInstant;

/**
 * Stores only the current wake obligation of each sleeping process.
 *
 * <p>Rescheduling a process replaces its previous entry, so retained memory follows the current
 * sleeping working set instead of the number of historical reschedules.</p>
 */
public final class SleepingProcessScheduler {

    private static final Comparator<ScheduledWake> ORDER = Comparator
            .comparing(ScheduledWake::wakeAt)
            .thenComparingLong(ScheduledWake::processId);

    private final NavigableSet<ScheduledWake> byTime = new TreeSet<>(ORDER);
    private final Map<Long, ScheduledWake> byProcess = new HashMap<>();

    private long schedules;
    private long replacements;
    private long cancellations;
    private long deliveredWakeups;

    public void sleepUntil(
            long processId,
            SimulationInstant lastEvaluatedAt,
            SimulationInstant wakeAt,
            WakeReason reason) {

        ScheduledWake next = new ScheduledWake(processId, lastEvaluatedAt, wakeAt, reason);
        ScheduledWake previous = byProcess.put(processId, next);
        if (previous != null) {
            byTime.remove(previous);
            replacements++;
        }
        byTime.add(next);
        schedules++;
    }

    public boolean cancel(long processId) {
        ScheduledWake previous = byProcess.remove(processId);
        if (previous == null) {
            return false;
        }
        byTime.remove(previous);
        cancellations++;
        return true;
    }

    /** Removes and returns all processes whose wake time is at or before {@code now}. */
    public List<ScheduledWake> drainDue(SimulationInstant now) {
        if (now == null) {
            throw new IllegalArgumentException("now must not be null");
        }

        List<ScheduledWake> due = new ArrayList<>();
        while (!byTime.isEmpty() && byTime.first().wakeAt().compareTo(now) <= 0) {
            ScheduledWake wake = byTime.pollFirst();
            ScheduledWake current = byProcess.get(wake.processId());
            if (current != wake) {
                throw new IllegalStateException("sleep scheduler indices diverged");
            }
            byProcess.remove(wake.processId());
            due.add(wake);
            deliveredWakeups++;
        }
        return List.copyOf(due);
    }

    public ScheduledWake wakeFor(long processId) {
        return byProcess.get(processId);
    }

    public ScheduledWake nextWake() {
        return byTime.isEmpty() ? null : byTime.first();
    }

    public int sleepingCount() {
        return byProcess.size();
    }

    /** Physical entries retained now; must always match the sleeping set. */
    public int queuedEntryCount() {
        return byTime.size();
    }

    public SleepingProcessSchedulerMetrics metrics() {
        return new SleepingProcessSchedulerMetrics(
                schedules,
                replacements,
                cancellations,
                deliveredWakeups,
                sleepingCount(),
                queuedEntryCount());
    }
}
