package io.github.evoforge.simulation.mechanics.terrainmutation.command;

import io.github.evoforge.simulation.kernel.command.CommandHandler;
import io.github.evoforge.simulation.mechanics.terrainmutation.TerrainMutations;
import io.github.evoforge.simulation.world.terrain.TerrainPlacementResult;

public final class PlaceTerrainHandler
        implements CommandHandler<
                PlaceTerrainCommand,
                PlaceTerrainResult> {

    private final TerrainMutations landscape;

    public PlaceTerrainHandler(
            TerrainMutations landscape) {

        if (landscape == null) {
            throw new IllegalArgumentException(
                    "landscape must not be null");
        }

        this.landscape = landscape;
    }

    @Override
    public PlaceTerrainResult handle(
            PlaceTerrainCommand command) {

        if (command == null) {
            throw new IllegalArgumentException(
                    "command must not be null");
        }

        TerrainPlacementResult result =
                landscape.placeTerrain(
                        command.x(),
                        command.y(),
                        command.z(),
                        command.definitionId());

        return switch (result) {
            case PLACED -> PlaceTerrainResult.PLACED;
            case POSITION_OCCUPIED ->
                    PlaceTerrainResult.POSITION_OCCUPIED;
        };
    }
}
