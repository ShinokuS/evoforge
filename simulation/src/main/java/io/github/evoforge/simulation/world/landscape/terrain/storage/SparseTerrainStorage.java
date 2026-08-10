package io.github.evoforge.simulation.world.landscape.terrain.storage;

import java.util.HashMap;
import java.util.Map;

import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.landscape.terrain.TerrainStorage;

public final class SparseTerrainStorage implements TerrainStorage {

    private final Map<Cell, LandscapeDefinitionId> terrain =
            new HashMap<>();

    @Override
    public LandscapeDefinitionId find(
            int x,
            int y,
            int z) {

        return terrain.get(
                new Cell(x, y, z));
    }

    @Override
    public void put(
            int x,
            int y,
            int z,
            LandscapeDefinitionId definitionId) {

        if (definitionId == null) {
            throw new IllegalArgumentException(
                    "definitionId must not be null");
        }

        terrain.put(
                new Cell(x, y, z),
                definitionId);
    }

    @Override
    public void remove(
            int x,
            int y,
            int z) {

        terrain.remove(
                new Cell(x, y, z));
    }

    private record Cell(
            int x,
            int y,
            int z) {
    }
}
