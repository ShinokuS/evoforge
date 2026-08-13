package io.github.evoforge.visualizer.scenario;

import io.github.evoforge.simulation.runtime.SimulationRuntime;

/** One fresh simulation instance plus its initial presentation focus. */
public record ScenarioSession(
        SimulationRuntime runtime,
        ScenarioView view) {

    public ScenarioSession {
        if (runtime == null) {
            throw new IllegalArgumentException("runtime must not be null");
        }
        if (view == null) {
            throw new IllegalArgumentException("view must not be null");
        }
    }
}
