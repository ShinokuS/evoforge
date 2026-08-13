package io.github.evoforge.visualizer.scenario;

import java.util.ArrayList;
import java.util.List;

import io.github.evoforge.simulation.world.pathfinding.PathQuery;
import io.github.evoforge.simulation.world.pathfinding.PathRoute;
import io.github.evoforge.simulation.world.pathfinding.PathSearch;
import io.github.evoforge.simulation.world.pathfinding.PathSearchMetrics;
import io.github.evoforge.simulation.world.pathfinding.PathSearchStatus;

/** Shared presentation helper for focused pathfinding scenarios, not a scenario DSL. */
final class PathfindingScenarioDiagnostics {

    private PathfindingScenarioDiagnostics() {
    }

    static PathSearch complete(
            PathSearch search) {

        while (search.status() == PathSearchStatus.RUNNING) {
            search.advance(512);
        }
        return search;
    }

    static ScenarioDiagnostics fromSearch(
            PathQuery query,
            PathSearch search,
            ScenarioCellMarker... extraMarkers) {

        List<ScenarioCellMarker> markers =
                new ArrayList<>();

        if (extraMarkers != null) {
            for (ScenarioCellMarker marker : extraMarkers) {
                if (marker != null) {
                    markers.add(marker);
                }
            }
        }

        if (search.status() == PathSearchStatus.FOUND) {
            PathRoute route = search.route();
            for (int index = 0; index < route.size(); index++) {
                markers.add(new ScenarioCellMarker(
                        route.x(index),
                        route.y(index),
                        route.z(index),
                        ScenarioCellMarkerStyle.ROUTE));
            }
        }

        markers.add(new ScenarioCellMarker(
                query.fromX(),
                query.fromY(),
                query.fromZ(),
                ScenarioCellMarkerStyle.START));
        markers.add(new ScenarioCellMarker(
                query.toX(),
                query.toY(),
                query.toZ(),
                ScenarioCellMarkerStyle.GOAL));

        return new ScenarioDiagnostics(
                markers.toArray(ScenarioCellMarker[]::new),
                summary(search));
    }

    private static String summary(
            PathSearch search) {

        PathSearchMetrics metrics = search.metrics();
        StringBuilder text = new StringBuilder()
                .append("status=")
                .append(search.status());

        if (search.status() == PathSearchStatus.FOUND) {
            text.append(" | steps=")
                    .append(search.route().size())
                    .append(" | cost=")
                    .append(search.route().totalCostUnits());
        }

        return text.append(" | expanded=")
                .append(metrics.expandedNodes())
                .append(" | generated=")
                .append(metrics.generatedTransitions())
                .append(" | frontier=")
                .append(metrics.peakFrontier())
                .toString();
    }
}
