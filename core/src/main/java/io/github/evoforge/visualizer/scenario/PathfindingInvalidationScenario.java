package io.github.evoforge.visualizer.scenario;

import io.github.evoforge.simulation.control.terrain.PlaceTerrainCommand;
import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.pathfinding.PathQuery;
import io.github.evoforge.simulation.world.pathfinding.PathSearch;
import io.github.evoforge.simulation.world.pathfinding.PathSearchStatus;

/** Demonstrates stale sliced search detection followed by a fresh replan. */
public final class PathfindingInvalidationScenario implements VisualizerScenario {

    private static final int BLOCK_X = 10;
    private static final int BLOCK_Y = 0;
    private static final int STALE_TICK = 4;
    private static final int REPLAN_TICK = 5;

    @Override
    public String id() {
        return "pathfinding-invalidation";
    }

    @Override
    public String title() {
        return "Pathfinding / Dynamic Invalidation";
    }

    @Override
    public String description() {
        return "Press N: ticks 1-3 search, tick 4 adds the orange block -> STALE, tick 5 fresh search -> FOUND detour.";
    }

    @Override
    public ScenarioSession create() {
        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId ground = assembly.landscapeDefinition(
                "scenario:path_invalidation_ground");
        ScenarioTerrain.fill(assembly, ground, 0, 20, -2, 2, -1);

        SimulationRuntime runtime = assembly.start();
        PathQuery query = PathQuery.between(0, 0, 0, 20, 0, 0);
        PathSearch search = runtime.view().pathfinder().begin(query);

        return new ScenarioSession(
                runtime,
                new ScenarioView(0, 10f, 0f, 0.9f),
                new InvalidationController(
                        runtime,
                        ground,
                        query,
                        search));
    }

    private static final class InvalidationController
            implements ScenarioController {

        private final SimulationRuntime runtime;
        private final LandscapeDefinitionId ground;
        private final PathQuery query;

        private PathSearch search;
        private ScenarioDiagnostics diagnostics;
        private long processedTick;
        private boolean mutated;

        private InvalidationController(
                SimulationRuntime runtime,
                LandscapeDefinitionId ground,
                PathQuery query,
                PathSearch search) {

            this.runtime = runtime;
            this.ground = ground;
            this.query = query;
            this.search = search;
            diagnostics = PathfindingScenarioDiagnostics.fromSearch(
                    query,
                    search);
        }

        @Override
        public void update(long tick) {
            if (tick < processedTick) {
                throw new IllegalStateException(
                        "scenario tick moved backwards");
            }

            while (processedTick < tick) {
                processedTick++;
                advanceTick(processedTick);
            }
        }

        @Override
        public ScenarioDiagnostics diagnostics() {
            return diagnostics;
        }

        private void advanceTick(long tick) {
            if (tick < STALE_TICK) {
                if (search.status() == PathSearchStatus.RUNNING) {
                    search.advance(1);
                }
                diagnostics = PathfindingScenarioDiagnostics.fromSearch(
                        query,
                        search);
                return;
            }

            if (tick == STALE_TICK) {
                runtime.submit(new PlaceTerrainCommand(
                        BLOCK_X,
                        BLOCK_Y,
                        0,
                        ground));
                mutated = true;

                if (search.status() == PathSearchStatus.RUNNING) {
                    search.advance(1);
                }
                if (search.status() != PathSearchStatus.STALE) {
                    throw new IllegalStateException(
                            "old path search must become STALE after traversal mutation");
                }

                diagnostics = diagnosticsWithMutation(search);
                return;
            }

            if (tick == REPLAN_TICK) {
                search = PathfindingScenarioDiagnostics.complete(
                        runtime.view().pathfinder().begin(query));
                if (search.status() != PathSearchStatus.FOUND) {
                    throw new IllegalStateException(
                            "fresh search must find the detour after mutation");
                }
                diagnostics = diagnosticsWithMutation(search);
            }
        }

        private ScenarioDiagnostics diagnosticsWithMutation(
                PathSearch currentSearch) {

            if (!mutated) {
                return PathfindingScenarioDiagnostics.fromSearch(
                        query,
                        currentSearch);
            }

            return PathfindingScenarioDiagnostics.fromSearch(
                    query,
                    currentSearch,
                    new ScenarioCellMarker(
                            BLOCK_X,
                            BLOCK_Y,
                            0,
                            ScenarioCellMarkerStyle.WARNING));
        }
    }
}
