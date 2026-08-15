package io.github.evoforge.simulation.world.environment.precipitation;

import io.github.evoforge.simulation.time.ProcessScheduler;
import io.github.evoforge.simulation.time.SimulationTime;

/** Owns cadence for one configured world-level precipitation source. */
public final class PeriodicPrecipitationSystem
        implements PrecipitationEventLookup {

    private static final long PROCESS_ID = 0L;

    private final SkyPrecipitationSystem precipitation;
    private final PrecipitationSchedule schedule;
    private final SimulationTime time;
    private ProcessScheduler scheduler;
    private boolean started;
    private boolean scheduled;
    private long nextEvaluationTick = -1L;
    private long lastEvaluationTick = -1L;
    private PrecipitationBatchResult lastResult;

    public PeriodicPrecipitationSystem(
            SkyPrecipitationSystem precipitation,
            PrecipitationSchedule schedule,
            SimulationTime time) {

        if (precipitation == null
                || schedule == null
                || time == null) {
            throw new IllegalArgumentException(
                    "periodic precipitation dependencies must not be null");
        }

        this.precipitation = precipitation;
        this.schedule = schedule;
        this.time = time;
    }

    public void bindScheduler(ProcessScheduler scheduler) {
        if (scheduler == null) {
            throw new IllegalArgumentException(
                    "scheduler must not be null");
        }
        if (this.scheduler != null) {
            throw new IllegalStateException(
                    "precipitation scheduler is already bound");
        }
        this.scheduler = scheduler;
    }

    public void start() {
        requireScheduler();
        if (started) {
            throw new IllegalStateException(
                    "periodic precipitation has already started");
        }
        started = true;
        scheduleNext();
    }

    public void resume(long processId) {
        requireScheduler();
        if (!started) {
            throw new IllegalStateException(
                    "periodic precipitation has not started");
        }
        if (processId != PROCESS_ID) {
            throw new IllegalStateException(
                    "unknown precipitation process: " + processId);
        }
        if (!scheduled) {
            throw new IllegalStateException(
                    "precipitation process is not scheduled");
        }

        scheduled = false;
        nextEvaluationTick = -1L;
        lastEvaluationTick = time.tick();
        lastResult = precipitation.applyUniform(
                schedule.amountPerColumn());
        scheduleNext();
    }

    @Override
    public boolean occursAt(long tick) {
        return tick == lastEvaluationTick
                || (scheduled && tick == nextEvaluationTick);
    }

    /** Whether the configured atmospheric rain window is active at this tick. */
    public boolean activeAt(long tick) {
        return schedule.activeAt(tick);
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

    public PrecipitationBatchResult lastResult() {
        return lastResult;
    }

    private void scheduleNext() {
        if (scheduled) {
            throw new IllegalStateException(
                    "precipitation process is already scheduled");
        }

        long delay = schedule.delayToNextEvent(time.tick());
        try {
            nextEvaluationTick = Math.addExact(
                    time.tick(),
                    delay);
        } catch (ArithmeticException exception) {
            throw new IllegalStateException(
                    "precipitation schedule tick overflow",
                    exception);
        }

        scheduled = true;
        scheduler.scheduleAfter(
                delay,
                PROCESS_ID);
    }

    private void requireScheduler() {
        if (scheduler == null) {
            throw new IllegalStateException(
                    "precipitation scheduler is not bound");
        }
    }
}
