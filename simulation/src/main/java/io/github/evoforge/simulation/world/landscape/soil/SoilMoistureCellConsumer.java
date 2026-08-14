package io.github.evoforge.simulation.world.landscape.soil;

@FunctionalInterface
public interface SoilMoistureCellConsumer {

    void accept(int x, int y, int z);
}
