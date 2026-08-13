package io.github.evoforge.simulation.world.mechanics.movement;

import io.github.evoforge.simulation.result.OperationResult;
import io.github.evoforge.simulation.result.ResultCode;

/** Result of attempting to start one concrete timed movement edge. */
public record MovementStartAttempt(
        boolean accepted,
        ResultCode code,
        MovementActionId actionId)
        implements OperationResult {

    public MovementStartAttempt {
        if (code == null) {
            throw new IllegalArgumentException(
                    "code must not be null");
        }
        if (accepted != (actionId != null)) {
            throw new IllegalArgumentException(
                    "accepted movement start must have exactly one action id");
        }
    }

    public static MovementStartAttempt started(
            ResultCode code,
            MovementActionId actionId) {

        return new MovementStartAttempt(
                true,
                code,
                actionId);
    }

    public static MovementStartAttempt rejected(
            ResultCode code) {

        return new MovementStartAttempt(
                false,
                code,
                null);
    }
}
