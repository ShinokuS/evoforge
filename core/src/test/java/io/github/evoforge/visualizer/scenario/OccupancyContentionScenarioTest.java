package io.github.evoforge.visualizer.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.world.mechanics.occupancy.OccupancyState;
import org.junit.jupiter.api.Test;

final class OccupancyContentionScenarioTest {

    @Test
    void oneMoverOwnsTheImmediateDestinationClaim() {
        SimulationRuntime runtime =
                new OccupancyContentionScenario().create().runtime();

        assertEquals(
                OccupancyState.OCCUPIED,
                runtime.view().occupancy().state(-1, 0, 1));
        assertEquals(
                OccupancyState.RESERVED,
                runtime.view().occupancy().state(0, 0, 1));
        assertEquals(
                OccupancyState.OCCUPIED,
                runtime.view().occupancy().state(1, 0, 1));

        runtime.stepper().advance();
        runtime.stepper().advance();

        assertEquals(
                OccupancyState.OCCUPIED,
                runtime.view().occupancy().state(0, 0, 1));
        assertEquals(1, runtime.view().cells().objectCount(1, 0, 1));
    }
}
