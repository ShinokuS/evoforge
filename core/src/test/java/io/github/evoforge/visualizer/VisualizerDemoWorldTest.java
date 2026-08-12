package io.github.evoforge.visualizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.world.mechanics.geometry.TransitionMask;
import org.junit.jupiter.api.Test;

final class VisualizerDemoWorldTest {

    @Test
    void demoExposesRealRampTopologyOnAllFourPlateauSides() {
        SimulationRuntime runtime = VisualizerDemoWorld.create();

        assertRamp(
                runtime,
                0, -4, 1,
                0, -1, -1,
                0, 1, 0);
        assertRamp(
                runtime,
                0, 4, 1,
                0, 1, -1,
                0, -1, 0);
        assertRamp(
                runtime,
                -5, 0, 1,
                -1, 0, -1,
                1, 0, 0);
        assertRamp(
                runtime,
                5, 0, 1,
                1, 0, -1,
                -1, 0, 0);
    }

    @Test
    void demoRepeatsRampTopologyAtSecondElevation() {
        SimulationRuntime runtime = VisualizerDemoWorld.create();

        assertRamp(
                runtime,
                0, 2, 2,
                -1, 0, -1,
                1, 0, 0);
    }

    @Test
    void fasterDemoMoverCompletesBeforeSlowerMoverOnPlateau() {
        SimulationRuntime runtime = VisualizerDemoWorld.create();

        assertEquals(1, runtime.view().cells().objectCount(-2, 0, 1));
        assertEquals(1, runtime.view().cells().objectCount(-2, -1, 1));

        runtime.stepper().advance();
        runtime.stepper().advance();

        assertEquals(1, runtime.view().cells().objectCount(-1, 0, 1));
        assertEquals(1, runtime.view().cells().objectCount(-2, -1, 1));

        for (int tick = 0; tick < 6; tick++) {
            runtime.stepper().advance();
        }

        assertEquals(1, runtime.view().cells().objectCount(-1, -1, 1));
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
