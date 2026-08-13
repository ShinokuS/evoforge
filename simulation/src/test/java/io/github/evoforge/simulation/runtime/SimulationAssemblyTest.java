package io.github.evoforge.simulation.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.control.movement.MoveStepCommand;
import io.github.evoforge.simulation.control.movement.MoveToCommand;
import io.github.evoforge.simulation.control.movement.MoveToResult;
import io.github.evoforge.simulation.control.terrain.ReplaceTerrainCommand;
import io.github.evoforge.simulation.control.terrain.ReplaceTerrainResult;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.mechanics.movement.MoveToCompletion;
import io.github.evoforge.simulation.world.mechanics.occupancy.OccupancyState;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;
import org.junit.jupiter.api.Test;

final class SimulationAssemblyTest {

    @Test
    void closesSetupMutationAfterStartAndExposesReadOnlyRuntimeView() {
        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId ground =
                assembly.landscapeDefinition("test:ground");
        ObjectDefinitionId walker =
                assembly.objectDefinition("test:walker");
        assembly.movementRate(walker, 100);

        ObjectId objectId = assembly.createObject(walker);
        assembly.placeObject(objectId, 2, 3, 0);
        assembly.placeTerrain(2, 3, -1, ground);

        SimulationRuntime runtime = assembly.start();

        assertEquals(1, runtime.view().cells().objectCount(2, 3, 0));
        assertEquals(objectId, runtime.view().cells().objectAt(2, 3, 0, 0));
        assertEquals(ground, runtime.view().terrain().find(2, 3, -1));

        assertThrows(
                IllegalStateException.class,
                () -> assembly.placeTerrain(3, 3, -1, ground));
        assertThrows(
                IllegalStateException.class,
                () -> assembly.createObject(walker));
        assertThrows(IllegalStateException.class, assembly::start);
    }

    @Test
    void runtimeTerrainReplacementUsesControlBoundary() {
        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId ground =
                assembly.landscapeDefinition("test:replace_ground", 1000);
        LandscapeDefinitionId slow =
                assembly.landscapeDefinition("test:replace_slow", 6000);
        assembly.placeTerrain(1, 0, -1, ground);

        SimulationRuntime runtime = assembly.start();

        assertEquals(
                ReplaceTerrainResult.REPLACED,
                runtime.submit(new ReplaceTerrainCommand(1, 0, -1, slow)));
        assertEquals(slow, runtime.view().terrain().find(1, 0, -1));
        assertEquals(
                ReplaceTerrainResult.TERRAIN_ABSENT,
                runtime.submit(new ReplaceTerrainCommand(2, 0, -1, slow)));
    }

    @Test
    void movementUsesProductionGraphAndKeepsCellIndexSynchronized() {
        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId ground =
                assembly.landscapeDefinition("test:ground");
        ObjectDefinitionId walker =
                assembly.objectDefinition("test:walker");
        assembly.movementRate(walker, 1000);

        ObjectId objectId = assembly.createObject(walker);
        assembly.placeObject(objectId, 0, 0, 0);
        assembly.placeTerrain(0, 0, -1, ground);
        assembly.placeTerrain(1, 0, -1, ground);

        SimulationRuntime runtime = assembly.start();

        assertTrue(
                runtime.submit(new MoveStepCommand(objectId, 1, 0, 0))
                        .accepted());

        assertEquals(1, runtime.view().cells().objectCount(0, 0, 0));
        assertEquals(0, runtime.view().cells().objectCount(1, 0, 0));

        runtime.stepper().advance();

        assertEquals(0, runtime.view().cells().objectCount(0, 0, 0));
        assertEquals(1, runtime.view().cells().objectCount(1, 0, 0));
        assertEquals(objectId, runtime.view().cells().objectAt(1, 0, 0, 0));
    }

    @Test
    void moveToChainsEdgesWithoutAddingOrchestrationTicks() {
        MovementRun run = linearMovementRun(3, 300, true);

        MoveToResult started = run.runtime().submit(
                new MoveToCommand(run.objectId(), 3, 0, 0));

        assertTrue(started.accepted());
        assertNotNull(started.actionId());
        assertTrue(run.runtime().view().moveTo().isActive(run.objectId()));
        assertEquals(
                OccupancyState.RESERVED,
                run.runtime().view().occupancy().state(1, 0, 0));

        advance(run.runtime(), 3);
        assertEquals(1, run.runtime().view().transforms().x(run.objectId()));
        assertEquals(
                OccupancyState.RESERVED,
                run.runtime().view().occupancy().state(2, 0, 0));

        advance(run.runtime(), 3);
        assertEquals(2, run.runtime().view().transforms().x(run.objectId()));
        assertEquals(
                OccupancyState.RESERVED,
                run.runtime().view().occupancy().state(3, 0, 0));

        advance(run.runtime(), 3);
        assertEquals(2, run.runtime().view().transforms().x(run.objectId()));
        advance(run.runtime(), 1);

        assertEquals(3, run.runtime().view().transforms().x(run.objectId()));
        assertEquals(10, run.runtime().time().tick());
        assertFalse(run.runtime().view().moveTo().isActive(run.objectId()));
        MoveToCompletion completion =
                run.runtime().view().moveTo().lastCompletion(run.objectId());
        assertNotNull(completion);
        assertTrue(completion.reachedGoal());
        assertEquals("movement:goal_reached", completion.code().value());
    }

    @Test
    void moveToClaimRejectsCompetingStandaloneMovement() {
        MovementRun run = linearMovementRun(2, 100, false);

        assertTrue(
                run.runtime().submit(
                        new MoveToCommand(run.objectId(), 2, 0, 0))
                        .accepted());

        var competing = run.runtime().submit(
                new MoveStepCommand(run.objectId(), 1, 0, 0));

        assertFalse(competing.accepted());
        assertEquals("movement:already_moving", competing.code().value());
    }

    @Test
    void moveToNoPathIsAcceptedIntentWithImmediateTerminalOutcome() {
        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId ground =
                assembly.landscapeDefinition("test:isolated_ground");
        ObjectDefinitionId walker =
                assembly.objectDefinition("test:isolated_walker");
        assembly.movementRate(walker, 1000);
        ObjectId objectId = assembly.createObject(walker);
        assembly.placeObject(objectId, 0, 0, 0);
        assembly.placeTerrain(0, 0, -1, ground);

        SimulationRuntime runtime = assembly.start();
        MoveToResult result = runtime.submit(
                new MoveToCommand(objectId, 2, 0, 0));

        assertTrue(result.accepted());
        assertEquals(0, runtime.time().tick());
        assertFalse(runtime.view().moveTo().isActive(objectId));
        MoveToCompletion completion =
                runtime.view().moveTo().lastCompletion(objectId);
        assertNotNull(completion);
        assertFalse(completion.reachedGoal());
        assertEquals("movement:no_path", completion.code().value());
        assertEquals(0, runtime.view().transforms().x(objectId));
    }

    @Test
    void moveToSourceEqualGoalCompletesWithoutPhysicalStep() {
        MovementRun run = linearMovementRun(0, 1000, true);

        MoveToResult result = run.runtime().submit(
                new MoveToCommand(run.objectId(), 0, 0, 0));

        assertTrue(result.accepted());
        assertEquals(0, run.runtime().time().tick());
        assertFalse(run.runtime().view().moveTo().isActive(run.objectId()));
        MoveToCompletion completion =
                run.runtime().view().moveTo().lastCompletion(run.objectId());
        assertNotNull(completion);
        assertTrue(completion.reachedGoal());
        assertEquals("movement:goal_reached", completion.code().value());
    }

    @Test
    void laterOccupiedRouteEdgeStopsAtLastCommittedCellAndReleasesClaim() {
        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId ground =
                assembly.landscapeDefinition("test:occupied_route_ground");
        ObjectDefinitionId walker =
                assembly.objectDefinition("test:occupied_route_walker");
        assembly.movementRate(walker, 1000);
        assembly.exclusiveOccupancy(walker);
        for (int x = 0; x <= 2; x++) {
            assembly.placeTerrain(x, 0, -1, ground);
        }

        ObjectId mover = assembly.createObject(walker);
        ObjectId blocker = assembly.createObject(walker);
        assembly.placeObject(mover, 0, 0, 0);
        assembly.placeObject(blocker, 2, 0, 0);

        SimulationRuntime runtime = assembly.start();
        assertTrue(
                runtime.submit(new MoveToCommand(mover, 2, 0, 0))
                        .accepted());

        runtime.stepper().advance();

        assertEquals(1, runtime.view().transforms().x(mover));
        assertFalse(runtime.view().moveTo().isActive(mover));
        MoveToCompletion completion =
                runtime.view().moveTo().lastCompletion(mover);
        assertNotNull(completion);
        assertFalse(completion.reachedGoal());
        assertEquals(
                "movement:destination_occupied",
                completion.code().value());

        assertTrue(
                runtime.submit(new MoveStepCommand(mover, 0, 0, 0))
                        .accepted());
    }

    @Test
    void occupancySemanticsCannotChangeAfterDefinitionInstancesArePlaced() {
        SimulationAssembly assembly = SimulationAssembly.create();
        ObjectDefinitionId definition =
                assembly.objectDefinition("test:object");
        ObjectId objectId = assembly.createObject(definition);

        assembly.placeObject(objectId, 0, 0, 0);

        assertThrows(
                IllegalStateException.class,
                () -> assembly.exclusiveOccupancy(definition));
    }

    @Test
    void occupancyMayBeConfiguredAfterCreationButBeforePlacement() {
        SimulationAssembly assembly = SimulationAssembly.create();
        ObjectDefinitionId definition =
                assembly.objectDefinition("test:object");
        ObjectId objectId = assembly.createObject(definition);

        assembly.exclusiveOccupancy(definition);
        assembly.placeObject(objectId, 0, 0, 0);

        SimulationRuntime runtime = assembly.start();

        assertEquals(
                OccupancyState.OCCUPIED,
                runtime.view().occupancy().state(0, 0, 0));
    }

    private static MovementRun linearMovementRun(
            int maxX,
            long rate,
            boolean exclusive) {

        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId ground =
                assembly.landscapeDefinition("test:linear_ground");
        ObjectDefinitionId walker =
                assembly.objectDefinition("test:linear_walker");
        assembly.movementRate(walker, rate);
        if (exclusive) {
            assembly.exclusiveOccupancy(walker);
        }
        for (int x = 0; x <= maxX; x++) {
            assembly.placeTerrain(x, 0, -1, ground);
        }
        ObjectId objectId = assembly.createObject(walker);
        assembly.placeObject(objectId, 0, 0, 0);
        return new MovementRun(assembly.start(), objectId);
    }

    private static void advance(
            SimulationRuntime runtime,
            int ticks) {

        for (int tick = 0; tick < ticks; tick++) {
            runtime.stepper().advance();
        }
    }

    private record MovementRun(
            SimulationRuntime runtime,
            ObjectId objectId) {
    }
}
