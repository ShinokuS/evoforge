package io.github.evoforge.simulation.world.landscape.terrain;

import io.github.evoforge.simulation.result.OperationResult;
import io.github.evoforge.simulation.result.ResultCode;

public enum TerrainPlacementResult implements OperationResult {

    PLACED(
            true,
            ResultCode.of("terrain", "placed")),
    POSITION_OCCUPIED(
            false,
            ResultCode.of("terrain", "position_occupied"));

    private final boolean accepted;
    private final ResultCode code;

    TerrainPlacementResult(
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
