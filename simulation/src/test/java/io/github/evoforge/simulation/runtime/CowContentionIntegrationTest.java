package io.github.evoforge.simulation.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.agents.CapabilityId;
import io.github.evoforge.simulation.agents.need.NeedId;
import io.github.evoforge.simulation.world.material.MaterialDefinitionId;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;
import org.junit.jupiter.api.Test;

final class CowContentionIntegrationTest {
    private static final NeedId HUNGER = NeedId.of("test:hunger");
    private static final CapabilityId GRAZE = CapabilityId.of("test:graze");

    @Test
    void exclusiveCowsNeverOverlapWhileCompetingForOneFiniteSource() {
        SimulationAssembly assembly = SimulationAssembly.create()
                .worldBounds(-1, 5, -1, 1, -1, 2);
        MaterialDefinitionId ground = assembly.landscapeDefinition("test:contention_ground");
        for (int x = 0; x <= 4; x++) assembly.placeTerrain(x, 0, 0, ground);

        ObjectDefinitionId cowDefinition = assembly.objectDefinition("test:contention_cow");
        ObjectDefinitionId grassDefinition = assembly.objectDefinition("test:contention_grass");
        assembly.movementRate(cowDefinition, 1_000);
        assembly.exclusiveOccupancy(cowDefinition);
        assembly.agent(cowDefinition, GRAZE);
        assembly.vision(cowDefinition, 6, 360);
        assembly.need(cowDefinition, HUNGER, 100, 80);
        assembly.needMotivation(cowDefinition, HUNGER, 10);
        assembly.consumableStock(grassDefinition, 1, 1);
        assembly.satisfiesNeed(grassDefinition, HUNGER, 50, 1, 2, GRAZE);

        ObjectId leftCow = assembly.createObject(cowDefinition);
        ObjectId rightCow = assembly.createObject(cowDefinition);
        ObjectId grass = assembly.createObject(grassDefinition);
        assembly.placeObject(leftCow, 0, 0, 1);
        assembly.placeObject(rightCow, 4, 0, 1);
        assembly.placeObject(grass, 2, 0, 1);
        assembly.initialFacing(leftCow, 1, 0);
        assembly.initialFacing(rightCow, -1, 0);

        SimulationRuntime runtime = assembly.start();
        for (int tick = 0; tick < 40; tick++) {
            runtime.stepper().advance();
            assertTrue(
                    runtime.view().positions().x(leftCow) != runtime.view().positions().x(rightCow)
                            || runtime.view().positions().y(leftCow) != runtime.view().positions().y(rightCow)
                            || runtime.view().positions().z(leftCow) != runtime.view().positions().z(rightCow),
                    "exclusive Cows must never occupy one cell");
        }

        assertEquals(0, runtime.view().consumableStocks().quantity(grass));
        assertTrue(
                runtime.view().needs().level(leftCow, HUNGER) < 80
                        || runtime.view().needs().level(rightCow, HUNGER) < 80,
                "exactly one finite source must still be consumable under contention");
    }
}
