package io.github.evoforge.visualizer.scenario.pathfinding;

import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.world.material.MaterialDefinitionId;
import io.github.evoforge.simulation.world.geometry.RampShape;
import io.github.evoforge.simulation.world.navigation.pathfinding.PathQuery;
import io.github.evoforge.simulation.world.navigation.pathfinding.PathSearch;
import io.github.evoforge.visualizer.scenario.ScenarioSession;
import io.github.evoforge.visualizer.scenario.ScenarioTerrain;
import io.github.evoforge.visualizer.scenario.ScenarioView;
import io.github.evoforge.visualizer.scenario.VisualizerScenario;

/** Start and goal share Z0, but the only connection climbs to Z2 and descends again. */
public final class PathfindingVerticalOverpassScenario implements VisualizerScenario {
    @Override public String id() { return "pathfinding-vertical-overpass"; }
    @Override public String title() { return "Pathfinding / Vertical Overpass"; }
    @Override public String description() {
        return "Start and goal are both Z0; the only route climbs to Z2, crosses, then descends.";
    }

    @Override
    public ScenarioSession create() {
        SimulationAssembly assembly = SimulationAssembly.create();
        MaterialDefinitionId ground = assembly.landscapeDefinition("scenario:path_vertical_overpass_ground");
        assembly.placeTerrain(-1, 0, -1, ground);
        ScenarioTerrain.placeRamp(assembly, ground, 0, 0, 0, RampShape.POSITIVE_X);
        assembly.placeTerrain(1, 0, 0, ground);
        ScenarioTerrain.placeRamp(assembly, ground, 2, 0, 1, RampShape.POSITIVE_X);
        assembly.placeTerrain(3, 0, 1, ground);
        assembly.placeTerrain(4, 0, 1, ground);
        assembly.placeTerrain(5, 0, 1, ground);
        ScenarioTerrain.placeRamp(assembly, ground, 6, 0, 1, RampShape.NEGATIVE_X);
        assembly.placeTerrain(7, 0, 0, ground);
        ScenarioTerrain.placeRamp(assembly, ground, 8, 0, 0, RampShape.NEGATIVE_X);
        assembly.placeTerrain(9, 0, -1, ground);
        assembly.placeTerrain(10, 0, -1, ground);
        SimulationRuntime runtime = assembly.start();
        PathQuery query = PathQuery.between(-1, 0, 0, 10, 0, 0);
        PathSearch search = PathfindingScenarioDiagnostics.complete(runtime.view().pathfinder().begin(query));
        return new ScenarioSession(
                runtime,
                new ScenarioView(1, 4.5f, 0f, 0.8f),
                PathfindingScenarioDiagnostics.fromSearch(query, search));
    }
}
