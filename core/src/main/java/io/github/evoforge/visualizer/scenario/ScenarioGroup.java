package io.github.evoforge.visualizer.scenario;

import java.util.List;

/** One explicit presentation-side category in the debug scenario browser. */
public record ScenarioGroup(
        String id,
        String title,
        List<VisualizerScenario> scenarios) {

    public ScenarioGroup {
        requireText(id, "group id");
        requireText(title, "group title");
        if (scenarios == null || scenarios.isEmpty()) {
            throw new IllegalArgumentException(
                    "group scenarios must not be empty");
        }
        scenarios = List.copyOf(scenarios);
        for (VisualizerScenario scenario : scenarios) {
            if (scenario == null) {
                throw new IllegalArgumentException(
                        "group scenario must not be null");
            }
        }
    }

    public static ScenarioGroup of(
            String id,
            String title,
            VisualizerScenario... scenarios) {

        if (scenarios == null) {
            throw new IllegalArgumentException(
                    "scenarios must not be null");
        }
        return new ScenarioGroup(id, title, List.of(scenarios));
    }

    private static void requireText(
            String value,
            String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    label + " must not be blank");
        }
    }
}
