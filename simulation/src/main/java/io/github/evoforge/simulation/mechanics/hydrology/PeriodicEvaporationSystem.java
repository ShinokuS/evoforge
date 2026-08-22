package io.github.evoforge.simulation.mechanics.hydrology;

import io.github.evoforge.simulation.kernel.scheduling.ProcessScheduler;
import io.github.evoforge.simulation.kernel.time.SimulationTime;
import io.github.evoforge.simulation.mechanics.hydrology.PrecipitationEventLookup;

/** Owns cadence for the simple world-level evaporation sink. */
public final class PeriodicEvaporationSystem {

    private static final long PROCESS_ID = 0L;

    private final EvaporationSystem evaporation;
    private final EvaporationSchedule schedule;
    private final SimulationTime time;
    private final PrecipitationEventLookup precipitation;
    private ProcessScheduler scheduler;
    private boolean started;
    private boolean scheduled;
    private boolean lastSuppressed;
    private long nextEvaluationTick = -1L;
    private long lastEvaluationTick = -1L;
    private EvaporationBatchResult lastResult;

    public PeriodicEvaporationSystem(
            EvaporationSystem evaporation,
            EvaporationSchedule schedule,
            SimulationTime time,
            PrecipitationEventLookup precipitation) {

        if (evaporation == null
                || schedule == null
                || time == null
                || precipitation == null) {
            throw new IllegalArgumentException(
                    "periodic evaporation dependencies must not be null");
        }

        this.evaporation = evaporation;
        this.schedule = schedule;
        this.time = time;
        this.precipitation = precipitation;
    }

    public void bindScheduler(ProcessScheduler scheduler) {
        if (scheduler == null) {
            throw new IllegalArgumentException(
                    "scheduler must not be null");
        }
        if (this.scheduler != null) {
            throw new IllegalStateException(
                    "evaporation scheduler is already bound");
        }
        this.scheduler = scheduler;
    }

    public void start() {
        requireScheduler();
        if (started) {
            throw new IllegalStateException(
                    "periodic evaporation has already started");
        }
        started = true;
        scheduleNext();
    }

    public void resume(long processId) {
        requireScheduler();
        if (!started) {
            throw new IllegalStateException(
                    "periodic evaporation has not started");
        }
        if (processId != PROCESS_ID) {
            throw new IllegalStateException(
                    "unknown evaporation process: " + processId);
        }
        if (!scheduled) {
            throw new IllegalStateException(
                    "evaporation process is not scheduled");
        }

        scheduled = false;
        nextEvaluationTick = -1L;
        lastEvaluationTick = time.tick();
        lastSuppressed = precipitation.occursAt(lastEvaluationTick);
        lastResult = lastSuppressed
                ? EvaporationBatchResult.empty()
                : evaporation.applyUniform(schedule.amountPerColumn());
        scheduleNext();
    }

    public boolean scheduled() {
        return scheduled;
    }

    public long nextEvaluationTick() {
        return nextEvaluationTick;
    }

    public long lastEvaluationTick() {
        return lastEvaluationTick;
    }

    public boolean lastSuppressed() {
        return lastSuppressed;
    }

    public EvaporationBatchResult lastResult() {
        return lastResult;
    }

    private void scheduleNext() {
        if (scheduled) {
            throw new IllegalStateException(
                    "evaporation process is already scheduled");
        }

        try {
            nextEvaluationTick = Math.addExact(
                    time.tick(),
                    schedule.intervalTicks());
        } catch (ArithmeticException exception) {
            throw new IllegalStateException(
                    "evaporation schedule tick overflow",
                    exception);
        }

        scheduled = true;
        scheduler.scheduleAfter(
                schedule.intervalTicks(),
                PROCESS_ID);
    }

    private void requireScheduler() {
        if (scheduler == null) {
            throw new IllegalStateException(
                    "evaporation scheduler is not bound");
        }
    }
}
