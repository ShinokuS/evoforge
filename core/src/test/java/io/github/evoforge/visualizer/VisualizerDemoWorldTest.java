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

        assertRamp(runtime, 0, -5, 1, 0, -1, -1, 0, 1, 0);
        assertRamp(runtime, 0, 5, 1, 0, 1, -1, 0, -1, 0);
        assertRamp(runtime, -7, 0, 1, -1, 0, -1, 1, 0, 0);
        assertRamp(runtime, 7, -4, 1, 1, 0, -1, -1, 0, 0);
    }

    @Test
    void demoContainsSuccessiveMountainRampElevations() {
        SimulationRuntime runtime = VisualizerDemoWorld.create();

        assertRamp(runtime, 1, -3, 2, -1, 0, -1, 1, 0, 0);
        assertRamp(runtime, 2, 2, 3, -1, 0, -1, 1, 0, 0);
        assertRamp(runtime, 4, 2, 4, -1, 0, -1, 1, 0, 0);
    }

    @Test
    void demoContainsCaveVoidAndDeepOpenShaft() {
        SimulationRuntime runtime = VisualizerDemoWorld.create();

        // Cave: mountain body at terrain Z=1 is absent while its floor at
        // terrain Z=0 remains present.
        assertFalse(runtime.view().terrain().contains(3, 0, 1));
        assertTrue(runtime.view().terrain().contains(3, 0, 0));
        assertTrue(runtime.view().terrain().contains(2, 2, 1));

        // Shaft: plateau floor and meadow floor are both absent; a much lower
        // surface exists at terrain Z=-3 for lower-depth rendering.
        assertFalse(runtime.view().terrain().contains(-4, 2, 0));
        assertFalse(runtime.view().terrain().contains(-4, 2, -1));
        assertTrue(runtime.view().terrain().contains(-4, 2, -3));
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

        assertTrue(
                TransitionMask.contains(
                        transitions,
                        downDx,
                        downDy,
                        downDz));
        assertTrue(
                TransitionMask.contains(
                        transitions,
                        upDx,
                        upDy,
                        upDz));
    }
}
