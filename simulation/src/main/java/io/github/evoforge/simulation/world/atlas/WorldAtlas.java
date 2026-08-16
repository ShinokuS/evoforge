package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.genesis.WorldGenesis;

/** Durable generated world facts that exist before detailed world materialization. */
public record WorldAtlas(
        WorldGenesis genesis,
        ElevationField elevation) {

    public WorldAtlas {
        if (genesis == null) {
            throw new IllegalArgumentException("genesis must not be null");
        }
        if (elevation == null) {
            throw new IllegalArgumentException("elevation must not be null");
        }
        if (!genesis.spec().bounds().equals(elevation.bounds())) {
            throw new IllegalArgumentException(
                    "elevation bounds must match world genesis bounds");
        }
    }
}
