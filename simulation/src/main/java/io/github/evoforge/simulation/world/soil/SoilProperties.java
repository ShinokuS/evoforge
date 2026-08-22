package io.github.evoforge.simulation.world.soil;

import io.github.evoforge.simulation.world.space.measurement.CellVolume;

/**
 * Physical porous properties of one Soil material.
 *
 * <p>{@code capacity} is total pore volume available to retained liquids.
 * {@code permeability} is the nominal per-tick uptake conductance for the
 * reference-viscosity liquid. Both use the project's normalized CellVolume scale.
 */
public record SoilProperties(
        int capacity,
        int permeability) {

    public SoilProperties {
        CellVolume.requireValid(capacity);
        CellVolume.requireValid(permeability);
    }
}
