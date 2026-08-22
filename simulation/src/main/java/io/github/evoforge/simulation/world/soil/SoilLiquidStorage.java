package io.github.evoforge.simulation.world.soil;

import io.github.evoforge.simulation.world.liquid.LiquidTypeId;

/** Replaceable storage port for retained liquid composition inside Soil cells. */
public interface SoilLiquidStorage {

    int amountOf(LiquidTypeId type, int x, int y, int z);

    int totalAmount(int x, int y, int z);

    /** Stores a strictly positive validated amount of one retained constituent. */
    void put(int x, int y, int z, LiquidTypeId type, int amount);

    void remove(int x, int y, int z, LiquidTypeId type);
}
