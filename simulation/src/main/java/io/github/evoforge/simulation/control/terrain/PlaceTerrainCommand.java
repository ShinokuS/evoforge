package io.github.evoforge.simulation.control.terrain;

import io.github.evoforge.simulation.control.core.Command;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;

public record PlaceTerrainCommand(
        int x,
        int y,
        int z,
        LandscapeDefinitionId definitionId)
        implements Command<PlaceTerrainResult> {

    public PlaceTerrainCommand {
        if (definitionId == null) {
            throw new IllegalArgumentException(
                    "definitionId must not be null");
        }
    }
}
