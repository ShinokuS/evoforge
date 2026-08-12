package io.github.evoforge.visualizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.world.mechanics.geometry.TransitionMask;
import org.junit.jupiter.api.Test;

final class VisualizerDemoWorldTest {

    @Test
    void demoExposesRealRampTopologyOnAllFourBaseSides() {
        SimulationRuntime runtime = VisualizerDemoWorld.create();

        assertRamp(runtime, 0, -6, 1, 0, -1, -1, 0, 1, 0);
        assertRamp(runtime, 0, 6, 1, 0, 1, -1, 0, -1, 0);
        assertRamp(runtime, -8, 0, 1, -1, 0, -1, 1, 0, 0);
        assertRamp(runtime, 8, -3, 1, 1, 0, -1, -1, 0, 0);
    }

    @Test
    void demoContainsSuccessiveMountainRampElevations() {
        SimulationRuntime runtime = VisualizerDemoWorld.create();

        assertRamp(runtime, 1, -4, 2, -1, 0, -1, 1, 0, 0);
        assertRamp(runtime, 2, 3, 3, -1, 0, -1, 1, 0, 0);
        assertRamp(runtime, 4, 3, 4, -1, 0, -1, 1, 0, 0);
    }

    @Test
    void mountainCaveHasFloorWallsAndRealRoof() {
        SimulationRuntime runtime = VisualizerDemoWorld.create();

        assertFalse(runtime.view().terrain().contains(3, 0, 1));
        assertTrue(runtime.view().terrain().contains(3, 0, 0));
        assertTrue(runtime.view().terrain().contains(3, 0, 2));
        assertTrue(runtime.view().terrain().contains(2, 2, 1));

        assertFalse(runtime.view().terrain().contains(1, 0, 1));
        assertTrue(runtime.view().terrain().contains(1, 0, 2));
    }

    @Test
    void flatRoofCavernAndDeepShaftAreRealGeometry() {
        SimulationRuntime runtime = VisualizerDemoWorld.create();

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

    @Test
    void fasterDemoMoverCompletesBeforeSlowerMoverOnPlateau() {
        SimulationRuntime runtime = VisualizerDemoWorld.create();

        assertEquals(1, runtime.view().cells().objectCount(-3, 0, 1));
        assertEquals(1, runtime.view().cells().objectCount(-3, -1, 1));

        runtime.stepper().advance();
        runtime.stepper().advance();

        assertEquals(1, runtime.view().cells().objectCount(-2, 0, 1));
        assertEquals(1, runtime.view().cells().objectCount(-3, -1, 1));

        for (int tick = 0; tick < 6; tick++) {
            runtime.stepper().advance();
        }

        assertEquals(1, runtime.view().cells().objectCount(-2, -1, 1));
        assertEquals(8, runtime.time().tick());
    }

    private static void assertRamp(
            SimulationRuntime runtime,
            int x,
            int y,
            int z,
            int downDx,
            int downDy,
            int downDz,
            int upDx,
            int upDy,
            int upDz) {

        int transitions = runtime.view().navigation().transitions(x, y, z);
        String label = "ramp (" + x + "," + y + "," + z
                + ") mask=" + Integer.toBinaryString(transitions);

        assertTrue(
                TransitionMask.contains(
                        transitions,
                        downDx,
                        downDy,
                        downDz),
                label + " missing down (" + downDx + "," + downDy + "," + downDz + ")");
        assertTrue(
                TransitionMask.contains(
                        transitions,
                        upDx,
                        upDy,
                        upDz),
                label + " missing up (" + upDx + "," + upDy + "," + upDz + ")");
    }
}
