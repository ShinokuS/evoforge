package io.github.evoforge.simulation.mechanics.terrainmutation.command;

import io.github.evoforge.simulation.kernel.command.Command;
import io.github.evoforge.simulation.world.material.MaterialDefinitionId;

public record ReplaceTerrainCommand(
        int x,
        int y,
        int z,
        MaterialDefinitionId definitionId)
        implements Command<ReplaceTerrainResult> {

    public ReplaceTerrainCommand {
        if (definitionId == null) {
            throw new IllegalArgumentException(
                    "definitionId must not be null");
        }
    }
}
