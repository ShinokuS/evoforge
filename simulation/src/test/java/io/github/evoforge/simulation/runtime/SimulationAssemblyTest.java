package io.github.evoforge.simulation.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.evoforge.simulation.control.movement.MoveStepCommand;
import io.github.evoforge.simulation.control.movement.MoveStepResult;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
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

        assertEquals(
                1,
                runtime.view().cells().objectCount(
                        2,
                        3,
                        0));
        assertEquals(
                objectId,
                runtime.view().cells().objectAt(
                        2,
                        3,
                        0,
                        0));
        assertEquals(
                ground,
                runtime.view().terrain().find(
                        2,
                        3,
                        -1));

        assertThrows(
                IllegalStateException.class,
                () -> assembly.placeTerrain(
                        3,
                        3,
                        -1,
                        ground));
        assertThrows(
                IllegalStateException.class,
                () -> assembly.createObject(walker));
        assertThrows(
                IllegalStateException.class,
                assembly::start);
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

        assertEquals(
                MoveStepResult.STARTED,
                runtime.submit(
                        new MoveStepCommand(
                                objectId,
                                1,
                                0,
                                0)));

        assertEquals(
                1,
                runtime.view().cells().objectCount(
                        0,
                        0,
                        0));
        assertEquals(
                0,
                runtime.view().cells().objectCount(
                        1,
                        0,
                        0));

        runtime.stepper().advance();

        assertEquals(
                0,
                runtime.view().cells().objectCount(
                        0,
                        0,
                        0));
        assertEquals(
                1,
                runtime.view().cells().objectCount(
                        1,
                        0,
                        0));
        assertEquals(
                objectId,
                runtime.view().cells().objectAt(
                        1,
                        0,
                        0,
                        0));
    }
}
