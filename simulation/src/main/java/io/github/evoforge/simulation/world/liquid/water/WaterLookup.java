package io.github.evoforge.simulation.world.liquid.water;

@FunctionalInterface
public interface WaterLookup {

    /** Liquid volume currently stored at the addressed world cell. */
    int amount(int x, int y, int z);
}
