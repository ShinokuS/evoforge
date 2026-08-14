package io.github.evoforge.simulation.world.landscape.water;

@FunctionalInterface
public interface WaterSurfaceConsumer {

    void accept(int x, int y, int z);
}
