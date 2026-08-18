package io.github.evoforge.simulation.world.terrain.generation;

import io.github.evoforge.simulation.world.atlas.DrainageField;
import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.atlas.SurfaceHydrologyField;
import io.github.evoforge.simulation.world.geology.GeologyField;
import io.github.evoforge.simulation.world.terrain.surface.SurfaceMorphologyField;

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

    /** Geology + hydrology-aware hook used by compatibility preparation paths. */
    default TerrainMaterialField generate(
            ElevationField elevation,
            GeologyField geology,
            DrainageField drainage,
            SurfaceHydrologyField surfaceHydrology,
            CompiledTerrainProfile profile) {
        if (geology == null) throw new IllegalArgumentException("geology must not be null");
        return generate(elevation, drainage, surfaceHydrology, profile);
    }

    /**
     * Full causal hook used when preparation has already derived reusable surface morphology.
     *
     * <p>Custom generators that do not need morphology retain their historical behavior through
     * this default. Production Terrain consumes the supplied field so downstream Soil/erosion
     * stages can share exactly the same generated geometric facts.</p>
     */
    default TerrainMaterialField generate(
            ElevationField elevation,
            GeologyField geology,
            DrainageField drainage,
            SurfaceHydrologyField surfaceHydrology,
            SurfaceMorphologyField morphology,
            CompiledTerrainProfile profile) {
        if (morphology == null) {
            throw new IllegalArgumentException("surface morphology must not be null");
        }
        if (elevation != null && !elevation.bounds().equals(morphology.bounds())) {
            throw new IllegalArgumentException("surface morphology bounds must match elevation bounds");
        }
        return generate(elevation, geology, drainage, surfaceHydrology, profile);
    }
}
