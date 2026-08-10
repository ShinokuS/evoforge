package io.github.evoforge.simulation.world.landscape.terrain;

import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;

public interface TerrainLookup {

    LandscapeDefinitionId find(int x, int y, int z);

    default boolean contains(int x, int y, int z) {
        return find(x, y, z) != null;
    }
}
