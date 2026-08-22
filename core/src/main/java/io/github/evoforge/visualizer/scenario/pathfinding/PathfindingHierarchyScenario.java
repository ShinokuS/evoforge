package io.github.evoforge.visualizer.scenario.pathfinding;

import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.navigation.pathfinding.PathQuery;
import io.github.evoforge.simulation.world.navigation.pathfinding.PathSearch;
import io.github.evoforge.visualizer.scenario.ScenarioCellMarker;
import io.github.evoforge.visualizer.scenario.ScenarioCellMarkerStyle;
import io.github.evoforge.visualizer.scenario.ScenarioSession;
import io.github.evoforge.visualizer.scenario.ScenarioTerrain;
import io.github.evoforge.visualizer.scenario.ScenarioView;
import io.github.evoforge.visualizer.scenario.VisualizerScenario;
import java.util.ArrayList;
import java.util.List;

public final class PathfindingHierarchyScenario implements VisualizerScenario {
    @Override public String id() { return "pathfinding-hierarchy"; }
    @Override public String title() { return "Pathfinding / Hierarchy"; }
    @Override public String description() {
        return "Long route crosses 8-cell hierarchy clusters; orange columns mark their cuts.";
    }

    @Override
    public ScenarioSession create() {
        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId ground = assembly.landscapeDefinition("scenario:path_hierarchy_ground");
        ScenarioTerrain.fill(assembly, ground, 0, 39, -2, 2, -1);
        SimulationRuntime runtime = assembly.start();
        PathQuery query = PathQuery.between(0, 0, 0, 39, 0, 0);
        PathSearch search = PathfindingScenarioDiagnostics.complete(runtime.view().pathfinder().begin(query));
        List<ScenarioCellMarker> markers = new ArrayList<>();
        for (int x = 8; x < 40; x += 8) {
            for (int y = -2; y <= 2; y++) {
                markers.add(new ScenarioCellMarker(x, y, 0, ScenarioCellMarkerStyle.WARNING));
            }
        }
        return new ScenarioSession(
                runtime,
                new ScenarioView(0, 19.5f, 0f, 1.65f),
                PathfindingScenarioDiagnostics.fromSearch(
                        query, search, markers.toArray(ScenarioCellMarker[]::new)));
    }
}
