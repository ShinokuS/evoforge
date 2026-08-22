package io.github.evoforge.simulation.world.liquid;

import io.github.evoforge.simulation.world.geometry.CellFace;
import io.github.evoforge.simulation.world.space.measurement.CellVolume;

/**
 * Dominant axis of the aggregate coherent net transfer through one cell during
 * the latest solver step. Opposing actual edge transfers cancel before this
 * diagnostic projection is published.
 */
public record LiquidFlowSample(
        LiquidTypeId type,
        int dx,
        int dy,
        int dz,
        int amount) {

    public LiquidFlowSample {
        if (type == null) {
            throw new IllegalArgumentException("liquid type must not be null");
        }
        CellFace.fromDelta(dx, dy, dz);
        if (amount <= CellVolume.EMPTY) {
            throw new IllegalArgumentException(
                    "flow sample amount must be positive: " + amount);
        }
        CellVolume.requireValid(amount);
    }
}
