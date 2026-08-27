package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.genesis.WorldGenesis;

/** Replaceable algorithm contract that authors one immutable elevation world fact. */
@FunctionalInterface
public interface ElevationGenerator {
    ElevationField generate(WorldGenesis genesis);
}
