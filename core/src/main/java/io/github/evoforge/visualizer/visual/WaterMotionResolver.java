package io.github.evoforge.visualizer.visual;

import io.github.evoforge.simulation.world.liquid.water.WaterFlowLookup;
import io.github.evoforge.simulation.world.liquid.water.WaterFlowSample;
import io.github.evoforge.simulation.world.liquid.water.WaterLookup;

/**
 * Maps objective latest-step Water transfers to presentation motion.
 *
 * <p>No hydraulic slope is inferred here: if the solver performed no coherent net
 * transfer for a cell, presentation is calm even when individual edge transfers
 * cancelled or a theoretical neighbour head is lower.
 *
 * <p>Horizontal flow is animated only when the coherent net flux is significant
 * relative to the Water currently occupying that cell. This keeps tiny late-stage
 * equalization currents in a deep pool visually calm while preserving readable
 * motion in shallow puddles and deliberate flow tests. Actual downward flow remains
 * visible regardless of that horizontal significance threshold.
 */
public final class WaterMotionResolver {
    private static final long FLOW_SIGNIFICANCE_SCALE = 1_000L;
    private static final long MIN_VISIBLE_HORIZONTAL_FLOW_PER_MILLE = 10L;

    private final WaterFlowLookup flow;
    private final WaterLookup water;

    public WaterMotionResolver(
            WaterFlowLookup flow,
            WaterLookup water) {
        if (flow == null || water == null) {
            throw new IllegalArgumentException(
                    "water motion dependencies must not be null");
        }
        this.flow = flow;
        this.water = water;
    }

    public WaterMotion resolve(int x, int y, int z) {
        WaterFlowSample sample = flow.find(x, y, z);
        if (sample == null) return WaterMotion.CALM;
        if (sample.dz() < 0) return WaterMotion.FALLING;
        if (!significantHorizontalFlow(sample.amount(), water.amount(x, y, z))) {
            return WaterMotion.CALM;
        }
        if (sample.dx() < 0) return WaterMotion.WEST;
        if (sample.dx() > 0) return WaterMotion.EAST;
        if (sample.dy() < 0) return WaterMotion.SOUTH;
        if (sample.dy() > 0) return WaterMotion.NORTH;
        return WaterMotion.CALM;
    }

    static boolean significantHorizontalFlow(
            int netFlowAmount,
            int localWaterAmount) {
        if (netFlowAmount <= 0 || localWaterAmount <= 0) return false;
        return (long) netFlowAmount * FLOW_SIGNIFICANCE_SCALE
                >= (long) localWaterAmount * MIN_VISIBLE_HORIZONTAL_FLOW_PER_MILLE;
    }
}
