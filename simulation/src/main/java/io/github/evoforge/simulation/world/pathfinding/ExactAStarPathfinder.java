package io.github.evoforge.simulation.world.pathfinding;

import java.util.ArrayDeque;
import java.util.Arrays;

import io.github.evoforge.simulation.world.mechanics.geometry.TransitionDirections;
import io.github.evoforge.simulation.world.mechanics.traversal.TransitionCostLookup;
import io.github.evoforge.simulation.world.mechanics.traversal.TraversalRevisionLookup;
import io.github.evoforge.simulation.world.navigation.NavigationLookup;

/** Exact deterministic A* over authoritative Navigation and TransitionCost facts. */
public final class ExactAStarPathfinder implements Pathfinder {

    private final NavigationLookup navigation;
    private final TransitionCostLookup transitionCosts;
    private final TraversalRevisionLookup revisions;
    private final PathHeuristic heuristic;
    private final WorkspacePool workspaces = new WorkspacePool();

    public ExactAStarPathfinder(
            NavigationLookup navigation,
            TransitionCostLookup transitionCosts,
            TraversalRevisionLookup revisions,
            PathHeuristic heuristic) {

        if (navigation == null) {
            throw new IllegalArgumentException(
                    "navigation must not be null");
        }
        if (transitionCosts == null) {
            throw new IllegalArgumentException(
                    "transitionCosts must not be null");
        }
        if (revisions == null) {
            throw new IllegalArgumentException(
                    "revisions must not be null");
        }
        if (heuristic == null) {
            throw new IllegalArgumentException(
                    "heuristic must not be null");
        }

        this.navigation = navigation;
        this.transitionCosts = transitionCosts;
        this.revisions = revisions;
        this.heuristic = heuristic;
    }

    @Override
    public PathSearch begin(
            PathQuery query) {

        if (query == null) {
            throw new IllegalArgumentException(
                    "query must not be null");
        }

        if (query.fromX() == query.toX()
                && query.fromY() == query.toY()
                && query.fromZ() == query.toZ()) {
            return new CompletedSearch(
                    PathRoute.empty(
                            query.fromX(),
                            query.fromY(),
                            query.fromZ()));
        }

        Workspace workspace = workspaces.acquire();

        try {
            return new Search(
                    navigation,
                    transitionCosts,
                    revisions,
                    heuristic,
                    workspaces,
                    workspace,
                    query);
        } catch (RuntimeException failure) {
            workspaces.release(workspace);
            throw failure;
        }
    }

    private static final class CompletedSearch
            implements PathSearch {

        private final PathRoute route;

        private CompletedSearch(
                PathRoute route) {
            this.route = route;
        }

        @Override
        public PathSearchStatus status() {
            return PathSearchStatus.FOUND;
        }

        @Override
        public PathSearchStatus advance(
                int expansionBudget) {
            requireBudget(expansionBudget);
            return PathSearchStatus.FOUND;
        }

        @Override
        public PathRoute route() {
            return route;
        }

        @Override
        public PathSearchMetrics metrics() {
            return new PathSearchMetrics(
                    0L, 0L, 0L, 0L, 0);
        }
    }

    private static final class Search
            implements PathSearch {

        private static final byte OPEN = 1;
        private static final byte CLOSED = 2;

        private final NavigationLookup navigation;
        private final TransitionCostLookup transitionCosts;
        private final TraversalRevisionLookup revisions;
        private final PathHeuristic heuristic;
        private final WorkspacePool pool;
        private final Workspace workspace;
        private final PathQuery query;
        private final long initialRevision;

        private PathSearchStatus status = PathSearchStatus.RUNNING;
        private PathRoute route;
        private long expandedNodes;
        private long generatedTransitions;
        private long relaxedNodes;
        private long reopenedNodes;
        private int peakFrontier;
        private boolean released;

        private Search(
                NavigationLookup navigation,
                TransitionCostLookup transitionCosts,
                TraversalRevisionLookup revisions,
                PathHeuristic heuristic,
                WorkspacePool pool,
                Workspace workspace,
                PathQuery query) {

            this.navigation = navigation;
            this.transitionCosts = transitionCosts;
            this.revisions = revisions;
            this.heuristic = heuristic;
            this.pool = pool;
            this.workspace = workspace;
            this.query = query;
            initialRevision = revisions.revision();

            int start = workspace.addNode(
                    query.fromX(),
                    query.fromY(),
                    query.fromZ());
            workspace.g[start] = 0L;
            workspace.h[start] = estimate(
                    query.fromX(),
                    query.fromY(),
                    query.fromZ());
            workspace.state[start] = OPEN;
            workspace.pushOrDecrease(start);
            peakFrontier = 1;
        }

        @Override
        public PathSearchStatus status() {
            return status;
        }

        @Override
        public PathSearchStatus advance(
                int expansionBudget) {

            requireBudget(expansionBudget);

            if (status != PathSearchStatus.RUNNING) {
                return status;
            }

            if (revisions.revision() != initialRevision) {
                finish(PathSearchStatus.STALE, null);
                return status;
            }

            int expandedThisCall = 0;

            while (expandedThisCall < expansionBudget
                    && workspace.heapSize > 0) {

                int current = workspace.popBest();
                workspace.state[current] = CLOSED;
                expandedNodes++;
                expandedThisCall++;

                int x = workspace.x[current];
                int y = workspace.y[current];
                int z = workspace.z[current];

                if (x == query.toX()
                        && y == query.toY()
                        && z == query.toZ()) {
                    finish(
                            PathSearchStatus.FOUND,
                            buildRoute(current));
                    return status;
                }

                expand(current, x, y, z);
            }

            if (revisions.revision() != initialRevision) {
                finish(PathSearchStatus.STALE, null);
            } else if (workspace.heapSize == 0) {
                finish(PathSearchStatus.NO_PATH, null);
            }

            return status;
        }

        @Override
        public PathRoute route() {
            if (status != PathSearchStatus.FOUND) {
                throw new IllegalStateException(
                        "route is available only for FOUND search");
            }
            return route;
        }

        @Override
        public PathSearchMetrics metrics() {
            return new PathSearchMetrics(
                    expandedNodes,
                    generatedTransitions,
                    relaxedNodes,
                    reopenedNodes,
                    peakFrontier);
        }

        private void expand(
                int current,
                int x,
                int y,
                int z) {

            int transitions = navigation.transitions(x, y, z);

            for (int direction = 0;
                    direction < TransitionDirections.COUNT;
                    direction++) {

                if ((transitions & TransitionDirections.mask(direction)) == 0) {
                    continue;
                }

                generatedTransitions++;

                int toX = Math.addExact(
                        x,
                        TransitionDirections.dx(direction));
                int toY = Math.addExact(
                        y,
                        TransitionDirections.dy(direction));
                int toZ = Math.addExact(
                        z,
                        TransitionDirections.dz(direction));

                if (!query.constraint().allows(
                        x, y, z,
                        toX, toY, toZ)) {
                    continue;
                }

                long edgeCost = transitionCosts.cost(
                        x, y, z,
                        toX, toY, toZ)
                        .units();
                long candidateG = Math.addExact(
                        workspace.g[current],
                        edgeCost);

                int next = workspace.findNode(
                        toX,
                        toY,
                        toZ);

                if (next < 0) {
                    next = workspace.addNode(
                            toX,
                            toY,
                            toZ);
                    workspace.h[next] = estimate(
                            toX,
                            toY,
                            toZ);
                }

                if (candidateG >= workspace.g[next]) {
                    continue;
                }

                boolean wasClosed = workspace.state[next] == CLOSED;
                workspace.g[next] = candidateG;
                workspace.parent[next] = current;
                workspace.state[next] = OPEN;
                workspace.pushOrDecrease(next);
                relaxedNodes++;

                if (wasClosed) {
                    reopenedNodes++;
                }

                peakFrontier = Math.max(
                        peakFrontier,
                        workspace.heapSize);
            }
        }

        private long estimate(
                int x,
                int y,
                int z) {

            long estimate = heuristic.estimate(
                    x,
                    y,
                    z,
                    query.toX(),
                    query.toY(),
                    query.toZ());

            if (estimate < 0) {
                throw new IllegalStateException(
                        "path heuristic must not return a negative estimate");
            }

            return estimate;
        }

        private PathRoute buildRoute(
                int goal) {

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
                    query.fromX(),
                    query.fromY(),
                    query.fromZ(),
                    query.toX(),
                    query.toY(),
                    query.toZ(),
                    workspace.g[goal],
                    xs,
                    ys,
                    zs);
        }

        private void finish(
                PathSearchStatus terminalStatus,
                PathRoute terminalRoute) {

            status = terminalStatus;
            route = terminalRoute;

            if (!released) {
                released = true;
                pool.release(workspace);
            }
        }
    }

    private static final class WorkspacePool {

        private final ArrayDeque<Workspace> available =
                new ArrayDeque<>();

        private Workspace acquire() {
            Workspace workspace = available.pollFirst();
            if (workspace == null) {
                workspace = new Workspace();
            }
            workspace.reset();
            return workspace;
        }

        private void release(
                Workspace workspace) {
            available.addFirst(workspace);
        }
    }

    private static final class Workspace {

        private static final int INITIAL_NODE_CAPACITY = 256;
        private static final int INITIAL_MAP_CAPACITY = 512;

        private int[] x = new int[INITIAL_NODE_CAPACITY];
        private int[] y = new int[INITIAL_NODE_CAPACITY];
        private int[] z = new int[INITIAL_NODE_CAPACITY];
        private long[] g = new long[INITIAL_NODE_CAPACITY];
        private long[] h = new long[INITIAL_NODE_CAPACITY];
        private int[] parent = new int[INITIAL_NODE_CAPACITY];
        private byte[] state = new byte[INITIAL_NODE_CAPACITY];
        private int[] heap = new int[INITIAL_NODE_CAPACITY];
        private int[] heapPosition = new int[INITIAL_NODE_CAPACITY];

        private int[] mapX = new int[INITIAL_MAP_CAPACITY];
        private int[] mapY = new int[INITIAL_MAP_CAPACITY];
        private int[] mapZ = new int[INITIAL_MAP_CAPACITY];
        private int[] mapValue = new int[INITIAL_MAP_CAPACITY];

        private int size;
        private int heapSize;

        private void reset() {
            size = 0;
            heapSize = 0;
            Arrays.fill(mapValue, 0);
        }

        private int findNode(
                int keyX,
                int keyY,
                int keyZ) {

            int mask = mapValue.length - 1;
            int slot = hash(keyX, keyY, keyZ) & mask;

            while (true) {
                int value = mapValue[slot];
                if (value == 0) {
                    return -1;
                }

                int node = value - 1;
                if (mapX[slot] == keyX
                        && mapY[slot] == keyY
                        && mapZ[slot] == keyZ) {
                    return node;
                }

                slot = (slot + 1) & mask;
            }
        }

        private int addNode(
                int nodeX,
                int nodeY,
                int nodeZ) {

            if (findNode(nodeX, nodeY, nodeZ) >= 0) {
                throw new IllegalStateException(
                        "path workspace node already exists");
            }

            ensureNodeCapacity(size + 1);
            ensureMapCapacity(size + 1);

            int node = size++;
            x[node] = nodeX;
            y[node] = nodeY;
            z[node] = nodeZ;
            g[node] = Long.MAX_VALUE;
            h[node] = 0L;
            parent[node] = -1;
            state[node] = 0;
            heapPosition[node] = -1;

            insertMap(node);
            return node;
        }

        private void pushOrDecrease(
                int node) {

            int position = heapPosition[node];
            if (position >= 0) {
                siftUp(position);
                return;
            }

            ensureNodeCapacity(heapSize + 1);
            int inserted = heapSize++;
            heap[inserted] = node;
            heapPosition[node] = inserted;
            siftUp(inserted);
        }

        private int popBest() {
            if (heapSize == 0) {
                throw new IllegalStateException(
                        "path frontier is empty");
            }

            int best = heap[0];
            int last = heap[--heapSize];
            heapPosition[best] = -1;

            if (heapSize > 0) {
                heap[0] = last;
                heapPosition[last] = 0;
                siftDown(0);
            }

            return best;
        }

        private void siftUp(
                int start) {

            int position = start;
            int node = heap[position];

            while (position > 0) {
                int parentPosition = (position - 1) >>> 1;
                int parentNode = heap[parentPosition];

                if (!less(node, parentNode)) {
                    break;
                }

                heap[position] = parentNode;
                heapPosition[parentNode] = position;
                position = parentPosition;
            }

            heap[position] = node;
            heapPosition[node] = position;
        }

        private void siftDown(
                int start) {

            int position = start;
            int node = heap[position];
            int half = heapSize >>> 1;

            while (position < half) {
                int left = (position << 1) + 1;
                int right = left + 1;
                int bestChild = left;

                if (right < heapSize
                        && less(heap[right], heap[left])) {
                    bestChild = right;
                }

                int childNode = heap[bestChild];
                if (!less(childNode, node)) {
                    break;
                }

                heap[position] = childNode;
                heapPosition[childNode] = position;
                position = bestChild;
            }

            heap[position] = node;
            heapPosition[node] = position;
        }

        private boolean less(
                int left,
                int right) {

            long leftF = Math.addExact(g[left], h[left]);
            long rightF = Math.addExact(g[right], h[right]);

            if (leftF != rightF) {
                return leftF < rightF;
            }
            if (h[left] != h[right]) {
                return h[left] < h[right];
            }
            if (z[left] != z[right]) {
                return z[left] < z[right];
            }
            if (y[left] != y[right]) {
                return y[left] < y[right];
            }
            return x[left] < x[right];
        }

        private void ensureNodeCapacity(
                int required) {

            if (required <= x.length) {
                return;
            }

            int capacity = Math.max(
                    required,
                    x.length * 2);
            x = Arrays.copyOf(x, capacity);
            y = Arrays.copyOf(y, capacity);
            z = Arrays.copyOf(z, capacity);
            g = Arrays.copyOf(g, capacity);
            h = Arrays.copyOf(h, capacity);
            parent = Arrays.copyOf(parent, capacity);
            state = Arrays.copyOf(state, capacity);
            heap = Arrays.copyOf(heap, capacity);
            heapPosition = Arrays.copyOf(heapPosition, capacity);
        }

        private void ensureMapCapacity(
                int requiredNodes) {

            if ((long) requiredNodes * 10
                    < (long) mapValue.length * 6) {
                return;
            }

            int capacity = Math.multiplyExact(
                    mapValue.length,
                    2);
            mapX = new int[capacity];
            mapY = new int[capacity];
            mapZ = new int[capacity];
            mapValue = new int[capacity];

            for (int node = 0; node < size; node++) {
                insertMap(node);
            }
        }

        private void insertMap(
                int node) {

            int mask = mapValue.length - 1;
            int slot = hash(
                    x[node],
                    y[node],
                    z[node]) & mask;

            while (mapValue[slot] != 0) {
                slot = (slot + 1) & mask;
            }

            mapX[slot] = x[node];
            mapY[slot] = y[node];
            mapZ[slot] = z[node];
            mapValue[slot] = node + 1;
        }

        private static int hash(
                int x,
                int y,
                int z) {

            int hash = x * 0x9E3779B9;
            hash ^= Integer.rotateLeft(
                    y * 0x85EBCA6B,
                    11);
            hash ^= Integer.rotateLeft(
                    z * 0xC2B2AE35,
                    22);
            hash ^= hash >>> 16;
            return hash;
        }
    }

    private static void requireBudget(
            int expansionBudget) {

        if (expansionBudget <= 0) {
            throw new IllegalArgumentException(
                    "expansionBudget must be > 0");
        }
    }
}
