package io.github.evoforge.visualizer.scenario;

import java.util.ArrayList;
import java.util.List;

import io.github.evoforge.simulation.runtime.SimulationView;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.pathfinding.PathRoute;
import io.github.evoforge.simulation.world.spatial.TransformLookup;

final class MoveToScenarioDiagnostics {

    private MoveToScenarioDiagnostics() {
    }

    static ScenarioDiagnostics snapshot(
            SimulationView view,
            ObjectId objectId,
            MoveToScenarioPlan plan,
            String summary) {

        List<ScenarioCellMarker> markers = new ArrayList<>();
        TransformLookup transforms = view.transforms();

        if (objectId != null && transforms.has(objectId)) {
            int x = transforms.x(objectId);
            int y = transforms.y(objectId);
            int z = transforms.z(objectId);
            markers.add(new ScenarioCellMarker(
                    x, y, z, ScenarioCellMarkerStyle.START));

            if (plan != null && plan.route() != null) {
                int next = remainingStart(plan.route(), x, y, z);
                for (int index = next; index < plan.route().size(); index++) {
                    markers.add(new ScenarioCellMarker(
                            plan.route().x(index),
                            plan.route().y(index),
                            plan.route().z(index),
                            ScenarioCellMarkerStyle.ROUTE));
                }
            }
        }

        if (plan != null) {
            markers.add(new ScenarioCellMarker(
                    plan.goalX(),
                    plan.goalY(),
                    plan.goalZ(),
                    ScenarioCellMarkerStyle.GOAL));
        }

        return new ScenarioDiagnostics(
                markers.toArray(ScenarioCellMarker[]::new),
                summary == null ? "" : summary);
    }

    private static int remainingStart(
            PathRoute route,
            int x,
            int y,
            int z) {

        if (route.sourceX() == x
                && route.sourceY() == y
                && route.sourceZ() == z) {
            return 0;
        }

        for (int index = 0; index < route.size(); index++) {
            if (route.x(index) == x
                    && route.y(index) == y
                    && route.z(index) == z) {
                return index + 1;
            }
        }
        return 0;
    }
}
