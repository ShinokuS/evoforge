package io.github.evoforge.simulation.control.terrain;

import io.github.evoforge.simulation.control.core.CommandHandler;
import io.github.evoforge.simulation.world.landscape.LandscapeMutations;
import io.github.evoforge.simulation.world.landscape.terrain.TerrainReplacementResult;

public final class ReplaceTerrainHandler
        implements CommandHandler<
                ReplaceTerrainCommand,
                ReplaceTerrainResult> {

    private final LandscapeMutations landscape;

    public ReplaceTerrainHandler(
            LandscapeMutations landscape) {

        if (landscape == null) {
            throw new IllegalArgumentException(
                    "landscape must not be null");
        }
        this.landscape = landscape;
    }

    @Override
    public ReplaceTerrainResult handle(
            ReplaceTerrainCommand command) {

        if (command == null) {
            throw new IllegalArgumentException(
                    "command must not be null");
        }

        TerrainReplacementResult result =
                landscape.replaceTerrain(
                        command.x(),
                        command.y(),
                        command.z(),
                        command.definitionId());

        return switch (result) {
            case REPLACED -> ReplaceTerrainResult.REPLACED;
            case TERRAIN_ABSENT ->
                    ReplaceTerrainResult.TERRAIN_ABSENT;
        };
    }
}
