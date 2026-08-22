package io.github.evoforge.simulation.kernel.scheduling;

import io.github.evoforge.simulation.kernel.time.SimulationInstant;

/** One current future obligation for a sleeping process. */
public record ScheduledWake(
        long processId,
        SimulationInstant lastEvaluatedAt,
        SimulationInstant wakeAt,
        WakeReason reason) {

    public ScheduledWake {
        if (processId < 0L) {
            throw new IllegalArgumentException("processId must be >= 0");
        }
        if (lastEvaluatedAt == null || wakeAt == null || reason == null) {
            throw new IllegalArgumentException("time and reason must not be null");
        }
        if (wakeAt.compareTo(lastEvaluatedAt) < 0) {
            throw new IllegalArgumentException("wakeAt must not be before lastEvaluatedAt");
        }
    }
}
