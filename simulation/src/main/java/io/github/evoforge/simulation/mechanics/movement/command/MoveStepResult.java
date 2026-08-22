package io.github.evoforge.simulation.mechanics.movement.command;

import io.github.evoforge.simulation.kernel.command.CommandResult;
import io.github.evoforge.simulation.kernel.operation.OperationResult;
import io.github.evoforge.simulation.kernel.operation.ResultCode;

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
