package io.github.evoforge.simulation.world.landscape.water;

import io.github.evoforge.simulation.time.ProcessScheduler;

/** Scheduler adapter that advances active surface hydrology one local step per tick. */
public final class WaterFlowProcess {

    private static final long PROCESS_ID = 0L;
    private static final long STEP_DELAY_TICKS = 1L;

    private final WaterFlowSystem flow;
    private final WaterSoilExchangeSystem soilExchange;
    private ProcessScheduler scheduler;
    private boolean scheduled;

    /** Backward-compatible flow-only process used by isolated solver tests. */
    public WaterFlowProcess(WaterFlowSystem flow) {
        this(flow, null);
    }

    public WaterFlowProcess(
            WaterFlowSystem flow,
            WaterSoilExchangeSystem soilExchange) {

        if (flow == null) {
            throw new IllegalArgumentException(
                    "flow must not be null");
        }
        this.flow = flow;
        this.soilExchange = soilExchange;
    }

    public void bindScheduler(ProcessScheduler scheduler) {
        if (scheduler == null) {
            throw new IllegalArgumentException(
                    "scheduler must not be null");
        }
        if (this.scheduler != null) {
            throw new IllegalStateException(
                    "water flow scheduler is already bound");
        }
        this.scheduler = scheduler;
    }

    /**
     * Ensures active hydrologic work has one scheduled continuation.
     * Repeated wakeups coalesce while a continuation is already scheduled.
     */
    public void activate() {
        requireScheduler();
        if (scheduled || flow.activeCellCount() == 0) {
            return;
        }

        scheduled = true;
        scheduler.scheduleAfter(
                STEP_DELAY_TICKS,
                PROCESS_ID);
    }

    public void resume(long processId) {
        requireScheduler();
        if (processId != PROCESS_ID) {
            throw new IllegalStateException(
                    "unknown water flow process: " + processId);
        }
        if (!scheduled) {
            throw new IllegalStateException(
                    "water flow process is not scheduled");
        }

        scheduled = false;
        if (soilExchange != null) {
            soilExchange.update();
        }
        flow.update();
        activate();
    }

    public boolean scheduled() {
        return scheduled;
    }

    private void requireScheduler() {
        if (scheduler == null) {
            throw new IllegalStateException(
                    "water flow scheduler is not bound");
        }
    }
}
