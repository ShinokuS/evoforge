package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.genesis.WorldGenesis;

/** Replaceable deterministic algorithm for generated finite surface-water initial conditions. */
@FunctionalInterface
public interface SurfaceHydrologyGenerator {
    SurfaceHydrologyField generate(
            WorldGenesis genesis,
            ElevationField elevation,
            DrainageField drainage);
}
