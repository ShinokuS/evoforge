package io.github.evoforge.simulation.world.terrain.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.kernel.command.SynchronousCommandGateway;
import io.github.evoforge.simulation.kernel.operation.ResultCode;
import io.github.evoforge.simulation.scenario.ScenarioBuilder;
import io.github.evoforge.simulation.scenario.ScenarioHarness;
import io.github.evoforge.simulation.world.landscape.LandscapeMutations;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.terrain.TerrainPlacementResult;
import io.github.evoforge.simulation.world.terrain.TerrainRemovalResult;
import io.github.evoforge.simulation.world.terrain.TerrainReplacementResult;

final class PlaceTerrainCommandIntegrationTest {

    private static final LandscapeDefinitionId UNKNOWN =
            LandscapeDefinitionId.of(2);

    @Test
    void synchronousGatewayPlacesTerrainAndReturnsStructuredRejection() {
        ScenarioBuilder builder = ScenarioBuilder.create();

        LandscapeDefinitionId granite =
                builder.landscapeDefinition("test:granite");
        LandscapeDefinitionId soil =
                builder.landscapeDefinition("test:soil");

        ScenarioHarness scenario = builder.start();

        PlaceTerrainResult first =
                scenario.submit(
                        new PlaceTerrainCommand(
                                4,
                                5,
                                6,
                                granite));

        assertEquals(
                PlaceTerrainResult.PLACED,
                first);
        assertTrue(first.accepted());
        assertEquals(
                ResultCode.of("terrain", "placed"),
                first.code());
        assertEquals(
                granite,
                scenario.terrain().find(4, 5, 6));

        PlaceTerrainResult second =
                scenario.submit(
                        new PlaceTerrainCommand(
                                4,
                                5,
                                6,
                                soil));

        assertEquals(
                PlaceTerrainResult.POSITION_OCCUPIED,
                second);
        assertFalse(second.accepted());
        assertEquals(
                ResultCode.of(
                        "terrain",
                        "position_occupied"),
                second.code());
        assertEquals(
                granite,
                scenario.terrain().find(4, 5, 6));
    }

    @Test
    void invalidRuntimeDefinitionRemainsProgrammingError() {
        ScenarioBuilder builder = ScenarioBuilder.create();
        builder.landscapeDefinition("test:granite");
        builder.landscapeDefinition("test:soil");

        ScenarioHarness scenario = builder.start();

        assertThrows(
                IllegalArgumentException.class,
                () -> scenario.submit(
                        new PlaceTerrainCommand(
                                1,
                                2,
                                3,
                                UNKNOWN)));
    }

    @Test
    void commandAndHandlerRejectNullProgrammingInputs() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PlaceTerrainCommand(
                        1,
                        2,
                        3,
                        null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PlaceTerrainHandler(null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SynchronousCommandGateway(null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PlaceTerrainHandler(
                        unusedLandscape())
                        .handle(null));
    }

    private static LandscapeMutations unusedLandscape() {
        return new LandscapeMutations() {
            @Override
            public TerrainPlacementResult placeTerrain(
                    int x,
                    int y,
                    int z,
                    LandscapeDefinitionId definitionId) {
                throw new AssertionError("must not be called");
            }

            @Override
            public TerrainReplacementResult replaceTerrain(
                    int x,
                    int y,
                    int z,
                    LandscapeDefinitionId definitionId) {
                throw new AssertionError("must not be called");
            }

            @Override
            public TerrainRemovalResult removeTerrain(
                    int x,
                    int y,
                    int z) {
                throw new AssertionError("must not be called");
            }
        };
    }
}
