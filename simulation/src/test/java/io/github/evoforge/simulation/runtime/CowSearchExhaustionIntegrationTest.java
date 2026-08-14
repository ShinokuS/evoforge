package io.github.evoforge.simulation.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.agent.CapabilityId;
import io.github.evoforge.simulation.world.agent.need.NeedId;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;
import org.junit.jupiter.api.Test;

final class CowSearchExhaustionIntegrationTest {

    private static final NeedId HUNGER = NeedId.of("core:hunger");
    private static final CapabilityId GRAZE = CapabilityId.of("core:graze");

    @Test
    void exhaustedLocalSweepExpandsSearchByMultiCellRelativeLegWithoutInventingTarget() {
        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId ground = assembly.landscapeDefinition("test:search_exploration_ground");
        ObjectDefinitionId cow = assembly.objectDefinition("test:search_exploration_cow");

        configureSearchingCow(assembly, cow, 4);
        for (int x = 0; x <= 6; x++) assembly.placeTerrain(x, 0, -1, ground);

        ObjectId cowId = assembly.createObject(cow);
        assembly.placeObject(cowId, 0, 0, 0);
        assembly.initialFacing(cowId, 1, 0);

        SimulationRuntime runtime = assembly.start();
        for (int tick = 0; tick < 12 && runtime.view().transforms().x(cowId) < 2; tick++) {
            runtime.stepper().advance();
        }

        assertTrue(runtime.view().transforms().x(cowId) >= 2);
        assertEquals(0, runtime.view().transforms().y(cowId));
        assertNull(runtime.view().agents().currentTarget(cowId));
        assertNotNull(runtime.view().searches().currentSearch(cowId));
        assertEquals("core:hunger", runtime.view().searches().currentSearch(cowId).motivation());
        assertEquals(80, runtime.view().needs().level(cowId, HUNGER));
    }

    @Test
    void foodOutsideInitialVisionIsFoundOnlyAfterPhysicalMultiCellExploration() {
        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId ground = assembly.landscapeDefinition("test:search_discovery_ground");
        ObjectDefinitionId cow = assembly.objectDefinition("test:search_discovery_cow");
        ObjectDefinitionId grass = assembly.objectDefinition("test:search_discovery_grass");

        configureSearchingCow(assembly, cow, 4);
        assembly.satisfiesNeed(grass, HUNGER, 30, GRAZE);
        for (int x = 0; x <= 10; x++) assembly.placeTerrain(x, 0, -1, ground);

        ObjectId cowId = assembly.createObject(cow);
        ObjectId grassId = assembly.createObject(grass);
        assembly.placeObject(cowId, 0, 0, 0);
        assembly.placeObject(grassId, 10, 0, 0);
        assembly.initialFacing(cowId, 1, 0);

        SimulationRuntime runtime = assembly.start();
        runtime.stepper().advance();
        assertFalse(runtime.view().vision().snapshot(cowId).isObjectVisible(grassId));
        assertTrue(runtime.view().agents().lastDecision(cowId).candidates().isEmpty());
        assertNull(runtime.view().agents().lastDecision(cowId).selected());

        for (int tick = 0; tick < 120 && runtime.view().needs().level(cowId, HUNGER) == 80; tick++) {
            runtime.stepper().advance();
        }

        assertEquals(50, runtime.view().needs().level(cowId, HUNGER));
        assertEquals(10, runtime.view().transforms().x(cowId));
        assertEquals(0, runtime.view().transforms().y(cowId));
    }

    private static void configureSearchingCow(
            SimulationAssembly assembly,
            ObjectDefinitionId cow,
            int visionRange) {
        assembly.movementRate(cow, 10_000);
        assembly.exclusiveOccupancy(cow);
        assembly.agent(cow, GRAZE);
        assembly.vision(cow, visionRange, 100);
        assembly.need(cow, HUNGER, 100, 80);
        assembly.knowsNeedSolution(cow, HUNGER);
    }
}
