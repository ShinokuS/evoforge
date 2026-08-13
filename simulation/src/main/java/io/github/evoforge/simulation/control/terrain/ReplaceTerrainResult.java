package io.github.evoforge.simulation.control.terrain;

import io.github.evoforge.simulation.control.core.CommandResult;
import io.github.evoforge.simulation.result.ResultCode;

public enum ReplaceTerrainResult implements CommandResult {

    REPLACED(
            true,
            ResultCode.of("terrain", "replaced")),
    TERRAIN_ABSENT(
            false,
            ResultCode.of("terrain", "terrain_absent"));

    private final boolean accepted;
    private final ResultCode code;

    ReplaceTerrainResult(
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
