package io.github.evoforge.simulation.world.landscape.terrain;

import io.github.evoforge.simulation.definition.DefinitionCatalog;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;

public final class TerrainSystem {

    private final TerrainStorage storage;
    private final DefinitionCatalog<LandscapeDefinitionId> definitions;
    private final TerrainLookup lookup;

    public TerrainSystem(
            TerrainStorage storage,
            DefinitionCatalog<LandscapeDefinitionId> definitions) {

        if (storage == null) {
            throw new IllegalArgumentException(
                    "storage must not be null");
        }

        if (definitions == null) {
            throw new IllegalArgumentException(
                    "definitions must not be null");
        }

        this.storage = storage;
        this.definitions = definitions;
        lookup = storage::find;
    }

    public TerrainLookup lookup() {
        return lookup;
    }

    public void place(
            int x,
            int y,
            int z,
            LandscapeDefinitionId definitionId) {

        requireKnownDefinition(definitionId);

        if (storage.find(x, y, z) != null) {
            throw new IllegalStateException(
                    "terrain already exists at " + position(x, y, z));
        }

        storage.put(x, y, z, definitionId);
    }

    public void replace(
            int x,
            int y,
            int z,
            LandscapeDefinitionId definitionId) {

        requireKnownDefinition(definitionId);

        if (storage.find(x, y, z) == null) {
            throw new IllegalStateException(
                    "terrain does not exist at " + position(x, y, z));
        }

        storage.put(x, y, z, definitionId);
    }

    public void remove(int x, int y, int z) {
        if (storage.find(x, y, z) == null) {
            throw new IllegalStateException(
                    "terrain does not exist at " + position(x, y, z));
        }

        storage.remove(x, y, z);
    }

    private void requireKnownDefinition(
            LandscapeDefinitionId definitionId) {

        if (definitionId == null) {
            throw new IllegalArgumentException(
                    "definitionId must not be null");
        }

        if (!definitions.contains(definitionId)) {
            throw new IllegalArgumentException(
                    "unknown landscape definition: " + definitionId);
        }
    }

    private static String position(int x, int y, int z) {
        return "(" + x + ", " + y + ", " + z + ")";
    }
}
