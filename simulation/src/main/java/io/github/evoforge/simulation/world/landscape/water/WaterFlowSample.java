package io.github.evoforge.simulation.world.landscape.water;

import io.github.evoforge.simulation.world.mechanics.geometry.CellFace;
import io.github.evoforge.simulation.world.mechanics.geometry.CellVolume;

/**
 * Read-only sample of the dominant actual Water transfer through one cell during
 * the latest solver step that performed work.
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
