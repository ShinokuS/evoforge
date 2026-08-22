package io.github.evoforge.simulation.mechanics.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.mechanics.movement.command.MoveStepCommand;
import io.github.evoforge.simulation.mechanics.movement.command.MoveStepResult;
import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.world.material.MaterialDefinitionId;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;
import org.junit.jupiter.api.Test;
import io.github.evoforge.simulation.world.space.occupancy.OccupancyState;

final class OccupancyMovementIntegrationTest {

    @Test
    void firstMovementClaimWinsAndLaterPhysicalOccupancyIsDistinct() {
        SimulationAssembly assembly = SimulationAssembly.create();
        MaterialDefinitionId ground = assembly.landscapeDefinition("test:ground");
        ObjectDefinitionId walker = assembly.objectDefinition("test:walker");
        assembly.movementRate(walker, 1000);
        assembly.exclusiveOccupancy(walker);

        for (int x = 0; x <= 2; x++) {
            assembly.placeTerrain(x, 0, -1, ground);
        }

        ObjectId first = assembly.createObject(walker);
        ObjectId second = assembly.createObject(walker);
        assembly.placeObject(first, 0, 0, 0);
        assembly.placeObject(second, 2, 0, 0);

        SimulationRuntime runtime = assembly.start();

        assertAccepted(
                runtime.submit(new MoveStepCommand(first, 1, 0, 0)));
        assertEquals(
                OccupancyState.OCCUPIED,
                runtime.view().occupancy().state(0, 0, 0));
        assertEquals(
                OccupancyState.RESERVED,
                runtime.view().occupancy().state(1, 0, 0));

        assertRejected(
                runtime.submit(new MoveStepCommand(second, 1, 0, 0)),
                "movement:destination_reserved");

        advance(runtime, 4);

        assertEquals(1, runtime.view().positions().x(first));
        assertEquals(
                OccupancyState.OCCUPIED,
                runtime.view().occupancy().state(1, 0, 0));
        assertRejected(
                runtime.submit(new MoveStepCommand(second, 1, 0, 0)),
                "movement:destination_occupied");
        assertEquals(2, runtime.view().positions().x(second));
    }

    @Test
    void rejectedClaimLeavesMoverFreeAndDoesNotConsumeTimingCarry() {
        SimulationAssembly assembly = SimulationAssembly.create();
        MaterialDefinitionId ground = assembly.landscapeDefinition("test:ground");
        ObjectDefinitionId walker = assembly.objectDefinition("test:walker");
        assembly.movementRate(walker, 600);
        assembly.exclusiveOccupancy(walker);

        for (int x = 0; x <= 3; x++) {
            assembly.placeTerrain(x, 0, -1, ground);
        }

        ObjectId first = assembly.createObject(walker);
        ObjectId second = assembly.createObject(walker);
        assembly.placeObject(first, 0, 0, 0);
        assembly.placeObject(second, 2, 0, 0);

        SimulationRuntime runtime = assembly.start();

        assertAccepted(
                runtime.submit(new MoveStepCommand(first, 1, 0, 0)));
        assertRejected(
                runtime.submit(new MoveStepCommand(second, 1, 0, 0)),
                "movement:destination_reserved");

        assertAccepted(
                runtime.submit(new MoveStepCommand(second, 3, 0, 0)));

        runtime.stepper().advance();

        assertEquals(
                3,
                runtime.view().positions().x(second));
        assertEquals(
                OccupancyState.OCCUPIED,
                runtime.view().occupancy().state(3, 0, 0));
    }

    @Test
    void nonExclusiveMoverMayShareCellWithExclusiveObject() {
        SimulationAssembly assembly = SimulationAssembly.create();
        MaterialDefinitionId ground = assembly.landscapeDefinition("test:ground");
        ObjectDefinitionId cow = assembly.objectDefinition("test:cow");
        ObjectDefinitionId transparentMover =
                assembly.objectDefinition("test:transparent_mover");
        assembly.movementRate(cow, 1000);
        assembly.movementRate(transparentMover, 1000);
        assembly.exclusiveOccupancy(cow);

        assembly.placeTerrain(0, 0, -1, ground);
        assembly.placeTerrain(1, 0, -1, ground);

        ObjectId mover = assembly.createObject(transparentMover);
        ObjectId blocker = assembly.createObject(cow);
        assembly.placeObject(mover, 0, 0, 0);
        assembly.placeObject(blocker, 1, 0, 0);

        SimulationRuntime runtime = assembly.start();

        assertEquals(
                OccupancyState.OCCUPIED,
                runtime.view().occupancy().state(1, 0, 0));
        assertAccepted(
                runtime.submit(new MoveStepCommand(mover, 1, 0, 0)));

        advance(runtime, 4);

        assertEquals(1, runtime.view().positions().x(mover));
        assertEquals(2, runtime.view().cells().objectCount(1, 0, 0));
        assertEquals(
                OccupancyState.OCCUPIED,
                runtime.view().occupancy().state(1, 0, 0));
    }

    private static void assertAccepted(
            MoveStepResult result) {
        assertTrue(result.accepted(), result.code().toString());
    }

    private static void assertRejected(
            MoveStepResult result,
            String code) {
        assertFalse(result.accepted());
        assertEquals(code, result.code().value());
    }

    private static void advance(
            SimulationRuntime runtime,
            int ticks) {

        for (int tick = 0; tick < ticks; tick++) {
            runtime.stepper().advance();
        }
    }
}
