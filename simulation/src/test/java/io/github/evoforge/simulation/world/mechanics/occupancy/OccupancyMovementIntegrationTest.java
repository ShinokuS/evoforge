package io.github.evoforge.simulation.world.mechanics.occupancy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.evoforge.simulation.control.movement.MoveStepCommand;
import io.github.evoforge.simulation.control.movement.MoveStepResult;
import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;
import org.junit.jupiter.api.Test;

final class OccupancyMovementIntegrationTest {

    @Test
    void firstMovementClaimWinsAndLaterPhysicalOccupancyIsDistinct() {
        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId ground = assembly.landscapeDefinition("test:ground");
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

        assertEquals(
                MoveStepResult.STARTED,
                runtime.submit(new MoveStepCommand(first, 1, 0, 0)));
        assertEquals(
                OccupancyState.OCCUPIED,
                runtime.view().occupancy().state(0, 0, 0));
        assertEquals(
                OccupancyState.RESERVED,
                runtime.view().occupancy().state(1, 0, 0));

        assertEquals(
                MoveStepResult.DESTINATION_RESERVED,
                runtime.submit(new MoveStepCommand(second, 1, 0, 0)));

        advance(runtime, 4);

        assertEquals(1, runtime.view().transforms().x(first));
        assertEquals(
                OccupancyState.OCCUPIED,
                runtime.view().occupancy().state(1, 0, 0));
        assertEquals(
                MoveStepResult.DESTINATION_OCCUPIED,
                runtime.submit(new MoveStepCommand(second, 1, 0, 0)));
        assertEquals(2, runtime.view().transforms().x(second));
    }

    @Test
    void rejectedClaimLeavesMoverFreeAndDoesNotConsumeTimingCarry() {
        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId ground = assembly.landscapeDefinition("test:ground");
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

        assertEquals(
                MoveStepResult.STARTED,
                runtime.submit(new MoveStepCommand(first, 1, 0, 0)));
        assertEquals(
                MoveStepResult.DESTINATION_RESERVED,
                runtime.submit(new MoveStepCommand(second, 1, 0, 0)));

        assertEquals(
                MoveStepResult.STARTED,
                runtime.submit(new MoveStepCommand(second, 3, 0, 0)));

        runtime.stepper().advance();

        assertEquals(
                3,
                runtime.view().transforms().x(second));
        assertEquals(
                OccupancyState.OCCUPIED,
                runtime.view().occupancy().state(3, 0, 0));
    }

    @Test
    void nonExclusiveMoverMayShareCellWithExclusiveObject() {
        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId ground = assembly.landscapeDefinition("test:ground");
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
        assertEquals(
                MoveStepResult.STARTED,
                runtime.submit(new MoveStepCommand(mover, 1, 0, 0)));

        advance(runtime, 4);

        assertEquals(1, runtime.view().transforms().x(mover));
        assertEquals(2, runtime.view().cells().objectCount(1, 0, 0));
        assertEquals(
                OccupancyState.OCCUPIED,
                runtime.view().occupancy().state(1, 0, 0));
    }

    private static void advance(
            SimulationRuntime runtime,
            int ticks) {

        for (int tick = 0; tick < ticks; tick++) {
            runtime.stepper().advance();
        }
    }
}
