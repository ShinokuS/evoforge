package io.github.evoforge.visualizer.scenario;

import io.github.evoforge.simulation.runtime.SimulationView;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.pathfinding.PathQuery;
import io.github.evoforge.simulation.world.pathfinding.PathSearch;
import io.github.evoforge.simulation.world.pathfinding.PathSearchStatus;

final class MoveToScenarioRoutes {

    private MoveToScenarioRoutes() {
    }

    static MoveToScenarioPlan plan(
            SimulationView view,
            ObjectId objectId,
            int goalX,
            int goalY,
            int goalZ) {

        PathQuery query = PathQuery.between(
                view.transforms().x(objectId),
                view.transforms().y(objectId),
                view.transforms().z(objectId),
                goalX,
                goalY,
                goalZ);
        PathSearch search = PathfindingScenarioDiagnostics.complete(
                view.pathfinder().begin(query));
        return new MoveToScenarioPlan(
                goalX,
                goalY,
                goalZ,
                search.status() == PathSearchStatus.FOUND
                        ? search.route()
                        : null);
    }
}
