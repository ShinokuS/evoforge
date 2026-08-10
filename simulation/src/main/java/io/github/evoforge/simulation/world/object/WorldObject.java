package io.github.evoforge.simulation.world.object;

import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;

public class WorldObject {

    private final ObjectId id;
    private final ObjectDefinitionId definitionId;

    protected WorldObject(
            ObjectId id,
            ObjectDefinitionId definitionId) {
        if (id == null) {
            throw new IllegalArgumentException(
                    "id must not be null");
        }

        if (definitionId == null) {
            throw new IllegalArgumentException(
                    "definitionId must not be null");
        }

        this.id = id;
        this.definitionId = definitionId;
    }

    public final ObjectId id() {
        return id;
    }

    public final ObjectDefinitionId definitionId() {
        return definitionId;
    }
}
