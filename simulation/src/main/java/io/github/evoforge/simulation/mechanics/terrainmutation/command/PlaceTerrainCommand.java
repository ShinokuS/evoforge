package io.github.evoforge.simulation.mechanics.terrainmutation.command;

import io.github.evoforge.simulation.kernel.command.Command;
import io.github.evoforge.simulation.world.material.MaterialDefinitionId;

public record PlaceTerrainCommand(
        int x,
        int y,
        int z,
        MaterialDefinitionId definitionId)
        implements Command<PlaceTerrainResult> {

    public PlaceTerrainCommand {
        if (definitionId == null) {
            throw new IllegalArgumentException(
                    "definitionId must not be null");
        }
    }
}
