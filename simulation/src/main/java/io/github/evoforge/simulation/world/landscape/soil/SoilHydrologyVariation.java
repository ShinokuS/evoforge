package io.github.evoforge.simulation.world.landscape.soil;

import io.github.evoforge.simulation.world.mechanics.geometry.CellVolume;

/** Deterministic coordinate-local variation around a material SoilHydrology base. */
public record SoilHydrologyVariation(
        long seed,
        int capacityAmplitude) {

    public SoilHydrologyVariation {
        CellVolume.requireValid(capacityAmplitude);
    }
}
