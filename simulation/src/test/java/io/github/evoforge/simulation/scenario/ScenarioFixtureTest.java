package io.github.evoforge.simulation.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.mechanics.terrainmutation.command.PlaceTerrainCommand;
import io.github.evoforge.simulation.mechanics.terrainmutation.command.PlaceTerrainResult;
import io.github.evoforge.simulation.world.material.MaterialDefinitionId;
import io.github.evoforge.simulation.world.geometry.FullShape;

final class ScenarioFixtureTest {

    @Test
    void arrangesBeforeStartThenUsesProductionCommandPath() {
        ScenarioBuilder builder = ScenarioBuilder.create();

        MaterialDefinitionId granite =
                builder.landscapeDefinition("test:granite");
        MaterialDefinitionId soil =
                builder.landscapeDefinition("test:soil");

        builder.placeTerrain(
                4,
                5,
                6,
                granite);

        ScenarioHarness scenario = builder.start();

        assertEquals(
                granite,
                scenario.terrain().find(4, 5, 6));
        assertSame(
                FullShape.INSTANCE,
                scenario.geometry().find(4, 5, 6));

        PlaceTerrainResult placed =
                scenario.submit(
                        new PlaceTerrainCommand(
                                7,
                                8,
                                9,
                                soil));

        assertTrue(placed.accepted());
        assertEquals(
                soil,
                scenario.terrain().find(7, 8, 9));

        PlaceTerrainResult rejected =
                scenario.submit(
                        new PlaceTerrainCommand(
                                7,
                                8,
                                9,
                                granite));

        assertFalse(rejected.accepted());
        assertEquals(
                soil,
                scenario.terrain().find(7, 8, 9));
    }

    @Test
    void startClosesArrangeCapabilities() {
        ScenarioBuilder builder = ScenarioBuilder.create();

        MaterialDefinitionId granite =
                builder.landscapeDefinition("test:granite");

        builder.start();

        assertThrows(
                IllegalStateException.class,
                () -> builder.landscapeDefinition("test:soil"));
        assertThrows(
                IllegalStateException.class,
                () -> builder.placeTerrain(
                        0,
                        0,
                        0,
                        granite));
        assertThrows(
                IllegalStateException.class,
                builder::start);
    }
}
