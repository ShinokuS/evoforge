package io.github.evoforge.visualizer.scenario.pathfinding;

import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.geometry.RampShape;
import io.github.evoforge.simulation.world.navigation.pathfinding.PathQuery;
import io.github.evoforge.simulation.world.navigation.pathfinding.PathSearch;
import io.github.evoforge.visualizer.scenario.ScenarioSession;
import io.github.evoforge.visualizer.scenario.ScenarioTerrain;
import io.github.evoforge.visualizer.scenario.ScenarioView;
import io.github.evoforge.visualizer.scenario.VisualizerScenario;

/** Two successive ramps demonstrate a route distributed across standing Z levels. */
public final class PathfindingRampScenario implements VisualizerScenario {
    @Override public String id() { return "pathfinding-ramp-3d"; }
    @Override public String title() { return "Pathfinding / 3D Ramps"; }
    @Override public String description() {
        return "One route climbs two Z levels. Use PgUp/PgDn to inspect its slices.";
    }

    @Override
    public ScenarioSession create() {
        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId ground = assembly.landscapeDefinition("scenario:path_ramp_ground");
        assembly.placeTerrain(-1, 0, -1, ground);
        ScenarioTerrain.placeRamp(assembly, ground, 0, 0, 0, RampShape.POSITIVE_X);
        assembly.placeTerrain(1, 0, 0, ground);
        ScenarioTerrain.placeRamp(assembly, ground, 2, 0, 1, RampShape.POSITIVE_X);
        assembly.placeTerrain(3, 0, 1, ground);
        SimulationRuntime runtime = assembly.start();
        PathQuery query = PathQuery.between(-1, 0, 0, 3, 0, 2);
        PathSearch search = PathfindingScenarioDiagnostics.complete(runtime.view().pathfinder().begin(query));
        return new ScenarioSession(
                runtime,
                new ScenarioView(1, 1f, 0f, 0.55f),
                PathfindingScenarioDiagnostics.fromSearch(query, search));
    }
}
