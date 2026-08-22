package io.github.evoforge.simulation.world.space.placement;

import io.github.evoforge.simulation.kernel.operation.OperationResult;
import io.github.evoforge.simulation.kernel.operation.ResultCode;

public enum ObjectPlacementResult implements OperationResult {

    PLACED(
            true,
            ResultCode.of("object-placement", "placed")),
    DESTINATION_OCCUPIED(
            false,
            ResultCode.of("object-placement", "destination_occupied")),
    DESTINATION_RESERVED(
            false,
            ResultCode.of("object-placement", "destination_reserved"));

    private final boolean accepted;
    private final ResultCode code;

    ObjectPlacementResult(
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
