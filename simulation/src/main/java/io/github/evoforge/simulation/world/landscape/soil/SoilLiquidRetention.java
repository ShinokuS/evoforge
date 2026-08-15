package io.github.evoforge.simulation.world.landscape.soil;

import io.github.evoforge.simulation.world.landscape.liquid.LiquidTypeId;

/** Narrow mutation capability for retaining free liquid inside Soil pore volume. */
public interface SoilLiquidRetention {

    int infiltrateAtMost(
            LiquidTypeId type,
            int x,
            int y,
            int z,
            int requested);
}
