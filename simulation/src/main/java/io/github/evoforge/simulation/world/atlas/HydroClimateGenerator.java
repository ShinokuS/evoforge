package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.genesis.WorldSpec;

/** Authors long-term hydrologic climate normals from the requested world specification. */
@FunctionalInterface
public interface HydroClimateGenerator {
    HydroClimateField generate(WorldSpec spec);
}
