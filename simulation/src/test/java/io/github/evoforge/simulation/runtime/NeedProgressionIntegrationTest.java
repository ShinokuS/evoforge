package io.github.evoforge.simulation.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.agents.CapabilityId;
import io.github.evoforge.simulation.agents.need.NeedId;
import io.github.evoforge.simulation.agents.need.progression.NeedProgressionTrace;
import io.github.evoforge.simulation.world.material.MaterialDefinitionId;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;
import org.junit.jupiter.api.Test;

final class NeedProgressionIntegrationTest {
    private static final NeedId HUNGER = NeedId.of("core:hunger");
    private static final NeedId THIRST = NeedId.of("core:thirst");
    private static final CapabilityId GRAZE = CapabilityId.of("core:graze");

    @Test
    void scheduledProgressionIncreasesDeficitAndClampsAtNeedMaximum() {
        SimulationAssembly assembly = SimulationAssembly.create();
        ObjectDefinitionId organism = assembly.objectDefinition("test:organism");
        assembly.need(organism, HUNGER, 10, 0);
        assembly.needProgression(organism, HUNGER, 3, 2);
        ObjectId id = assembly.createObject(organism);
        SimulationRuntime runtime = assembly.start();

        assertEquals(0, runtime.view().needs().level(id, HUNGER));
        runtime.stepper().advance();
        assertEquals(0, runtime.view().needs().level(id, HUNGER));
        runtime.stepper().advance();
        assertEquals(3, runtime.view().needs().level(id, HUNGER));
        advance(runtime, 6);
        assertEquals(10, runtime.view().needs().level(id, HUNGER));

        NeedProgressionTrace trace = runtime.view().needProgression().lastEvaluation(id, HUNGER);
        assertNotNull(trace);
        assertEquals(8, trace.tick());
        assertEquals(3, trace.resolvedAmount());
        assertEquals(1, trace.appliedAmount());
        assertEquals(10, trace.levelAfter());
        assertEquals(10, trace.maxLevel());
    }

    @Test
    void multipleOpenNeedIdsProgressIndependentlyWithoutNeedSpecificCode() {
        SimulationAssembly assembly = SimulationAssembly.create();
        ObjectDefinitionId organism = assembly.objectDefinition("test:multi_need_organism");
        NeedId custom = NeedId.of("test:custom_deficit");
        assembly.need(organism, custom, 50, 0);
        assembly.need(organism, THIRST, 50, 0);
        assembly.needProgression(organism, custom, 2, 3);
        assembly.needProgression(organism, THIRST, 5, 2);
        ObjectId id = assembly.createObject(organism);
        SimulationRuntime runtime = assembly.start();

        advance(runtime, 6);
        assertEquals(4, runtime.view().needs().level(id, custom));
        assertEquals(15, runtime.view().needs().level(id, THIRST));
        assertTrue(runtime.view().needProgression().has(id, custom));
        assertTrue(runtime.view().needProgression().has(id, THIRST));
    }

    @Test
    void progressionForUndeclaredNeedIsConfigurationFailure() {
        SimulationAssembly assembly = SimulationAssembly.create();
        ObjectDefinitionId organism = assembly.objectDefinition("test:invalid_progression");
        assembly.need(organism, HUNGER, 100, 0);
        assembly.needProgression(organism, THIRST, 1, 5);
        assembly.createObject(organism);
        assertThrows(IllegalStateException.class, assembly::start);
    }

    @Test
    void initiallySatisfiedCowBecomesHungryAndExistingAgentBehaviorConsumesFood() {
        SimulationAssembly assembly = SimulationAssembly.create();
        MaterialDefinitionId ground = assembly.landscapeDefinition("test:physiology_ground");
        ObjectDefinitionId cow = assembly.objectDefinition("test:physiology_cow");
        ObjectDefinitionId grass = assembly.objectDefinition("test:physiology_grass");

        assembly.movementRate(cow, 10_000);
        assembly.exclusiveOccupancy(cow);
        assembly.agent(cow, GRAZE);
        assembly.vision(cow, 4, 120);
        assembly.need(cow, HUNGER, 100, 0);
        assembly.needProgression(cow, HUNGER, 20, 2);
        assembly.consumableStock(grass, 5, 5);
        assembly.satisfiesNeed(grass, HUNGER, 60, 1, GRAZE);
        for (int x = 0; x <= 1; x++) assembly.placeTerrain(x, 0, -1, ground);

        ObjectId cowId = assembly.createObject(cow);
        ObjectId grassId = assembly.createObject(grass);
        assembly.placeObject(cowId, 0, 0, 0);
        assembly.placeObject(grassId, 1, 0, 0);
        assembly.initialFacing(cowId, 1, 0);
        SimulationRuntime runtime = assembly.start();

        assertEquals(0, runtime.view().needs().level(cowId, HUNGER));
        assertEquals(5, runtime.view().consumableStocks().quantity(grassId));
        advance(runtime, 15);

        assertTrue(runtime.view().consumableStocks().quantity(grassId) < 5);
        assertTrue(runtime.view().needs().level(cowId, HUNGER) < 100);
        assertNotNull(runtime.view().agents().lastDecision(cowId));
    }

    private static void advance(SimulationRuntime runtime, int ticks) {
        for (int tick = 0; tick < ticks; tick++) runtime.stepper().advance();
    }
}
