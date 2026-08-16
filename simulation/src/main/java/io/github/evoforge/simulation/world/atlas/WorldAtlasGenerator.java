package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.genesis.WorldGenesis;

/** Thin orchestration boundary that composes typed World Atlas generation algorithms. */
public final class WorldAtlasGenerator {
    private final ElevationGenerator elevationGenerator;
    private final DrainageGenerator drainageGenerator;
    private final SurfaceHydrologyGenerator surfaceHydrologyGenerator;
    private final HydroClimateGenerator hydroClimateGenerator;

    public WorldAtlasGenerator() {
        this(
                new ElevationGenerationStage(),
                new DrainageGenerationStage(),
                new SurfaceHydrologyGenerationStage(),
                new HydroClimateGenerationStage());
    }

    public WorldAtlasGenerator(ElevationGenerator elevationGenerator) {
        this(
                elevationGenerator,
                new DrainageGenerationStage(),
                new SurfaceHydrologyGenerationStage(),
                new HydroClimateGenerationStage());
    }

    public WorldAtlasGenerator(
            ElevationGenerator elevationGenerator,
            DrainageGenerator drainageGenerator) {
        this(
                elevationGenerator,
                drainageGenerator,
                new SurfaceHydrologyGenerationStage(),
                new HydroClimateGenerationStage());
    }

    /** Compatibility constructor retaining the previous three-algorithm injection surface. */
    public WorldAtlasGenerator(
            ElevationGenerator elevationGenerator,
            DrainageGenerator drainageGenerator,
            HydroClimateGenerator hydroClimateGenerator) {
        this(
                elevationGenerator,
                drainageGenerator,
                new SurfaceHydrologyGenerationStage(),
                hydroClimateGenerator);
    }

    public WorldAtlasGenerator(
            ElevationGenerator elevationGenerator,
            DrainageGenerator drainageGenerator,
            SurfaceHydrologyGenerator surfaceHydrologyGenerator,
            HydroClimateGenerator hydroClimateGenerator) {
        if (elevationGenerator == null) {
            throw new IllegalArgumentException("elevationGenerator must not be null");
        }
        if (drainageGenerator == null) {
            throw new IllegalArgumentException("drainageGenerator must not be null");
        }
        if (surfaceHydrologyGenerator == null) {
            throw new IllegalArgumentException("surfaceHydrologyGenerator must not be null");
        }
        if (hydroClimateGenerator == null) {
            throw new IllegalArgumentException("hydroClimateGenerator must not be null");
        }
        this.elevationGenerator = elevationGenerator;
        this.drainageGenerator = drainageGenerator;
        this.surfaceHydrologyGenerator = surfaceHydrologyGenerator;
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
        SurfaceHydrologyField surfaceHydrology = surfaceHydrologyGenerator.generate(
                genesis,
                elevation,
                drainage);
        if (surfaceHydrology == null) {
            throw new IllegalStateException("surfaceHydrologyGenerator returned null");
        }
        HydroClimateField hydroClimate = hydroClimateGenerator.generate(genesis.spec());
        if (hydroClimate == null) {
            throw new IllegalStateException("hydroClimateGenerator returned null");
        }
        return new WorldAtlas(
                genesis,
                elevation,
                drainage,
                surfaceHydrology,
                hydroClimate);
    }
}
