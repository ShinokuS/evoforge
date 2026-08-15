package io.github.evoforge.visualizer.scenario;

import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.visualizer.presentation.object.ObjectPresentationBindings;
import io.github.evoforge.visualizer.presentation.portal.ViewPortalLookup;
import io.github.evoforge.visualizer.presentation.weather.WeatherPresentationLookup;

/** One fresh simulation instance plus its initial presentation focus and tooling. */
public record ScenarioSession(
        SimulationRuntime runtime,
        ScenarioView view,
        ScenarioController controller,
        ObjectPresentationBindings objectPresentations,
        WeatherPresentationLookup weather,
        ViewPortalLookup portals) {

    public ScenarioSession(
            SimulationRuntime runtime,
            ScenarioView view) {
        this(
                runtime,
                view,
                ScenarioController.NONE,
                ObjectPresentationBindings.empty(),
                WeatherPresentationLookup.CLEAR_LOOKUP,
                ViewPortalLookup.EMPTY);
    }

    public ScenarioSession(
            SimulationRuntime runtime,
            ScenarioView view,
            ScenarioController controller) {
        this(
                runtime,
                view,
                controller,
                ObjectPresentationBindings.empty(),
                WeatherPresentationLookup.CLEAR_LOOKUP,
                ViewPortalLookup.EMPTY);
    }

    public ScenarioSession(
            SimulationRuntime runtime,
            ScenarioView view,
            ScenarioDiagnostics diagnostics) {
        this(
                runtime,
                view,
                ScenarioController.fixed(diagnostics),
                ObjectPresentationBindings.empty(),
                WeatherPresentationLookup.CLEAR_LOOKUP,
                ViewPortalLookup.EMPTY);
    }

    /** Preserves the previous canonical presentation constructor for existing scenarios. */
    public ScenarioSession(
            SimulationRuntime runtime,
            ScenarioView view,
            ScenarioController controller,
            ObjectPresentationBindings objectPresentations) {
        this(
                runtime,
                view,
                controller,
                objectPresentations,
                WeatherPresentationLookup.CLEAR_LOOKUP,
                ViewPortalLookup.EMPTY);
    }

    public ScenarioSession(
            SimulationRuntime runtime,
            ScenarioView view,
            WeatherPresentationLookup weather) {
        this(
                runtime,
                view,
                ScenarioController.NONE,
                ObjectPresentationBindings.empty(),
                weather,
                ViewPortalLookup.EMPTY);
    }

    public ScenarioSession(
            SimulationRuntime runtime,
            ScenarioView view,
            ScenarioController controller,
            ObjectPresentationBindings objectPresentations,
            WeatherPresentationLookup weather) {
        this(
                runtime,
                view,
                controller,
                objectPresentations,
                weather,
                ViewPortalLookup.EMPTY);
    }

    public ScenarioSession {
        if (runtime == null) throw new IllegalArgumentException("runtime must not be null");
        if (view == null) throw new IllegalArgumentException("view must not be null");
        if (controller == null) throw new IllegalArgumentException("controller must not be null");
        if (objectPresentations == null) {
            throw new IllegalArgumentException("objectPresentations must not be null");
        }
        if (weather == null) {
            throw new IllegalArgumentException("weather must not be null");
        }
        if (portals == null) {
            throw new IllegalArgumentException("portals must not be null");
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
