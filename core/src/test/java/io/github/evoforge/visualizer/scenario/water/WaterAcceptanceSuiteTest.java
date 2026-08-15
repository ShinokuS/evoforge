package io.github.evoforge.visualizer.scenario.water;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.world.landscape.water.WaterSystem;
import io.github.evoforge.visualizer.scenario.ScenarioSession;
import io.github.evoforge.visualizer.scenario.environment.RainHydrologyScenario;

final class WaterAcceptanceSuiteTest {

    @Test
    void zFlowContainsRealStackedPoolAndSettlesToFlatStableSurface() {
        ScenarioSession session = new WaterZStackScenario().create();
        SimulationRuntime runtime = session.runtime();

        assertEquals(2L, runtime.time().tick());
        assertTrue(sumWater(runtime, -6, -5, -1, 0, 0) > 0L);
        assertTrue(
                sumWater(runtime, -6, -5, -1, 0, 1) > 0L,
                "deep pool must retain a real second Water Z layer");
        assertTrue(
                runtime.view().water().amount(4, 0, 1) > 0
                        || runtime.view().water().amount(4, 0, 2) > 0,
                "falling shaft must occupy an intermediate Z after two local solver steps");

        advance(runtime, 160);
        for (int x = -9; x <= 9; x++) {
            for (int y = -5; y <= 5; y++) {
                for (int z = 0; z <= 4; z++) {
                    assertNull(
                            runtime.view().waterFlow().find(x, y, z),
                            "settled Water Z scene must expose no actual flow sample");
                }
            }
        }

        int minTop = Integer.MAX_VALUE;
        int maxTop = Integer.MIN_VALUE;
        for (int x = -6; x <= -5; x++) {
            for (int y = -1; y <= 0; y++) {
                int amount = runtime.view().water().amount(x, y, 1);
                minTop = Math.min(minTop, amount);
                maxTop = Math.max(maxTop, amount);
            }
        }
        assertTrue(
                maxTop - minTop <= 7,
                "identical settled cells exceeded the integer relaxation fixed point: min="
                        + minTop + ", max=" + maxTop);

        int[] settledCells = waterSnapshot(runtime, -9, 9, -5, 5, 0, 4);
        advance(runtime, 12);
        assertArrayEquals(
                settledCells,
                waterSnapshot(runtime, -9, 9, -5, 5, 0, 4),
                "settled Water must keep every cell at the same fixed point without hidden oscillation");
    }

    @Test
    void geometryStressKeepsSymmetryBlocksSolidCellsAndRespectsRampFaces() {
        ScenarioSession session = new WaterRampGatesScenario().create();
        SimulationRuntime runtime = session.runtime();

        int west = runtime.view().water().amount(-7, 3, 0);
        int east = runtime.view().water().amount(-5, 3, 0);
        int south = runtime.view().water().amount(-6, 2, 0);
        int north = runtime.view().water().amount(-6, 4, 0);
        assertTrue(west > 0);
        assertEquals(west, east);
        assertEquals(west, south);
        assertEquals(west, north);

        for (int y = -6; y <= -2; y++) {
            assertEquals(
                    0,
                    runtime.view().water().amount(-1, y, 0),
                    "FullShape barrier must never contain Water");
        }
        assertTrue(
                sumWater(runtime, 0, 2, -6, 0, 0) > 0L,
                "Water must route around the open end instead of stopping at the barrier");

        assertTrue(
                runtime.view().water().amount(6, 2, 0) > 0,
                "Ramp low face must admit Water into partial free volume");
        assertEquals(
                0,
                runtime.view().water().amount(6, -2, 0),
                "Ramp high face must remain closed to same-level Water");
    }

    @Test
    void rainCycleWetsSoilAndFormsPuddlesWhileRainIsStillFalling() {
        ScenarioSession session = new RainHydrologyScenario().create();
        SimulationRuntime runtime = session.runtime();

        assertEquals(0L, runtime.time().tick());
        for (int x = -1; x <= 1; x++) {
            for (int y = -5; y <= 5; y++) {
                assertEquals(
                        0,
                        retainedWater(runtime, x, y, -1),
                        "all exposed soil must start equally dry");
                assertEquals(
                        0,
                        runtime.view().water().amount(x, y, 0),
                        "dry soil acceptance must not start with puddles");
            }
        }

        advance(runtime, 40);
        assertEquals(40L, runtime.time().tick());
        assertTrue(
                sumRetainedWater(runtime, -1, 1, -5, 5, -1) > 0L,
                "retained Soil Water must increase during visible rain");
        assertEquals(
                0L,
                sumWater(runtime, -1, 1, -5, 5, 0),
                "the early light shower must still be fully absorbed in the central strip");

        advance(runtime, 40);
        assertEquals(80L, runtime.time().tick());
        int puddlesDuringRain = 0;
        int soilOnlyDuringRain = 0;
        for (int x = -1; x <= 1; x++) {
            for (int y = -5; y <= 5; y++) {
                int water = runtime.view().water().amount(x, y, 0);
                int retained = retainedWater(runtime, x, y, -1);
                if (water > 0) {
                    puddlesDuringRain++;
                } else if (retained > 0) {
                    soilOnlyDuringRain++;
                }
            }
        }
        assertTrue(
                puddlesDuringRain > 0,
                "low-capacity soil must puddle while rain is still falling");
        assertTrue(
                soilOnlyDuringRain > 0,
                "higher-capacity soil must remain surface-dry under the same rainfall");

        advance(runtime, 40);
        assertEquals(120L, runtime.time().tick());
        long lakeAfterRain = sumWater(runtime, -6, -4, -1, 1, -1);
        assertTrue(lakeAfterRain > 0L);

        advance(runtime, 120);
        assertEquals(240L, runtime.time().tick());
        assertEquals(
                0L,
                sumWater(runtime, -1, 1, -5, 5, 0),
                "transient central puddles must dry during the clear part of the cycle");
        long lakeDuringDry = sumWater(runtime, -6, -4, -1, 1, -1);
        assertTrue(lakeDuringDry > 0L);
        assertTrue(
                lakeDuringDry < lakeAfterRain,
                "the separate lake must visibly evaporate after rain stops");
    }

    private static void advance(
            SimulationRuntime runtime,
            int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            runtime.stepper().advance();
        }
    }

    private static int retainedWater(
            SimulationRuntime runtime,
            int x,
            int y,
            int z) {
        return runtime.view().soilLiquids().amountOf(
                WaterSystem.TYPE,
                x,
                y,
                z);
    }

    private static long sumWater(
            SimulationRuntime runtime,
            int minX,
            int maxX,
            int minY,
            int maxY,
            int z) {

        long total = 0L;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                total += runtime.view().water().amount(x, y, z);
            }
        }
        return total;
    }

    private static long sumRetainedWater(
            SimulationRuntime runtime,
            int minX,
            int maxX,
            int minY,
            int maxY,
            int z) {
        long total = 0L;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                total += retainedWater(runtime, x, y, z);
            }
        }
        return total;
    }

    private static int[] waterSnapshot(
            SimulationRuntime runtime,
            int minX,
            int maxX,
            int minY,
            int maxY,
            int minZ,
            int maxZ) {
        int width = maxX - minX + 1;
        int height = maxY - minY + 1;
        int depth = maxZ - minZ + 1;
        int[] snapshot = new int[Math.multiplyExact(Math.multiplyExact(width, height), depth)];
        int index = 0;
        for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    snapshot[index++] = runtime.view().water().amount(x, y, z);
                }
            }
        }
        return snapshot;
    }
}
