package io.github.evoforge.simulation.mechanics.terrainmutation.command;

import io.github.evoforge.simulation.kernel.command.CommandHandler;
import io.github.evoforge.simulation.mechanics.terrainmutation.TerrainMutations;
import io.github.evoforge.simulation.world.terrain.TerrainReplacementResult;

public final class ReplaceTerrainHandler
        implements CommandHandler<
                ReplaceTerrainCommand,
                ReplaceTerrainResult> {

    private final TerrainMutations landscape;

    public ReplaceTerrainHandler(
            TerrainMutations landscape) {

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
