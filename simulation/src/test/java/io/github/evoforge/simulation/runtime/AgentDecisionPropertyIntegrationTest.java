package io.github.evoforge.simulation.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.agents.CapabilityId;
import io.github.evoforge.simulation.agents.decision.AgentDecisionTrace;
import io.github.evoforge.simulation.agents.decision.AgentIntentPhase;
import io.github.evoforge.simulation.agents.decision.AgentIntentTrace;
import io.github.evoforge.simulation.agents.need.NeedId;
import io.github.evoforge.simulation.world.material.MaterialDefinitionId;
import io.github.evoforge.simulation.world.liquid.water.WaterSystem;
import io.github.evoforge.simulation.world.interaction.InteractionReachProfiles;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/** Generated and metamorphic integration checks for generic autonomous decisions. */
final class AgentDecisionPropertyIntegrationTest {
    private static final NeedId HUNGER = NeedId.of("test:hunger");
    private static final NeedId THIRST = NeedId.of("test:thirst");
    private static final CapabilityId GRAZE = CapabilityId.of("test:graze");
    private static final long REPLAY_SEED = 0x4147454E54524C4CL;

    @Test
    void committedIntentSurvivesAChallengerBecomingSlightlyBetterDuringMovement() {
        SimulationAssembly assembly = SimulationAssembly.create()
                .worldBounds(-2, 2, -1, 1, -1, 2);
        MaterialDefinitionId ground = assembly.landscapeDefinition("test:commitment_ground");
        assembly.surfaceRetention(ground, 100_000);
        for (int x = -2; x <= 2; x++) assembly.placeTerrain(x, 0, 0, ground);

        ObjectDefinitionId cowDefinition = assembly.objectDefinition("test:commitment_cow");
        ObjectDefinitionId grassDefinition = assembly.objectDefinition("test:commitment_grass");
        assembly.movementRate(cowDefinition, 1);
        assembly.exclusiveOccupancy(cowDefinition);
        assembly.agent(cowDefinition, GRAZE);
        assembly.vision(cowDefinition, 4, 360);
        assembly.need(cowDefinition, HUNGER, 100, 95);
        assembly.need(cowDefinition, THIRST, 100, 10);
        assembly.needMotivation(cowDefinition, HUNGER, 1);
        assembly.needMotivation(cowDefinition, THIRST, 1);
        assembly.needProgression(cowDefinition, THIRST, 20, 1);
        assembly.consumableStock(grassDefinition, 5, 5);
        assembly.satisfiesNeed(grassDefinition, HUNGER, 100, 1, 2, GRAZE);
        assembly.physicalCellVolumeMilliliters(1_000_000L);
        assembly.drinksLiquid(
                cowDefinition,
                THIRST,
                WaterSystem.TYPE,
                10_000L,
                100L,
                2L,
                InteractionReachProfiles.cardinalSameOrOneBelow());

        ObjectId cow = assembly.createObject(cowDefinition);
        ObjectId grass = assembly.createObject(grassDefinition);
        assembly.placeObject(cow, 0, 0, 1);
        assembly.placeObject(grass, 1, 0, 1);
        assembly.initialWater(-1, 0, 1, 20_000);
        assembly.initialFacing(cow, 1, 0);

        SimulationRuntime runtime = assembly.start();
        runtime.stepper().advance();

        AgentDecisionTrace initialDecision = runtime.view().agents().lastDecision(cow);
        assertNotNull(initialDecision);
        assertNotNull(initialDecision.selected());
        assertEquals("needs:satisfaction", initialDecision.selected().providerId());
        String committedTarget = "object:" + grass.asLong();
        assertEquals(committedTarget, runtime.view().agents().currentTargetKey(cow));
        long decisionTick = initialDecision.tick();

        while (runtime.view().needs().level(cow, THIRST)
                <= runtime.view().needs().level(cow, HUNGER)) {
            runtime.stepper().advance();
        }

        assertTrue(runtime.view().needs().level(cow, THIRST) > runtime.view().needs().level(cow, HUNGER));
        assertEquals(committedTarget, runtime.view().agents().currentTargetKey(cow));
        AgentIntentTrace intent = runtime.view().agents().currentIntent(cow);
        assertNotNull(intent);
        assertEquals(AgentIntentPhase.MOVING_TO_OPPORTUNITY, intent.phase());
        assertEquals(
                decisionTick,
                runtime.view().agents().lastDecision(cow).tick(),
                "a committed MoveTo must not trigger unrelated Utility rescoring each poll");
    }

    @Test
    void identicalGeneratedWorldsProduceIdenticalAutonomousStateTraces() {
        SplittableRandom random = new SplittableRandom(REPLAY_SEED);
        for (int sample = 0; sample < 24; sample++) {
            long hunger = 10L + random.nextLong(91L);
            long thirst = 10L + random.nextLong(91L);
            int grassX = random.nextBoolean() ? 2 : -2;
            int grassY = random.nextBoolean() ? 1 : -1;
            int waterX = grassX > 0 ? -1 : 1;
            int waterY = random.nextBoolean() ? 1 : -1;

            ReplayFixture first = replayFixture(sample, hunger, thirst, grassX, grassY, waterX, waterY);
            ReplayFixture second = replayFixture(sample, hunger, thirst, grassX, grassY, waterX, waterY);

            for (int tick = 0; tick < 50; tick++) {
                first.runtime.stepper().advance();
                second.runtime.stepper().advance();
                assertReplayStateEquals(first, second, sample, tick);
            }
        }
    }

    private static ReplayFixture replayFixture(
            int sample,
            long hunger,
            long thirst,
            int grassX,
            int grassY,
            int waterX,
            int waterY) {
        SimulationAssembly assembly = SimulationAssembly.create()
                .worldBounds(-3, 3, -3, 3, -1, 2);
        MaterialDefinitionId ground = assembly.landscapeDefinition("test:replay_ground_" + sample);
        assembly.surfaceRetention(ground, 100_000);
        for (int x = -3; x <= 3; x++) {
            for (int y = -3; y <= 3; y++) assembly.placeTerrain(x, y, 0, ground);
        }

        ObjectDefinitionId cowDefinition = assembly.objectDefinition("test:replay_cow_" + sample);
        ObjectDefinitionId grassDefinition = assembly.objectDefinition("test:replay_grass_" + sample);
        assembly.movementRate(cowDefinition, 1_000);
        assembly.exclusiveOccupancy(cowDefinition);
        assembly.agent(cowDefinition, GRAZE);
        assembly.vision(cowDefinition, 5, 360);
        assembly.need(cowDefinition, HUNGER, 100, hunger);
        assembly.need(cowDefinition, THIRST, 100, thirst);
        assembly.needMotivation(cowDefinition, HUNGER, 10);
        assembly.needMotivation(cowDefinition, THIRST, 10);
        assembly.needProgression(cowDefinition, HUNGER, 2, 4);
        assembly.needProgression(cowDefinition, THIRST, 3, 3);
        assembly.consumableStock(grassDefinition, 4, 4);
        assembly.satisfiesNeed(grassDefinition, HUNGER, 45, 1, 2, GRAZE);
        assembly.physicalCellVolumeMilliliters(1_000_000L);
        assembly.drinksLiquid(
                cowDefinition,
                THIRST,
                WaterSystem.TYPE,
                10_000L,
                45L,
                2L,
                InteractionReachProfiles.cardinalSameOrOneBelow());

        ObjectId cow = assembly.createObject(cowDefinition);
        ObjectId grass = assembly.createObject(grassDefinition);
        assembly.placeObject(cow, 0, 0, 1);
        assembly.placeObject(grass, grassX, grassY, 1);
        assembly.initialWater(waterX, waterY, 1, 40_000);
        assembly.initialFacing(cow, 1, 0);
        return new ReplayFixture(assembly.start(), cow, grass);
    }

    private static void assertReplayStateEquals(
            ReplayFixture first,
            ReplayFixture second,
            int sample,
            int tick) {
        String context = "seed=" + REPLAY_SEED + ", sample=" + sample + ", tick=" + tick;
        assertEquals(first.runtime.view().positions().x(first.cow), second.runtime.view().positions().x(second.cow), context);
        assertEquals(first.runtime.view().positions().y(first.cow), second.runtime.view().positions().y(second.cow), context);
        assertEquals(first.runtime.view().positions().z(first.cow), second.runtime.view().positions().z(second.cow), context);
        assertEquals(first.runtime.view().needs().level(first.cow, HUNGER), second.runtime.view().needs().level(second.cow, HUNGER), context);
        assertEquals(first.runtime.view().needs().level(first.cow, THIRST), second.runtime.view().needs().level(second.cow, THIRST), context);
        assertEquals(first.runtime.view().consumableStocks().quantity(first.grass), second.runtime.view().consumableStocks().quantity(second.grass), context);
        assertEquals(first.runtime.view().agents().currentTargetKey(first.cow), second.runtime.view().agents().currentTargetKey(second.cow), context);
        assertEquals(first.runtime.view().agents().currentIntent(first.cow), second.runtime.view().agents().currentIntent(second.cow), context);
        assertEquals(first.runtime.view().agents().lastDecision(first.cow), second.runtime.view().agents().lastDecision(second.cow), context);
    }

    private record ReplayFixture(SimulationRuntime runtime, ObjectId cow, ObjectId grass) { }
}
