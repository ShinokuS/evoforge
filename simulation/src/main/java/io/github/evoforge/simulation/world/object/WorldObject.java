package io.github.evoforge.simulation.world.object;

import io.github.evoforge.simulation.world.definition.DefinitionId;

public abstract class WorldObject {

    private final ObjectId id;
    private final DefinitionId definitionId;

    protected WorldObject(
            ObjectId id,
            DefinitionId definitionId) {
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

    public final DefinitionId definitionId() {
        return definitionId;
    }
}