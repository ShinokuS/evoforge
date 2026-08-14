package io.github.evoforge.simulation.world.landscape.soil;

import io.github.evoforge.simulation.world.mechanics.geometry.CellVolume;

/**
 * Material hydrology declared by a landscape definition.
 *
 * <p>Both values use the same normalized fixed-point volume scale as surface water.
 * {@code capacity} is the maximum moisture retained by one terrain cell and
 * {@code infiltrationPerTick} bounds how much precipitation may enter that soil in
 * one simulation tick.
 */
public record SoilHydrology(
        int capacity,
        int infiltrationPerTick) {

    public SoilHydrology {
        CellVolume.requireValid(capacity);
        CellVolume.requireValid(infiltrationPerTick);
    }
}
