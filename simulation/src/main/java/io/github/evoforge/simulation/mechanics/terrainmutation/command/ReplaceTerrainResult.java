package io.github.evoforge.simulation.mechanics.terrainmutation.command;

import io.github.evoforge.simulation.kernel.command.CommandResult;
import io.github.evoforge.simulation.kernel.operation.ResultCode;

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
