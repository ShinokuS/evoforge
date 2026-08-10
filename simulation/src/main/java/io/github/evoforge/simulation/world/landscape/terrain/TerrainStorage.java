package io.github.evoforge.simulation.world.landscape.terrain;

import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;

public interface TerrainStorage {

    LandscapeDefinitionId find(int x, int y, int z);

    void put(
            int x,
            int y,
            int z,
            LandscapeDefinitionId definitionId);

    void remove(int x, int y, int z);
}
