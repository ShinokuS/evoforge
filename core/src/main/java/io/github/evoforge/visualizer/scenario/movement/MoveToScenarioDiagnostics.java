package io.github.evoforge.visualizer.scenario.movement;

import io.github.evoforge.simulation.runtime.SimulationView;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.navigation.pathfinding.PathRoute;
import io.github.evoforge.simulation.world.space.position.PositionLookup;
import io.github.evoforge.visualizer.presentation.route.RoutePresentation;
import io.github.evoforge.visualizer.scenario.ScenarioCellMarker;
import io.github.evoforge.visualizer.scenario.ScenarioCellMarkerStyle;
import io.github.evoforge.visualizer.scenario.ScenarioDiagnostics;
import java.util.ArrayList;
import java.util.List;

final class MoveToScenarioDiagnostics {
    private MoveToScenarioDiagnostics() { }

    static ScenarioDiagnostics snapshot(
            SimulationView view,
            ObjectId objectId,
            MoveToScenarioPlan plan,
            String summary) {
        List<ScenarioCellMarker> markers = new ArrayList<>();
        RoutePresentation routePresentation = RoutePresentation.EMPTY;
        PositionLookup transforms = view.positions();
        if (objectId != null && transforms.has(objectId)) {
            int x = transforms.x(objectId);
            int y = transforms.y(objectId);
            int z = transforms.z(objectId);
            markers.add(new ScenarioCellMarker(x, y, z, ScenarioCellMarkerStyle.START));
            if (plan != null && plan.route() != null) {
                PathRoute route = plan.route();
                int next = remainingStart(route, x, y, z);
                routePresentation = remainingRoute(route, x, y, z, next);
                for (int index = next; index < route.size(); index++) {
                    int routeX = route.x(index);
                    int routeY = route.y(index);
                    int routeZ = route.z(index);
                    if (view.cells().objectCount(routeX, routeY, routeZ) > 0) {
                        markers.add(new ScenarioCellMarker(
                                routeX, routeY, routeZ, ScenarioCellMarkerStyle.WARNING));
                    }
                }
            }
        }
        if (plan != null) {
            markers.add(new ScenarioCellMarker(
                    plan.goalX(), plan.goalY(), plan.goalZ(), ScenarioCellMarkerStyle.GOAL));
        }
        return new ScenarioDiagnostics(
                markers.toArray(ScenarioCellMarker[]::new),
                routePresentation,
                summary == null ? "" : summary);
    }

    private static RoutePresentation remainingRoute(
            PathRoute route,
            int currentX,
            int currentY,
            int currentZ,
            int next) {
        int count = 1 + route.size() - next;
        int[] xs = new int[count];
        int[] ys = new int[count];
        int[] zs = new int[count];
        xs[0] = currentX;
        ys[0] = currentY;
        zs[0] = currentZ;
        for (int index = next; index < route.size(); index++) {
            int target = index - next + 1;
            xs[target] = route.x(index);
            ys[target] = route.y(index);
            zs[target] = route.z(index);
        }
        return RoutePresentation.of(xs, ys, zs);
    }

    private static int remainingStart(PathRoute route, int x, int y, int z) {
        if (route.sourceX() == x && route.sourceY() == y && route.sourceZ() == z) return 0;
        for (int index = 0; index < route.size(); index++) {
            if (route.x(index) == x && route.y(index) == y && route.z(index) == z) {
                return index + 1;
            }
        }
        return 0;
    }
}
