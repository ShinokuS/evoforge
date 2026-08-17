package io.github.evoforge.simulation.world.genesis;

import io.github.evoforge.simulation.world.spatial.WorldBounds;

/** Requested immutable specification for a generated world before generation begins. */
public record WorldSpec(
        WorldBounds bounds,
        ClimateSpec climate) {

    public WorldSpec(WorldBounds bounds) {
        this(bounds, ClimateSpec.STANDARD_BASELINE);
    }

    public WorldSpec {
        if (bounds == null) {
            throw new IllegalArgumentException("bounds must not be null");
        }
        if (climate == null) {
            throw new IllegalArgumentException("climate must not be null");
        }
    }
}
