package io.github.evoforge.simulation.world.soil;

import io.github.evoforge.simulation.world.liquid.LiquidTypeId;

/** Read-only retained liquid composition inside Soil cells. */
public interface SoilLiquidLookup {

    /** Returns the retained amount of one liquid constituent. */
    int amountOf(LiquidTypeId type, int x, int y, int z);

    /** Returns total pore volume currently occupied by all retained liquids. */
    int totalAmount(int x, int y, int z);
}
