package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.genesis.WorldGenesis;

/** Thin orchestration boundary that composes typed World Atlas generation algorithms. */
public final class WorldAtlasGenerator {
    private final ElevationGenerator elevationGenerator;
    private final DrainageGenerator drainageGenerator;

    public WorldAtlasGenerator() {
        this(new ElevationGenerationStage(), new DrainageGenerationStage());
    }

    public WorldAtlasGenerator(ElevationGenerator elevationGenerator) {
        this(elevationGenerator, new DrainageGenerationStage());
    }

    public WorldAtlasGenerator(
            ElevationGenerator elevationGenerator,
            DrainageGenerator drainageGenerator) {
        if (elevationGenerator == null) {
            throw new IllegalArgumentException("elevationGenerator must not be null");
        }
        if (drainageGenerator == null) {
            throw new IllegalArgumentException("drainageGenerator must not be null");
        }
        this.elevationGenerator = elevationGenerator;
        this.drainageGenerator = drainageGenerator;
    }

    public WorldAtlas generate(WorldGenesis genesis) {
        if (genesis == null) {
            throw new IllegalArgumentException("genesis must not be null");
        }
        ElevationField elevation = elevationGenerator.generate(genesis);
        if (elevation == null) {
            throw new IllegalStateException("elevationGenerator returned null");
        }
        DrainageField drainage = drainageGenerator.generate(elevation);
        if (drainage == null) {
            throw new IllegalStateException("drainageGenerator returned null");
        }
        return new WorldAtlas(genesis, elevation, drainage);
    }
}
