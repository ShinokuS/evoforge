package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.genesis.WorldGenesis;

/** Thin orchestration boundary that composes typed World Atlas generation algorithms. */
public final class WorldAtlasGenerator {
    private final ElevationGenerator elevationGenerator;

    public WorldAtlasGenerator() {
        this(new ElevationGenerationStage());
    }

    public WorldAtlasGenerator(ElevationGenerator elevationGenerator) {
        if (elevationGenerator == null) {
            throw new IllegalArgumentException("elevationGenerator must not be null");
        }
        this.elevationGenerator = elevationGenerator;
    }

    public WorldAtlas generate(WorldGenesis genesis) {
        if (genesis == null) {
            throw new IllegalArgumentException("genesis must not be null");
        }
        ElevationField elevation = elevationGenerator.generate(genesis);
        if (elevation == null) {
            throw new IllegalStateException("elevationGenerator returned null");
        }
        return new WorldAtlas(genesis, elevation);
    }
}
