package io.github.evoforge.simulation.world.pathfinding;

import java.util.ArrayDeque;
import java.util.Arrays;

/** Reusable primitive node store + coordinate index + binary heap for exact A*. */
final class AStarWorkspace {

    private static final int INITIAL_NODE_CAPACITY = 256;
    private static final int INITIAL_MAP_CAPACITY = 512;

    int[] x = new int[INITIAL_NODE_CAPACITY];
    int[] y = new int[INITIAL_NODE_CAPACITY];
    int[] z = new int[INITIAL_NODE_CAPACITY];
    long[] g = new long[INITIAL_NODE_CAPACITY];
    long[] h = new long[INITIAL_NODE_CAPACITY];
    int[] parent = new int[INITIAL_NODE_CAPACITY];
    byte[] state = new byte[INITIAL_NODE_CAPACITY];

    private int[] heap = new int[INITIAL_NODE_CAPACITY];
    private int[] heapPosition = new int[INITIAL_NODE_CAPACITY];
    private int[] mapX = new int[INITIAL_MAP_CAPACITY];
    private int[] mapY = new int[INITIAL_MAP_CAPACITY];
    private int[] mapZ = new int[INITIAL_MAP_CAPACITY];
    private int[] mapValue = new int[INITIAL_MAP_CAPACITY];

    private int size;
    int heapSize;

    void reset() {
        size = 0;
        heapSize = 0;
        Arrays.fill(mapValue, 0);
    }

    int findNode(int keyX, int keyY, int keyZ) {
        int mask = mapValue.length - 1;
        int slot = hash(keyX, keyY, keyZ) & mask;
        while (true) {
            int value = mapValue[slot];
            if (value == 0) {
                return -1;
            }
            int node = value - 1;
            if (mapX[slot] == keyX && mapY[slot] == keyY && mapZ[slot] == keyZ) {
                return node;
            }
            slot = (slot + 1) & mask;
        }
    }

    int addNode(int nodeX, int nodeY, int nodeZ) {
        if (findNode(nodeX, nodeY, nodeZ) >= 0) {
            throw new IllegalStateException("path workspace node already exists");
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

    void pushOrDecrease(int node) {
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

    int popBest() {
        if (heapSize == 0) {
            throw new IllegalStateException("path frontier is empty");
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

    private void siftUp(int start) {
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

    private void siftDown(int start) {
        int position = start;
        int node = heap[position];
        int half = heapSize >>> 1;
        while (position < half) {
            int left = (position << 1) + 1;
            int right = left + 1;
            int childPosition = right < heapSize && less(heap[right], heap[left])
                    ? right
                    : left;
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

    private boolean less(int left, int right) {
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

    private void ensureNodeCapacity(int required) {
        if (required <= x.length) {
            return;
        }
        int capacity = Math.max(required, Math.multiplyExact(x.length, 2));
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

    private void ensureMapCapacity(int requiredNodes) {
        if ((long) requiredNodes * 10 < (long) mapValue.length * 6) {
            return;
        }
        int capacity = Math.multiplyExact(mapValue.length, 2);
        mapX = new int[capacity];
        mapY = new int[capacity];
        mapZ = new int[capacity];
        mapValue = new int[capacity];
        for (int node = 0; node < size; node++) {
            insertMap(node);
        }
    }

    private void insertMap(int node) {
        int mask = mapValue.length - 1;
        int slot = hash(x[node], y[node], z[node]) & mask;
        while (mapValue[slot] != 0) {
            slot = (slot + 1) & mask;
        }
        mapX[slot] = x[node];
        mapY[slot] = y[node];
        mapZ[slot] = z[node];
        mapValue[slot] = node + 1;
    }

    private static int hash(int x, int y, int z) {
        int hash = x * 0x9E3779B9;
        hash ^= Integer.rotateLeft(y * 0x85EBCA6B, 11);
        hash ^= Integer.rotateLeft(z * 0xC2B2AE35, 22);
        return hash ^ (hash >>> 16);
    }

    static final class Pool {
        private final ArrayDeque<AStarWorkspace> available = new ArrayDeque<>();

        AStarWorkspace acquire() {
            AStarWorkspace workspace = available.pollFirst();
            if (workspace == null) {
                workspace = new AStarWorkspace();
            }
            workspace.reset();
            return workspace;
        }

        void release(AStarWorkspace workspace) {
            available.addFirst(workspace);
        }
    }
}
