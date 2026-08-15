package io.github.evoforge.simulation.world.mechanics.movement;

import io.github.evoforge.simulation.result.OperationResult;
import io.github.evoforge.simulation.result.ResultCode;

/** Result of requesting cancellation of one active long-range movement order. */
public record MoveToCancelAttempt(
        boolean accepted,
        ResultCode code)
        implements OperationResult {

    public MoveToCancelAttempt {
        if (code == null) {
            throw new IllegalArgumentException("code must not be null");
        }
    }

    public static MoveToCancelAttempt accepted(ResultCode code) {
        return new MoveToCancelAttempt(true, code);
    }

    public static MoveToCancelAttempt rejected(ResultCode code) {
        return new MoveToCancelAttempt(false, code);
    }
}
