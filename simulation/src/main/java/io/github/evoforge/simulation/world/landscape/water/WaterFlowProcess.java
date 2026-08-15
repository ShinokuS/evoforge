package io.github.evoforge.simulation.world.landscape.water;

import io.github.evoforge.simulation.time.ProcessScheduler;
import io.github.evoforge.simulation.world.landscape.liquid.LiquidFlowPreparation;
import io.github.evoforge.simulation.world.landscape.liquid.LiquidFlowProcess;

/**
 * Water-hydrology adapter around the shared scheduled liquid-flow process.
 *
 * <p>The generic process owns cadence/dormancy. Water contributes only its
 * deterministic pre-flow Soil exchange.
 */
public final class WaterFlowProcess {

    private final LiquidFlowProcess delegate;

    /** Flow-only adapter used by isolated Water solver tests. */
    public WaterFlowProcess(WaterFlowSystem flow) {
        this(flow, LiquidFlowPreparation.NONE);
    }

    public WaterFlowProcess(
            WaterFlowSystem flow,
            WaterSoilExchangeSystem soilExchange) {

        if (soilExchange == null) {
            throw new IllegalArgumentException("soil exchange must not be null");
        }
        this.delegate = createDelegate(flow, soilExchange::update);
    }

    private WaterFlowProcess(
            WaterFlowSystem flow,
            LiquidFlowPreparation preparation) {

        this.delegate = createDelegate(flow, preparation);
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

    private static LiquidFlowProcess createDelegate(
            WaterFlowSystem flow,
            LiquidFlowPreparation preparation) {

        if (flow == null) {
            throw new IllegalArgumentException("flow must not be null");
        }
        return new LiquidFlowProcess(
                flow.liquidFlowSystem(),
                preparation);
    }
}
