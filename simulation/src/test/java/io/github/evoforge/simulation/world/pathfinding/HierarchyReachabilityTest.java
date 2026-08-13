package io.github.evoforge.simulation.world.pathfinding;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;

import io.github.evoforge.simulation.world.mechanics.geometry.TransitionMask;
import io.github.evoforge.simulation.world.mechanics.traversal.TransitionCost;
import io.github.evoforge.simulation.world.mechanics.traversal.TraversalChangeTracker;
import io.github.evoforge.simulation.world.navigation.NavigationLookup;
import org.junit.jupiter.api.Test;

final class HierarchyReachabilityTest {

    @Test
    void coarseNoPathAvoidsExactCostSearch() {
        Fixture fixture = new Fixture();
        for (int x = 0; x < 4; x++) fixture.edge(x, x + 1);
        for (int x = 8; x < 12; x++) fixture.edge(x, x + 1);
        PathSearch search = fixture.pathfinder().begin(
                PathQuery.between(0, 0, 0, 12, 0, 0));
        complete(search);
        assertEquals(PathSearchStatus.NO_PATH, search.status());
        assertEquals(0, fixture.costQueries);
    }

    @Test
    void reachableCoarseRouteIsStillRefinedExactly() {
        Fixture fixture = new Fixture();
        for (int x = 0; x < 12; x++) fixture.edge(x, x + 1);
        PathSearch search = fixture.pathfinder().begin(
                PathQuery.between(0, 0, 0, 12, 0, 0));
        complete(search);
        assertEquals(PathSearchStatus.FOUND, search.status());
        assertEquals(12000, search.route().totalCostUnits());
        assertEquals(12, fixture.costQueries);
    }

    private static void complete(PathSearch search) {
        while (search.status() == PathSearchStatus.RUNNING) search.advance(64);
    }

    private record Cell(int x) { }
    private record Edge(int from, int to) { }

    private static final class Fixture {
        private final Map<Cell, Integer> transitions = new HashMap<>();
        private final Map<Edge, Long> costs = new HashMap<>();
        private final TraversalChangeTracker changes = new TraversalChangeTracker();
        private int costQueries;

        void edge(int from, int to) {
            transitions.merge(new Cell(from), TransitionMask.of(1, 0, 0), (a, b) -> a | b);
            costs.put(new Edge(from, to), 1000L);
        }

        HierarchicalPathfinder pathfinder() {
            NavigationLookup navigation =
                    (x, y, z) -> transitions.getOrDefault(new Cell(x), TransitionMask.NONE);
            var transitionCosts =
                    (io.github.evoforge.simulation.world.mechanics.traversal.TransitionCostLookup)
                            (fx, fy, fz, tx, ty, tz) -> {
                                costQueries++;
                                return TransitionCost.of(costs.get(new Edge(fx, tx)));
                            };
            ExactAStarPathfinder exact = new ExactAStarPathfinder(
                    navigation, transitionCosts, changes, PathHeuristics.chebyshev(1000));
            return new HierarchicalPathfinder(
                    new PathHierarchyIndex(
                            navigation, changes, new PathHierarchyConfig(4, 4, 4)),
                    exact);
        }
    }
}
