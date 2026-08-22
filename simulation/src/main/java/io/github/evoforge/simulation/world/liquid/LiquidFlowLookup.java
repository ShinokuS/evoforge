package io.github.evoforge.simulation.world.liquid;

@FunctionalInterface
public interface LiquidFlowLookup {

    LiquidFlowLookup NONE = (x, y, z) -> null;

    LiquidFlowSample find(int x, int y, int z);
}
