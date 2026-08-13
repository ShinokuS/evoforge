package io.github.evoforge.visualizer.scenario;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.runtime.SimulationRuntime;
import org.junit.jupiter.api.Test;

final class CutawayScenarioTest {

    @Test
    void mountainCaveHasFloorWallsAndRealRoof() {
        SimulationRuntime runtime = new CutawayScenario().create().runtime();

        assertFalse(runtime.view().terrain().contains(3, 0, 1));
        assertTrue(runtime.view().terrain().contains(3, 0, 0));
        assertTrue(runtime.view().terrain().contains(3, 0, 2));
        assertTrue(runtime.view().terrain().contains(2, 2, 1));

        assertFalse(runtime.view().terrain().contains(1, 0, 1));
        assertTrue(runtime.view().terrain().contains(1, 0, 2));
    }

    @Test
    void flatRoofCavernAndDeepShaftAreRealGeometry() {
        SimulationRuntime runtime = new CutawayScenario().create().runtime();

        assertTrue(runtime.view().terrain().contains(-12, 1, -1));
        assertFalse(runtime.view().terrain().contains(-12, 1, 0));
        assertFalse(runtime.view().terrain().contains(-12, 1, 1));
        assertTrue(runtime.view().terrain().contains(-12, 1, 2));
        assertFalse(runtime.view().terrain().contains(-11, 0, 2));

        assertFalse(runtime.view().terrain().contains(-4, 2, 0));
        assertFalse(runtime.view().terrain().contains(-4, 2, -1));
        assertFalse(runtime.view().terrain().contains(-4, 2, -2));
        assertFalse(runtime.view().terrain().contains(-4, 2, -3));
        assertFalse(runtime.view().terrain().contains(-4, 2, -4));
        assertTrue(runtime.view().terrain().contains(-4, 2, -5));
    }
}
