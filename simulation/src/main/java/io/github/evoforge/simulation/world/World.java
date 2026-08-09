package io.github.evoforge.simulation.world;

import io.github.evoforge.simulation.world.definition.DefinitionResolver;
import io.github.evoforge.simulation.world.object.ObjectFactory;
import io.github.evoforge.simulation.world.object.ObjectRepository;

public final class World {

    private final ObjectRepository objects;
    private final ObjectFactory objectFactory;

    public World(DefinitionResolver definitions) {
        if (definitions == null) {
            throw new IllegalArgumentException(
                    "definitions must not be null");
        }

        objects = new ObjectRepository();

        objectFactory = new ObjectFactory(
                objects,
                definitions);
    }

    public ObjectRepository objects() {
        return objects;
    }

    public ObjectFactory objectFactory() {
        return objectFactory;
    }
}