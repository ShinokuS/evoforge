package io.github.evoforge.simulation.time;

/** Read-only longevity diagnostics for sleeping-process scheduling. */
public record SleepingProcessSchedulerMetrics(
        long schedules,
        long replacements,
        long cancellations,
        long deliveredWakeups,
        int sleepingProcesses,
        int queuedEntries) {
}
