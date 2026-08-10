package io.github.evoforge.simulation.world;

import io.github.evoforge.simulation.definition.DefinitionCatalog;
import io.github.evoforge.simulation.world.object.ObjectFactory;
import io.github.evoforge.simulation.world.object.ObjectLookup;
import io.github.evoforge.simulation.world.object.ObjectRepository;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;

public final class World {

    private final ObjectRepository objects;
    private final ObjectFactory objectFactory;

    public World(DefinitionCatalog<ObjectDefinitionId> definitions) {
        if (definitions == null) {
            throw new IllegalArgumentException(
                    "definitions must not be null");
        }

        objects = new ObjectRepository();

        objectFactory = new ObjectFactory(
                objects,
                definitions);
    }

    public ObjectLookup objects() {
        return objects;
    }

    public ObjectFactory objectFactory() {
        return objectFactory;
    }
}
