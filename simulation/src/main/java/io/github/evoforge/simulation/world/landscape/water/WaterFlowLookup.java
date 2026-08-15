package io.github.evoforge.simulation.world.landscape.water;

/** Read-only actual transfer state from the latest Water solver step. */
@FunctionalInterface
public interface WaterFlowLookup {

    WaterFlowLookup NONE = (x, y, z) -> null;

    /** Returns the dominant actual transfer through this cell, or null when calm. */
    WaterFlowSample find(int x, int y, int z);
}
