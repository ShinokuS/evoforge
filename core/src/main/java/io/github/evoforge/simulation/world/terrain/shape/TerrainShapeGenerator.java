package io.github.evoforge.simulation.world.terrain.shape;

import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.genesis.GenerationRevision;

/** Replaceable deterministic compiler from precise elevation to supported discrete surface geometry. */
@FunctionalInterface
public interface TerrainShapeGenerator {
    TerrainShapeField generate(ElevationField elevation);

    /**
     * Revision-aware preparation seam. Custom generators remain revision-neutral by default, while
     * standard generation stages may opt into stable revision-specific target compilation.
     */
    default TerrainShapeField generate(GenerationRevision revision, ElevationField elevation) {
        if (revision == null) throw new IllegalArgumentException("generation revision must not be null");
        return generate(elevation);
    }
}
