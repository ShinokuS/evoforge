package io.github.evoforge.simulation.world.landscape.liquid;

import io.github.evoforge.simulation.world.mechanics.geometry.CellVolume;

/**
 * Free-liquid volume retained by supporting surface microtopography before
 * horizontal runoff.
 *
 * <p>The current terrain implementation is material-owned and identical for every
 * liquid identity. The type remains part of the port so a future physical wetting
 * model can derive retention from liquid/surface properties without changing the
 * hydraulic solver. Concrete identity switches and pair tables do not belong here.
 * Vertical falling is intentionally unaffected.
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
