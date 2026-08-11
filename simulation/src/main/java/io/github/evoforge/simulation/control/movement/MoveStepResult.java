package io.github.evoforge.simulation.control.movement;

import io.github.evoforge.simulation.control.core.CommandResult;
import io.github.evoforge.simulation.result.ResultCode;

public enum MoveStepResult
        implements CommandResult {

    STARTED(
            true,
            ResultCode.of("movement", "started")),
    MOVEMENT_UNAVAILABLE(
            false,
            ResultCode.of("movement", "movement_unavailable")),
    NOT_PLACED(
            false,
            ResultCode.of("movement", "not_placed")),
    ALREADY_MOVING(
            false,
            ResultCode.of("movement", "already_moving")),
    NOT_ADJACENT(
            false,
            ResultCode.of("movement", "not_adjacent")),
    TRANSITION_UNAVAILABLE(
            false,
            ResultCode.of("movement", "transition_unavailable"));

    private final boolean accepted;
    private final ResultCode code;

    MoveStepResult(
            boolean accepted,
            ResultCode code) {
        this.accepted = accepted;
        this.code = code;
    }

    @Override
    public boolean accepted() {
        return accepted;
    }

    @Override
    public ResultCode code() {
        return code;
    }
}
