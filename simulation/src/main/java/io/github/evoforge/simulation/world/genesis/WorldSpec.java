package io.github.evoforge.simulation.world.genesis;

import io.github.evoforge.simulation.world.spatial.WorldBounds;

/** Structural address-space envelope requested for a generated world. */
public record WorldSpec(WorldBounds bounds) {
    public WorldSpec {
        if (bounds == null) {
            throw new IllegalArgumentException("bounds must not be null");
        }
    }
}
