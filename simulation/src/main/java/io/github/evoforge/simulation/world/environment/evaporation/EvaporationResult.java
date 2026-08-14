package io.github.evoforge.simulation.world.environment.evaporation;

import io.github.evoforge.simulation.world.mechanics.geometry.CellVolume;

/** Exact sink accounting for one evaporation column request. */
public record EvaporationResult(
        int requested,
        int surfaceWaterRemoved,
        int soilMoistureRemoved,
        int unfulfilled) {

    public EvaporationResult {
        CellVolume.requireValid(requested);
        CellVolume.requireValid(surfaceWaterRemoved);
        CellVolume.requireValid(soilMoistureRemoved);
        CellVolume.requireValid(unfulfilled);

        long accounted = (long) surfaceWaterRemoved
                + soilMoistureRemoved
                + unfulfilled;
        if (accounted != requested) {
            throw new IllegalArgumentException(
                    "evaporation accounting mismatch: "
                            + accounted
                            + " != "
                            + requested);
        }
    }
}
