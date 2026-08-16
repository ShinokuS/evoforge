package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.genesis.WorldGenesis;

/** Durable generated world facts that exist before detailed world materialization. */
public final class WorldAtlas {
    private final WorldGenesis genesis;
    private final ElevationField elevation;
    private final DrainageField drainage;
    private final HydroClimateField hydroClimate;

    WorldAtlas(
            WorldGenesis genesis,
            ElevationField elevation,
            DrainageField drainage,
            HydroClimateField hydroClimate) {
        if (genesis == null) {
            throw new IllegalArgumentException("genesis must not be null");
        }
        if (elevation == null) {
            throw new IllegalArgumentException("elevation must not be null");
        }
        if (drainage == null) {
            throw new IllegalArgumentException("drainage must not be null");
        }
        if (hydroClimate == null) {
            throw new IllegalArgumentException("hydroClimate must not be null");
        }
        if (!genesis.spec().bounds().equals(elevation.bounds())) {
            throw new IllegalArgumentException(
                    "elevation bounds must match world genesis bounds");
        }
        if (!genesis.spec().bounds().equals(drainage.bounds())) {
            throw new IllegalArgumentException(
                    "drainage bounds must match world genesis bounds");
        }
        if (!genesis.spec().bounds().equals(hydroClimate.bounds())) {
            throw new IllegalArgumentException(
                    "hydroClimate bounds must match world genesis bounds");
        }
        this.genesis = genesis;
        this.elevation = elevation;
        this.drainage = drainage;
        this.hydroClimate = hydroClimate;
    }

    public WorldGenesis genesis() {
        return genesis;
    }

    public ElevationField elevation() {
        return elevation;
    }

    public DrainageField drainage() {
        return drainage;
    }

    public HydroClimateField hydroClimate() {
        return hydroClimate;
    }
}
