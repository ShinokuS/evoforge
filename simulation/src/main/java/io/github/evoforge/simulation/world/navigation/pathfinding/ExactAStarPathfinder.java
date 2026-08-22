package io.github.evoforge.simulation.world.navigation.pathfinding;

import io.github.evoforge.simulation.world.geometry.TransitionDirections;
import io.github.evoforge.simulation.world.navigation.traversal.TransitionCostLookup;
import io.github.evoforge.simulation.world.navigation.traversal.TraversalRevisionLookup;
import io.github.evoforge.simulation.world.navigation.NavigationLookup;

/** Exact deterministic A* over authoritative Navigation and TransitionCost facts. */
public final class ExactAStarPathfinder implements Pathfinder {

    private static final byte OPEN = 1;
    private static final byte CLOSED = 2;

    private final NavigationLookup navigation;
    private final TransitionCostLookup transitionCosts;
    private final TraversalRevisionLookup revisions;
    private final PathHeuristic heuristic;
    private final AStarWorkspace.Pool workspaces = new AStarWorkspace.Pool();

    public ExactAStarPathfinder(
            NavigationLookup navigation,
            TransitionCostLookup transitionCosts,
            TraversalRevisionLookup revisions,
            PathHeuristic heuristic) {
        if (navigation == null) {
            throw new IllegalArgumentException("navigation must not be null");
        }
        if (transitionCosts == null) {
            throw new IllegalArgumentException("transitionCosts must not be null");
        }
        if (revisions == null) {
            throw new IllegalArgumentException("revisions must not be null");
        }
        if (heuristic == null) {
            throw new IllegalArgumentException("heuristic must not be null");
        }
        this.navigation = navigation;
        this.transitionCosts = transitionCosts;
        this.revisions = revisions;
        this.heuristic = heuristic;
    }

    @Override
    public PathSearch begin(PathQuery query) {
        if (query == null) {
            throw new IllegalArgumentException("query must not be null");
        }
        if (query.fromX() == query.toX()
                && query.fromY() == query.toY()
                && query.fromZ() == query.toZ()) {
            return new CompletedSearch(
                    PathRoute.empty(query.fromX(), query.fromY(), query.fromZ()));
        }
        AStarWorkspace workspace = workspaces.acquire();
        try {
            return new Search(workspace, query);
        } catch (RuntimeException failure) {
            workspaces.release(workspace);
            throw failure;
        }
    }

    private final class Search implements PathSearch {
        private final AStarWorkspace workspace;
        private final PathQuery query;
        private final long initialTraversalRevision;
        private final long initialConstraintRevision;
        private PathSearchStatus status = PathSearchStatus.RUNNING;
        private PathRoute route;
        private long expandedNodes;
        private long generatedTransitions;
        private long relaxedNodes;
        private long reopenedNodes;
        private int peakFrontier;
        private boolean released;

        private Search(AStarWorkspace workspace, PathQuery query) {
            this.workspace = workspace;
            this.query = query;
            initialTraversalRevision = revisions.revision();
            initialConstraintRevision = query.constraint().revision();
            int start = workspace.addNode(query.fromX(), query.fromY(), query.fromZ());
            workspace.g[start] = 0L;
            workspace.h[start] = estimate(query.fromX(), query.fromY(), query.fromZ());
            workspace.state[start] = OPEN;
            workspace.pushOrDecrease(start);
            peakFrontier = 1;
        }

        @Override public PathSearchStatus status() { return status; }

        @Override
        public PathSearchStatus advance(int expansionBudget) {
            requireBudget(expansionBudget);
            if (status != PathSearchStatus.RUNNING) return status;
            if (stale()) {
                finish(PathSearchStatus.STALE, null);
                return status;
            }
            int expandedThisCall = 0;
            while (expandedThisCall < expansionBudget && workspace.heapSize > 0) {
                int current = workspace.popBest();
                workspace.state[current] = CLOSED;
                expandedNodes++;
                expandedThisCall++;
                int x = workspace.x[current];
                int y = workspace.y[current];
                int z = workspace.z[current];
                if (x == query.toX() && y == query.toY() && z == query.toZ()) {
                    finish(PathSearchStatus.FOUND, buildRoute(current));
                    return status;
                }
                expand(current, x, y, z);
            }
            if (stale()) {
                finish(PathSearchStatus.STALE, null);
            } else if (workspace.heapSize == 0) {
                finish(PathSearchStatus.NO_PATH, null);
            }
            return status;
        }

        @Override
        public void cancel() {
            if (status == PathSearchStatus.RUNNING) {
                finish(PathSearchStatus.CANCELLED, null);
            }
        }

        @Override
        public PathRoute route() {
            if (status != PathSearchStatus.FOUND) {
                throw new IllegalStateException("route is available only for FOUND search");
            }
            return route;
        }

        @Override
        public PathSearchMetrics metrics() {
            return new PathSearchMetrics(
                    expandedNodes, generatedTransitions, relaxedNodes, reopenedNodes, peakFrontier);
        }

        private boolean stale() {
            return revisions.revision() != initialTraversalRevision
                    || query.constraint().revision() != initialConstraintRevision;
        }

        private void expand(int current, int x, int y, int z) {
            int transitions = navigation.transitions(x, y, z);
            for (int direction = 0; direction < TransitionDirections.COUNT; direction++) {
                if ((transitions & TransitionDirections.mask(direction)) == 0) continue;
                generatedTransitions++;
                int toX = Math.addExact(x, TransitionDirections.dx(direction));
                int toY = Math.addExact(y, TransitionDirections.dy(direction));
                int toZ = Math.addExact(z, TransitionDirections.dz(direction));
                if (!query.constraint().allows(x, y, z, toX, toY, toZ)) continue;
                long edgeCost = transitionCosts.cost(x, y, z, toX, toY, toZ).units();
                long candidateG = Math.addExact(workspace.g[current], edgeCost);
                int next = workspace.findNode(toX, toY, toZ);
                if (next < 0) {
                    next = workspace.addNode(toX, toY, toZ);
                    workspace.h[next] = estimate(toX, toY, toZ);
                }
                if (candidateG >= workspace.g[next]) continue;
                boolean wasClosed = workspace.state[next] == CLOSED;
                workspace.g[next] = candidateG;
                workspace.parent[next] = current;
                workspace.state[next] = OPEN;
                workspace.pushOrDecrease(next);
                relaxedNodes++;
                if (wasClosed) reopenedNodes++;
                peakFrontier = Math.max(peakFrontier, workspace.heapSize);
            }
        }

        private long estimate(int x, int y, int z) {
            long estimate = heuristic.estimate(x, y, z, query.toX(), query.toY(), query.toZ());
            if (estimate < 0) {
                throw new IllegalStateException("path heuristic must not return a negative estimate");
            }
            return estimate;
        }

        private PathRoute buildRoute(int goal) {
            int length = 0;
            int cursor = goal;
            while (workspace.parent[cursor] >= 0) {
                length++;
                cursor = workspace.parent[cursor];
            }
            int[] xs = new int[length];
            int[] ys = new int[length];
            int[] zs = new int[length];
            cursor = goal;
            for (int index = length - 1; index >= 0; index--) {
                xs[index] = workspace.x[cursor];
                ys[index] = workspace.y[cursor];
                zs[index] = workspace.z[cursor];
                cursor = workspace.parent[cursor];
            }
            return new PathRoute(
                    query.fromX(), query.fromY(), query.fromZ(),
                    query.toX(), query.toY(), query.toZ(),
                    workspace.g[goal], xs, ys, zs);
        }

        private void finish(PathSearchStatus terminal, PathRoute terminalRoute) {
            status = terminal;
            route = terminalRoute;
            if (!released) {
                released = true;
                workspaces.release(workspace);
            }
        }
    }

    private static final class CompletedSearch implements PathSearch {
        private final PathRoute route;
        private CompletedSearch(PathRoute route) { this.route = route; }
        @Override public PathSearchStatus status() { return PathSearchStatus.FOUND; }
        @Override public PathSearchStatus advance(int expansionBudget) {
            requireBudget(expansionBudget);
            return PathSearchStatus.FOUND;
        }
        @Override public void cancel() { }
        @Override public PathRoute route() { return route; }
        @Override public PathSearchMetrics metrics() {
            return new PathSearchMetrics(0L, 0L, 0L, 0L, 0);
        }
    }

    private static void requireBudget(int expansionBudget) {
        if (expansionBudget <= 0) {
            throw new IllegalArgumentException("expansionBudget must be > 0");
        }
    }
}
