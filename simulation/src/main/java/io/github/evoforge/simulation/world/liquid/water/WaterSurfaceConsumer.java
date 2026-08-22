package io.github.evoforge.simulation.world.liquid.water;

@FunctionalInterface
public interface WaterSurfaceConsumer {

    void accept(int x, int y, int z);
}
