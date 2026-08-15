package io.github.evoforge.simulation.world.landscape.soil;

@FunctionalInterface
public interface SoilLiquidCellConsumer {
    void accept(int x, int y, int z);
}
