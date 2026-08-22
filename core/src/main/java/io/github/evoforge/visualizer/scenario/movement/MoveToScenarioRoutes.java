package io.github.evoforge.visualizer.scenario.movement;

import io.github.evoforge.simulation.runtime.SimulationView;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.navigation.pathfinding.PathQuery;
import io.github.evoforge.simulation.world.navigation.pathfinding.PathSearch;
import io.github.evoforge.simulation.world.navigation.pathfinding.PathSearchStatus;

final class MoveToScenarioRoutes {
    private MoveToScenarioRoutes() { }

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
                goalX, goalY, goalZ);
        PathSearch search = complete(view.pathfinder().begin(query));
        return new MoveToScenarioPlan(
                goalX, goalY, goalZ,
                search.status() == PathSearchStatus.FOUND ? search.route() : null);
    }

    private static PathSearch complete(PathSearch search) {
        while (search.status() == PathSearchStatus.RUNNING) {
            search.advance(512);
        }
        return search;
    }
}
