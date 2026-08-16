package io.github.evoforge.simulation.world.genesis;

import io.github.evoforge.simulation.world.spatial.WorldBounds;

/** Requested immutable specification for a generated world before generation begins. */
public record WorldSpec(
        WorldBounds bounds,
        HydroClimateSpec hydroClimate) {

    public WorldSpec(WorldBounds bounds) {
        this(bounds, HydroClimateSpec.UNFORCED);
    }

    public WorldSpec {
        if (bounds == null) {
            throw new IllegalArgumentException("bounds must not be null");
        }
        if (hydroClimate == null) {
            throw new IllegalArgumentException("hydroClimate must not be null");
        }
    }
}
