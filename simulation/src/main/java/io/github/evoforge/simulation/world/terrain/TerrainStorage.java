package io.github.evoforge.simulation.world.terrain;

import io.github.evoforge.simulation.world.material.MaterialDefinitionId;

public interface TerrainStorage {

    MaterialDefinitionId find(int x, int y, int z);

    void put(
            int x,
            int y,
            int z,
            MaterialDefinitionId definitionId);

    void remove(int x, int y, int z);
}
