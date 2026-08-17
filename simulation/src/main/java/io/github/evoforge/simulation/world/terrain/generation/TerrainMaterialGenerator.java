package io.github.evoforge.simulation.world.terrain.generation;

import io.github.evoforge.simulation.world.atlas.DrainageField;
import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.atlas.SurfaceHydrologyField;
import io.github.evoforge.simulation.world.geology.GeologyField;

/** Replaceable deterministic algorithm that derives material strata from generated causal facts. */
@FunctionalInterface
public interface TerrainMaterialGenerator {
    TerrainMaterialField generate(
            ElevationField elevation,
            DrainageField drainage,
            CompiledTerrainProfile profile);

    /** Hydrology-aware hook preserving compatibility for custom generators. */
    default TerrainMaterialField generate(
            ElevationField elevation,
            DrainageField drainage,
            SurfaceHydrologyField surfaceHydrology,
            CompiledTerrainProfile profile) {
        if (surfaceHydrology == null) {
            throw new IllegalArgumentException("surfaceHydrology must not be null");
        }
        return generate(elevation, drainage, profile);
    }

    /** Geology + hydrology-aware hook used by the production generated-world path. */
    default TerrainMaterialField generate(
            ElevationField elevation,
            GeologyField geology,
            DrainageField drainage,
            SurfaceHydrologyField surfaceHydrology,
            CompiledTerrainProfile profile) {
        if (geology == null) throw new IllegalArgumentException("geology must not be null");
        return generate(elevation, drainage, surfaceHydrology, profile);
    }
}
