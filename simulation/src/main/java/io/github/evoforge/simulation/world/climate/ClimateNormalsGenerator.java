package io.github.evoforge.simulation.world.climate;

import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;

/** Authors durable climate normals from immutable world genesis and elevation facts. */
@FunctionalInterface
public interface ClimateNormalsGenerator {
    ClimateNormalsField generate(WorldGenesis genesis, ElevationField elevation);
}
