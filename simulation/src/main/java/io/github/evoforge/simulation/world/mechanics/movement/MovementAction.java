package io.github.evoforge.simulation.world.mechanics.movement;

import io.github.evoforge.simulation.world.object.ObjectId;

public record MovementAction(
        MovementActionId id,
        ObjectId objectId,
        int fromX,
        int fromY,
        int fromZ,
        int toX,
        int toY,
        int toZ) {

    public MovementAction {
        if (id == null) {
            throw new IllegalArgumentException(
                    "id must not be null");
        }
        if (objectId == null) {
            throw new IllegalArgumentException(
                    "objectId must not be null");
        }
    }
}
