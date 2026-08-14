package io.github.evoforge.simulation.world.landscape.water;

public interface WaterStorage {

    int amount(int x, int y, int z);

    /** Stores a strictly positive validated amount. */
    void put(int x, int y, int z, int amount);

    void remove(int x, int y, int z);
}
