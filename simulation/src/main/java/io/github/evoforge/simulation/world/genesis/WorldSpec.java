package io.github.evoforge.simulation.world.genesis;

import io.github.evoforge.simulation.world.spatial.WorldBounds;

/** Requested immutable shape of a generated world before generation begins. */
public record WorldSpec(WorldBounds bounds) {
    public WorldSpec {
        if (bounds == null) {
            throw new IllegalArgumentException("bounds must not be null");
        }
    }
}
