package io.github.evoforge.simulation.world.terrain;

import io.github.evoforge.simulation.kernel.operation.OperationResult;
import io.github.evoforge.simulation.kernel.operation.ResultCode;

public enum TerrainRemovalResult implements OperationResult {

    REMOVED(
            true,
            ResultCode.of("terrain", "removed")),
    TERRAIN_ABSENT(
            false,
            ResultCode.of("terrain", "terrain_absent"));

    private final boolean accepted;
    private final ResultCode code;

    TerrainRemovalResult(
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
