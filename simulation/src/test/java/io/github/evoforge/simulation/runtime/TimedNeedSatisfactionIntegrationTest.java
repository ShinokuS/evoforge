package io.github.evoforge.simulation.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.agent.CapabilityId;
import io.github.evoforge.simulation.world.agent.decision.AgentIntentPhase;
import io.github.evoforge.simulation.world.agent.decision.AgentIntentTrace;
import io.github.evoforge.simulation.world.agent.need.NeedId;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;
import org.junit.jupiter.api.Test;

final class TimedNeedSatisfactionIntegrationTest {
    private static final NeedId HUNGER = NeedId.of("core:hunger");
    private static final CapabilityId GRAZE = CapabilityId.of("core:graze");

    @Test
    void timedUseDoesNotMutateNeedOrStockBeforeAuthoritativeCompletionTick() {
        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId ground = assembly.landscapeDefinition("test:timed_use_ground");
        ObjectDefinitionId cow = assembly.objectDefinition("test:timed_use_cow");
        ObjectDefinitionId grass = assembly.objectDefinition("test:timed_use_grass");

        assembly.movementRate(cow, 10_000);
        assembly.exclusiveOccupancy(cow);
        assembly.agent(cow, GRAZE);
        assembly.vision(cow, 4, 120);
        assembly.need(cow, HUNGER, 100, 60);
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
}
