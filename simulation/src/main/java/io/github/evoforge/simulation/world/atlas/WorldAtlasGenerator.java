package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.climate.ClimateNormalsField;
import io.github.evoforge.simulation.world.climate.ClimateNormalsGenerationStage;
import io.github.evoforge.simulation.world.climate.ClimateNormalsGenerator;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.geology.GeologyField;
import io.github.evoforge.simulation.world.geology.GeologyGenerationStage;
import io.github.evoforge.simulation.world.geology.GeologyGenerator;

/** Thin orchestration boundary that composes typed World Atlas generation algorithms. */
public final class WorldAtlasGenerator {
    private final ElevationGenerator elevationGenerator;
    private final GeologyGenerator geologyGenerator;
    private final ClimateNormalsGenerator climateGenerator;
    private final DrainageGenerator drainageGenerator;
    private final SurfaceHydrologyGenerator surfaceHydrologyGenerator;

    public WorldAtlasGenerator() {
        this(
                new ElevationGenerationStage(),
                new GeologyGenerationStage(),
                new ClimateNormalsGenerationStage(),
                new DrainageGenerationStage(),
                new SurfaceHydrologyGenerationStage());
    }

    public WorldAtlasGenerator(ElevationGenerator elevationGenerator) {
        this(
                elevationGenerator,
                new GeologyGenerationStage(),
                new ClimateNormalsGenerationStage(),
                new DrainageGenerationStage(),
                new SurfaceHydrologyGenerationStage());
    }

    /** Named injection seam avoids ambiguity with the legacy single-lambda elevation constructor. */
    public static WorldAtlasGenerator withGeology(GeologyGenerator geologyGenerator) {
        return new WorldAtlasGenerator(
                new ElevationGenerationStage(),
                geologyGenerator,
                new ClimateNormalsGenerationStage(),
                new DrainageGenerationStage(),
                new SurfaceHydrologyGenerationStage());
    }

    /** Named injection seam for custom climate generation without widening the constructor surface. */
    public static WorldAtlasGenerator withClimate(ClimateNormalsGenerator climateGenerator) {
        return new WorldAtlasGenerator(
                new ElevationGenerationStage(),
                new GeologyGenerationStage(),
                climateGenerator,
                new DrainageGenerationStage(),
                new SurfaceHydrologyGenerationStage());
    }

    public WorldAtlasGenerator(
            ElevationGenerator elevationGenerator,
            DrainageGenerator drainageGenerator) {
        this(
                elevationGenerator,
                new GeologyGenerationStage(),
                new ClimateNormalsGenerationStage(),
                drainageGenerator,
                new SurfaceHydrologyGenerationStage());
    }

    public WorldAtlasGenerator(
            ElevationGenerator elevationGenerator,
            DrainageGenerator drainageGenerator,
            SurfaceHydrologyGenerator surfaceHydrologyGenerator) {
        this(
                elevationGenerator,
                new GeologyGenerationStage(),
                new ClimateNormalsGenerationStage(),
                drainageGenerator,
                surfaceHydrologyGenerator);
    }

    public WorldAtlasGenerator(
            ElevationGenerator elevationGenerator,
            GeologyGenerator geologyGenerator,
            ClimateNormalsGenerator climateGenerator,
            DrainageGenerator drainageGenerator,
            SurfaceHydrologyGenerator surfaceHydrologyGenerator) {
        if (elevationGenerator == null
                || geologyGenerator == null
                || climateGenerator == null
                || drainageGenerator == null
                || surfaceHydrologyGenerator == null) {
            throw new IllegalArgumentException("WorldAtlas generators must not be null");
        }
        this.elevationGenerator = elevationGenerator;
        this.geologyGenerator = geologyGenerator;
        this.climateGenerator = climateGenerator;
        this.drainageGenerator = drainageGenerator;
        this.surfaceHydrologyGenerator = surfaceHydrologyGenerator;
    }

    public WorldAtlas generate(WorldGenesis genesis) {
        if (genesis == null) throw new IllegalArgumentException("genesis must not be null");

        ElevationField elevation = elevationGenerator.generate(genesis);
        if (elevation == null) throw new IllegalStateException("elevationGenerator returned null");

        GeologyField geology = geologyGenerator.generate(genesis);
        if (geology == null) throw new IllegalStateException("geologyGenerator returned null");

        ClimateNormalsField climate = climateGenerator.generate(genesis, elevation);
        if (climate == null) throw new IllegalStateException("climateGenerator returned null");

        DrainageField drainage = drainageGenerator.generate(elevation);
        if (drainage == null) throw new IllegalStateException("drainageGenerator returned null");

        SurfaceHydrologyField surfaceHydrology = surfaceHydrologyGenerator.generate(
                genesis,
                elevation,
                drainage);
        if (surfaceHydrology == null) {
            throw new IllegalStateException("surfaceHydrologyGenerator returned null");
        }

        return new WorldAtlas(
                genesis,
                elevation,
                geology,
                climate,
                drainage,
                surfaceHydrology);
    }
}
