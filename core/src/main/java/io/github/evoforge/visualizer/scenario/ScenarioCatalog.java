package io.github.evoforge.visualizer.scenario;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Fixed ordered catalog shown by the development visualizer selector. */
public final class ScenarioCatalog {

    private final List<VisualizerScenario> scenarios;

    public ScenarioCatalog(List<VisualizerScenario> scenarios) {
        if (scenarios == null || scenarios.isEmpty()) {
            throw new IllegalArgumentException("scenarios must not be empty");
        }

        List<VisualizerScenario> copy = List.copyOf(scenarios);
        Set<String> ids = new HashSet<>();
        for (VisualizerScenario scenario : copy) {
            if (scenario == null) {
                throw new IllegalArgumentException("scenario must not be null");
            }
            requireText(scenario.id(), "scenario id");
            requireText(scenario.title(), "scenario title");
            requireText(scenario.description(), "scenario description");
            if (!ids.add(scenario.id())) {
                throw new IllegalArgumentException(
                        "duplicate scenario id: " + scenario.id());
            }
        }
        this.scenarios = copy;
    }

    public static ScenarioCatalog standard() {
        return new ScenarioCatalog(List.of(
                new CutawayScenario(),
                new RampNavigationScenario(),
                new TimedMovementScenario(),
                new OccupancyContentionScenario()));
    }

    public int size() {
        return scenarios.size();
    }

    public VisualizerScenario get(int index) {
        return scenarios.get(index);
    }

    public List<VisualizerScenario> scenarios() {
        return scenarios;
    }

    private static void requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
    }
}
