package io.github.evoforge.simulation.world.soil;

import io.github.evoforge.simulation.world.liquid.LiquidTypeId;

/** Narrow mutation capability for retaining free liquid inside Soil pore volume. */
public interface SoilLiquidRetention {

    int infiltrateAtMost(
            LiquidTypeId type,
            int x,
            int y,
            int z,
            int requested);
}
