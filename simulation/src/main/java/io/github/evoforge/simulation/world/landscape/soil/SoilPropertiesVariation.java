package io.github.evoforge.simulation.world.landscape.soil;

import io.github.evoforge.simulation.world.mechanics.geometry.CellVolume;

/** Deterministic coordinate-local variation of Soil pore capacity. */
public record SoilPropertiesVariation(
        long seed,
        int capacityAmplitude) {

    public SoilPropertiesVariation {
        CellVolume.requireValid(capacityAmplitude);
    }
}
