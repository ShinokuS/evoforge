package io.github.evoforge.simulation.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.github.evoforge.simulation.world.agent.CapabilityId;
import io.github.evoforge.simulation.world.agent.need.NeedId;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;
import org.junit.jupiter.api.Test;

final class FiniteConsumableStockIntegrationTest {
    private static final NeedId HUNGER = NeedId.of("core:hunger");
    private static final CapabilityId GRAZE = CapabilityId.of("core:graze");

    @Test
    void needSatisfactionConsumesAuthoritativeSourceStock() {
        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId ground = assembly.landscapeDefinition("test:finite_stock_ground");
        ObjectDefinitionId cow = assembly.objectDefinition("test:finite_stock_cow");
        ObjectDefinitionId grass = assembly.objectDefinition("test:finite_stock_grass");
        configureCow(assembly, cow);
        assembly.consumableStock(grass, 1, 1);
        assembly.satisfiesNeed(grass, HUNGER, 30, 1, GRAZE);
        for (int x = 0; x <= 2; x++) assembly.placeTerrain(x, 0, -1, ground);

        ObjectId cowId = assembly.createObject(cow);
        ObjectId grassId = assembly.createObject(grass);
        assembly.placeObject(cowId, 0, 0, 0);
        assembly.placeObject(grassId, 2, 0, 0);

        SimulationRuntime runtime = assembly.start();
        for (int tick = 0; tick < 8; tick++) runtime.stepper().advance();

        assertEquals(50, runtime.view().needs().level(cowId, HUNGER));
        assertEquals(0, runtime.view().consumableStocks().quantity(grassId));
        assertEquals(1, runtime.view().consumableStocks().capacity(grassId));
        assertNull(runtime.view().agents().currentTargetKey(cowId));
    }

    @Test
    void emptyFiniteSourceIsNotAdvertisedAsAnOpportunity() {
        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId ground = assembly.landscapeDefinition("test:empty_stock_ground");
        ObjectDefinitionId cow = assembly.objectDefinition("test:empty_stock_cow");
        ObjectDefinitionId grass = assembly.objectDefinition("test:empty_stock_grass");
        configureCow(assembly, cow);
        assembly.consumableStock(grass, 4, 0);
        assembly.satisfiesNeed(grass, HUNGER, 30, 1, GRAZE);
        for (int x = 0; x <= 1; x++) assembly.placeTerrain(x, 0, -1, ground);

        ObjectId cowId = assembly.createObject(cow);
        ObjectId grassId = assembly.createObject(grass);
        assembly.placeObject(cowId, 0, 0, 0);
        assembly.placeObject(grassId, 1, 0, 0);

        SimulationRuntime runtime = assembly.start();
        runtime.stepper().advance();

        assertEquals(0, runtime.view().agents().lastDecision(cowId).candidates().size());
        assertEquals(80, runtime.view().needs().level(cowId, HUNGER));
        assertEquals(0, runtime.view().consumableStocks().quantity(grassId));
    }

    private static void configureCow(SimulationAssembly assembly, ObjectDefinitionId cow) {
        assembly.movementRate(cow, 10_000);
        assembly.exclusiveOccupancy(cow);
        assembly.agent(cow, GRAZE);
        assembly.vision(cow, 6, 120);
        assembly.need(cow, HUNGER, 100, 80);
    }
}
