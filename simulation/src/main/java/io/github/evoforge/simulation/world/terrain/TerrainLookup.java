package io.github.evoforge.simulation.world.terrain;

import io.github.evoforge.simulation.world.material.MaterialDefinitionId;

public interface TerrainLookup {

    MaterialDefinitionId find(int x, int y, int z);

    default boolean contains(int x, int y, int z) {
        return find(x, y, z) != null;
    }
}
