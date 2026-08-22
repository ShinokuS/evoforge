package io.github.evoforge.simulation.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.object.stock.growth.GrowthStatus;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;
import org.junit.jupiter.api.Test;

final class GrowthIntegrationTest {

    @Test
    void growthRestoresFiniteStockOnItsOwnSchedule() {
        SimulationAssembly assembly = SimulationAssembly.create();
        ObjectDefinitionId plant = assembly.objectDefinition("test:growing_plant");
        assembly.consumableStock(plant, 20, 2);
        assembly.growth(plant, 3, 4);
        ObjectId plantId = assembly.createObject(plant);

        SimulationRuntime runtime = assembly.start();
        assertEquals(2, runtime.view().consumableStocks().quantity(plantId));
        assertTrue(runtime.view().growth().has(plantId));
        assertEquals(GrowthStatus.GROWING, runtime.view().growth().status(plantId));
        assertEquals(4, runtime.view().growth().nextEvaluationTick(plantId));
        assertNull(runtime.view().growth().lastEvaluation(plantId));

        advance(runtime, 3);
        assertEquals(2, runtime.view().consumableStocks().quantity(plantId));
        advance(runtime, 1);

        assertEquals(5, runtime.view().consumableStocks().quantity(plantId));
        var trace = runtime.view().growth().lastEvaluation(plantId);
        assertNotNull(trace);
        assertEquals(4, trace.tick());
        assertEquals(3, trace.resolvedAmount());
        assertEquals(3, trace.appliedAmount());
        assertEquals(5, trace.quantityAfter());
        assertEquals(8, runtime.view().growth().nextEvaluationTick(plantId));
    }

    @Test
    void growthStopsSchedulingAfterReachingStockCapacity() {
        SimulationAssembly assembly = SimulationAssembly.create();
        ObjectDefinitionId plant = assembly.objectDefinition("test:bounded_growth");
        assembly.consumableStock(plant, 10, 9);
        assembly.growth(plant, 5, 2);
        ObjectId plantId = assembly.createObject(plant);

        SimulationRuntime runtime = assembly.start();
        advance(runtime, 2);
        assertEquals(10, runtime.view().consumableStocks().quantity(plantId));
        assertEquals(1, runtime.view().growth().lastEvaluation(plantId).appliedAmount());
        assertEquals(5, runtime.view().growth().lastEvaluation(plantId).resolvedAmount());
        assertEquals(GrowthStatus.DORMANT_FULL, runtime.view().growth().status(plantId));
        assertEquals(-1, runtime.view().growth().nextEvaluationTick(plantId));

        advance(runtime, 20);
        assertEquals(10, runtime.view().consumableStocks().quantity(plantId));
        assertEquals(2, runtime.view().growth().lastEvaluation(plantId).tick());
        assertEquals(1, runtime.view().growth().lastEvaluation(plantId).appliedAmount());
        assertEquals(GrowthStatus.DORMANT_FULL, runtime.view().growth().status(plantId));
    }

    @Test
    void differentPlantDefinitionsShareOneGrowthMechanic() {
        SimulationAssembly assembly = SimulationAssembly.create();
        ObjectDefinitionId grass = assembly.objectDefinition("test:grass");
        ObjectDefinitionId clover = assembly.objectDefinition("test:clover");
        ObjectDefinitionId dandelion = assembly.objectDefinition("test:dandelion");
        assembly.consumableStock(grass, 20, 0);
        assembly.consumableStock(clover, 20, 0);
        assembly.consumableStock(dandelion, 20, 0);
        assembly.growth(grass, 1, 2);
        assembly.growth(clover, 3, 3);
        assembly.growth(dandelion, 5, 5);
        ObjectId grassId = assembly.createObject(grass);
        ObjectId cloverId = assembly.createObject(clover);
        ObjectId dandelionId = assembly.createObject(dandelion);

        SimulationRuntime runtime = assembly.start();
        advance(runtime, 6);

        assertEquals(3, runtime.view().consumableStocks().quantity(grassId));
        assertEquals(6, runtime.view().consumableStocks().quantity(cloverId));
        assertEquals(5, runtime.view().consumableStocks().quantity(dandelionId));
    }

    @Test
    void growthWithoutConsumableStockIsConfigurationFailure() {
        SimulationAssembly assembly = SimulationAssembly.create();
        ObjectDefinitionId invalid = assembly.objectDefinition("test:growth_without_stock");
        assembly.growth(invalid, 1, 2);
        assembly.createObject(invalid);

        IllegalStateException exception = assertThrows(IllegalStateException.class, assembly::start);
        assertTrue(exception.getMessage().contains("must own consumable stock"));
    }

    private static void advance(SimulationRuntime runtime, int ticks) {
        for (int tick = 0; tick < ticks; tick++) runtime.stepper().advance();
    }
}
