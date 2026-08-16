package io.github.evoforge.simulation.world.terrain.generation;

import io.github.evoforge.simulation.world.atlas.DrainageField;
import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.atlas.SurfaceHydrologyField;

/** Replaceable deterministic algorithm that derives material strata from generated causal facts. */
@FunctionalInterface
public interface TerrainMaterialGenerator {
    TerrainMaterialField generate(
            ElevationField elevation,
            DrainageField drainage,
            CompiledTerrainProfile profile);

    /**
     * Hydrology-aware generation hook. Existing custom generators remain valid and ignore the new
     * fact until they intentionally opt into it.
     */
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
}
