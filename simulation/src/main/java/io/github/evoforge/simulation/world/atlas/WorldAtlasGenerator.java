package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.geology.GeologyField;
import io.github.evoforge.simulation.world.geology.GeologyGenerationStage;
import io.github.evoforge.simulation.world.geology.GeologyGenerator;

/** Thin orchestration boundary that composes typed World Atlas generation algorithms. */
public final class WorldAtlasGenerator {
    private final ElevationGenerator elevationGenerator;
    private final GeologyGenerator geologyGenerator;
    private final DrainageGenerator drainageGenerator;
    private final SurfaceHydrologyGenerator surfaceHydrologyGenerator;
    private final HydroClimateGenerator hydroClimateGenerator;

    public WorldAtlasGenerator() {
        this(
                new ElevationGenerationStage(),
                new GeologyGenerationStage(),
                new DrainageGenerationStage(),
                new SurfaceHydrologyGenerationStage(),
                new HydroClimateGenerationStage());
    }

    public WorldAtlasGenerator(ElevationGenerator elevationGenerator) {
        this(
                elevationGenerator,
                new GeologyGenerationStage(),
                new DrainageGenerationStage(),
                new SurfaceHydrologyGenerationStage(),
                new HydroClimateGenerationStage());
    }

    public WorldAtlasGenerator(GeologyGenerator geologyGenerator) {
        this(
                new ElevationGenerationStage(),
                geologyGenerator,
                new DrainageGenerationStage(),
                new SurfaceHydrologyGenerationStage(),
                new HydroClimateGenerationStage());
    }

    public WorldAtlasGenerator(
            ElevationGenerator elevationGenerator,
            DrainageGenerator drainageGenerator) {
        this(
                elevationGenerator,
                new GeologyGenerationStage(),
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
                new GeologyGenerationStage(),
                drainageGenerator,
                new SurfaceHydrologyGenerationStage(),
                hydroClimateGenerator);
    }

    /** Compatibility constructor retaining the previous four-algorithm injection surface. */
    public WorldAtlasGenerator(
            ElevationGenerator elevationGenerator,
            DrainageGenerator drainageGenerator,
            SurfaceHydrologyGenerator surfaceHydrologyGenerator,
            HydroClimateGenerator hydroClimateGenerator) {
        this(
                elevationGenerator,
                new GeologyGenerationStage(),
                drainageGenerator,
                surfaceHydrologyGenerator,
                hydroClimateGenerator);
    }

    public WorldAtlasGenerator(
            ElevationGenerator elevationGenerator,
            GeologyGenerator geologyGenerator,
            DrainageGenerator drainageGenerator,
            SurfaceHydrologyGenerator surfaceHydrologyGenerator,
            HydroClimateGenerator hydroClimateGenerator) {
        if (elevationGenerator == null
                || geologyGenerator == null
                || drainageGenerator == null
                || surfaceHydrologyGenerator == null
                || hydroClimateGenerator == null) {
            throw new IllegalArgumentException("WorldAtlas generators must not be null");
        }
        this.elevationGenerator = elevationGenerator;
        this.geologyGenerator = geologyGenerator;
        this.drainageGenerator = drainageGenerator;
        this.surfaceHydrologyGenerator = surfaceHydrologyGenerator;
        this.hydroClimateGenerator = hydroClimateGenerator;
    }

    public WorldAtlas generate(WorldGenesis genesis) {
        if (genesis == null) throw new IllegalArgumentException("genesis must not be null");

        ElevationField elevation = elevationGenerator.generate(genesis);
        if (elevation == null) throw new IllegalStateException("elevationGenerator returned null");

        GeologyField geology = geologyGenerator.generate(genesis);
        if (geology == null) throw new IllegalStateException("geologyGenerator returned null");

        DrainageField drainage = drainageGenerator.generate(elevation);
        if (drainage == null) throw new IllegalStateException("drainageGenerator returned null");

        SurfaceHydrologyField surfaceHydrology = surfaceHydrologyGenerator.generate(
                genesis,
                elevation,
                drainage);
        if (surfaceHydrology == null) {
            throw new IllegalStateException("surfaceHydrologyGenerator returned null");
        }

        HydroClimateField hydroClimate = hydroClimateGenerator.generate(genesis.spec());
        if (hydroClimate == null) throw new IllegalStateException("hydroClimateGenerator returned null");

        return new WorldAtlas(
                genesis,
                elevation,
                geology,
                drainage,
                surfaceHydrology,
                hydroClimate);
    }
}
