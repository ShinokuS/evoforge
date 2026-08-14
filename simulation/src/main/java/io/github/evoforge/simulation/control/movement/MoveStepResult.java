package io.github.evoforge.simulation.control.movement;

import io.github.evoforge.simulation.control.core.CommandResult;
import io.github.evoforge.simulation.result.OperationResult;
import io.github.evoforge.simulation.result.ResultCode;

/** Domain-neutral command observation of one movement-start attempt. */
public record MoveStepResult(
        boolean accepted,
        ResultCode code)
        implements CommandResult {

    public MoveStepResult {
        if (code == null) {
            throw new IllegalArgumentException(
                    "code must not be null");
        }
    }

    public static MoveStepResult from(
            OperationResult result) {

        if (result == null) {
            throw new IllegalArgumentException(
                    "result must not be null");
        }

        return new MoveStepResult(
                result.accepted(),
                result.code());
    }
}
