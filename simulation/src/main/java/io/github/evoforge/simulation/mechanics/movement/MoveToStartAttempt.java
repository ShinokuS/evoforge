package io.github.evoforge.simulation.mechanics.movement;

import io.github.evoforge.simulation.kernel.operation.OperationResult;
import io.github.evoforge.simulation.kernel.operation.ResultCode;

/** Result of accepting or rejecting one external long-range movement intent. */
public record MoveToStartAttempt(
        boolean accepted,
        ResultCode code,
        MoveToActionId actionId)
        implements OperationResult {

    public MoveToStartAttempt {
        if (code == null) {
            throw new IllegalArgumentException(
                    "code must not be null");
        }
        if (accepted != (actionId != null)) {
            throw new IllegalArgumentException(
                    "accepted MoveTo must have exactly one action id");
        }
    }

    public static MoveToStartAttempt started(
            ResultCode code,
            MoveToActionId actionId) {
        return new MoveToStartAttempt(
                true,
                code,
                actionId);
    }

    public static MoveToStartAttempt rejected(
            ResultCode code) {
        return new MoveToStartAttempt(
                false,
                code,
                null);
    }
}
