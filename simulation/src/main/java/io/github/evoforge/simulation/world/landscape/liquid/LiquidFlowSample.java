package io.github.evoforge.simulation.world.landscape.liquid;

import io.github.evoforge.simulation.world.mechanics.geometry.CellFace;
import io.github.evoforge.simulation.world.mechanics.geometry.CellVolume;

/** Dominant actual transfer through one cell during the latest solver step. */
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
