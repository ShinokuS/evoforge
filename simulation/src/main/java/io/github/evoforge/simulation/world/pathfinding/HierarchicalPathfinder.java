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
        if (hierarchy == null || exact == null) {
            throw new IllegalArgumentException("pathfinder dependencies must not be null");
        }
        this.hierarchy = hierarchy;
        this.exact = exact;
    }

    public PathHierarchyIndexMetrics hierarchyMetrics() {
        return hierarchy.metrics();
    }

    @Override
    public PathSearch begin(PathQuery query) {
        if (query == null) {
            throw new IllegalArgumentException("query must not be null");
        }
        int fx = hierarchy.clusterX(query.fromX());
        int fy = hierarchy.clusterY(query.fromY());
        int fz = hierarchy.clusterZ(query.fromZ());
        int tx = hierarchy.clusterX(query.toX());
        int ty = hierarchy.clusterY(query.toY());
        int tz = hierarchy.clusterZ(query.toZ());
        if (fx == tx && fy == ty && fz == tz) {
            return exact.begin(query);
        }
        return new Search(hierarchy, exact, query, fx, fy, fz, tx, ty, tz);
    }

    private static final class Search implements PathSearch {
        private final PathHierarchyIndex hierarchy;
        private final Pathfinder exact;
        private final PathQuery query;
        private final int goalX;
        private final int goalY;
        private final int goalZ;
        private final long initialRevision;
        private final ClusterWorkspace workspace = new ClusterWorkspace();
        private PathSearchStatus status = PathSearchStatus.RUNNING;
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
                int fromX, int fromY, int fromZ,
                int goalX, int goalY, int goalZ) {
            this.hierarchy = hierarchy;
            this.exact = exact;
            this.query = query;
            this.goalX = goalX;
            this.goalY = goalY;
            this.goalZ = goalZ;
            initialRevision = hierarchy.traversalRevision();
            int start = workspace.addNode(fromX, fromY, fromZ);
            workspace.g[start] = 0;
            workspace.h[start] = chebyshev(fromX, fromY, fromZ, goalX, goalY, goalZ);
            workspace.state[start] = ClusterWorkspace.OPEN;
            workspace.pushOrDecrease(start);
            coarsePeakFrontier = 1;
        }

        @Override public PathSearchStatus status() { return status; }

        @Override
        public PathSearchStatus advance(int expansionBudget) {
            if (expansionBudget <= 0) {
                throw new IllegalArgumentException("expansionBudget must be > 0");
            }
            if (status != PathSearchStatus.RUNNING) return status;
            if (hierarchy.traversalRevision() != initialRevision) {
                status = PathSearchStatus.STALE;
                return status;
            }

            int remaining = expansionBudget;
            while (remaining > 0 && exactSearch == null) {
                if (workspace.heapSize == 0) {
                    status = PathSearchStatus.NO_PATH;
                    return status;
                }
                int current = workspace.popBest();
                workspace.state[current] = ClusterWorkspace.CLOSED;
                coarseExpanded++;
                remaining--;
                int x = workspace.x[current];
                int y = workspace.y[current];
                int z = workspace.z[current];
                if (x == goalX && y == goalY && z == goalZ) {
                    exactSearch = exact.begin(query);
                    break;
                }

                int transitions = hierarchy.outgoingTransitions(x, y, z);
                for (int d = 0; d < TransitionDirections.COUNT; d++) {
                    if ((transitions & TransitionDirections.mask(d)) == 0) continue;
                    coarseGenerated++;
                    int tx = Math.addExact(x, TransitionDirections.dx(d));
                    int ty = Math.addExact(y, TransitionDirections.dy(d));
                    int tz = Math.addExact(z, TransitionDirections.dz(d));
                    int candidateG = Math.addExact(workspace.g[current], 1);
                    int next = workspace.findNode(tx, ty, tz);
                    if (next < 0) {
                        next = workspace.addNode(tx, ty, tz);
                        workspace.h[next] = chebyshev(tx, ty, tz, goalX, goalY, goalZ);
                    }
                    if (candidateG >= workspace.g[next]) continue;
                    boolean reopened = workspace.state[next] == ClusterWorkspace.CLOSED;
                    workspace.g[next] = candidateG;
                    workspace.state[next] = ClusterWorkspace.OPEN;
                    workspace.pushOrDecrease(next);
                    coarseRelaxed++;
                    if (reopened) coarseReopened++;
                    coarsePeakFrontier = Math.max(coarsePeakFrontier, workspace.heapSize);
                }
            }

            if (hierarchy.traversalRevision() != initialRevision) {
                status = PathSearchStatus.STALE;
                return status;
            }
            if (exactSearch != null && remaining > 0) {
                PathSearchStatus exactStatus = exactSearch.advance(remaining);
                if (exactStatus != PathSearchStatus.RUNNING) status = exactStatus;
            }
            return status;
        }

        @Override
        public PathRoute route() {
            if (status != PathSearchStatus.FOUND || exactSearch == null) {
                throw new IllegalStateException("route is available only for FOUND search");
            }
            return exactSearch.route();
        }

        @Override
        public PathSearchMetrics metrics() {
            PathSearchMetrics e = exactSearch == null
                    ? new PathSearchMetrics(0, 0, 0, 0, 0)
                    : exactSearch.metrics();
            return new PathSearchMetrics(
                    Math.addExact(coarseExpanded, e.expandedNodes()),
                    Math.addExact(coarseGenerated, e.generatedTransitions()),
                    Math.addExact(coarseRelaxed, e.relaxedNodes()),
                    Math.addExact(coarseReopened, e.reopenedNodes()),
                    Math.max(coarsePeakFrontier, e.peakFrontier()));
        }

        private static int chebyshev(
                int x, int y, int z,
                int gx, int gy, int gz) {
            long dx = Math.abs((long) gx - x);
            long dy = Math.abs((long) gy - y);
            long dz = Math.abs((long) gz - z);
            return (int) Math.min(Integer.MAX_VALUE, Math.max(dx, Math.max(dy, dz)));
        }
    }

    private static final class ClusterWorkspace {
        private static final byte OPEN = 1;
        private static final byte CLOSED = 2;
        private int[] x = new int[64];
        private int[] y = new int[64];
        private int[] z = new int[64];
        private int[] g = new int[64];
        private int[] h = new int[64];
        private byte[] state = new byte[64];
        private int[] heap = new int[64];
        private int[] heapPosition = new int[64];
        private int[] mapX = new int[128];
        private int[] mapY = new int[128];
        private int[] mapZ = new int[128];
        private int[] mapValue = new int[128];
        private int size;
        private int heapSize;

        private int findNode(int keyX, int keyY, int keyZ) {
            int mask = mapValue.length - 1;
            int slot = hash(keyX, keyY, keyZ) & mask;
            while (true) {
                int value = mapValue[slot];
                if (value == 0) return -1;
                int node = value - 1;
                if (mapX[slot] == keyX && mapY[slot] == keyY && mapZ[slot] == keyZ) return node;
                slot = (slot + 1) & mask;
            }
        }

        private int addNode(int nx, int ny, int nz) {
            ensureNodeCapacity(size + 1);
            ensureMapCapacity(size + 1);
            int node = size++;
            x[node] = nx; y[node] = ny; z[node] = nz;
            g[node] = Integer.MAX_VALUE;
            h[node] = 0;
            state[node] = 0;
            heapPosition[node] = -1;
            insertMap(node);
            return node;
        }

        private void pushOrDecrease(int node) {
            int p = heapPosition[node];
            if (p >= 0) { siftUp(p); return; }
            int inserted = heapSize++;
            heap[inserted] = node;
            heapPosition[node] = inserted;
            siftUp(inserted);
        }

        private int popBest() {
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

        private void siftUp(int start) {
            int p = start;
            int node = heap[p];
            while (p > 0) {
                int pp = (p - 1) >>> 1;
                int pn = heap[pp];
                if (!less(node, pn)) break;
                heap[p] = pn; heapPosition[pn] = p; p = pp;
            }
            heap[p] = node; heapPosition[node] = p;
        }

        private void siftDown(int start) {
            int p = start;
            int node = heap[p];
            int half = heapSize >>> 1;
            while (p < half) {
                int left = (p << 1) + 1;
                int right = left + 1;
                int childPos = right < heapSize && less(heap[right], heap[left]) ? right : left;
                int child = heap[childPos];
                if (!less(child, node)) break;
                heap[p] = child; heapPosition[child] = p; p = childPos;
            }
            heap[p] = node; heapPosition[node] = p;
        }

        private boolean less(int a, int b) {
            long fa = (long) g[a] + h[a];
            long fb = (long) g[b] + h[b];
            if (fa != fb) return fa < fb;
            if (h[a] != h[b]) return h[a] < h[b];
            if (z[a] != z[b]) return z[a] < z[b];
            if (y[a] != y[b]) return y[a] < y[b];
            return x[a] < x[b];
        }

        private void ensureNodeCapacity(int required) {
            if (required <= x.length) return;
            int capacity = Math.max(required, x.length * 2);
            x = Arrays.copyOf(x, capacity); y = Arrays.copyOf(y, capacity); z = Arrays.copyOf(z, capacity);
            g = Arrays.copyOf(g, capacity); h = Arrays.copyOf(h, capacity); state = Arrays.copyOf(state, capacity);
            heap = Arrays.copyOf(heap, capacity); heapPosition = Arrays.copyOf(heapPosition, capacity);
        }

        private void ensureMapCapacity(int required) {
            if ((long) required * 10 < (long) mapValue.length * 6) return;
            int capacity = Math.multiplyExact(mapValue.length, 2);
            mapX = new int[capacity]; mapY = new int[capacity]; mapZ = new int[capacity]; mapValue = new int[capacity];
            for (int node = 0; node < size; node++) insertMap(node);
        }

        private void insertMap(int node) {
            int mask = mapValue.length - 1;
            int slot = hash(x[node], y[node], z[node]) & mask;
            while (mapValue[slot] != 0) slot = (slot + 1) & mask;
            mapX[slot] = x[node]; mapY[slot] = y[node]; mapZ[slot] = z[node]; mapValue[slot] = node + 1;
        }

        private static int hash(int x, int y, int z) {
            int hash = x * 0x9E3779B9;
            hash ^= Integer.rotateLeft(y * 0x85EBCA6B, 11);
            hash ^= Integer.rotateLeft(z * 0xC2B2AE35, 22);
            return hash ^ (hash >>> 16);
        }
    }
}
