package io.github.evoforge.simulation.world.landscape.soil;

import io.github.evoforge.simulation.world.landscape.liquid.LiquidTypeId;

/**
 * Resolves the effective per-step infiltration limit for one liquid at one Soil cell.
 *
 * <p>The current default preserves the material's existing hydrology limit. Future
 * definition-backed resolvers may vary that limit by liquid/material interaction
 * without adding liquid-name switches to the generic Soil system.
 */
@FunctionalInterface
public interface SoilLiquidInteractionLookup {

    SoilLiquidInteractionLookup DEFAULT =
            (type, x, y, z, hydrology) -> hydrology.infiltrationLimit();

    int infiltrationLimitAt(
            LiquidTypeId type,
            int x,
            int y,
            int z,
            SoilHydrology hydrology);
}
