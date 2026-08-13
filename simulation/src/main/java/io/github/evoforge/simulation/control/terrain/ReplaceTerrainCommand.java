package io.github.evoforge.simulation.control.terrain;

import io.github.evoforge.simulation.control.core.Command;
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
