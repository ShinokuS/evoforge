package io.github.evoforge.simulation.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.agents.CapabilityId;
import io.github.evoforge.simulation.agents.decision.AgentDecisionTrace;
import io.github.evoforge.simulation.agents.need.NeedId;
import io.github.evoforge.simulation.world.material.MaterialDefinitionId;
import io.github.evoforge.simulation.world.liquid.water.WaterSystem;
import io.github.evoforge.simulation.world.interaction.InteractionReachProfiles;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;
import org.junit.jupiter.api.Test;

final class CowNeedCompetitionIntegrationTest {
    private static final NeedId HUNGER = NeedId.of("test:hunger");
    private static final NeedId THIRST = NeedId.of("test:thirst");
    private static final CapabilityId GRAZE = CapabilityId.of("test:graze");

    @Test
    void higherThirstOutranksHungerOnOneCommonUtilityScale() {
        Fixture fixture = new Fixture(40, 90);
        AgentDecisionTrace decision = fixture.firstDecision();

        assertTrue(decision.candidates().size() >= 2);
        assertTrue(decision.candidates().stream().anyMatch(candidate ->
                candidate.providerId().equals("needs:satisfaction")));
        assertTrue(decision.candidates().stream().anyMatch(candidate ->
                candidate.providerId().equals("needs:liquid_drink")));
        assertEquals("needs:liquid_drink", decision.selected().providerId());
        assertEquals(THIRST.value(), decision.selected().motivation());
    }

    @Test
    void higherHungerCanOutrankThirstWithoutProviderSpecificFinalScores() {
        Fixture fixture = new Fixture(90, 40);
        AgentDecisionTrace decision = fixture.firstDecision();

        assertTrue(decision.candidates().size() >= 2);
        assertEquals("needs:satisfaction", decision.selected().providerId());
        assertEquals(HUNGER.value(), decision.selected().motivation());
        assertEquals("object:" + fixture.grass.asLong(), decision.selected().targetKey());
    }

    private static final class Fixture {
        private final SimulationRuntime runtime;
        private final ObjectId cow;
        private final ObjectId grass;

        private Fixture(long hunger, long thirst) {
            SimulationAssembly assembly = SimulationAssembly.create()
                    .worldBounds(-2, 2, -2, 2, -1, 3);
            MaterialDefinitionId ground = assembly.landscapeDefinition("test:competition_ground");
            assembly.surfaceRetention(ground, 100_000);
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) assembly.placeTerrain(x, y, 0, ground);
            }

            ObjectDefinitionId cowDefinition = assembly.objectDefinition("test:competition_cow");
            ObjectDefinitionId grassDefinition = assembly.objectDefinition("test:competition_grass");
            assembly.movementRate(cowDefinition, 1_000);
            assembly.exclusiveOccupancy(cowDefinition);
            assembly.agent(cowDefinition, GRAZE);
            assembly.vision(cowDefinition, 4, 360);
            assembly.need(cowDefinition, HUNGER, 100, hunger);
            assembly.need(cowDefinition, THIRST, 100, thirst);
            assembly.needMotivation(cowDefinition, HUNGER, 10);
            assembly.needMotivation(cowDefinition, THIRST, 10);
            assembly.satisfiesNeed(grassDefinition, HUNGER, 60, GRAZE);
            assembly.physicalCellVolumeMilliliters(1_000_000L);
            assembly.drinksLiquid(
                    cowDefinition,
                    THIRST,
                    WaterSystem.TYPE,
                    10_000L,
                    50L,
                    2L,
                    InteractionReachProfiles.cardinalSameOrOneBelow());
            assembly.initialWater(1, 0, 1, 20_000);

            grass = assembly.createObject(grassDefinition);
            cow = assembly.createObject(cowDefinition);
            assembly.placeObject(grass, 0, 0, 1);
            assembly.placeObject(cow, 0, 0, 1);
            assembly.initialFacing(cow, 1, 0);
            runtime = assembly.start();
        }

        private AgentDecisionTrace firstDecision() {
            runtime.stepper().advance();
            return runtime.view().agents().lastDecision(cow);
        }
    }
}
