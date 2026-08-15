package io.github.evoforge.simulation.world.landscape.liquid;

/** Replaceable storage port for positive single-component free-liquid cells. */
public interface LiquidStorage {

    LiquidTypeId typeAt(int x, int y, int z);

    int amount(int x, int y, int z);

    /** Stores a strictly positive validated amount of one liquid type. */
    void put(int x, int y, int z, LiquidTypeId type, int amount);

    void remove(int x, int y, int z);
}
