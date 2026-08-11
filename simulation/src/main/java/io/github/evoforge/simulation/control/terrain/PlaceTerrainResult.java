package io.github.evoforge.simulation.control.terrain;

import io.github.evoforge.simulation.control.core.CommandResult;
import io.github.evoforge.simulation.result.ResultCode;

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
