package io.github.evoforge.simulation.world.landscape.water;

import io.github.evoforge.simulation.time.ProcessScheduler;
import io.github.evoforge.simulation.world.landscape.liquid.LiquidFlowPreparation;
import io.github.evoforge.simulation.world.landscape.liquid.LiquidFlowProcess;

/**
 * Water-facing adapter around the shared scheduled liquid-flow process.
 *
 * <p>The generic process owns cadence/dormancy and accepts a generic deterministic
 * pre-flow preparation capability. Current Water-oriented runtime wiring supplies
 * Soil liquid infiltration through that seam.
 */
public final class WaterFlowProcess {

    private final LiquidFlowProcess delegate;

    /** Flow-only adapter used by isolated Water solver tests. */
    public WaterFlowProcess(WaterFlowSystem flow) {
        this(flow, LiquidFlowPreparation.NONE);
    }

    public WaterFlowProcess(
            WaterFlowSystem flow,
            LiquidFlowPreparation preparation) {

        if (flow == null || preparation == null) {
            throw new IllegalArgumentException(
                    "Water flow process dependencies must not be null");
        }
        delegate = new LiquidFlowProcess(
                flow.liquidFlowSystem(),
                preparation);
    }

    public void bindScheduler(ProcessScheduler scheduler) {
        delegate.bindScheduler(scheduler);
    }

    public void activate() {
        delegate.activate();
    }

    public void resume(long processId) {
        delegate.resume(processId);
    }

    public boolean scheduled() {
        return delegate.scheduled();
    }
}
