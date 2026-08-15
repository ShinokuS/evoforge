package io.github.evoforge.simulation.world.landscape.liquid;

import io.github.evoforge.simulation.world.mechanics.geometry.CellVolume;

/**
 * Free-liquid volume retained by a supporting surface before horizontal runoff.
 *
 * <p>The lookup is typed because future materials may retain different liquids
 * differently. Vertical falling is intentionally unaffected by this capability.
 */
@FunctionalInterface
public interface LiquidSurfaceRetentionLookup {

    LiquidSurfaceRetentionLookup NONE =
            (type, x, y, z) -> CellVolume.EMPTY;

    int capacityAt(
            LiquidTypeId type,
            int x,
            int y,
            int z);
}
