package io.github.evoforge.simulation.world.landscape.water;

import io.github.evoforge.simulation.world.mechanics.geometry.CellFace;
import io.github.evoforge.simulation.world.mechanics.geometry.CellVolume;

/**
 * Read-only Water projection of the dominant axis of aggregate coherent net
 * transfer through one cell during the latest solver step. Opposing actual edge
 * transfers cancel before this diagnostic sample is published.
 */
public record WaterFlowSample(
        int dx,
        int dy,
        int dz,
        int amount) {

    public WaterFlowSample {
        CellFace.fromDelta(dx, dy, dz);
        if (amount <= CellVolume.EMPTY) {
            throw new IllegalArgumentException(
                    "flow sample amount must be positive: " + amount);
        }
        CellVolume.requireValid(amount);
    }
}
