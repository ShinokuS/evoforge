package io.github.evoforge.simulation.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.agent.CapabilityId;
import io.github.evoforge.simulation.world.agent.need.NeedId;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;
import org.junit.jupiter.api.Test;

final class CowOpportunityRecoveryIntegrationTest {
    private static final NeedId HUNGER = NeedId.of("test:hunger");
    private static final CapabilityId GRAZE = CapabilityId.of("test:graze");

    @Test
    void failedOccupiedOpportunityDoesNotTrapAgentWhenAnotherCandidateExists() {
        SimulationAssembly assembly = SimulationAssembly.create()
                .worldBounds(-1, 4, -1, 4, -1, 2);
        LandscapeDefinitionId ground = assembly.landscapeDefinition("test:recovery_ground");
        for (int x = -1; x <= 4; x++) {
            for (int y = -1; y <= 4; y++) assembly.placeTerrain(x, y, 0, ground);
        }

        ObjectDefinitionId cowDefinition = assembly.objectDefinition("test:recovery_cow");
        ObjectDefinitionId grassDefinition = assembly.objectDefinition("test:recovery_grass");
        ObjectDefinitionId blockerDefinition = assembly.objectDefinition("test:recovery_blocker");
        assembly.movementRate(cowDefinition, 1_000);
        assembly.exclusiveOccupancy(cowDefinition);
        assembly.exclusiveOccupancy(blockerDefinition);
        assembly.agent(cowDefinition, GRAZE);
        assembly.vision(cowDefinition, 6, 360);
        assembly.need(cowDefinition, HUNGER, 100, 80);
        assembly.needMotivation(cowDefinition, HUNGER, 10);
        assembly.consumableStock(grassDefinition, 1, 1);
        assembly.satisfiesNeed(grassDefinition, HUNGER, 50, 1, 2, GRAZE);

        ObjectId cow = assembly.createObject(cowDefinition);
        ObjectId blockedGrass = assembly.createObject(grassDefinition);
        ObjectId alternativeGrass = assembly.createObject(grassDefinition);
        ObjectId blocker = assembly.createObject(blockerDefinition);
        assembly.placeObject(cow, 0, 0, 1);
        assembly.placeObject(blocker, 2, 0, 1);
        assembly.placeObject(blockedGrass, 2, 0, 1);
        assembly.placeObject(alternativeGrass, 0, 3, 1);
        assembly.initialFacing(cow, 1, 0);

        SimulationRuntime runtime = assembly.start();
        for (int tick = 0; tick < 60
                && runtime.view().consumableStocks().quantity(alternativeGrass) > 0; tick++) {
            runtime.stepper().advance();
            assertTrue(
                    runtime.view().transforms().x(cow) != runtime.view().transforms().x(blocker)
                            || runtime.view().transforms().y(cow) != runtime.view().transforms().y(blocker)
                            || runtime.view().transforms().z(cow) != runtime.view().transforms().z(blocker),
                    "exclusive objects must never overlap while recovery is attempted");
        }

        assertEquals(1, runtime.view().consumableStocks().quantity(blockedGrass));
        assertEquals(0, runtime.view().consumableStocks().quantity(alternativeGrass));
        assertTrue(runtime.view().needs().level(cow, HUNGER) < 80);
    }

    @Test
    void multipleBlockedHigherRankedOpportunitiesDoNotStarveReachableCandidate() {
        SimulationAssembly assembly = SimulationAssembly.create()
                .worldBounds(-3, 3, -3, 3, -1, 2);
        LandscapeDefinitionId ground = assembly.landscapeDefinition("test:multi_recovery_ground");
        for (int x = -3; x <= 3; x++) {
            for (int y = -3; y <= 3; y++) assembly.placeTerrain(x, y, 0, ground);
        }

        ObjectDefinitionId cowDefinition = assembly.objectDefinition("test:multi_recovery_cow");
        ObjectDefinitionId grassDefinition = assembly.objectDefinition("test:multi_recovery_grass");
        ObjectDefinitionId blockerDefinition = assembly.objectDefinition("test:multi_recovery_blocker");
        assembly.movementRate(cowDefinition, 1_000);
        assembly.exclusiveOccupancy(cowDefinition);
        assembly.exclusiveOccupancy(blockerDefinition);
        assembly.agent(cowDefinition, GRAZE);
        assembly.vision(cowDefinition, 6, 360);
        assembly.need(cowDefinition, HUNGER, 100, 80);
        assembly.needMotivation(cowDefinition, HUNGER, 10);
        assembly.consumableStock(grassDefinition, 1, 1);
        assembly.satisfiesNeed(grassDefinition, HUNGER, 50, 1, 2, GRAZE);

        ObjectId cow = assembly.createObject(cowDefinition);
        ObjectId blockedGrassEast = assembly.createObject(grassDefinition);
        ObjectId blockedGrassNorth = assembly.createObject(grassDefinition);
        ObjectId reachableGrass = assembly.createObject(grassDefinition);
        ObjectId eastBlocker = assembly.createObject(blockerDefinition);
        ObjectId northBlocker = assembly.createObject(blockerDefinition);

        assembly.placeObject(cow, 0, 0, 1);
        assembly.placeObject(eastBlocker, 1, 0, 1);
        assembly.placeObject(blockedGrassEast, 1, 0, 1);
        assembly.placeObject(northBlocker, 0, 1, 1);
        assembly.placeObject(blockedGrassNorth, 0, 1, 1);
        assembly.placeObject(reachableGrass, -2, 0, 1);
        assembly.initialFacing(cow, 1, 0);

        SimulationRuntime runtime = assembly.start();
        for (int tick = 0; tick < 80
                && runtime.view().consumableStocks().quantity(reachableGrass) > 0; tick++) {
            runtime.stepper().advance();
            assertNotAt(runtime, cow, eastBlocker);
            assertNotAt(runtime, cow, northBlocker);
        }

        assertEquals(1, runtime.view().consumableStocks().quantity(blockedGrassEast));
        assertEquals(1, runtime.view().consumableStocks().quantity(blockedGrassNorth));
        assertEquals(0, runtime.view().consumableStocks().quantity(reachableGrass));
        assertTrue(runtime.view().needs().level(cow, HUNGER) < 80,
                "multiple failed favorites must not form an A/B retry loop that starves a reachable fallback");
    }

    private static void assertNotAt(SimulationRuntime runtime, ObjectId first, ObjectId second) {
        assertTrue(
                runtime.view().transforms().x(first) != runtime.view().transforms().x(second)
                        || runtime.view().transforms().y(first) != runtime.view().transforms().y(second)
                        || runtime.view().transforms().z(first) != runtime.view().transforms().z(second),
                "exclusive objects must never overlap while recovery is attempted");
    }
}
