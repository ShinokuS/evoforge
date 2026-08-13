package io.github.evoforge.simulation.world.mechanics.movement;

import io.github.evoforge.simulation.result.ResultCode;
import io.github.evoforge.simulation.world.object.ObjectId;

/** Stable edge-completion fact plus open diagnostic data. */
public record MovementStepCompletion(
        MovementActionId actionId,
        ObjectId objectId,
        boolean committed,
        ResultCode code) {

    public MovementStepCompletion {
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
