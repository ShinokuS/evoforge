package io.github.evoforge.visualizer.scenario;

import io.github.evoforge.simulation.runtime.SimulationRuntime;

/** One fresh simulation instance plus its initial presentation focus and diagnostics. */
public record ScenarioSession(
        SimulationRuntime runtime,
        ScenarioView view,
        ScenarioDiagnostics diagnostics) {

    public ScenarioSession(
            SimulationRuntime runtime,
            ScenarioView view) {
        this(runtime, view, ScenarioDiagnostics.NONE);
    }

    public ScenarioSession {
        if (runtime == null) {
            throw new IllegalArgumentException("runtime must not be null");
        }
        if (view == null) {
            throw new IllegalArgumentException("view must not be null");
        }
        if (diagnostics == null) {
            throw new IllegalArgumentException("diagnostics must not be null");
        }
    }
}
