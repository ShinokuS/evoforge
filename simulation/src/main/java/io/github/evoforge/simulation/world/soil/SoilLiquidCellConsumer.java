package io.github.evoforge.simulation.world.soil;

@FunctionalInterface
public interface SoilLiquidCellConsumer {
    void accept(int x, int y, int z);
}
