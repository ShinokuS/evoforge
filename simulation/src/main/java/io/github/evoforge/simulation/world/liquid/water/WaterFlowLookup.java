package io.github.evoforge.simulation.world.liquid.water;

import io.github.evoforge.simulation.world.liquid.LiquidFlowLookup;
import io.github.evoforge.simulation.world.liquid.LiquidFlowSample;

/** Read-only latest actual Water transfer sample at a world cell. */
@FunctionalInterface
public interface WaterFlowLookup {

    WaterFlowLookup NONE = (x, y, z) -> null;

    WaterFlowSample find(int x, int y, int z);

    /** Projects Water-only diagnostics from one shared generic liquid-flow lookup. */
    static WaterFlowLookup from(LiquidFlowLookup flow) {
        if (flow == null) {
            throw new IllegalArgumentException("liquid flow lookup must not be null");
        }
        return (x, y, z) -> {
            LiquidFlowSample sample = flow.find(x, y, z);
            if (sample == null || !WaterSystem.TYPE.equals(sample.type())) {
                return null;
            }
            return new WaterFlowSample(
                    sample.dx(),
                    sample.dy(),
                    sample.dz(),
                    sample.amount());
        };
    }
}
