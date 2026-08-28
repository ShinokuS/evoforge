package io.github.evoforge.simulation.world.genesis;

import io.github.evoforge.simulation.world.spatial.WorldBounds;

/** Minimal compatibility shell used only by the historical world-generation preview UI. */
public record WorldSpec(WorldBounds bounds) {
    public WorldSpec {
        if (bounds == null) throw new IllegalArgumentException("bounds must not be null");
    }
}
