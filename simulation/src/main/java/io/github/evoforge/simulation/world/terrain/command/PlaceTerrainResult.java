package io.github.evoforge.simulation.world.terrain.command;

import io.github.evoforge.simulation.kernel.command.CommandResult;
import io.github.evoforge.simulation.kernel.operation.ResultCode;

public enum PlaceTerrainResult implements CommandResult {

    PLACED(
            true,
            ResultCode.of("terrain", "placed")),
    POSITION_OCCUPIED(
            false,
            ResultCode.of("terrain", "position_occupied"));

    private final boolean accepted;
    private final ResultCode code;

    PlaceTerrainResult(
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
