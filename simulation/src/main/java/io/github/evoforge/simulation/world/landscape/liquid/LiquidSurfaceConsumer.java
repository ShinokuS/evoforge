package io.github.evoforge.simulation.world.landscape.liquid;

@FunctionalInterface
public interface LiquidSurfaceConsumer {

    void accept(int x, int y, int z, LiquidTypeId type);
}
