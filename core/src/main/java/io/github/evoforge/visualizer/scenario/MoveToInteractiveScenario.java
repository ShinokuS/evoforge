package io.github.evoforge.visualizer.scenario;

public final class MoveToInteractiveScenario implements VisualizerScenario {
    @Override public String id() { return "movement-click-to-move"; }
    @Override public String title() { return "Click To Move"; }
    @Override public String description() { return "LMB select; RMB move."; }
    @Override public ScenarioSession create() { throw new UnsupportedOperationException(); }
}
