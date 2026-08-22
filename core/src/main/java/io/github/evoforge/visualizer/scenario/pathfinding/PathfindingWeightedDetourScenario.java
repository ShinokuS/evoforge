package io.github.evoforge.visualizer.scenario.pathfinding;

import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.navigation.pathfinding.PathQuery;
import io.github.evoforge.simulation.world.navigation.pathfinding.PathSearch;
import io.github.evoforge.visualizer.scenario.ScenarioCellMarker;
import io.github.evoforge.visualizer.scenario.ScenarioCellMarkerStyle;
import io.github.evoforge.visualizer.scenario.ScenarioSession;
import io.github.evoforge.visualizer.scenario.ScenarioView;
import io.github.evoforge.visualizer.scenario.VisualizerScenario;
import java.util.ArrayList;
import java.util.List;

public final class PathfindingWeightedDetourScenario implements VisualizerScenario {
    @Override public String id() { return "pathfinding-weighted-detour"; }
    @Override public String title() { return "Pathfinding / Weighted Detour"; }
    @Override public String description() {
        return "Orange cells are valid but expensive; the cheapest route should avoid them.";
    }

    @Override
    public ScenarioSession create() {
        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId ground = assembly.landscapeDefinition("scenario:path_weighted_ground", 1000);
        LandscapeDefinitionId costly = assembly.landscapeDefinition("scenario:path_weighted_costly", 6000);
        List<ScenarioCellMarker> warnings = new ArrayList<>();
        for (int x = 0; x <= 12; x++) {
            for (int y = -2; y <= 2; y++) {
                boolean expensive = y == 0 && x >= 3 && x <= 9;
                assembly.placeTerrain(x, y, -1, expensive ? costly : ground);
                if (expensive) warnings.add(new ScenarioCellMarker(x, y, 0, ScenarioCellMarkerStyle.WARNING));
            }
        }
        SimulationRuntime runtime = assembly.start();
        PathQuery query = PathQuery.between(0, 0, 0, 12, 0, 0);
        PathSearch search = PathfindingScenarioDiagnostics.complete(runtime.view().pathfinder().begin(query));
        return new ScenarioSession(
                runtime,
                new ScenarioView(0, 6f, 0f, 0.8f),
                PathfindingScenarioDiagnostics.fromSearch(
                        query, search, warnings.toArray(ScenarioCellMarker[]::new)));
    }
}
