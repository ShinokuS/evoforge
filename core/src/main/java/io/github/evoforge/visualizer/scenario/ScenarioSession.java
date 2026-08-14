package io.github.evoforge.visualizer.scenario;

import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.visualizer.presentation.object.ObjectPresentationBindings;

/** One fresh simulation instance plus its initial presentation focus and tooling. */
public record ScenarioSession(
        SimulationRuntime runtime,
        ScenarioView view,
        ScenarioController controller,
        ObjectPresentationBindings objectPresentations) {

    public ScenarioSession(
            SimulationRuntime runtime,
            ScenarioView view) {
        this(runtime, view, ScenarioController.NONE, ObjectPresentationBindings.empty());
    }

    public ScenarioSession(
            SimulationRuntime runtime,
            ScenarioView view,
            ScenarioController controller) {
        this(runtime, view, controller, ObjectPresentationBindings.empty());
    }

    public ScenarioSession(
            SimulationRuntime runtime,
            ScenarioView view,
            ScenarioDiagnostics diagnostics) {
        this(runtime, view, ScenarioController.fixed(diagnostics), ObjectPresentationBindings.empty());
    }

    public ScenarioSession {
        if (runtime == null) throw new IllegalArgumentException("runtime must not be null");
        if (view == null) throw new IllegalArgumentException("view must not be null");
        if (controller == null) throw new IllegalArgumentException("controller must not be null");
        if (objectPresentations == null) {
            throw new IllegalArgumentException("objectPresentations must not be null");
        }
    }

    /** Advance presentation-only scenario tooling to the runtime's current tick. */
    public void update() {
        controller.update(runtime.time().tick());
    }

    /** Current diagnostics; fixed scenarios return the same immutable value every time. */
    public ScenarioDiagnostics diagnostics() {
        return controller.diagnostics();
    }
}
