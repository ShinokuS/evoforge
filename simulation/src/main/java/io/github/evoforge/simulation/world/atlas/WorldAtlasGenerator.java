package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.genesis.WorldGenesis;

/** Thin orchestration boundary that composes typed World Atlas generation algorithms. */
public final class WorldAtlasGenerator {
    private final ElevationGenerator elevationGenerator;
    private final DrainageGenerator drainageGenerator;
    private final HydroClimateGenerator hydroClimateGenerator;

    public WorldAtlasGenerator() {
        this(new ElevationGenerationStage(), new DrainageGenerationStage(), new HydroClimateGenerationStage());
    }

    public WorldAtlasGenerator(ElevationGenerator elevationGenerator) {
        this(elevationGenerator, new DrainageGenerationStage(), new HydroClimateGenerationStage());
    }

    public WorldAtlasGenerator(ElevationGenerator elevationGenerator, DrainageGenerator drainageGenerator) {
        this(elevationGenerator, drainageGenerator, new HydroClimateGenerationStage());
    }

    public WorldAtlasGenerator(
            ElevationGenerator elevationGenerator,
            DrainageGenerator drainageGenerator,
            HydroClimateGenerator hydroClimateGenerator) {
        if (elevationGenerator == null) {
            throw new IllegalArgumentException("elevationGenerator must not be null");
        }
        if (drainageGenerator == null) {
            throw new IllegalArgumentException("drainageGenerator must not be null");
        }
        if (hydroClimateGenerator == null) {
            throw new IllegalArgumentException("hydroClimateGenerator must not be null");
        }
        this.elevationGenerator = elevationGenerator;
        this.drainageGenerator = drainageGenerator;
        this.hydroClimateGenerator = hydroClimateGenerator;
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
        HydroClimateField hydroClimate = hydroClimateGenerator.generate(genesis.spec());
        if (hydroClimate == null) {
            throw new IllegalStateException("hydroClimateGenerator returned null");
        }
        return new WorldAtlas(genesis, elevation, drainage, hydroClimate);
    }
}
