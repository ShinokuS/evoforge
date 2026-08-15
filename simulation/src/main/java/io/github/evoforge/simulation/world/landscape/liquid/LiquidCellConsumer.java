package io.github.evoforge.simulation.world.landscape.liquid;

@FunctionalInterface
public interface LiquidCellConsumer {

    void accept(int x, int y, int z);
}
