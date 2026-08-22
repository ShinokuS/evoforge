package io.github.evoforge.simulation.world.terrain.command;

import io.github.evoforge.simulation.kernel.command.Command;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;

public record ReplaceTerrainCommand(
        int x,
        int y,
        int z,
        LandscapeDefinitionId definitionId)
        implements Command<ReplaceTerrainResult> {

    public ReplaceTerrainCommand {
        if (definitionId == null) {
            throw new IllegalArgumentException(
                    "definitionId must not be null");
        }
    }
}
