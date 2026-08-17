package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.climate.ClimateNormalsGenerationStage;
import io.github.evoforge.simulation.world.climate.ClimateNormalsGenerator;
import io.github.evoforge.simulation.world.geology.GeologyGenerationStage;
import io.github.evoforge.simulation.world.geology.GeologyGenerator;

/**
 * Typed replaceable algorithm set for WorldAtlas generation.
 *
 * <p>This is deliberately an explicit record rather than a generic registry. Every dependency is
 * visible in the type system, while callers can replace any combination without expanding
 * WorldAtlasGenerator constructor overloads.</p>
 */
public record WorldGenerationAlgorithms(
        ElevationGenerator elevation,
        GeologyGenerator geology,
        ClimateNormalsGenerator climate,
        DrainageGenerator drainage,
        HydrographyGenerator hydrography,
        SurfaceHydrologyGenerator surfaceHydrology) {

    public WorldGenerationAlgorithms {
        if (elevation == null
                || geology == null
                || climate == null
                || drainage == null
                || hydrography == null
                || surfaceHydrology == null) {
            throw new IllegalArgumentException("world generation algorithms must not be null");
        }
    }

    public static WorldGenerationAlgorithms standard() {
        return new WorldGenerationAlgorithms(
                new ElevationGenerationStage(),
                new GeologyGenerationStage(),
                new ClimateNormalsGenerationStage(),
                new DrainageGenerationStage(),
                new HydrographyGenerationStage(),
                new SurfaceHydrologyGenerationStage());
    }

    public WorldGenerationAlgorithms withElevation(ElevationGenerator replacement) {
        return new WorldGenerationAlgorithms(replacement, geology, climate, drainage, hydrography, surfaceHydrology);
    }

    public WorldGenerationAlgorithms withGeology(GeologyGenerator replacement) {
        return new WorldGenerationAlgorithms(elevation, replacement, climate, drainage, hydrography, surfaceHydrology);
    }

    public WorldGenerationAlgorithms withClimate(ClimateNormalsGenerator replacement) {
        return new WorldGenerationAlgorithms(elevation, geology, replacement, drainage, hydrography, surfaceHydrology);
    }

    public WorldGenerationAlgorithms withDrainage(DrainageGenerator replacement) {
        return new WorldGenerationAlgorithms(elevation, geology, climate, replacement, hydrography, surfaceHydrology);
    }

    public WorldGenerationAlgorithms withHydrography(HydrographyGenerator replacement) {
        return new WorldGenerationAlgorithms(elevation, geology, climate, drainage, replacement, surfaceHydrology);
    }

    public WorldGenerationAlgorithms withSurfaceHydrology(SurfaceHydrologyGenerator replacement) {
        return new WorldGenerationAlgorithms(elevation, geology, climate, drainage, hydrography, replacement);
    }
}
