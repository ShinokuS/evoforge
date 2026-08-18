package io.github.evoforge.simulation.world.terrain.shape;

import io.github.evoforge.simulation.world.atlas.ElevationField;

/** Replaceable deterministic compiler from precise elevation to supported discrete surface geometry. */
@FunctionalInterface
public interface TerrainShapeGenerator {
    TerrainShapeField generate(ElevationField elevation);
}
