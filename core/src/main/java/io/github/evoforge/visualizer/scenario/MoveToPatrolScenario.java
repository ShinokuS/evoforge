package io.github.evoforge.visualizer.scenario;

public final class MoveToPatrolScenario implements VisualizerScenario {
    @Override public String id() { return "movement-patrol"; }
    @Override public String title() { return "Movement Patrol"; }
    @Override public String description() { return "MoveTo patrol scenario."; }
    @Override public ScenarioSession create() { throw new UnsupportedOperationException(); }
}
