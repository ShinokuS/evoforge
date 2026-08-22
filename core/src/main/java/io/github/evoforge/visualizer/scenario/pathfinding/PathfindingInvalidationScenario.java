package io.github.evoforge.visualizer.scenario.pathfinding;

import io.github.evoforge.simulation.mechanics.terrainmutation.command.PlaceTerrainCommand;
import io.github.evoforge.simulation.mechanics.terrainmutation.command.ReplaceTerrainCommand;
import io.github.evoforge.simulation.kernel.operation.OperationResults;
import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.world.material.MaterialDefinitionId;
import io.github.evoforge.simulation.world.navigation.pathfinding.PathQuery;
import io.github.evoforge.simulation.world.navigation.pathfinding.PathRoute;
import io.github.evoforge.simulation.world.navigation.pathfinding.PathSearch;
import io.github.evoforge.simulation.world.navigation.pathfinding.PathSearchStatus;
import io.github.evoforge.visualizer.scenario.ScenarioCellMarker;
import io.github.evoforge.visualizer.scenario.ScenarioCellMarkerStyle;
import io.github.evoforge.visualizer.scenario.ScenarioController;
import io.github.evoforge.visualizer.scenario.ScenarioDiagnostics;
import io.github.evoforge.visualizer.scenario.ScenarioSession;
import io.github.evoforge.visualizer.scenario.ScenarioTerrain;
import io.github.evoforge.visualizer.scenario.ScenarioView;
import io.github.evoforge.visualizer.scenario.VisualizerScenario;

/** Shows one visible route becoming stale and then being replaced after world changes. */
public final class PathfindingInvalidationScenario implements VisualizerScenario {
    private static final int BLOCK_X = 8;
    private static final int SLOW_X = 14;
    private static final int CENTER_Y = 0;
    private static final int MUTATION_TICK = 4;
    private static final int REPLAN_TICK = 5;

    @Override public String id() { return "pathfinding-invalidation"; }
    @Override public String title() { return "Pathfinding / Dynamic Invalidation"; }
    @Override public String description() {
        return "3-wide corridor: initial route is straight. Press N x4 to add a solid block at x8 and slow floor at x14; N once more replans.";
    }

    @Override
    public ScenarioSession create() {
        SimulationAssembly assembly = SimulationAssembly.create();
        MaterialDefinitionId ground = assembly.landscapeDefinition("scenario:path_invalidation_ground", 1000);
        MaterialDefinitionId slow = assembly.landscapeDefinition("scenario:path_invalidation_slow", 9000);
        ScenarioTerrain.fill(assembly, ground, 0, 20, -1, 1, -1);
        SimulationRuntime runtime = assembly.start();
        PathQuery query = PathQuery.between(0, CENTER_Y, 0, 20, CENTER_Y, 0);
        PathSearch visibleSearch = PathfindingScenarioDiagnostics.complete(runtime.view().pathfinder().begin(query));
        requireFound(visibleSearch, "initial route");
        requireStraightCenterRoute(visibleSearch.route());
        PathSearch watchedSearch = runtime.view().pathfinder().begin(query);
        return new ScenarioSession(
                runtime,
                new ScenarioView(0, 10f, 0f, 0.9f),
                new InvalidationController(runtime, ground, slow, query, visibleSearch, watchedSearch));
    }

    private static final class InvalidationController implements ScenarioController {
        private final SimulationRuntime runtime;
        private final MaterialDefinitionId ground;
        private final MaterialDefinitionId slow;
        private final PathQuery query;
        private final PathSearch initialVisibleSearch;
        private PathSearch watchedSearch;
        private ScenarioDiagnostics diagnostics;
        private long processedTick;

        private InvalidationController(
                SimulationRuntime runtime,
                MaterialDefinitionId ground,
                MaterialDefinitionId slow,
                PathQuery query,
                PathSearch initialVisibleSearch,
                PathSearch watchedSearch) {
            this.runtime = runtime;
            this.ground = ground;
            this.slow = slow;
            this.query = query;
            this.initialVisibleSearch = initialVisibleSearch;
            this.watchedSearch = watchedSearch;
            diagnostics = PathfindingScenarioDiagnostics.fromSearch(query, initialVisibleSearch);
        }

        @Override
        public void update(long tick) {
            if (tick < processedTick) throw new IllegalStateException("scenario tick moved backwards");
            while (processedTick < tick) {
                processedTick++;
                advanceTick(processedTick);
            }
        }

        @Override public ScenarioDiagnostics diagnostics() { return diagnostics; }

        private void advanceTick(long tick) {
            if (tick < MUTATION_TICK) {
                if (watchedSearch.status() != PathSearchStatus.RUNNING) {
                    throw new IllegalStateException("watched path search completed before mutation");
                }
                watchedSearch.advance(1);
                return;
            }
            if (tick == MUTATION_TICK) {
                if (watchedSearch.status() != PathSearchStatus.RUNNING) {
                    throw new IllegalStateException("watched path search must be RUNNING at mutation");
                }
                OperationResults.requireAccepted(runtime.submit(new PlaceTerrainCommand(BLOCK_X, CENTER_Y, 0, ground)));
                OperationResults.requireAccepted(runtime.submit(new ReplaceTerrainCommand(SLOW_X, CENTER_Y, -1, slow)));
                watchedSearch.advance(1);
                if (watchedSearch.status() != PathSearchStatus.STALE) {
                    throw new IllegalStateException("old path search must become STALE after traversal mutation");
                }
                diagnostics = staleDiagnostics();
                return;
            }
            if (tick == REPLAN_TICK) {
                PathSearch freshSearch = PathfindingScenarioDiagnostics.complete(runtime.view().pathfinder().begin(query));
                requireFound(freshSearch, "fresh route");
                requireChangedDetour(freshSearch.route());
                diagnostics = PathfindingScenarioDiagnostics.fromSearch(query, freshSearch, mutationMarkers());
            }
        }

        private ScenarioDiagnostics staleDiagnostics() {
            ScenarioDiagnostics previous = PathfindingScenarioDiagnostics.fromSearch(
                    query, initialVisibleSearch, mutationMarkers());
            ScenarioCellMarker[] cells = new ScenarioCellMarker[previous.cellCount()];
            for (int index = 0; index < cells.length; index++) cells[index] = previous.cell(index);
            return new ScenarioDiagnostics(
                    cells,
                    previous.route(),
                    "status=STALE | showing pre-change route | solid x8 | slow x14");
        }
    }

    private static ScenarioCellMarker[] mutationMarkers() {
        return new ScenarioCellMarker[] {
                new ScenarioCellMarker(BLOCK_X, CENTER_Y, 0, ScenarioCellMarkerStyle.WARNING),
                new ScenarioCellMarker(SLOW_X, CENTER_Y, 0, ScenarioCellMarkerStyle.WARNING)
        };
    }

    private static void requireFound(PathSearch search, String label) {
        if (search.status() != PathSearchStatus.FOUND) {
            throw new IllegalStateException(label + " must be FOUND, was " + search.status());
        }
    }

    private static void requireStraightCenterRoute(PathRoute route) {
        for (int index = 0; index < route.size(); index++) {
            if (route.y(index) != CENTER_Y) {
                throw new IllegalStateException("initial route must stay on the center lane");
            }
        }
    }

    private static void requireChangedDetour(PathRoute route) {
        boolean usedSideLane = false;
        for (int index = 0; index < route.size(); index++) {
            int x = route.x(index);
            int y = route.y(index);
            if (y != CENTER_Y) usedSideLane = true;
            if (y == CENTER_Y && (x == BLOCK_X || x == SLOW_X)) {
                throw new IllegalStateException("fresh route must avoid both changed center cells");
            }
        }
        if (!usedSideLane) throw new IllegalStateException("fresh route must leave the center lane");
    }
}
