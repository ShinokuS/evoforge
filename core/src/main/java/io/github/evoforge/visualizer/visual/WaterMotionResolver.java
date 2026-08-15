package io.github.evoforge.visualizer.visual;

import io.github.evoforge.simulation.world.landscape.water.WaterFlowLookup;
import io.github.evoforge.simulation.world.landscape.water.WaterFlowSample;

/**
 * Maps objective latest-step Water transfers to presentation motion.
 *
 * <p>No hydraulic slope is inferred here: if the solver performed no transfer for
 * a cell, presentation is calm even when a theoretical neighbour head is lower.
 */
public final class WaterMotionResolver {

    private final WaterFlowLookup flow;

    public WaterMotionResolver(WaterFlowLookup flow) {
        if (flow == null) {
            throw new IllegalArgumentException("water flow lookup must not be null");
        }
        this.flow = flow;
    }

    public WaterMotion resolve(int x, int y, int z) {
        WaterFlowSample sample = flow.find(x, y, z);
        if (sample == null) {
            return WaterMotion.CALM;
        }
        if (sample.dz() < 0) {
            return WaterMotion.FALLING;
        }
        if (sample.dx() < 0) {
            return WaterMotion.WEST;
        }
        if (sample.dx() > 0) {
            return WaterMotion.EAST;
        }
        if (sample.dy() < 0) {
            return WaterMotion.SOUTH;
        }
        if (sample.dy() > 0) {
            return WaterMotion.NORTH;
        }
        return WaterMotion.CALM;
    }
}
