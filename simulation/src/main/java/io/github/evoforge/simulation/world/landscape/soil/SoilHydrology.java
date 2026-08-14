package io.github.evoforge.simulation.world.landscape.soil;

import io.github.evoforge.simulation.world.mechanics.geometry.CellVolume;

/**
 * Material hydrology declared by a landscape definition.
 *
 * <p>Both values use the same normalized fixed-point volume scale as surface water.
 * {@code capacity} is the maximum moisture retained by one terrain cell and
 * {@code infiltrationLimit} bounds one requested infiltration transfer. Time-based
 * rainfall rates remain the responsibility of a future weather process that owns
 * simulation cadence.
 */
public record SoilHydrology(
        int capacity,
        int infiltrationLimit) {

    public SoilHydrology {
        CellVolume.requireValid(capacity);
        CellVolume.requireValid(infiltrationLimit);
    }
}
