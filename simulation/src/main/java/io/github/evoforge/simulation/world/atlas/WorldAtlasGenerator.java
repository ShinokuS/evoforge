package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.genesis.WorldGenesis;

/** Thin orchestration boundary for causal World Atlas generation stages. */
public final class WorldAtlasGenerator {
    private final ElevationGenerationStage elevationStage;

    public WorldAtlasGenerator() {
        this(new ElevationGenerationStage());
    }

    WorldAtlasGenerator(ElevationGenerationStage elevationStage) {
        if (elevationStage == null) {
            throw new IllegalArgumentException("elevationStage must not be null");
        }
        this.elevationStage = elevationStage;
    }

    public WorldAtlas generate(WorldGenesis genesis) {
        if (genesis == null) {
            throw new IllegalArgumentException("genesis must not be null");
        }
        ElevationField elevation = elevationStage.generate(genesis);
        return new WorldAtlas(genesis, elevation);
    }
}
