package io.github.evoforge.simulation.world.landscape.soil;

public interface SoilMoistureStorage {

    int amount(int x, int y, int z);

    void put(int x, int y, int z, int amount);

    void remove(int x, int y, int z);
}
