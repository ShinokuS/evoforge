package io.github.evoforge.simulation.world.liquid;

import io.github.evoforge.simulation.kernel.scheduling.ProcessScheduler;

/** Advances active free-liquid work one deterministic local solve per tick. */
public final class LiquidFlowProcess {

    private static final long PROCESS_ID = 0L;
    private static final long STEP_DELAY_TICKS = 1L;

    private final LiquidFlowSystem flow;
    private final LiquidFlowPreparation preparation;
    private ProcessScheduler scheduler;
    private boolean scheduled;

    public LiquidFlowProcess(LiquidFlowSystem flow) {
        this(flow, LiquidFlowPreparation.NONE);
    }

    public LiquidFlowProcess(
            LiquidFlowSystem flow,
            LiquidFlowPreparation preparation) {

        if (flow == null || preparation == null) {
            throw new IllegalArgumentException(
                    "liquid flow process dependencies must not be null");
        }
        this.flow = flow;
        this.preparation = preparation;
    }

    public void bindScheduler(ProcessScheduler scheduler) {
        if (scheduler == null) {
            throw new IllegalArgumentException("scheduler must not be null");
        }
        if (this.scheduler != null) {
            throw new IllegalStateException(
                    "liquid flow scheduler is already bound");
        }
        this.scheduler = scheduler;
    }

    /** Coalesces wakeups while one continuation is already scheduled. */
    public void activate() {
        requireScheduler();
        if (scheduled || flow.activeCellCount() == 0) return;

        scheduled = true;
        scheduler.scheduleAfter(STEP_DELAY_TICKS, PROCESS_ID);
    }

    public void resume(long processId) {
        requireScheduler();
        if (processId != PROCESS_ID) {
            throw new IllegalStateException(
                    "unknown liquid flow process: " + processId);
        }
        if (!scheduled) {
            throw new IllegalStateException(
                    "liquid flow process is not scheduled");
        }

        scheduled = false;
        preparation.prepare();
        flow.update();
        activate();
    }

    public boolean scheduled() {
        return scheduled;
    }

    private void requireScheduler() {
        if (scheduler == null) {
            throw new IllegalStateException(
                    "liquid flow scheduler is not bound");
        }
    }
}
