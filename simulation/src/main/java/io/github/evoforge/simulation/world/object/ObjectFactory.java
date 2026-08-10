package io.github.evoforge.simulation.world.object;

import io.github.evoforge.simulation.definition.DefinitionCatalog;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;

import java.util.function.BiFunction;

public final class ObjectFactory {

    private final ObjectRepository objects;
    private final DefinitionCatalog<ObjectDefinitionId> definitions;

    public ObjectFactory(
            ObjectRepository objects,
            DefinitionCatalog<ObjectDefinitionId> definitions) {

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

    public WorldObject create(
            String definitionKey) {

        return create(
                definitionKey,
                WorldObject::new);
    }

    public WorldObject create(
            ObjectDefinitionId definitionId) {

        return create(
                definitionId,
                WorldObject::new);
    }

    public <T extends WorldObject> T create(
            String definitionKey,
            BiFunction<ObjectId, ObjectDefinitionId, T> creator) {

        if (definitionKey == null) {
            throw new IllegalArgumentException(
                    "definitionKey must not be null");
        }

        ObjectDefinitionId definitionId = definitions.resolve(definitionKey);

        if (definitionId == null) {
            throw new IllegalArgumentException(
                    "unknown definition: " + definitionKey);
        }

        return create(definitionId, creator);
    }

    public <T extends WorldObject> T create(
            ObjectDefinitionId definitionId,
            BiFunction<ObjectId, ObjectDefinitionId, T> creator) {

        if (definitionId == null) {
            throw new IllegalArgumentException(
                    "definitionId must not be null");
        }

        if (!definitions.contains(definitionId)) {
            throw new IllegalArgumentException(
                    "unknown definition: " + definitionId);
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
            ObjectDefinitionId definitionId,
            BiFunction<ObjectId, ObjectDefinitionId, T> creator) {

        T object = creator.apply(
                objectId,
                definitionId);

        if (object != null
                && !definitionId.equals(object.definitionId())) {

            throw new IllegalArgumentException(
                    "created object must use the supplied ObjectDefinitionId");
        }

        return object;
    }
}
