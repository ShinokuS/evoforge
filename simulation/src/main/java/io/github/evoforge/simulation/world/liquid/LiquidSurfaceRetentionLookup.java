package io.github.evoforge.simulation.world.liquid;

import io.github.evoforge.simulation.world.space.measurement.CellVolume;

/**
 * Free-liquid volume retained by supporting surface microtopography before
 * horizontal runoff.
 *
 * <p>This is a property of the supporting material/geometry, not a concrete
 * liquid/material pair table. If future wetting physics needs surface tension or
 * contact angle, that mechanic should introduce physical capabilities rather than
 * identity switches. Vertical falling is intentionally unaffected.
 */
@FunctionalInterface
public interface LiquidSurfaceRetentionLookup {

    LiquidSurfaceRetentionLookup NONE =
            (x, y, z) -> CellVolume.EMPTY;

    int capacityAt(int x, int y, int z);
}
