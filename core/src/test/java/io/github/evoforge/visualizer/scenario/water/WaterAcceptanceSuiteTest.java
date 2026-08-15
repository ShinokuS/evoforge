package io.github.evoforge.visualizer.scenario.water;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.visualizer.scenario.ScenarioSession;
import io.github.evoforge.visualizer.scenario.environment.RainHydrologyScenario;

final class WaterAcceptanceSuiteTest {

    @Test
    void zFlowContainsRealStackedPoolAndSettlesToNoActualTransfer() {
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

        long settledTotal = sumWaterAllZ(runtime, -9, 9, -5, 5, 0, 4);
        advance(runtime, 12);
        assertEquals(
                settledTotal,
                sumWaterAllZ(runtime, -9, 9, -5, 5, 0, 4),
                "settled Water must stay at the same fixed point without hidden oscillation");
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
    void rainCycleStartsDryFormsUnevenPuddlesAndEvaporatesSeparateLake() {
        ScenarioSession session = new RainHydrologyScenario().create();
        SimulationRuntime runtime = session.runtime();

        assertEquals(0L, runtime.time().tick());
        for (int x = -2; x <= 3; x++) {
            for (int y = -5; y <= 5; y++) {
                assertEquals(
                        0,
                        runtime.view().soilMoisture().amount(x, y, -1),
                        "all exposed soil must start equally dry");
                assertEquals(
                        0,
                        runtime.view().water().amount(x, y, 0),
                        "dry soil acceptance must not start with puddles");
            }
        }

        long initialLake = sumWater(runtime, -6, -4, -1, 1, -1);
        assertTrue(initialLake > 0L);

        // Before the first shower at tick 120 the isolated lake must already lose
        // finite Water to evaporation while the dry ground stays dry.
        advance(runtime, 80);
        long evaporatedLake = sumWater(runtime, -6, -4, -1, 1, -1);
        assertTrue(evaporatedLake > 0L);
        assertTrue(evaporatedLake < initialLake);

        // Finish the first shower. All cells receive the same 3 mm input, but their
        // deterministic local maximum Soil capacity differs.
        advance(runtime, 40);
        assertEquals(120L, runtime.time().tick());

        int puddled = 0;
        int absorbed = 0;
        for (int x = -2; x <= 3; x++) {
            for (int y = -5; y <= 5; y++) {
                if (runtime.view().water().amount(x, y, 0) > 0) {
                    puddled++;
                } else {
                    absorbed++;
                }
            }
        }
        assertTrue(puddled > 0, "low-capacity cells must form puddles");
        assertTrue(absorbed > 0, "higher-capacity cells must absorb the same shower");
        assertEquals(
                0,
                runtime.view().soilMoisture().amount(4, 0, -1),
                "ground beneath the elevated roof must remain shielded");

        // The deliberately dry climate phase removes both transient puddles and
        // retained moisture before the next shower while the deeper lake remains.
        advance(runtime, 100);
        assertEquals(
                0L,
                sumWater(runtime, -2, 3, -5, 5, 0),
                "transient puddles must dry before the next shower");
        assertTrue(
                sumWater(runtime, -6, -4, -1, 1, -1) > 0L,
                "the finite lake should evaporate gradually rather than disappear in one cycle");
    }

    private static void advance(
            SimulationRuntime runtime,
            int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            runtime.stepper().advance();
        }
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

    private static long sumWaterAllZ(
            SimulationRuntime runtime,
            int minX,
            int maxX,
            int minY,
            int maxY,
            int minZ,
            int maxZ) {

        long total = 0L;
        for (int z = minZ; z <= maxZ; z++) {
            total += sumWater(runtime, minX, maxX, minY, maxY, z);
        }
        return total;
    }
}
