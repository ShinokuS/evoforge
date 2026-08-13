package io.github.evoforge.visualizer.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.evoforge.simulation.runtime.SimulationRuntime;
import org.junit.jupiter.api.Test;

final class TimedMovementScenarioTest {

    @Test
    void fasterMoverCompletesBeforeSlowerMover() {
        SimulationRuntime runtime = new TimedMovementScenario().create().runtime();

        assertEquals(1, runtime.view().cells().objectCount(-3, 1, 1));
        assertEquals(1, runtime.view().cells().objectCount(-3, -1, 1));

        runtime.stepper().advance();
        runtime.stepper().advance();

        assertEquals(1, runtime.view().cells().objectCount(-2, 1, 1));
        assertEquals(1, runtime.view().cells().objectCount(-3, -1, 1));

        for (int tick = 0; tick < 6; tick++) {
            runtime.stepper().advance();
        }

        assertEquals(1, runtime.view().cells().objectCount(-2, -1, 1));
        assertEquals(8, runtime.time().tick());
    }
}
