package io.github.evoforge.simulation.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.agents.CapabilityId;
import io.github.evoforge.simulation.agents.decision.AgentIntentPhase;
import io.github.evoforge.simulation.agents.decision.AgentIntentTrace;
import io.github.evoforge.simulation.agents.need.NeedId;
import io.github.evoforge.simulation.world.material.MaterialDefinitionId;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;
import org.junit.jupiter.api.Test;

final class TimedNeedSatisfactionIntegrationTest {
    private static final NeedId HUNGER = NeedId.of("core:hunger");
    private static final CapabilityId GRAZE = CapabilityId.of("core:graze");

    @Test
    void timedUseDoesNotMutateNeedOrStockBeforeAuthoritativeCompletionTick() {
        SimulationAssembly assembly = SimulationAssembly.create();
        MaterialDefinitionId ground = assembly.landscapeDefinition("test:timed_use_ground");
        ObjectDefinitionId cow = assembly.objectDefinition("test:timed_use_cow");
        ObjectDefinitionId grass = assembly.objectDefinition("test:timed_use_grass");

        assembly.movementRate(cow, 10_000);
        assembly.exclusiveOccupancy(cow);
        assembly.agent(cow, GRAZE);
        assembly.vision(cow, 4, 120);
        assembly.need(cow, HUNGER, 100, 60);
        assembly.needMotivation(cow, HUNGER, 60);
        assembly.consumableStock(grass, 5, 5);
        assembly.satisfiesNeed(grass, HUNGER, 30, 2, 4, GRAZE);
        assembly.placeTerrain(0, 0, -1, ground);

        ObjectId cowId = assembly.createObject(cow);
        ObjectId grassId = assembly.createObject(grass);
        assembly.placeObject(cowId, 0, 0, 0);
        assembly.placeObject(grassId, 0, 0, 0);
        assembly.initialFacing(cowId, 1, 0);
        SimulationRuntime runtime = assembly.start();

        runtime.stepper().advance();
        runtime.stepper().advance();

        AgentIntentTrace use = runtime.view().agents().currentIntent(cowId);
        assertNotNull(use);
        assertEquals(AgentIntentPhase.USING_OPPORTUNITY, use.phase());
        assertEquals(2, use.startedTick());
        assertEquals(6, use.expectedCompletionTick());
        assertEquals(60, runtime.view().needs().level(cowId, HUNGER));
        assertEquals(5, runtime.view().consumableStocks().quantity(grassId));

        while (runtime.time().tick() < use.expectedCompletionTick() - 1) {
            runtime.stepper().advance();
            assertEquals(60, runtime.view().needs().level(cowId, HUNGER));
            assertEquals(5, runtime.view().consumableStocks().quantity(grassId));
        }

        runtime.stepper().advance();
        assertEquals(30, runtime.view().needs().level(cowId, HUNGER));
        assertEquals(3, runtime.view().consumableStocks().quantity(grassId));
        assertTrue(runtime.view().agents().currentIntent(cowId) == null
                || runtime.view().agents().currentIntent(cowId).phase() != AgentIntentPhase.USING_OPPORTUNITY);
    }

    @Test
    void motivationThresholdPreventsNibblingAtTinyDeficits() {
        SimulationAssembly assembly = SimulationAssembly.create();
        MaterialDefinitionId ground = assembly.landscapeDefinition("test:motivation_ground");
        ObjectDefinitionId cow = assembly.objectDefinition("test:motivation_cow");
        ObjectDefinitionId grass = assembly.objectDefinition("test:motivation_grass");

        assembly.movementRate(cow, 10_000);
        assembly.exclusiveOccupancy(cow);
        assembly.agent(cow, GRAZE);
        assembly.vision(cow, 4, 120);
        assembly.need(cow, HUNGER, 100, 39);
        assembly.needMotivation(cow, HUNGER, 40);
        assembly.needProgression(cow, HUNGER, 1, 5);
        assembly.consumableStock(grass, 5, 5);
        assembly.satisfiesNeed(grass, HUNGER, 20, 1, 3, GRAZE);
        assembly.placeTerrain(0, 0, -1, ground);

        ObjectId cowId = assembly.createObject(cow);
        ObjectId grassId = assembly.createObject(grass);
        assembly.placeObject(cowId, 0, 0, 0);
        assembly.placeObject(grassId, 0, 0, 0);
        assembly.initialFacing(cowId, 1, 0);
        SimulationRuntime runtime = assembly.start();

        for (int tick = 0; tick < 5; tick++) {
            runtime.stepper().advance();
            if (runtime.time().tick() < 5) {
                assertNull(runtime.view().agents().currentIntent(cowId));
                assertEquals(5, runtime.view().consumableStocks().quantity(grassId));
            }
        }
        assertEquals(40, runtime.view().needs().level(cowId, HUNGER));

        AgentIntentTrace use = null;
        for (int tick = 0; tick < 12 && use == null; tick++) {
            runtime.stepper().advance();
            AgentIntentTrace intent = runtime.view().agents().currentIntent(cowId);
            if (intent != null && intent.phase() == AgentIntentPhase.USING_OPPORTUNITY) use = intent;
        }
        assertNotNull(use);
    }

    @Test
    void stillDesiredUseContinuesWithoutDroppingUsingIntentBetweenBites() {
        SimulationAssembly assembly = SimulationAssembly.create();
        MaterialDefinitionId ground = assembly.landscapeDefinition("test:continued_use_ground");
        ObjectDefinitionId cow = assembly.objectDefinition("test:continued_use_cow");
        ObjectDefinitionId grass = assembly.objectDefinition("test:continued_use_grass");

        assembly.movementRate(cow, 10_000);
        assembly.exclusiveOccupancy(cow);
        assembly.agent(cow, GRAZE);
        assembly.vision(cow, 4, 120);
        assembly.need(cow, HUNGER, 100, 90);
        assembly.needMotivation(cow, HUNGER, 40);
        assembly.consumableStock(grass, 10, 10);
        assembly.satisfiesNeed(grass, HUNGER, 20, 1, 2, GRAZE);
        assembly.placeTerrain(0, 0, -1, ground);

        ObjectId cowId = assembly.createObject(cow);
        ObjectId grassId = assembly.createObject(grass);
        assembly.placeObject(cowId, 0, 0, 0);
        assembly.placeObject(grassId, 0, 0, 0);
        assembly.initialFacing(cowId, 1, 0);
        SimulationRuntime runtime = assembly.start();

        runtime.stepper().advance();
        runtime.stepper().advance();
        AgentIntentTrace first = runtime.view().agents().currentIntent(cowId);
        assertNotNull(first);
        assertEquals(AgentIntentPhase.USING_OPPORTUNITY, first.phase());
        long firstCompletion = first.expectedCompletionTick();

        while (runtime.time().tick() < firstCompletion) runtime.stepper().advance();

        AgentIntentTrace continued = runtime.view().agents().currentIntent(cowId);
        assertNotNull(continued);
        assertEquals(AgentIntentPhase.USING_OPPORTUNITY, continued.phase());
        assertEquals(firstCompletion, continued.startedTick());
        assertTrue(continued.expectedCompletionTick() > continued.startedTick());
        assertEquals(70, runtime.view().needs().level(cowId, HUNGER));
        assertEquals(9, runtime.view().consumableStocks().quantity(grassId));
    }
}
