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
import java.util.ArrayList;
import java.util.List;
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
        fillGround(assembly, ground, -6, 6, -6, 6);

        ObjectId cowId = assembly.createObject(cow);
        assembly.placeObject(cowId, 0, 0, 0);
        assembly.initialFacing(cowId, 1, 0);

        SimulationRuntime runtime = assembly.start();
        for (int tick = 0; tick < 20 && chebyshevDistanceFromOrigin(runtime, cowId) < 2; tick++) {
            runtime.stepper().advance();
        }

        assertTrue(chebyshevDistanceFromOrigin(runtime, cowId) >= 2);
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
        fillGround(assembly, ground, -8, 8, -8, 8);

        ObjectId cowId = assembly.createObject(cow);
        List<ObjectId> grassIds = placeFoodRing(assembly, grass);
        assembly.placeObject(cowId, 0, 0, 0);
        assembly.initialFacing(cowId, 1, 0);

        SimulationRuntime runtime = assembly.start();
        runtime.stepper().advance();
        for (ObjectId grassId : grassIds) {
            assertFalse(runtime.view().vision().snapshot(cowId).isObjectVisible(grassId));
        }
        assertTrue(runtime.view().agents().lastDecision(cowId).candidates().isEmpty());
        assertNull(runtime.view().agents().lastDecision(cowId).selected());

        boolean moved = false;
        boolean sawConcreteTarget = false;
        for (int tick = 0; tick < 120 && runtime.view().needs().level(cowId, HUNGER) == 80; tick++) {
            runtime.stepper().advance();
            moved |= chebyshevDistanceFromOrigin(runtime, cowId) >= 2;
            sawConcreteTarget |= runtime.view().agents().currentTarget(cowId) != null;
        }

        assertTrue(moved);
        assertTrue(sawConcreteTarget);
        assertTrue(runtime.view().needs().level(cowId, HUNGER) < 80);
    }

    private static List<ObjectId> placeFoodRing(
            SimulationAssembly assembly,
            ObjectDefinitionId grass) {
        int[][] positions = {
                {6, 0}, {4, -4}, {0, -6}, {-4, -4},
                {-6, 0}, {-4, 4}, {0, 6}, {4, 4}
        };
        List<ObjectId> result = new ArrayList<>(positions.length);
        for (int[] position : positions) {
            ObjectId grassId = assembly.createObject(grass);
            assembly.placeObject(grassId, position[0], position[1], 0);
            result.add(grassId);
        }
        return result;
    }

    private static void fillGround(
            SimulationAssembly assembly,
            LandscapeDefinitionId ground,
            int minX,
            int maxX,
            int minY,
            int maxY) {
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                assembly.placeTerrain(x, y, -1, ground);
            }
        }
    }

    private static int chebyshevDistanceFromOrigin(SimulationRuntime runtime, ObjectId objectId) {
        return Math.max(
                Math.abs(runtime.view().transforms().x(objectId)),
                Math.abs(runtime.view().transforms().y(objectId)));
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
