package io.github.evoforge.visualizer.scenario;

import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.pathfinding.PathQuery;
import io.github.evoforge.simulation.world.pathfinding.PathSearch;

public final class PathfindingStructuralDetourScenario implements VisualizerScenario {
    @Override public String id() { return "pathfinding-structural-detour"; }
    @Override public String title() { return "Pathfinding / Structural Detour"; }
    @Override public String description() { return "Missing support forces Navigation to route through the upper opening."; }

    @Override
    public ScenarioSession create() {
        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId ground = assembly.landscapeDefinition("scenario:path_detour_ground");
        for (int x = 0; x <= 12; x++) {
            for (int y = -3; y <= 3; y++) {
                if (x == 6 && y >= -2 && y <= 2) continue;
                assembly.placeTerrain(x, y, -1, ground);
            }
        }
        SimulationRuntime runtime = assembly.start();
        PathQuery query = PathQuery.between(1, 0, 0, 11, 0, 0);
        PathSearch search = PathfindingScenarioDiagnostics.complete(runtime.view().pathfinder().begin(query));
        return new ScenarioSession(
                runtime,
                new ScenarioView(0, 6f, 0f, 0.8f),
                PathfindingScenarioDiagnostics.fromSearch(query, search));
    }
}
