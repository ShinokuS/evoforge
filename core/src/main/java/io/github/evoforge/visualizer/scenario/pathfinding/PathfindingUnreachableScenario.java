package io.github.evoforge.visualizer.scenario.pathfinding;

import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.navigation.pathfinding.PathQuery;
import io.github.evoforge.simulation.world.navigation.pathfinding.PathSearch;
import io.github.evoforge.visualizer.scenario.ScenarioSession;
import io.github.evoforge.visualizer.scenario.ScenarioTerrain;
import io.github.evoforge.visualizer.scenario.ScenarioView;
import io.github.evoforge.visualizer.scenario.VisualizerScenario;

public final class PathfindingUnreachableScenario implements VisualizerScenario {
    @Override public String id() { return "pathfinding-unreachable"; }
    @Override public String title() { return "Pathfinding / Unreachable"; }
    @Override public String description() {
        return "Disconnected structural islands should terminate as NO_PATH.";
    }

    @Override
    public ScenarioSession create() {
        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId ground = assembly.landscapeDefinition("scenario:path_unreachable_ground");
        ScenarioTerrain.fill(assembly, ground, 0, 5, -2, 2, -1);
        ScenarioTerrain.fill(assembly, ground, 10, 15, -2, 2, -1);
        SimulationRuntime runtime = assembly.start();
        PathQuery query = PathQuery.between(2, 0, 0, 12, 0, 0);
        PathSearch search = PathfindingScenarioDiagnostics.complete(runtime.view().pathfinder().begin(query));
        return new ScenarioSession(
                runtime,
                new ScenarioView(0, 7.5f, 0f, 0.9f),
                PathfindingScenarioDiagnostics.fromSearch(query, search));
    }
}
