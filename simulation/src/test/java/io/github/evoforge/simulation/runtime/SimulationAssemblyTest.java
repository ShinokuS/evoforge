package io.github.evoforge.simulation.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.control.movement.MoveStepCommand;
import io.github.evoforge.simulation.control.terrain.ReplaceTerrainCommand;
import io.github.evoforge.simulation.control.terrain.ReplaceTerrainResult;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.mechanics.occupancy.OccupancyState;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;
import org.junit.jupiter.api.Test;

final class SimulationAssemblyTest {

    @Test
    void closesSetupMutationAfterStartAndExposesReadOnlyRuntimeView() {
        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId ground = assembly.landscapeDefinition("test:ground");
        ObjectDefinitionId walker = assembly.objectDefinition("test:walker");
        assembly.movementRate(walker, 100);
        ObjectId objectId = assembly.createObject(walker);
        assembly.placeObject(objectId, 2, 3, 0);
        assembly.placeTerrain(2, 3, -1, ground);
        SimulationRuntime runtime = assembly.start();
        assertEquals(1, runtime.view().cells().objectCount(2, 3, 0));
        assertEquals(objectId, runtime.view().cells().objectAt(2, 3, 0, 0));
        assertEquals(ground, runtime.view().terrain().find(2, 3, -1));
        assertThrows(IllegalStateException.class, () -> assembly.placeTerrain(3, 3, -1, ground));
        assertThrows(IllegalStateException.class, () -> assembly.createObject(walker));
        assertThrows(IllegalStateException.class, assembly::start);
    }

    @Test
    void runtimeTerrainReplacementUsesControlBoundary() {
        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId ground = assembly.landscapeDefinition("test:replace_ground", 1000);
        LandscapeDefinitionId slow = assembly.landscapeDefinition("test:replace_slow", 6000);
        assembly.placeTerrain(1, 0, -1, ground);
        SimulationRuntime runtime = assembly.start();
        assertEquals(ReplaceTerrainResult.REPLACED,
                runtime.submit(new ReplaceTerrainCommand(1, 0, -1, slow)));
        assertEquals(slow, runtime.view().terrain().find(1, 0, -1));
        assertEquals(ReplaceTerrainResult.TERRAIN_ABSENT,
                runtime.submit(new ReplaceTerrainCommand(2, 0, -1, slow)));
    }

    @Test
    void movementUsesProductionGraphAndKeepsCellIndexSynchronized() {
        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId ground = assembly.landscapeDefinition("test:ground");
        ObjectDefinitionId walker = assembly.objectDefinition("test:walker");
        assembly.movementRate(walker, 1000);
        ObjectId objectId = assembly.createObject(walker);
        assembly.placeObject(objectId, 0, 0, 0);
        assembly.placeTerrain(0, 0, -1, ground);
        assembly.placeTerrain(1, 0, -1, ground);
        SimulationRuntime runtime = assembly.start();
        assertTrue(runtime.submit(new MoveStepCommand(objectId, 1, 0, 0)).accepted());
        assertEquals(1, runtime.view().cells().objectCount(0, 0, 0));
        assertEquals(0, runtime.view().cells().objectCount(1, 0, 0));
        runtime.stepper().advance();
        assertEquals(0, runtime.view().cells().objectCount(0, 0, 0));
        assertEquals(1, runtime.view().cells().objectCount(1, 0, 0));
        assertEquals(objectId, runtime.view().cells().objectAt(1, 0, 0, 0));
    }

    @Test
    void occupancySemanticsCannotChangeAfterDefinitionInstancesArePlaced() {
        SimulationAssembly assembly = SimulationAssembly.create();
        ObjectDefinitionId definition = assembly.objectDefinition("test:object");
        ObjectId objectId = assembly.createObject(definition);
        assembly.placeObject(objectId, 0, 0, 0);
        assertThrows(IllegalStateException.class, () -> assembly.exclusiveOccupancy(definition));
    }

    @Test
    void occupancyMayBeConfiguredAfterCreationButBeforePlacement() {
        SimulationAssembly assembly = SimulationAssembly.create();
        ObjectDefinitionId definition = assembly.objectDefinition("test:object");
        ObjectId objectId = assembly.createObject(definition);
        assembly.exclusiveOccupancy(definition);
        assembly.placeObject(objectId, 0, 0, 0);
        SimulationRuntime runtime = assembly.start();
        assertEquals(OccupancyState.OCCUPIED, runtime.view().occupancy().state(0, 0, 0));
    }
}
