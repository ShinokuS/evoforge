package io.github.evoforge.simulation.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.mechanics.movement.command.MoveStepCommand;
import io.github.evoforge.simulation.mechanics.movement.command.MoveStepResult;
import io.github.evoforge.simulation.mechanics.movement.command.MoveToCommand;
import io.github.evoforge.simulation.mechanics.movement.command.MoveToResult;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.mechanics.movement.MoveToCompletion;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;
import io.github.evoforge.simulation.world.navigation.pathfinding.PathRoute;

final class WaterWadingRuntimeIntegrationTest {

    @Test
    void moveToPlansAroundCurrentTooDeepSurfaceWater() {
        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId soil =
                assembly.landscapeDefinition("test:wading_soil");
        LandscapeDefinitionId basin =
                assembly.landscapeDefinition("test:wading_basin");
        assembly.soilProperties(soil, 1_000_000, 1_000_000);
        assembly.periodicPrecipitation(300_000, 1L);

        ObjectDefinitionId walker =
                assembly.objectDefinition("test:wading_walker");
        assembly.movementRate(walker, 1_000);
        assembly.waterWading(walker, 100_000);

        // Two-row floor. Only the direct middle cell is non-absorbing, so the first
        // rain event creates a deep Water obstacle there while the detour stays dry.
        for (int x = 0; x <= 2; x++) {
            for (int y = 0; y <= 1; y++) {
                LandscapeDefinitionId definition =
                        x == 1 && y == 0 ? basin : soil;
                assembly.placeTerrain(x, y, 0, definition);
            }
        }

        ObjectId mover = assembly.createObject(walker);
        assembly.placeObject(mover, 0, 0, 1);
        SimulationRuntime runtime = assembly.start();

        runtime.stepper().advance();
        assertEquals(300_000, runtime.view().water().amount(1, 0, 1));
        assertEquals(0, runtime.view().water().amount(1, 1, 1));

        MoveToResult result = runtime.submit(
                new MoveToCommand(mover, 2, 0, 1));
        assertTrue(result.accepted());

        PathRoute route = runtime.view().moveTo().activeRoute(mover);
        assertNotNull(route);
        for (int index = 0; index < route.size(); index++) {
            assertFalse(
                    route.x(index) == 1
                            && route.y(index) == 0
                            && route.z(index) == 1,
                    "planned route entered water deeper than mover tolerance");
        }
    }

    @Test
    void rainThatArrivesDuringTimedStepCancelsAuthoritativeCommit() {
        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId soil =
                assembly.landscapeDefinition("test:commit_soil");
        LandscapeDefinitionId basin =
                assembly.landscapeDefinition("test:commit_basin");
        assembly.soilProperties(soil, 1_000_000, 1_000_000);
        assembly.periodicPrecipitation(900_000, 1L);

        ObjectDefinitionId walker =
                assembly.objectDefinition("test:commit_walker");
        assembly.movementRate(walker, 500);
        assembly.waterWading(walker, 100_000);

        assembly.placeTerrain(0, 0, 0, soil);
        assembly.placeTerrain(1, 0, 0, basin);

        // Three absorbing full-shape walls contain the destination Water laterally;
        // the only open lateral exit is back toward the source.
        assembly.placeTerrain(2, 0, 1, soil);
        assembly.placeTerrain(1, -1, 1, soil);
        assembly.placeTerrain(1, 1, 1, soil);

        ObjectId mover = assembly.createObject(walker);
        assembly.placeObject(mover, 0, 0, 1);
        SimulationRuntime runtime = assembly.start();

        MoveStepResult started = runtime.submit(
                new MoveStepCommand(mover, 1, 0, 1));
        assertTrue(started.accepted());

        runtime.stepper().advance();
        assertEquals(1L, runtime.time().tick());
        assertEquals(900_000, runtime.view().water().amount(1, 0, 1));

        runtime.stepper().advance();
        assertEquals(2L, runtime.time().tick());
        assertEquals(0, runtime.view().transforms().x(mover));
        assertEquals(0, runtime.view().transforms().y(mover));
        assertEquals(1, runtime.view().transforms().z(mover));
    }

    @Test
    void moverWithoutWadingProfileKeepsPreviousWaterNeutralBehavior() {
        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId basin =
                assembly.landscapeDefinition("test:neutral_basin");
        assembly.periodicPrecipitation(300_000, 1L);

        ObjectDefinitionId walker =
                assembly.objectDefinition("test:neutral_walker");
        assembly.movementRate(walker, 1_000);
        assembly.placeTerrain(0, 0, 0, basin);
        assembly.placeTerrain(1, 0, 0, basin);

        ObjectId mover = assembly.createObject(walker);
        assembly.placeObject(mover, 0, 0, 1);
        SimulationRuntime runtime = assembly.start();
        runtime.stepper().advance();

        MoveStepResult result = runtime.submit(
                new MoveStepCommand(mover, 1, 0, 1));
        assertTrue(result.accepted());
    }
}
