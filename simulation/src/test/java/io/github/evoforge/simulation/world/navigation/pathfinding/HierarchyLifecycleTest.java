package io.github.evoforge.simulation.world.navigation.pathfinding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import io.github.evoforge.simulation.world.geometry.TransitionMask;
import io.github.evoforge.simulation.world.navigation.traversal.TransitionCost;
import io.github.evoforge.simulation.world.navigation.traversal.TraversalChangeTracker;
import io.github.evoforge.simulation.world.navigation.NavigationLookup;
import org.junit.jupiter.api.Test;

final class HierarchyLifecycleTest {

    @Test
    void cacheUsesLocalInvalidationAndGlobalFallback() {
        Fixture fixture = new Fixture();
        PathHierarchyIndex index = fixture.index();
        index.outgoingTransitions(0, 0, 0);
        index.outgoingTransitions(10, 0, 0);
        long misses = index.metrics().cacheMisses();

        fixture.changes.changed(1, 1, 0);
        index.outgoingTransitions(10, 0, 0);
        assertEquals(misses, index.metrics().cacheMisses());
        index.outgoingTransitions(0, 0, 0);
        assertEquals(misses + 1, index.metrics().cacheMisses());
        assertTrue(index.metrics().localInvalidations() > 0);

        fixture.changes.changed(2, 1, 0);
        fixture.changes.changed(3, 1, 0);
        index.outgoingTransitions(10, 0, 0);
        assertEquals(1, index.metrics().globalInvalidations());
    }

    @Test
    void traversalAndConstraintChangesStaleSearch() {
        Fixture traversal = Fixture.line(20);
        PathSearch first = traversal.pathfinder().begin(
                PathQuery.between(0, 0, 0, 20, 0, 0));
        assertEquals(PathSearchStatus.RUNNING, first.advance(1));
        traversal.changes.changed(100, 100, 100);
        assertEquals(PathSearchStatus.STALE, first.advance(1));

        Fixture dynamic = Fixture.line(20);
        MutableConstraint constraint = new MutableConstraint();
        PathSearch second = dynamic.pathfinder().begin(
                PathQuery.between(0, 0, 0, 20, 0, 0)
                        .withConstraint(constraint));
        assertEquals(PathSearchStatus.RUNNING, second.advance(1));
        constraint.revision++;
        assertEquals(PathSearchStatus.STALE, second.advance(1));
    }

    @Test
    void cancellationIsTerminal() {
        Fixture fixture = Fixture.line(20);
        PathSearch search = fixture.pathfinder().begin(
                PathQuery.between(0, 0, 0, 20, 0, 0));
        search.advance(1);
        search.cancel();
        assertEquals(PathSearchStatus.CANCELLED, search.status());
        assertEquals(PathSearchStatus.CANCELLED, search.advance(10));
        assertThrows(IllegalStateException.class, search::route);
    }

    private static final class MutableConstraint implements PathTransitionConstraint {
        private long revision;
        @Override public boolean allows(int fx, int fy, int fz, int tx, int ty, int tz) { return true; }
        @Override public long revision() { return revision; }
    }

    private record Cell(int x) { }

    private static final class Fixture {
        private final Map<Cell, Integer> transitions = new HashMap<>();
        private final TraversalChangeTracker changes = new TraversalChangeTracker();

        static Fixture line(int length) {
            Fixture fixture = new Fixture();
            for (int x = 0; x < length; x++) fixture.edge(x);
            return fixture;
        }

        void edge(int x) {
            transitions.merge(new Cell(x), TransitionMask.of(1, 0, 0), (a, b) -> a | b);
        }

        NavigationLookup navigation() {
            return (x, y, z) -> transitions.getOrDefault(new Cell(x), TransitionMask.NONE);
        }

        PathHierarchyIndex index() {
            return new PathHierarchyIndex(
                    navigation(), changes, new PathHierarchyConfig(4, 4, 4));
        }

        HierarchicalPathfinder pathfinder() {
            NavigationLookup navigation = navigation();
            var costs =
                    (io.github.evoforge.simulation.world.navigation.traversal.TransitionCostLookup)
                            (fx, fy, fz, tx, ty, tz) -> TransitionCost.of(1000);
            ExactAStarPathfinder exact = new ExactAStarPathfinder(
                    navigation, costs, changes, PathHeuristics.chebyshev(1000));
            return new HierarchicalPathfinder(index(), exact);
        }
    }
}
