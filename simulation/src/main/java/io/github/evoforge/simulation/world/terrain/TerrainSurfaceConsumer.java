package io.github.evoforge.simulation.world.terrain;

@FunctionalInterface
public interface TerrainSurfaceConsumer {

    void accept(int x, int y, int z);
}
