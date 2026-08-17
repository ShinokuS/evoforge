package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.climate.ClimateNormalsField;
import io.github.evoforge.simulation.world.climate.ClimateNormalsGenerator;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.geology.GeologyField;
import io.github.evoforge.simulation.world.geology.GeologyGenerator;

/** Thin orchestration boundary that composes typed World Atlas generation algorithms. */
public final class WorldAtlasGenerator {
    private final WorldGenerationAlgorithms algorithms;

    public WorldAtlasGenerator() {
        this(WorldGenerationAlgorithms.standard());
    }

    /** Canonical injection seam for any combination of generation algorithms. */
    public WorldAtlasGenerator(WorldGenerationAlgorithms algorithms) {
        if (algorithms == null) {
            throw new IllegalArgumentException("world generation algorithms must not be null");
        }
        this.algorithms = algorithms;
    }

    /** Compatibility seam retaining the historical single custom elevation constructor. */
    public WorldAtlasGenerator(ElevationGenerator elevationGenerator) {
        this(WorldGenerationAlgorithms.standard().withElevation(elevationGenerator));
    }

    public static WorldAtlasGenerator withGeology(GeologyGenerator geologyGenerator) {
        return new WorldAtlasGenerator(
                WorldGenerationAlgorithms.standard().withGeology(geologyGenerator));
    }

    public static WorldAtlasGenerator withClimate(ClimateNormalsGenerator climateGenerator) {
        return new WorldAtlasGenerator(
                WorldGenerationAlgorithms.standard().withClimate(climateGenerator));
    }

    public static WorldAtlasGenerator withHydrography(HydrographyGenerator hydrographyGenerator) {
        return new WorldAtlasGenerator(
                WorldGenerationAlgorithms.standard().withHydrography(hydrographyGenerator));
    }

    /** Compatibility constructors delegate into the canonical typed bundle. */
    public WorldAtlasGenerator(
            ElevationGenerator elevationGenerator,
            DrainageGenerator drainageGenerator) {
        this(WorldGenerationAlgorithms.standard()
                .withElevation(elevationGenerator)
                .withDrainage(drainageGenerator));
    }

    public WorldAtlasGenerator(
            ElevationGenerator elevationGenerator,
            DrainageGenerator drainageGenerator,
            SurfaceHydrologyGenerator surfaceHydrologyGenerator) {
        this(WorldGenerationAlgorithms.standard()
                .withElevation(elevationGenerator)
                .withDrainage(drainageGenerator)
                .withSurfaceHydrology(surfaceHydrologyGenerator));
    }

    public WorldAtlasGenerator(
            ElevationGenerator elevationGenerator,
            GeologyGenerator geologyGenerator,
            ClimateNormalsGenerator climateGenerator,
            DrainageGenerator drainageGenerator,
            SurfaceHydrologyGenerator surfaceHydrologyGenerator) {
        this(WorldGenerationAlgorithms.standard()
                .withElevation(elevationGenerator)
                .withGeology(geologyGenerator)
                .withClimate(climateGenerator)
                .withDrainage(drainageGenerator)
                .withSurfaceHydrology(surfaceHydrologyGenerator));
    }

    public WorldAtlasGenerator(
            ElevationGenerator elevationGenerator,
            GeologyGenerator geologyGenerator,
            ClimateNormalsGenerator climateGenerator,
            DrainageGenerator drainageGenerator,
            HydrographyGenerator hydrographyGenerator,
            SurfaceHydrologyGenerator surfaceHydrologyGenerator) {
        this(new WorldGenerationAlgorithms(
                elevationGenerator,
                geologyGenerator,
                climateGenerator,
                drainageGenerator,
                hydrographyGenerator,
                surfaceHydrologyGenerator));
    }

    public WorldAtlas generate(WorldGenesis genesis) {
        if (genesis == null) throw new IllegalArgumentException("genesis must not be null");

        ElevationField elevation = algorithms.elevation().generate(genesis);
        if (elevation == null) throw new IllegalStateException("elevation generator returned null");

        GeologyField geology = algorithms.geology().generate(genesis);
        if (geology == null) throw new IllegalStateException("geology generator returned null");

        ClimateNormalsField climate = algorithms.climate().generate(genesis, elevation);
        if (climate == null) throw new IllegalStateException("climate generator returned null");

        DrainageField drainage = algorithms.drainage().generate(elevation);
        if (drainage == null) throw new IllegalStateException("drainage generator returned null");

        HydrographyField hydrography = algorithms.hydrography().generate(genesis, elevation, drainage);
        if (hydrography == null) throw new IllegalStateException("hydrography generator returned null");

        SurfaceHydrologyField surfaceHydrology = algorithms.surfaceHydrology().generate(
                genesis,
                elevation,
                drainage,
                hydrography,
                climate);
        if (surfaceHydrology == null) {
            throw new IllegalStateException("surface hydrology generator returned null");
        }

        return new WorldAtlas(
                genesis,
                elevation,
                geology,
                climate,
                drainage,
                hydrography,
                surfaceHydrology);
    }
}
