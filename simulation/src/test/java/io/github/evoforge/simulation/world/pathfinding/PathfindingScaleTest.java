package io.github.evoforge.simulation.world.pathfinding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.mechanics.geometry.TransitionMask;
import io.github.evoforge.simulation.world.mechanics.traversal.TransitionCost;
import io.github.evoforge.simulation.world.navigation.NavigationLookup;
import org.junit.jupiter.api.Test;

final class PathfindingScaleTest {

    @Test
    void admissibleHeuristicCutsOpenGridExpansionsWithoutChangingCost() {
        int max = 63;
        NavigationLookup navigation = (x, y, z) -> {
            if (z != 0 || x < 0 || x > max || y < 0 || y > max) {
                return TransitionMask.NONE;
            }
            int mask = TransitionMask.NONE;
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dy == 0) continue;
                    int tx = x + dx;
                    int ty = y + dy;
                    if (tx >= 0 && tx <= max && ty >= 0 && ty <= max) {
                        mask |= TransitionMask.of(dx, dy, 0);
                    }
                }
            }
            return mask;
        };
        var costs =
                (io.github.evoforge.simulation.world.mechanics.traversal.TransitionCostLookup)
                        (fx, fy, fz, tx, ty, tz) -> TransitionCost.of(1000);

        ExactAStarPathfinder dijkstra = new ExactAStarPathfinder(
                navigation, costs, () -> 0L, PathHeuristics.ZERO);
        ExactAStarPathfinder informed = new ExactAStarPathfinder(
                navigation, costs, () -> 0L, PathHeuristics.chebyshev(1000));

        PathSearch baseline = dijkstra.begin(
                PathQuery.between(0, 0, 0, max, max, 0));
        PathSearch accelerated = informed.begin(
                PathQuery.between(0, 0, 0, max, max, 0));
        complete(baseline);
        complete(accelerated);

        assertEquals(PathSearchStatus.FOUND, baseline.status());
        assertEquals(PathSearchStatus.FOUND, accelerated.status());
        assertEquals(
                baseline.route().totalCostUnits(),
                accelerated.route().totalCostUnits());
        assertTrue(
                accelerated.metrics().expandedNodes() * 8
                        < baseline.metrics().expandedNodes(),
                "expected strong lower-bound heuristic to reduce open-grid expansions");
    }

    private static void complete(PathSearch search) {
        while (search.status() == PathSearchStatus.RUNNING) {
            search.advance(4096);
        }
    }
}
