package io.github.evoforge.simulation.mechanics.movement;

import io.github.evoforge.simulation.kernel.operation.OperationResult;
import io.github.evoforge.simulation.kernel.operation.ResultCode;

/** Result plus ownership token from one locomotion-claim attempt. */
public record MovementClaimAttempt(
        boolean accepted,
        ResultCode code,
        MovementClaimId claimId)
        implements OperationResult {

    public MovementClaimAttempt {
        if (code == null) {
            throw new IllegalArgumentException(
                    "code must not be null");
        }
        if (accepted != (claimId != null)) {
            throw new IllegalArgumentException(
                    "accepted movement claim must have exactly one claim id");
        }
    }

    public static MovementClaimAttempt acquired(
            ResultCode code,
            MovementClaimId claimId) {
        return new MovementClaimAttempt(
                true,
                code,
                claimId);
    }

    public static MovementClaimAttempt rejected(
            ResultCode code) {
        return new MovementClaimAttempt(
                false,
                code,
                null);
    }
}
