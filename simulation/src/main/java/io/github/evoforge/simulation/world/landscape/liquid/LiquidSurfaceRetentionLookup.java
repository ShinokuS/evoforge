package io.github.evoforge.simulation.world.landscape.liquid;

import io.github.evoforge.simulation.world.mechanics.geometry.CellVolume;

/**
 * Free-liquid volume retained by supporting surface microtopography before
 * horizontal runoff.
 *
 * <p>The current capability is a property of the supporting material/geometry,
 * not a liquid/material pair table. Vertical falling is intentionally unaffected.
 * If future wetting physics needs surface tension/contact-angle effects, those must
 * be derived from physical capabilities rather than concrete liquid identities.
 */
@FunctionalInterface
public interface LiquidSurfaceRetentionLookup {

    LiquidSurfaceRetentionLookup NONE =
            (x, y, z) -> CellVolume.EMPTY;

    int capacityAt(int x, int y, int z);
}
