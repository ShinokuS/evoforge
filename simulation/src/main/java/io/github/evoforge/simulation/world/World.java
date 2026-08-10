package io.github.evoforge.simulation.world;

import io.github.evoforge.simulation.definition.DefinitionCatalog;
import io.github.evoforge.simulation.world.object.ObjectFactory;
import io.github.evoforge.simulation.world.object.ObjectLookup;
import io.github.evoforge.simulation.world.object.ObjectRepository;

public final class World {

    private final ObjectRepository objects;
    private final ObjectFactory objectFactory;

    public World(DefinitionCatalog definitions) {
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