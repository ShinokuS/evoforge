package io.github.evoforge.simulation.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.control.movement.MoveStepCommand;
import io.github.evoforge.simulation.control.movement.MoveToCommand;
import io.github.evoforge.simulation.control.movement.MoveToResult;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.mechanics.geometry.RampShape;
import io.github.evoforge.simulation.world.mechanics.movement.MoveToCompletion;
import io.github.evoforge.simulation.world.mechanics.occupancy.OccupancyState;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;
import org.junit.jupiter.api.Test;

final class MoveToIntegrationTest {

    @Test
    void moveToChainsEdgesWithoutAddingOrchestrationTicks() {
        MovementRun run = linearMovementRun(3, 300, true);
        MoveToResult started = run.runtime().submit(new MoveToCommand(run.objectId(), 3, 0, 0));
        assertTrue(started.accepted());
        assertNotNull(started.actionId());
        assertTrue(run.runtime().view().moveTo().isActive(run.objectId()));
        assertEquals(OccupancyState.RESERVED, run.runtime().view().occupancy().state(1, 0, 0));
        advance(run.runtime(), 3);
        assertEquals(1, run.runtime().view().transforms().x(run.objectId()));
        assertEquals(OccupancyState.RESERVED, run.runtime().view().occupancy().state(2, 0, 0));
        advance(run.runtime(), 3);
        assertEquals(2, run.runtime().view().transforms().x(run.objectId()));
        assertEquals(OccupancyState.RESERVED, run.runtime().view().occupancy().state(3, 0, 0));
        advance(run.runtime(), 3);
        assertEquals(2, run.runtime().view().transforms().x(run.objectId()));
        advance(run.runtime(), 1);
        assertEquals(3, run.runtime().view().transforms().x(run.objectId()));
        assertEquals(10, run.runtime().time().tick());
        assertFalse(run.runtime().view().moveTo().isActive(run.objectId()));
        MoveToCompletion completion = run.runtime().view().moveTo().lastCompletion(run.objectId());
        assertNotNull(completion);
        assertTrue(completion.reachedGoal());
        assertEquals("movement:goal_reached", completion.code().value());
    }

    @Test
    void moveToClaimRejectsCompetingStandaloneMovement() {
        MovementRun run = linearMovementRun(2, 100, false);
        assertTrue(run.runtime().submit(new MoveToCommand(run.objectId(), 2, 0, 0)).accepted());
        var competing = run.runtime().submit(new MoveStepCommand(run.objectId(), 1, 0, 0));
        assertFalse(competing.accepted());
        assertEquals("movement:already_moving", competing.code().value());
    }

    @Test
    void moveToNoPathIsAcceptedIntentWithImmediateTerminalOutcome() {
        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId ground = assembly.landscapeDefinition("test:isolated_ground");
        ObjectDefinitionId walker = assembly.objectDefinition("test:isolated_walker");
        assembly.movementRate(walker, 1000);
        ObjectId objectId = assembly.createObject(walker);
        assembly.placeObject(objectId, 0, 0, 0);
        assembly.placeTerrain(0, 0, -1, ground);
        SimulationRuntime runtime = assembly.start();
        MoveToResult result = runtime.submit(new MoveToCommand(objectId, 2, 0, 0));
        assertTrue(result.accepted());
        assertEquals(0, runtime.time().tick());
        assertFalse(runtime.view().moveTo().isActive(objectId));
        MoveToCompletion completion = runtime.view().moveTo().lastCompletion(objectId);
        assertNotNull(completion);
        assertFalse(completion.reachedGoal());
        assertEquals("movement:no_path", completion.code().value());
        assertEquals(0, runtime.view().transforms().x(objectId));
    }

    @Test
    void moveToSourceEqualGoalCompletesWithoutPhysicalStep() {
        MovementRun run = linearMovementRun(0, 1000, true);
        MoveToResult result = run.runtime().submit(new MoveToCommand(run.objectId(), 0, 0, 0));
        assertTrue(result.accepted());
        assertEquals(0, run.runtime().time().tick());
        assertFalse(run.runtime().view().moveTo().isActive(run.objectId()));
        MoveToCompletion completion = run.runtime().view().moveTo().lastCompletion(run.objectId());
        assertNotNull(completion);
        assertTrue(completion.reachedGoal());
        assertEquals("movement:goal_reached", completion.code().value());
    }

    @Test
    void laterOccupiedRouteEdgeStopsAtLastCommittedCellAndReleasesClaim() {
        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId ground = assembly.landscapeDefinition("test:occupied_route_ground");
        ObjectDefinitionId walker = assembly.objectDefinition("test:occupied_route_walker");
        assembly.movementRate(walker, 1000);
        assembly.exclusiveOccupancy(walker);
        for (int x = 0; x <= 2; x++) assembly.placeTerrain(x, 0, -1, ground);
        ObjectId mover = assembly.createObject(walker);
        ObjectId blocker = assembly.createObject(walker);
        assembly.placeObject(mover, 0, 0, 0);
        assembly.placeObject(blocker, 2, 0, 0);
        SimulationRuntime runtime = assembly.start();
        assertTrue(runtime.submit(new MoveToCommand(mover, 2, 0, 0)).accepted());
        runtime.stepper().advance();
        assertEquals(1, runtime.view().transforms().x(mover));
        assertFalse(runtime.view().moveTo().isActive(mover));
        MoveToCompletion completion = runtime.view().moveTo().lastCompletion(mover);
        assertNotNull(completion);
        assertFalse(completion.reachedGoal());
        assertEquals("movement:destination_occupied", completion.code().value());
        assertTrue(runtime.submit(new MoveStepCommand(mover, 0, 0, 0)).accepted());
    }

    @Test
    void moveToAndManualEdgesHaveEquivalentTimingCarryAndOccupancy() {
        MovementRun routed = linearMovementRun(4, 300, true);
        MovementRun manual = linearMovementRun(4, 300, true);
        assertTrue(routed.runtime().submit(
                new MoveToCommand(routed.objectId(), 3, 0, 0)).accepted());
        assertTrue(manual.runtime().submit(
                new MoveStepCommand(manual.objectId(), 1, 0, 0)).accepted());
        advance(manual.runtime(), 3);
        assertTrue(manual.runtime().submit(
                new MoveStepCommand(manual.objectId(), 2, 0, 0)).accepted());
        advance(manual.runtime(), 3);
        assertTrue(manual.runtime().submit(
                new MoveStepCommand(manual.objectId(), 3, 0, 0)).accepted());
        advance(manual.runtime(), 4);
        advance(routed.runtime(), 10);
        assertEquals(10, routed.runtime().time().tick());
        assertEquals(10, manual.runtime().time().tick());
        assertEquals(3, routed.runtime().view().transforms().x(routed.objectId()));
        assertEquals(3, manual.runtime().view().transforms().x(manual.objectId()));
        assertEquals(routed.runtime().view().occupancy().state(3, 0, 0),
                manual.runtime().view().occupancy().state(3, 0, 0));
        assertTrue(routed.runtime().submit(
                new MoveStepCommand(routed.objectId(), 4, 0, 0)).accepted());
        assertTrue(manual.runtime().submit(
                new MoveStepCommand(manual.objectId(), 4, 0, 0)).accepted());
        advance(routed.runtime(), 2);
        advance(manual.runtime(), 2);
        assertEquals(3, routed.runtime().view().transforms().x(routed.objectId()));
        assertEquals(3, manual.runtime().view().transforms().x(manual.objectId()));
        advance(routed.runtime(), 1);
        advance(manual.runtime(), 1);
        assertEquals(4, routed.runtime().view().transforms().x(routed.objectId()));
        assertEquals(4, manual.runtime().view().transforms().x(manual.objectId()));
        assertEquals(13, routed.runtime().time().tick());
        assertEquals(13, manual.runtime().time().tick());
    }

    @Test
    void moveToExecutesMultiZRouteThroughRamp() {
        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId ground = assembly.landscapeDefinition("test:ramp_ground");
        ObjectDefinitionId walker = assembly.objectDefinition("test:ramp_walker");
        assembly.movementRate(walker, 10_000);
        assembly.exclusiveOccupancy(walker);
        assembly.placeTerrain(0, 0, -1, ground);
        assembly.placeTerrain(0, 1, 0, ground);
        assembly.placeTerrain(0, 2, 0, ground);
        assembly.setShape(0, 1, 0, RampShape.POSITIVE_Y);
        ObjectId objectId = assembly.createObject(walker);
        assembly.placeObject(objectId, 0, 0, 0);
        SimulationRuntime runtime = assembly.start();
        assertTrue(runtime.submit(new MoveToCommand(objectId, 0, 2, 1)).accepted());
        runtime.stepper().advance();
        assertEquals(0, runtime.view().transforms().x(objectId));
        assertEquals(1, runtime.view().transforms().y(objectId));
        assertEquals(1, runtime.view().transforms().z(objectId));
        runtime.stepper().advance();
        assertEquals(0, runtime.view().transforms().x(objectId));
        assertEquals(2, runtime.view().transforms().y(objectId));
        assertEquals(1, runtime.view().transforms().z(objectId));
        MoveToCompletion completion = runtime.view().moveTo().lastCompletion(objectId);
        assertNotNull(completion);
        assertTrue(completion.reachedGoal());
    }

    private static MovementRun linearMovementRun(int maxX, long rate, boolean exclusive) {
        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId ground = assembly.landscapeDefinition("test:linear_ground");
        ObjectDefinitionId walker = assembly.objectDefinition("test:linear_walker");
        assembly.movementRate(walker, rate);
        if (exclusive) assembly.exclusiveOccupancy(walker);
        for (int x = 0; x <= maxX; x++) assembly.placeTerrain(x, 0, -1, ground);
        ObjectId objectId = assembly.createObject(walker);
        assembly.placeObject(objectId, 0, 0, 0);
        return new MovementRun(assembly.start(), objectId);
    }

    private static void advance(SimulationRuntime runtime, int ticks) {
        for (int tick = 0; tick < ticks; tick++) runtime.stepper().advance();
    }

    private record MovementRun(SimulationRuntime runtime, ObjectId objectId) {
    }
}
