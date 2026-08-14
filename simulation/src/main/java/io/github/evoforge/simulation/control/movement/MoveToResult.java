package io.github.evoforge.simulation.control.movement;

import io.github.evoforge.simulation.control.core.CommandResult;
import io.github.evoforge.simulation.result.ResultCode;
import io.github.evoforge.simulation.world.mechanics.movement.MoveToStartAttempt;
import io.github.evoforge.simulation.world.mechanics.movement.MoveToActionId;

/** Synchronous observation of whether a MoveTo intent was accepted. */
public record MoveToResult(
        boolean accepted,
        ResultCode code,
        MoveToActionId actionId)
        implements CommandResult {

    public MoveToResult {
        if (code == null) {
            throw new IllegalArgumentException(
                    "code must not be null");
        }
        if (accepted != (actionId != null)) {
            throw new IllegalArgumentException(
                    "accepted MoveTo result must have exactly one action id");
        }
    }

    public static MoveToResult from(
            MoveToStartAttempt attempt) {

        if (attempt == null) {
            throw new IllegalArgumentException(
                    "attempt must not be null");
        }
        return new MoveToResult(
                attempt.accepted(),
                attempt.code(),
                attempt.actionId());
    }
}
