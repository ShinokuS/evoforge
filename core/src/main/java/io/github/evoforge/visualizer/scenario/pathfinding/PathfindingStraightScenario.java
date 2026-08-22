package io.github.evoforge.visualizer.scenario.pathfinding;

import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.world.material.MaterialDefinitionId;
import io.github.evoforge.simulation.world.navigation.pathfinding.PathQuery;
import io.github.evoforge.simulation.world.navigation.pathfinding.PathSearch;
import io.github.evoforge.visualizer.scenario.ScenarioSession;
import io.github.evoforge.visualizer.scenario.ScenarioTerrain;
import io.github.evoforge.visualizer.scenario.ScenarioView;
import io.github.evoforge.visualizer.scenario.VisualizerScenario;

/** Minimal straight-line production pathfinding scene. */
public final class PathfindingStraightScenario implements VisualizerScenario {
    @Override public String id() { return "pathfinding-straight"; }
    @Override public String title() { return "Pathfinding / Straight"; }
    @Override public String description() {
        return "Baseline exact route on simple flat structural Navigation.";
    }

    @Override
    public ScenarioSession create() {
        SimulationAssembly assembly = SimulationAssembly.create();
        MaterialDefinitionId ground = assembly.landscapeDefinition("scenario:path_straight_ground");
        ScenarioTerrain.fill(assembly, ground, 0, 12, 0, 0, -1);
        SimulationRuntime runtime = assembly.start();
        PathQuery query = PathQuery.between(0, 0, 0, 12, 0, 0);
        PathSearch search = PathfindingScenarioDiagnostics.complete(runtime.view().pathfinder().begin(query));
        return new ScenarioSession(
                runtime,
                new ScenarioView(0, 6f, 0f, 0.7f),
                PathfindingScenarioDiagnostics.fromSearch(query, search));
    }
}
