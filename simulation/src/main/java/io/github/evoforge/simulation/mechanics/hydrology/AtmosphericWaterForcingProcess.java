package io.github.evoforge.simulation.mechanics.hydrology;

import io.github.evoforge.simulation.kernel.scheduling.ProcessScheduler;
import io.github.evoforge.simulation.kernel.time.SimulationTime;

/** Scheduler-owned cadence adapter for atmospheric Water forcing. */
public final class AtmosphericWaterForcingProcess {
    private static final long PROCESS_ID = 0L;

    private final AtmosphericWaterForcingSystem forcing;
    private final SimulationTime time;
    private ProcessScheduler scheduler;
    private boolean started;
    private boolean scheduled;
    private long nextEvaluationTick = -1L;
    private long lastEvaluationTick = -1L;
    private AtmosphericWaterForcingResult lastResult;

    public AtmosphericWaterForcingProcess(
            AtmosphericWaterForcingSystem forcing,
            SimulationTime time) {
        if (forcing == null || time == null) {
            throw new IllegalArgumentException("atmospheric forcing process dependencies must not be null");
        }
        this.forcing = forcing;
        this.time = time;
    }

    public void bindScheduler(ProcessScheduler scheduler) {
        if (scheduler == null) throw new IllegalArgumentException("scheduler must not be null");
        if (this.scheduler != null) {
            throw new IllegalStateException("atmospheric forcing scheduler is already bound");
        }
        this.scheduler = scheduler;
    }

    public void start() {
        requireScheduler();
        if (started) throw new IllegalStateException("atmospheric forcing process has already started");
        started = true;
        scheduleNext();
    }

    public void resume(long processId) {
        requireScheduler();
        if (!started) throw new IllegalStateException("atmospheric forcing process has not started");
        if (processId != PROCESS_ID) {
            throw new IllegalStateException("unknown atmospheric forcing process: " + processId);
        }
        if (!scheduled) throw new IllegalStateException("atmospheric forcing process is not scheduled");

        scheduled = false;
        nextEvaluationTick = -1L;
        lastEvaluationTick = time.tick();
        lastResult = forcing.update(lastEvaluationTick);
        scheduleNext();
    }

    public boolean scheduled() { return scheduled; }
    public long nextEvaluationTick() { return nextEvaluationTick; }
    public long lastEvaluationTick() { return lastEvaluationTick; }
    public AtmosphericWaterForcingResult lastResult() { return lastResult; }

    private void scheduleNext() {
        if (scheduled) throw new IllegalStateException("atmospheric forcing process is already scheduled");
        if (time.tick() == Long.MAX_VALUE) {
            throw new IllegalStateException("atmospheric forcing schedule tick overflow");
        }
        nextEvaluationTick = time.tick() + 1L;
        scheduled = true;
        scheduler.scheduleAfter(1L, PROCESS_ID);
    }

    private void requireScheduler() {
        if (scheduler == null) {
            throw new IllegalStateException("atmospheric forcing scheduler is not bound");
        }
    }
}
