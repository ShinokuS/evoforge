package io.github.evoforge.simulation.world.pathfinding;

import java.util.Arrays;

import io.github.evoforge.simulation.world.mechanics.geometry.TransitionDirections;

/**
 * Exact spatial Pathfinder with a derived 3D cluster reachability preflight.
 * Coarse connectivity may prove NO_PATH, but every positive result is refined
 * by the exact Pathfinder before a route is returned.
 */
public final class HierarchicalPathfinder implements Pathfinder {

    private final PathHierarchyIndex hierarchy;
    private final Pathfinder exact;

    public HierarchicalPathfinder(
            PathHierarchyIndex hierarchy,
            Pathfinder exact) {

        if (hierarchy == null) {
            throw new IllegalArgumentException(
                    "hierarchy must not be null");
        }
        if (exact == null) {
            throw new IllegalArgumentException(
                    "exact must not be null");
        }

        this.hierarchy = hierarchy;
        this.exact = exact;
    }

    public PathHierarchyIndexMetrics hierarchyMetrics() {
        return hierarchy.metrics();
    }

    @Override
    public PathSearch begin(
            PathQuery query) {

        if (query == null) {
            throw new IllegalArgumentException(
                    "query must not be null");
        }

        int fromX = hierarchy.clusterX(query.fromX());
        int fromY = hierarchy.clusterY(query.fromY());
        int fromZ = hierarchy.clusterZ(query.fromZ());
        int toX = hierarchy.clusterX(query.toX());
        int toY = hierarchy.clusterY(query.toY());
        int toZ = hierarchy.clusterZ(query.toZ());

        if (fromX == toX
                && fromY == toY
                && fromZ == toZ) {
            return exact.begin(query);
        }

        return new Search(
                hierarchy,
                exact,
                query,
                fromX,
                fromY,
                fromZ,
                toX,
                toY,
                toZ);
    }

    private static final class Search implements PathSearch {

        private final PathHierarchyIndex hierarchy;
        private final Pathfinder exact;
        private final PathQuery query;
        private final int goalX;
        private final int goalY;
        private final int goalZ;
        private final long initialTraversalRevision;
        private final long initialConstraintRevision;
        private final ClusterWorkspace workspace =
                new ClusterWorkspace();

        private PathSearchStatus status =
                PathSearchStatus.RUNNING;
        private PathSearch exactSearch;
        private long coarseExpanded;
        private long coarseGenerated;
        private long coarseRelaxed;
        private long coarseReopened;
        private int coarsePeakFrontier;

        private Search(
                PathHierarchyIndex hierarchy,
                Pathfinder exact,
                PathQuery query,
                int fromX,
                int fromY,
                int fromZ,
                int goalX,
                int goalY,
                int goalZ) {

            this.hierarchy = hierarchy;
            this.exact = exact;
            this.query = query;
            this.goalX = goalX;
            this.goalY = goalY;
            this.goalZ = goalZ;
            initialTraversalRevision =
                    hierarchy.traversalRevision();
            initialConstraintRevision =
                    query.constraint().revision();

            int start = workspace.addNode(
                    fromX,
                    fromY,
                    fromZ);
            workspace.g[start] = 0;
            workspace.h[start] = chebyshev(
                    fromX,
                    fromY,
                    fromZ,
                    goalX,
                    goalY,
                    goalZ);
            workspace.state[start] =
                    ClusterWorkspace.OPEN;
            workspace.pushOrDecrease(start);
            coarsePeakFrontier = 1;
        }

        @Override
        public PathSearchStatus status() {
            return status;
        }

        @Override
        public PathSearchStatus advance(
                int expansionBudget) {

            if (expansionBudget <= 0) {
                throw new IllegalArgumentException(
                        "expansionBudget must be > 0");
            }
            if (status != PathSearchStatus.RUNNING) {
                return status;
            }
            if (stale()) {
                staleSearch();
                return status;
            }

            int remaining = expansionBudget;

            while (remaining > 0
                    && exactSearch == null) {

                if (workspace.heapSize == 0) {
                    status = PathSearchStatus.NO_PATH;
                    return status;
                }

                int current = workspace.popBest();
                workspace.state[current] =
                        ClusterWorkspace.CLOSED;
                coarseExpanded++;
                remaining--;

                int x = workspace.x[current];
                int y = workspace.y[current];
                int z = workspace.z[current];

                if (x == goalX
                        && y == goalY
                        && z == goalZ) {
                    exactSearch = exact.begin(query);
                    break;
                }

                expandCoarse(
                        current,
                        x,
                        y,
                        z);
            }

            if (stale()) {
                staleSearch();
                return status;
            }

            if (exactSearch != null
                    && remaining > 0) {
                PathSearchStatus exactStatus =
                        exactSearch.advance(remaining);

                if (exactStatus != PathSearchStatus.RUNNING) {
                    status = exactStatus;
                }
            }

            return status;
        }

        @Override
        public void cancel() {
            if (status != PathSearchStatus.RUNNING) {
                return;
            }

            if (exactSearch != null) {
                exactSearch.cancel();
            }

            status = PathSearchStatus.CANCELLED;
        }

        @Override
        public PathRoute route() {
            if (status != PathSearchStatus.FOUND
                    || exactSearch == null) {
                throw new IllegalStateException(
                        "route is available only for FOUND search");
            }

            return exactSearch.route();
        }

        @Override
        public PathSearchMetrics metrics() {
            PathSearchMetrics exactMetrics =
                    exactSearch == null
                            ? new PathSearchMetrics(
                                    0L,
                                    0L,
                                    0L,
                                    0L,
                                    0)
                            : exactSearch.metrics();

            return new PathSearchMetrics(
                    Math.addExact(
                            coarseExpanded,
                            exactMetrics.expandedNodes()),
                    Math.addExact(
                            coarseGenerated,
                            exactMetrics.generatedTransitions()),
                    Math.addExact(
                            coarseRelaxed,
                            exactMetrics.relaxedNodes()),
                    Math.addExact(
                            coarseReopened,
                            exactMetrics.reopenedNodes()),
                    Math.max(
                            coarsePeakFrontier,
                            exactMetrics.peakFrontier()));
        }

        private boolean stale() {
            return hierarchy.traversalRevision()
                            != initialTraversalRevision
                    || query.constraint().revision()
                            != initialConstraintRevision;
        }

        private void staleSearch() {
            if (exactSearch != null
                    && exactSearch.status()
                            == PathSearchStatus.RUNNING) {
                exactSearch.cancel();
            }

            status = PathSearchStatus.STALE;
        }

        private void expandCoarse(
                int current,
                int x,
                int y,
                int z) {

            int transitions =
                    hierarchy.outgoingTransitions(
                            x,
                            y,
                            z);

            for (int direction = 0;
                    direction < TransitionDirections.COUNT;
                    direction++) {

                if ((transitions
                        & TransitionDirections.mask(direction)) == 0) {
                    continue;
                }

                coarseGenerated++;

                int toX = Math.addExact(
                        x,
                        TransitionDirections.dx(direction));
                int toY = Math.addExact(
                        y,
                        TransitionDirections.dy(direction));
                int toZ = Math.addExact(
                        z,
                        TransitionDirections.dz(direction));
                int candidateG = Math.addExact(
                        workspace.g[current],
                        1);

                int next = workspace.findNode(
                        toX,
                        toY,
                        toZ);

                if (next < 0) {
                    next = workspace.addNode(
                            toX,
                            toY,
                            toZ);
                    workspace.h[next] = chebyshev(
                            toX,
                            toY,
                            toZ,
                            goalX,
                            goalY,
                            goalZ);
                }

                if (candidateG >= workspace.g[next]) {
                    continue;
                }

                boolean reopened =
                        workspace.state[next]
                                == ClusterWorkspace.CLOSED;

                workspace.g[next] = candidateG;
                workspace.state[next] =
                        ClusterWorkspace.OPEN;
                workspace.pushOrDecrease(next);
                coarseRelaxed++;

                if (reopened) {
                    coarseReopened++;
                }

                coarsePeakFrontier = Math.max(
                        coarsePeakFrontier,
                        workspace.heapSize);
            }
        }

        private static int chebyshev(
                int x,
                int y,
                int z,
                int goalX,
                int goalY,
                int goalZ) {

            long dx = Math.abs((long) goalX - x);
            long dy = Math.abs((long) goalY - y);
            long dz = Math.abs((long) goalZ - z);
            long result = Math.max(
                    dx,
                    Math.max(dy, dz));

            return (int) Math.min(
                    Integer.MAX_VALUE,
                    result);
        }
    }

    private static final class ClusterWorkspace {

        private static final byte OPEN = 1;
        private static final byte CLOSED = 2;
        private static final int INITIAL_NODE_CAPACITY = 64;
        private static final int INITIAL_MAP_CAPACITY = 128;

        private int[] x =
                new int[INITIAL_NODE_CAPACITY];
        private int[] y =
                new int[INITIAL_NODE_CAPACITY];
        private int[] z =
                new int[INITIAL_NODE_CAPACITY];
        private int[] g =
                new int[INITIAL_NODE_CAPACITY];
        private int[] h =
                new int[INITIAL_NODE_CAPACITY];
        private byte[] state =
                new byte[INITIAL_NODE_CAPACITY];
        private int[] heap =
                new int[INITIAL_NODE_CAPACITY];
        private int[] heapPosition =
                new int[INITIAL_NODE_CAPACITY];

        private int[] mapX =
                new int[INITIAL_MAP_CAPACITY];
        private int[] mapY =
                new int[INITIAL_MAP_CAPACITY];
        private int[] mapZ =
                new int[INITIAL_MAP_CAPACITY];
        private int[] mapValue =
                new int[INITIAL_MAP_CAPACITY];

        private int size;
        private int heapSize;

        private int findNode(
                int keyX,
                int keyY,
                int keyZ) {

            int mask = mapValue.length - 1;
            int slot = hash(
                    keyX,
                    keyY,
                    keyZ) & mask;

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

            ensureNodeCapacity(size + 1);
            ensureMapCapacity(size + 1);

            int node = size++;
            x[node] = nodeX;
            y[node] = nodeY;
            z[node] = nodeZ;
            g[node] = Integer.MAX_VALUE;
            h[node] = 0;
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
                        "hierarchy frontier is empty");
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
                int parentPosition =
                        (position - 1) >>> 1;
                int parentNode =
                        heap[parentPosition];

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
                int childPosition = left;

                if (right < heapSize
                        && less(heap[right], heap[left])) {
                    childPosition = right;
                }

                int child = heap[childPosition];
                if (!less(child, node)) {
                    break;
                }

                heap[position] = child;
                heapPosition[child] = position;
                position = childPosition;
            }

            heap[position] = node;
            heapPosition[node] = position;
        }

        private boolean less(
                int left,
                int right) {

            long leftF = (long) g[left] + h[left];
            long rightF = (long) g[right] + h[right];

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
                    Math.multiplyExact(x.length, 2));

            x = Arrays.copyOf(x, capacity);
            y = Arrays.copyOf(y, capacity);
            z = Arrays.copyOf(z, capacity);
            g = Arrays.copyOf(g, capacity);
            h = Arrays.copyOf(h, capacity);
            state = Arrays.copyOf(state, capacity);
            heap = Arrays.copyOf(heap, capacity);
            heapPosition = Arrays.copyOf(
                    heapPosition,
                    capacity);
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
            return hash ^ (hash >>> 16);
        }
    }
}
