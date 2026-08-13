package io.github.evoforge.visualizer.scenario;

/**
 * Builds one deterministic, human-readable visualizer scenario.
 *
 * <p>Scenarios belong to presentation tooling. They compose a fresh production
 * simulation runtime and optional initial presentation focus; simulation
 * systems never depend on scenario types.</p>
 */
public interface VisualizerScenario {

    String id();

    String title();

    String description();

    ScenarioSession create();
}
