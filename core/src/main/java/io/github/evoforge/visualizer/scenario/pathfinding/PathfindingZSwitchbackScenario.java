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

/** A turning ramp chain proves 3D routing is not tied to one horizontal axis. */
public final class PathfindingZSwitchbackScenario implements VisualizerScenario {
    @Override public String id() { return "pathfinding-z-switchback"; }
    @Override public String title() { return "Pathfinding / Z Switchback"; }
    @Override public String description() {
        return "The route climbs while turning +X, +Y, then -X across Z slices.";
    }

    @Override
    public ScenarioSession create() {
        SimulationAssembly assembly = SimulationAssembly.create();
        MaterialDefinitionId ground = assembly.landscapeDefinition("scenario:path_z_switchback_ground");
        assembly.placeTerrain(-1, 0, -1, ground);
        ScenarioTerrain.placeRamp(assembly, ground, 0, 0, 0, RampShape.POSITIVE_X);
        assembly.placeTerrain(1, 0, 0, ground);
        assembly.placeTerrain(1, 1, 0, ground);
        ScenarioTerrain.placeRamp(assembly, ground, 1, 2, 1, RampShape.POSITIVE_Y);
        assembly.placeTerrain(1, 3, 1, ground);
        assembly.placeTerrain(0, 3, 1, ground);
        ScenarioTerrain.placeRamp(assembly, ground, -1, 3, 2, RampShape.NEGATIVE_X);
        assembly.placeTerrain(-2, 3, 2, ground);
        SimulationRuntime runtime = assembly.start();
        PathQuery query = PathQuery.between(-1, 0, 0, -2, 3, 3);
        PathSearch search = PathfindingScenarioDiagnostics.complete(runtime.view().pathfinder().begin(query));
        return new ScenarioSession(
                runtime,
                new ScenarioView(1, 0f, 1.5f, 0.65f),
                PathfindingScenarioDiagnostics.fromSearch(query, search));
    }
}
