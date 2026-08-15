package io.github.evoforge.visualizer.scenario.water;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.visualizer.scenario.ScenarioSession;

final class WaterAcceptanceSuiteTest {

    @Test
    void deepStackActuallyOccupiesMultipleWaterLevels() {
        ScenarioSession session = new WaterZStackScenario().create();
        SimulationRuntime runtime = session.runtime();

        assertEquals(46L, runtime.time().tick());
        assertTrue(sumWater(runtime, -2, 2, -2, 2, 0) > 0L);
        assertTrue(
                sumWater(runtime, -2, 2, -2, 2, 1) > 0L,
                "deep-basin scenario must create a real second Water Z layer");
    }

    @Test
    void elevatedCollectorCreatesWaterAcrossMoreThanOneZLayer() {
        ScenarioSession session = new WaterVerticalFallScenario().create();
        SimulationRuntime runtime = session.runtime();

        int wetLayers = 0;
        for (int z = 0; z <= 3; z++) {
            if (sumWater(runtime, -5, 5, -4, 4, z) > 0L) {
                wetLayers++;
            }
        }
        assertTrue(
                wetLayers >= 2,
                "vertical-fall scenario must exercise Water in multiple Z layers");
    }

    @Test
    void equalizationGateFeedsInitiallyNonOverflowingRightChamber() {
        ScenarioSession session = new WaterEqualizationScenario().create();
        SimulationRuntime runtime = session.runtime();

        assertTrue(sumWater(runtime, -5, -1, -3, 3, 0) > 0L);
        assertTrue(
                sumWater(runtime, 0, 5, -3, 3, 0) > 0L,
                "right chamber should receive Water through the single physical gate");
        assertTrue(
                runtime.view().soilMoisture().amount(4, 0, -1) < 1_000_000,
                "right terrain must still be below local rain saturation");
    }

    @Test
    void symmetricSourceProducesEqualImmediateCardinalOutflow() {
        ScenarioSession session = new WaterSymmetricSplitScenario().create();
        SimulationRuntime runtime = session.runtime();

        int west = runtime.view().water().amount(-1, 0, 0);
        int east = runtime.view().water().amount(1, 0, 0);
        int south = runtime.view().water().amount(0, -1, 0);
        int north = runtime.view().water().amount(0, 1, 0);

        assertTrue(west > 0);
        assertEquals(west, east);
        assertEquals(west, south);
        assertEquals(west, north);
    }

    @Test
    void rampScenarioContainsRealWaterAtPartialRampAnchor() {
        ScenarioSession session = new WaterRampGatesScenario().create();
        SimulationRuntime runtime = session.runtime();

        assertTrue(
                runtime.view().water().amount(0, -2, 0) > 0
                        || runtime.view().water().amount(-1, -2, 0) > 0,
                "low-face lane must contain Water at or immediately beside the Ramp");
        assertTrue(
                runtime.view().water().amount(2, 2, 0) > 0,
                "high-face source lane must retain finite Water on its source side");
    }

    @Test
    void solidDetourBarrierNeverStoresWaterInsideItsFullCells() {
        ScenarioSession session = new WaterBarrierDetourScenario().create();
        SimulationRuntime runtime = session.runtime();

        assertTrue(sumWater(runtime, -5, -3, -1, 1, 0) > 0L);
        for (int y = -3; y <= 2; y++) {
            assertEquals(0, runtime.view().water().amount(0, y, 0));
        }
    }

    @Test
    void roofShieldsLowerChamberFromDirectRain() {
        ScenarioSession session = new WaterSkyShieldScenario().create();
        SimulationRuntime runtime = session.runtime();

        assertTrue(sumWater(runtime, -5, -1, -3, 3, 0) > 0L);
        assertEquals(
                0L,
                sumWater(runtime, 1, 5, -3, 3, 0),
                "roofed lower chamber should have no direct surface Water");
        assertEquals(
                0,
                runtime.view().soilMoisture().amount(3, 0, -1),
                "roofed lower terrain should receive no direct precipitation");
        assertTrue(
                runtime.view().soilMoisture().amount(3, 0, 1) > 0,
                "roof terrain itself should receive the precipitation");
    }

    @Test
    void evaporationRemovesWaterBetweenPhysicalRainPulses() {
        ScenarioSession session = new WaterEvaporationCycleScenario().create();
        SimulationRuntime runtime = session.runtime();

        long before = sumWater(runtime, -5, -1, -3, 3, 0);
        assertTrue(before > 0L);
        assertTrue(runtime.view().soilMoisture().amount(4, 0, -1) > 0);

        runtime.stepper().advance();

        long after = sumWater(runtime, -5, -1, -3, 3, 0);
        assertTrue(
                after < before,
                "exposed Water must shrink on the tick after the rain pulse");
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
