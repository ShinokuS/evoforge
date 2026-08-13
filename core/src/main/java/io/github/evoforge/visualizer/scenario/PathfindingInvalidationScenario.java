package io.github.evoforge.visualizer.scenario;

import io.github.evoforge.simulation.control.terrain.PlaceTerrainCommand;
import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.pathfinding.PathQuery;
import io.github.evoforge.simulation.world.pathfinding.PathSearch;

public final class PathfindingInvalidationScenario implements VisualizerScenario {
    @Override public String id() { return "pathfinding-invalidation"; }
    @Override public String title() { return "Pathfinding / Dynamic Invalidation"; }
    @Override public String description() { return "Traversal mutation makes a sliced search STALE instead of mixing revisions."; }

    @Override
    public ScenarioSession create() {
        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId ground = assembly.landscapeDefinition("scenario:path_invalidation_ground");
        ScenarioTerrain.fill(assembly, ground, 0, 20, 0, 0, -1);
        SimulationRuntime runtime = assembly.start();
        PathQuery query = PathQuery.between(0, 0, 0, 20, 0, 0);
        PathSearch search = runtime.view().pathfinder().begin(query);
        search.advance(1);
        runtime.submit(new PlaceTerrainCommand(10, 1, -1, ground));
        search.advance(1);
        return new ScenarioSession(
                runtime,
                new ScenarioView(0, 10f, 0f, 1.0f),
                PathfindingScenarioDiagnostics.fromSearch(
                        query,
                        search,
                        new ScenarioCellMarker(10, 1, 0, ScenarioCellMarkerStyle.WARNING)));
    }
}
