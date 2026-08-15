package io.github.evoforge.visualizer.scenario.water;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.visualizer.scenario.ScenarioSession;
import io.github.evoforge.visualizer.scenario.environment.RainHydrologyScenario;

final class WaterAcceptanceSuiteTest {

    @Test
    void zFlowContainsRealStackedPoolAndMultiLevelFall() {
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
    void rainCycleCreatesOnlyTransientPuddlesAndShieldsCoveredGround() {
        ScenarioSession session = new RainHydrologyScenario().create();
        SimulationRuntime runtime = session.runtime();

        assertEquals(240L, runtime.time().tick());
        assertTrue(
                sumWater(runtime, -6, -2, -4, 4, 0) > 0L,
                "impermeable surface should form a shallow puddle after the rain pulse");
        assertTrue(
                sumWater(runtime, -1, 1, -4, 4, 0) > 0L,
                "low-infiltration clay should overflow a fraction of the shower");
        assertEquals(
                0L,
                sumWater(runtime, 2, 6, -4, 4, 0),
                "loam should absorb the complete 3 mm event before free Water forms");
        assertEquals(
                0,
                runtime.view().soilMoisture().amount(3, 0, 0),
                "elevated roofed ground must receive no direct precipitation");
        assertTrue(
                runtime.view().soilMoisture().amount(3, 0, 2) > 0,
                "the exposed roof itself must receive the rain pulse");

        // Check late in the dry part of the same climate cycle, still twenty
        // ticks before the next 3 mm precipitation event at tick 480.
        for (int tick = 0; tick < 220; tick++) {
            runtime.stepper().advance();
        }

        assertEquals(
                0L,
                sumWater(runtime, -6, 6, -4, 4, 0),
                "exposed surface puddles must dry before the next 240-tick rain cycle");
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
}
