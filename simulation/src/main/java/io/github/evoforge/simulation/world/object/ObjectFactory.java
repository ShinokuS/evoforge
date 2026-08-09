package io.github.evoforge.simulation.world.object;

import io.github.evoforge.simulation.world.definition.DefinitionId;
import io.github.evoforge.simulation.world.definition.DefinitionResolver;

import java.util.function.BiFunction;

public final class ObjectFactory {

    private final ObjectRepository objects;
    private final DefinitionResolver definitions;

    public ObjectFactory(
            ObjectRepository objects,
            DefinitionResolver definitions) {
        if (objects == null) {
            throw new IllegalArgumentException(
                    "objects must not be null");
        }

        if (definitions == null) {
            throw new IllegalArgumentException(
                    "definitions must not be null");
        }

        this.objects = objects;
        this.definitions = definitions;
    }

    public <T extends WorldObject> T create(
            String definitionKey,
            BiFunction<ObjectId, DefinitionId, T> creator) {
        if (definitionKey == null) {
            throw new IllegalArgumentException(
                    "definitionKey must not be null");
        }

        DefinitionId definitionId = definitions.resolve(definitionKey);

        if (definitionId == null) {
            throw new IllegalArgumentException(
                    "unknown definition: " + definitionKey);
        }

        return create(definitionId, creator);
    }

    public <T extends WorldObject> T create(
            DefinitionId definitionId,
            BiFunction<ObjectId, DefinitionId, T> creator) {
        if (definitionId == null) {
            throw new IllegalArgumentException(
                    "definitionId must not be null");
        }

        if (creator == null) {
            throw new IllegalArgumentException(
                    "creator must not be null");
        }

        return objects.create(
                objectId -> createObject(
                        objectId,
                        definitionId,
                        creator));
    }

    private <T extends WorldObject> T createObject(
            ObjectId objectId,
            DefinitionId definitionId,
            BiFunction<ObjectId, DefinitionId, T> creator) {
        T object = creator.apply(
                objectId,
                definitionId);

        if (object != null
                && !definitionId.equals(object.definitionId())) {
            throw new IllegalArgumentException(
                    "created object must use the supplied DefinitionId");
        }

        return object;
    }
}