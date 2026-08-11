package io.github.evoforge.simulation.world;

import io.github.evoforge.simulation.definition.DefinitionCatalog;
import io.github.evoforge.simulation.world.landscape.terrain.TerrainLookup;
import io.github.evoforge.simulation.world.object.ObjectFactory;
import io.github.evoforge.simulation.world.object.ObjectLookup;
import io.github.evoforge.simulation.world.object.ObjectRepository;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;

public final class World {

    private final ObjectRepository objects;
    private final ObjectFactory objectFactory;
    private final TerrainLookup terrain;

    public World(
            DefinitionCatalog<ObjectDefinitionId> objectDefinitions,
            TerrainLookup terrain) {

        if (objectDefinitions == null) {
            throw new IllegalArgumentException(
                    "objectDefinitions must not be null");
        }

        if (terrain == null) {
            throw new IllegalArgumentException(
                    "terrain must not be null");
        }

        objects = new ObjectRepository();

        objectFactory = new ObjectFactory(
                objects,
                objectDefinitions);

        this.terrain = terrain;
    }

    public ObjectLookup objects() {
        return objects;
    }

    public ObjectFactory objectFactory() {
        return objectFactory;
    }

    public TerrainLookup terrain() {
        return terrain;
    }
}
