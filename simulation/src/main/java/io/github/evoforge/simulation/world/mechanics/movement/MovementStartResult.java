package io.github.evoforge.simulation.world.mechanics.movement;

import io.github.evoforge.simulation.result.OperationResult;
import io.github.evoforge.simulation.result.ResultCode;

public enum MovementStartResult
        implements OperationResult {

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
            ResultCode.of("movement", "transition_unavailable")),
    DESTINATION_OCCUPIED(
            false,
            ResultCode.of("movement", "destination_occupied")),
    DESTINATION_RESERVED(
            false,
            ResultCode.of("movement", "destination_reserved"));

    private final boolean accepted;
    private final ResultCode code;

    MovementStartResult(
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
