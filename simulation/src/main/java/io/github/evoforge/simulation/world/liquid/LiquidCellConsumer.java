package io.github.evoforge.simulation.world.liquid;

@FunctionalInterface
public interface LiquidCellConsumer {

    void accept(int x, int y, int z);
}
