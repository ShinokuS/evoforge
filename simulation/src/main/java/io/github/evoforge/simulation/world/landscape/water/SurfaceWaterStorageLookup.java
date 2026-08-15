package io.github.evoforge.simulation.world.landscape.water;

import io.github.evoforge.simulation.world.mechanics.geometry.CellVolume;

/**
 * Objective surface storage available to a Water cell before horizontal runoff.
 *
 * <p>The value is retained free Water, not SoilMoisture and not a numerical solver
 * epsilon. Consumers decide when the capacity matters. Vertical falling Water must
 * not be blocked by this capability.
 */
@FunctionalInterface
public interface SurfaceWaterStorageLookup {

    SurfaceWaterStorageLookup NONE =
            (x, y, z) -> CellVolume.EMPTY;

    int capacityAtWaterCell(int x, int y, int z);
}
