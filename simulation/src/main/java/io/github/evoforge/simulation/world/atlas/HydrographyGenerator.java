package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.genesis.WorldGenesis;

/** Replaceable deterministic algorithm for durable generated hydrographic structure. */
@FunctionalInterface
public interface HydrographyGenerator {
    HydrographyField generate(
            WorldGenesis genesis,
            ElevationField elevation,
            DrainageField drainage);
}
