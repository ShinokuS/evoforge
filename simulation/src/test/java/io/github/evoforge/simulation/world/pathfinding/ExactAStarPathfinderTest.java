package io.github.evoforge.simulation.world.pathfinding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;

import io.github.evoforge.simulation.world.mechanics.geometry.TransitionMask;
import io.github.evoforge.simulation.world.mechanics.traversal.TransitionCost;
import io.github.evoforge.simulation.world.mechanics.traversal.TransitionCostLookup;
import io.github.evoforge.simulation.world.navigation.NavigationLookup;
import org.junit.jupiter.api.Test;

final class ExactAStarPathfinderTest {

    @Test
    void findsCheapestRouteRatherThanFewestSteps() {
        Graph graph = new Graph();
        graph.edge(0, 0, 0, 1, 0, 0, 4000);
        graph.edge(1, 0, 0, 2, 0, 0, 4000);
        graph.edge(0, 0, 0, 0, 1, 0, 1000);
        graph.edge(0, 1, 0, 1, 1, 0, 1000);
        graph.edge(1, 1, 0, 2, 0, 0, 1000);

        PathSearch search = graph.pathfinder().begin(
                PathQuery.between(0, 0, 0, 2, 0, 0));

        assertEquals(PathSearchStatus.FOUND, complete(search));
        PathRoute route = search.route();
        assertEquals(3, route.size());
        assertEquals(3000, route.totalCostUnits());
        assertEquals(0, route.x(0));
        assertEquals(1, route.y(0));
        assertEquals(2, route.x(2));
        assertEquals(0, route.y(2));
    }

    @Test
    void resumesByDeterministicExpansionBudget() {
        Graph graph = new Graph();
        for (int x = 0; x < 8; x++) {
            graph.edge(x, 0, 0, x + 1, 0, 0, 1000);
        }

        PathSearch search = graph.pathfinder().begin(
                PathQuery.between(0, 0, 0, 8, 0, 0));

        assertEquals(PathSearchStatus.RUNNING, search.advance(1));
        assertEquals(1, search.metrics().expandedNodes());
        assertEquals(PathSearchStatus.FOUND, complete(search));
        assertEquals(8, search.route().size());
    }

    @Test
    void becomesStaleWhenTraversalFactsChangeBetweenSlices() {
        Graph graph = new Graph();
        graph.edge(0, 0, 0, 1, 0, 0, 1000);
        graph.edge(1, 0, 0, 2, 0, 0, 1000);

        PathSearch search = graph.pathfinder().begin(
                PathQuery.between(0, 0, 0, 2, 0, 0));
        assertEquals(PathSearchStatus.RUNNING, search.advance(1));
        graph.revision++;

        assertEquals(PathSearchStatus.STALE, search.advance(1));
        assertThrows(IllegalStateException.class, search::route);
    }

    @Test
    void becomesStaleWhenDynamicConstraintChangesBetweenSlices() {
        Graph graph = new Graph();
        for (int x = 0; x < 4; x++) {
            graph.edge(x, 0, 0, x + 1, 0, 0, 1000);
        }
        MutableConstraint constraint = new MutableConstraint();
        PathSearch search = graph.pathfinder().begin(
                PathQuery.between(0, 0, 0, 4, 0, 0)
                        .withConstraint(constraint));

        assertEquals(PathSearchStatus.RUNNING, search.advance(1));
        constraint.revision++;

        assertEquals(PathSearchStatus.STALE, search.advance(1));
    }

    @Test
    void cancellationIsTerminalAndRouteUnavailable() {
        Graph graph = new Graph();
        for (int x = 0; x < 20; x++) {
            graph.edge(x, 0, 0, x + 1, 0, 0, 1000);
        }
        PathSearch search = graph.pathfinder().begin(
                PathQuery.between(0, 0, 0, 20, 0, 0));

        search.advance(1);
        search.cancel();

        assertEquals(PathSearchStatus.CANCELLED, search.status());
        assertEquals(PathSearchStatus.CANCELLED, search.advance(10));
        assertThrows(IllegalStateException.class, search::route);
    }

    @Test
    void reportsNoPathAsStructuredTerminalStatus() {
        Graph graph = new Graph();
        graph.edge(0, 0, 0, 1, 0, 0, 1000);
        PathSearch search = graph.pathfinder().begin(
                PathQuery.between(0, 0, 0, 10, 0, 0));

        assertEquals(PathSearchStatus.NO_PATH, complete(search));
        assertThrows(IllegalStateException.class, search::route);
    }

    @Test
    void startAtGoalReturnsEmptySuccessfulRoute() {
        Graph graph = new Graph();
        PathSearch search = graph.pathfinder().begin(
                PathQuery.between(4, -2, 7, 4, -2, 7));

        assertEquals(PathSearchStatus.FOUND, search.status());
        assertEquals(0, search.route().size());
        assertEquals(0, search.route().totalCostUnits());
    }

    @Test
    void queryConstraintCanRejectOtherwiseStructuralEdge() {
        Graph graph = new Graph();
        graph.edge(0, 0, 0, 1, 0, 0, 1000);
        graph.edge(1, 0, 0, 2, 0, 0, 1000);
        PathQuery query = PathQuery.between(0, 0, 0, 2, 0, 0)
                .withConstraint(
                        (fromX, fromY, fromZ, toX, toY, toZ) -> toX != 1);

        assertEquals(
                PathSearchStatus.NO_PATH,
                complete(graph.pathfinder().begin(query)));
    }

    @Test
    void equalCostTieBreakIsStable() {
        Graph graph = new Graph();
        graph.edge(0, 0, 0, 1, -1, 0, 1000);
        graph.edge(1, -1, 0, 2, 0, 0, 1000);
        graph.edge(0, 0, 0, 1, 1, 0, 1000);
        graph.edge(1, 1, 0, 2, 0, 0, 1000);

        for (int attempt = 0; attempt < 20; attempt++) {
            PathSearch search = graph.pathfinder().begin(
                    PathQuery.between(0, 0, 0, 2, 0, 0));
            assertEquals(PathSearchStatus.FOUND, complete(search));
            assertEquals(-1, search.route().y(0));
        }
    }

    private static PathSearchStatus complete(PathSearch search) {
        while (search.status() == PathSearchStatus.RUNNING) {
            search.advance(64);
        }
        return search.status();
    }

    private record Cell(int x, int y, int z) { }
    private record Edge(Cell from, Cell to) { }

    private static final class MutableConstraint
            implements PathTransitionConstraint {
        private long revision;

        @Override
        public boolean allows(
                int fromX,
                int fromY,
                int fromZ,
                int toX,
                int toY,
                int toZ) {
            return true;
        }

        @Override
        public long revision() {
            return revision;
        }
    }

    private static final class Graph {
        private final Map<Cell, Integer> transitions = new HashMap<>();
        private final Map<Edge, Long> costs = new HashMap<>();
        private long revision;

        void edge(
                int fromX,
                int fromY,
                int fromZ,
                int toX,
                int toY,
                int toZ,
                long cost) {
            int dx = toX - fromX;
            int dy = toY - fromY;
            int dz = toZ - fromZ;
            Cell from = new Cell(fromX, fromY, fromZ);
            Cell to = new Cell(toX, toY, toZ);
            transitions.merge(
                    from,
                    TransitionMask.of(dx, dy, dz),
                    (left, right) -> left | right);
            costs.put(new Edge(from, to), cost);
        }

        ExactAStarPathfinder pathfinder() {
            NavigationLookup navigation =
                    (x, y, z) -> transitions.getOrDefault(
                            new Cell(x, y, z),
                            TransitionMask.NONE);
            TransitionCostLookup transitionCosts =
                    (fromX, fromY, fromZ, toX, toY, toZ) ->
                            TransitionCost.of(
                                    costs.get(new Edge(
                                            new Cell(fromX, fromY, fromZ),
                                            new Cell(toX, toY, toZ))));
            return new ExactAStarPathfinder(
                    navigation,
                    transitionCosts,
                    () -> revision,
                    PathHeuristics.chebyshev(1));
        }
    }
}
