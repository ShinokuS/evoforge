package io.github.evoforge.simulation.agents.search;

import io.github.evoforge.simulation.world.space.orientation.FacingDirection;

/**
 * Coordinate-free observer-relative target for one unguided exploration leg.
 * The offset is a displacement from the agent's current physical position, never an absolute world location.
 */
public record SearchRelocationRequest(int offsetX, int offsetY) {
    public SearchRelocationRequest {
        if (offsetX == 0 && offsetY == 0) {
            throw new IllegalArgumentException("search relocation offset must not be zero");
        }
    }

    /** Coarse physical facing used before taking a fresh Vision snapshot toward the selected point. */
    public FacingDirection heading() {
        return FacingDirection.of(offsetX, offsetY);
    }

    /** Number of local grid transitions needed by an unobstructed shortest 8-neighbor route. */
    public int distance() {
        return Math.max(Math.abs(offsetX), Math.abs(offsetY));
    }
}
