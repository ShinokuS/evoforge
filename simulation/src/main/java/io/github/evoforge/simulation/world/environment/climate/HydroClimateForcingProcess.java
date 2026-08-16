package io.github.evoforge.simulation.world.environment.climate;

import io.github.evoforge.simulation.time.ProcessScheduler;
import io.github.evoforge.simulation.time.SimulationTime;

/**
 * Scheduler-owned cadence adapter for generated hydrologic climate forcing.
 *
 * <p>The process owns only activation/cadence metadata. Water and Soil mutations remain
 * delegated through {@link HydroClimateForcingSystem}; simulation time remains owned by the
 * runtime clock.</p>
 */
public final class HydroClimateForcingProcess {

    private static final long PROCESS_ID = 0L;

    private final HydroClimateForcingSystem forcing;
    private final SimulationTime time;
    private ProcessScheduler scheduler;
    private boolean started;
    private boolean scheduled;
    private long nextEvaluationTick = -1L;
    private long lastEvaluationTick = -1L;
    private HydroClimateForcingResult lastResult;

    public HydroClimateForcingProcess(
            HydroClimateForcingSystem forcing,
            SimulationTime time) {
        if (forcing == null || time == null) {
            throw new IllegalArgumentException(
                    "hydro-climate forcing process dependencies must not be null");
        }
        this.forcing = forcing;
        this.time = time;
    }

    public void bindScheduler(ProcessScheduler scheduler) {
        if (scheduler == null) {
            throw new IllegalArgumentException("scheduler must not be null");
        }
        if (this.scheduler != null) {
            throw new IllegalStateException(
                    "hydro-climate forcing scheduler is already bound");
        }
        this.scheduler = scheduler;
    }

    /** Starts one forcing evaluation for every subsequently advanced simulation tick. */
    public void start() {
        requireScheduler();
        if (started) {
            throw new IllegalStateException(
                    "hydro-climate forcing process has already started");
        }
        started = true;
        scheduleNext();
    }

    public void resume(long processId) {
        requireScheduler();
        if (!started) {
            throw new IllegalStateException(
                    "hydro-climate forcing process has not started");
        }
        if (processId != PROCESS_ID) {
            throw new IllegalStateException(
                    "unknown hydro-climate forcing process: " + processId);
        }
        if (!scheduled) {
            throw new IllegalStateException(
                    "hydro-climate forcing process is not scheduled");
        }

        scheduled = false;
        nextEvaluationTick = -1L;
        lastEvaluationTick = time.tick();
        lastResult = forcing.update(lastEvaluationTick);
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

    public HydroClimateForcingResult lastResult() {
        return lastResult;
    }

    private void scheduleNext() {
        if (scheduled) {
            throw new IllegalStateException(
                    "hydro-climate forcing process is already scheduled");
        }
        if (time.tick() == Long.MAX_VALUE) {
            throw new IllegalStateException(
                    "hydro-climate forcing schedule tick overflow");
        }

        nextEvaluationTick = time.tick() + 1L;
        scheduled = true;
        scheduler.scheduleAfter(1L, PROCESS_ID);
    }

    private void requireScheduler() {
        if (scheduler == null) {
            throw new IllegalStateException(
                    "hydro-climate forcing scheduler is not bound");
        }
    }
}
