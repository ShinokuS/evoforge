package io.github.evoforge.visualizer.scenario;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.world.mechanics.geometry.TransitionMask;
import org.junit.jupiter.api.Test;

final class RampNavigationScenarioTest {

    @Test
    void exposesRealRampTopologyOnAllFourBaseSides() {
        SimulationRuntime runtime = new RampNavigationScenario().create().runtime();

        assertRamp(runtime, 0, -6, 1, 0, -1, -1, 0, 1, 0);
        assertRamp(runtime, 0, 6, 1, 0, 1, -1, 0, -1, 0);
        assertRamp(runtime, -8, 0, 1, -1, 0, -1, 1, 0, 0);
        assertRamp(runtime, 8, 0, 1, 1, 0, -1, -1, 0, 0);
    }

    @Test
    void containsSuccessiveRampElevations() {
        SimulationRuntime runtime = new RampNavigationScenario().create().runtime();

        assertRamp(runtime, 1, -4, 2, -1, 0, -1, 1, 0, 0);
        assertRamp(runtime, 2, 3, 3, -1, 0, -1, 1, 0, 0);
        assertRamp(runtime, 4, 3, 4, -1, 0, -1, 1, 0, 0);
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
                label + " missing down transition");
        assertTrue(
                TransitionMask.contains(
                        transitions,
                        upDx,
                        upDy,
                        upDz),
                label + " missing up transition");
    }
}
