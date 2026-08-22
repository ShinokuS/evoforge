package io.github.evoforge.simulation.mechanics.movement;

import io.github.evoforge.simulation.kernel.operation.ResultCode;
import io.github.evoforge.simulation.world.object.ObjectId;

/** Latest bounded terminal observation for one object's MoveTo operation. */
public record MoveToCompletion(
        MoveToActionId actionId,
        ObjectId objectId,
        boolean reachedGoal,
        ResultCode code) {

    public MoveToCompletion {
        if (actionId == null) {
            throw new IllegalArgumentException(
                    "actionId must not be null");
        }
        if (objectId == null) {
            throw new IllegalArgumentException(
                    "objectId must not be null");
        }
        if (code == null) {
            throw new IllegalArgumentException(
                    "code must not be null");
        }
    }
}
