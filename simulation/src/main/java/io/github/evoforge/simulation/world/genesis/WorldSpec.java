package io.github.evoforge.simulation.world.genesis;

import io.github.evoforge.simulation.world.spatial.WorldBounds;

/** Requested immutable specification for a generated world before generation begins. */
public record WorldSpec(
        WorldBounds bounds,
        HydroClimateSpec hydroClimate,
        ClimateNormalsSpec climateNormals) {

    public WorldSpec(WorldBounds bounds) {
        this(bounds, HydroClimateSpec.UNFORCED, ClimateNormalsSpec.STANDARD);
    }

    /** Compatibility constructor retaining the previous explicit hydrologic-climate surface. */
    public WorldSpec(WorldBounds bounds, HydroClimateSpec hydroClimate) {
        this(bounds, hydroClimate, ClimateNormalsSpec.STANDARD);
    }

    public WorldSpec {
        if (bounds == null) {
            throw new IllegalArgumentException("bounds must not be null");
        }
        if (hydroClimate == null) {
            throw new IllegalArgumentException("hydroClimate must not be null");
        }
        if (climateNormals == null) {
            throw new IllegalArgumentException("climateNormals must not be null");
        }
    }
}
