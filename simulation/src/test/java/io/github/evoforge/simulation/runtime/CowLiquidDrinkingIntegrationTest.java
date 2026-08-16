package io.github.evoforge.simulation.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.agent.need.NeedId;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.landscape.water.WaterSystem;
import io.github.evoforge.simulation.world.mechanics.interaction.InteractionReachProfiles;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;
import org.junit.jupiter.api.Test;

final class CowLiquidDrinkingIntegrationTest {
    private static final NeedId THIRST = NeedId.of("test:thirst");

    @Test
    void cowOnStandingLevelCanDrinkCardinalWaterOneCellBelow() {
        Fixture fixture = new Fixture(false);
        SimulationRuntime runtime = fixture.start();
        int initialWater = runtime.view().water().amount(1, 0, 0);

        advanceUntilSatisfied(runtime, fixture.cow, 40);

        assertEquals(initialWater - 10_000, runtime.view().water().amount(1, 0, 0));
        assertTrue(runtime.view().needs().level(fixture.cow, THIRST) < 80L);
        assertEquals(0, runtime.view().transforms().x(fixture.cow));
        assertEquals(0, runtime.view().transforms().y(fixture.cow));
        assertEquals(1, runtime.view().transforms().z(fixture.cow));
    }

    @Test
    void cowCanDrinkCardinalRainPuddleAtItsOwnStandingLevel() {
        Fixture fixture = new Fixture(true);
        SimulationRuntime runtime = fixture.start();
        int initialWater = runtime.view().water().amount(1, 0, 1);

        advanceUntilSatisfied(runtime, fixture.cow, 40);

        assertEquals(initialWater - 10_000, runtime.view().water().amount(1, 0, 1));
        assertTrue(runtime.view().needs().level(fixture.cow, THIRST) < 80L);
        assertEquals(0, runtime.view().transforms().x(fixture.cow));
        assertEquals(1, runtime.view().transforms().z(fixture.cow));
    }

    private static void advanceUntilSatisfied(
            SimulationRuntime runtime,
            ObjectId cow,
            int budget) {
        long initial = runtime.view().needs().level(cow, THIRST);
        for (int tick = 0; tick < budget
                && runtime.view().needs().level(cow, THIRST) == initial; tick++) {
            runtime.stepper().advance();
        }
        assertTrue(runtime.view().needs().level(cow, THIRST) < initial);
    }

    private static final class Fixture {
        private final SimulationAssembly assembly = SimulationAssembly.create();
        private final ObjectId cow;

        private Fixture(boolean sameLevelPuddle) {
            assembly.worldBounds(-3, 3, -2, 2, -1, 3);
            LandscapeDefinitionId ground = assembly.landscapeDefinition("test:ground");
            assembly.surfaceRetention(ground, 100_000);

            ObjectDefinitionId cowDefinition = assembly.objectDefinition("test:cow");
            assembly.movementRate(cowDefinition, 1_000);
            assembly.exclusiveOccupancy(cowDefinition);
            assembly.agent(cowDefinition);
            assembly.vision(cowDefinition, 5, 360);
            assembly.need(cowDefinition, THIRST, 100, 80);
            assembly.needMotivation(cowDefinition, THIRST, 10);
            assembly.knowsNeedSolution(cowDefinition, THIRST);
            assembly.physicalCellVolumeMilliliters(1_000_000L);
            assembly.drinksLiquid(
                    cowDefinition,
                    THIRST,
                    WaterSystem.TYPE,
                    10_000L,
                    50L,
                    2L,
                    InteractionReachProfiles.cardinalSameOrOneBelow());

            // Cow walks to (0,0,1). The target column is either a retained puddle
            // over terrain z=0 or lower free Water over its own floor at z=-1.
            assembly.placeTerrain(-1, 0, 0, ground);
            assembly.placeTerrain(0, 0, 0, ground);
            if (sameLevelPuddle) {
                assembly.placeTerrain(1, 0, 0, ground);
                assembly.initialWater(1, 0, 1, 20_000);
            } else {
                assembly.placeTerrain(1, 0, -1, ground);
                assembly.initialWater(1, 0, 0, 20_000);
            }

            cow = assembly.createObject(cowDefinition);
            assembly.placeObject(cow, -1, 0, 1);
            assembly.initialFacing(cow, 1, 0);
        }

        private SimulationRuntime start() {
            return assembly.start();
        }
    }
}
