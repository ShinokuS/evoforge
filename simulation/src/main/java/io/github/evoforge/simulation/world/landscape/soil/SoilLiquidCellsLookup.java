package io.github.evoforge.simulation.world.landscape.soil;

import io.github.evoforge.simulation.world.landscape.liquid.LiquidTypeId;

/** Deterministic retained-liquid cell iteration, both aggregate and by constituent. */
public interface SoilLiquidCellsLookup {

    int occupiedCellCount();

    int cellCount(LiquidTypeId type);

    void forEach(SoilLiquidCellConsumer consumer);

    void forEach(LiquidTypeId type, SoilLiquidCellConsumer consumer);
}
