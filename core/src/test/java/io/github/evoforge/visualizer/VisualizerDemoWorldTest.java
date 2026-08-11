package io.github.evoforge.visualizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.world.mechanics.geometry.TransitionMask;
import org.junit.jupiter.api.Test;

final class VisualizerDemoWorldTest {

    @Test
    void demoExposesRealRampTopology() {
        SimulationRuntime runtime = VisualizerDemoWorld.create();

        int transitions = runtime.view().navigation().transitions(
                4,
                1,
                0);

        assertTrue(
                TransitionMask.contains(
                        transitions,
                        0,
                        -1,
                        -1));
        assertTrue(
                TransitionMask.contains(
                        transitions,
                        0,
                        1,
                        0));
    }

    @Test
    void fasterDemoMoverCompletesBeforeSlowerMover() {
        SimulationRuntime runtime = VisualizerDemoWorld.create();

        assertEquals(
                1,
                runtime.view().cells().objectCount(
                        -4,
                        -1,
                        0));
        assertEquals(
                1,
                runtime.view().cells().objectCount(
                        -4,
                        1,
                        0));

        runtime.stepper().advance();
        runtime.stepper().advance();

        assertEquals(
                1,
                runtime.view().cells().objectCount(
                        -3,
                        -1,
                        0));
        assertEquals(
                1,
                runtime.view().cells().objectCount(
                        -4,
                        1,
                        0));

        for (int tick = 0; tick < 6; tick++) {
            runtime.stepper().advance();
        }

        assertEquals(
                1,
                runtime.view().cells().objectCount(
                        -3,
                        1,
                        0));
        assertEquals(8, runtime.time().tick());
    }
}
