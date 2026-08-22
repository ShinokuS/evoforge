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

/** Four successive ramps make the route climb across five standing Z slices. */
public final class PathfindingMultiLevelClimbScenario implements VisualizerScenario {
    @Override public String id() { return "pathfinding-multi-level-climb"; }
    @Override public String title() { return "Pathfinding / Multi-Level Climb"; }
    @Override public String description() {
        return "A four-ramp staircase climbs from Z0 to Z4. Use PgUp/PgDn to follow the route.";
    }

    @Override
    public ScenarioSession create() {
        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId ground = assembly.landscapeDefinition("scenario:path_multi_level_ground");
        assembly.placeTerrain(-1, 0, -1, ground);
        for (int level = 0; level < 4; level++) {
            int rampX = level * 2;
            ScenarioTerrain.placeRamp(assembly, ground, rampX, 0, level, RampShape.POSITIVE_X);
            assembly.placeTerrain(rampX + 1, 0, level, ground);
        }
        SimulationRuntime runtime = assembly.start();
        PathQuery query = PathQuery.between(-1, 0, 0, 7, 0, 4);
        PathSearch search = PathfindingScenarioDiagnostics.complete(runtime.view().pathfinder().begin(query));
        return new ScenarioSession(
                runtime,
                new ScenarioView(2, 3f, 0f, 0.7f),
                PathfindingScenarioDiagnostics.fromSearch(query, search));
    }
}
