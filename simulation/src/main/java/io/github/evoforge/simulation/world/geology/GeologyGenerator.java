package io.github.evoforge.simulation.world.geology;

import io.github.evoforge.simulation.world.genesis.WorldGenesis;

/** Replaceable deterministic algorithm for durable generated geology facts. */
@FunctionalInterface
public interface GeologyGenerator {
    GeologyField generate(WorldGenesis genesis);
}
