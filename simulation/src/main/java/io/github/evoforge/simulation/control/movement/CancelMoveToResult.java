package io.github.evoforge.simulation.control.movement;

import io.github.evoforge.simulation.control.core.CommandResult;
import io.github.evoforge.simulation.result.ResultCode;
import io.github.evoforge.simulation.world.mechanics.movement.MoveToCancelAttempt;

/** Synchronous observation of whether cancellation was accepted. */
public record CancelMoveToResult(
        boolean accepted,
        ResultCode code)
        implements CommandResult {

    public CancelMoveToResult {
        if (code == null) {
            throw new IllegalArgumentException("code must not be null");
        }
    }

    public static CancelMoveToResult from(MoveToCancelAttempt attempt) {
        if (attempt == null) {
            throw new IllegalArgumentException("attempt must not be null");
        }
        return new CancelMoveToResult(attempt.accepted(), attempt.code());
    }
}
