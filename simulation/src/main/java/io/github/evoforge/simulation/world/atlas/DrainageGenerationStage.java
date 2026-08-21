package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.Arrays;

/** Deterministic D8-style closed-world drainage over precise Atlas elevation. */
public final class DrainageGenerationStage implements DrainageGenerator {
    private static final int[] DX = {-1, 0, 1, -1, 1, -1, 0, 1};
    private static final int[] DY = {-1, -1, -1, 0, 0, 1, 1, 1};
    private static final double SQRT_TWO = StrictMath.sqrt(2.0);

    private static final int FLAT_UNSEEN = Integer.MIN_VALUE;
    private static final int FLAT_COMPLETE = Integer.MIN_VALUE + 1;
    private static final int FLAT_DISCOVERED = -1;

    @Override
    public DrainageField generate(ElevationField elevation) {
        if (elevation == null) {
            throw new IllegalArgumentException("elevation must not be null");
        }

        WorldBounds bounds = elevation.bounds();
        int width = Math.toIntExact((long) bounds.maxX() - bounds.minX() + 1L);
        int height = Math.toIntExact((long) bounds.maxY() - bounds.minY() + 1L);
        int count = Math.multiplyExact(width, height);
        long[] heights = new long[count];
        int[] downstream = new int[count];
        int[] traversalScratch = new int[count];
        Arrays.fill(downstream, DenseDrainageField.TERMINAL);

        for (int localY = 0; localY < height; localY++) {
            int y = bounds.minY() + localY;
            for (int localX = 0; localX < width; localX++) {
                int x = bounds.minX() + localX;
                int index = localY * width + localX;
                heights[index] = elevation.elevationSubunitsAt(x, y);
            }
        }

        assignStrictDownhill(width, height, heights, downstream);
        resolveEqualElevationFlats(width, height, heights, downstream, traversalScratch);

        long[] contributingArea = accumulateContributingArea(downstream, traversalScratch);
        int[] terminal = resolveTerminals(downstream, traversalScratch);
        return DenseDrainageField.takeOwnership(bounds, downstream, contributingArea, terminal);
    }

    private static void assignStrictDownhill(
            int width,
            int height,
            long[] elevations,
            int[] downstream) {
        for (int index = 0; index < elevations.length; index++) {
            int x = index % width;
            int y = index / width;
            long current = elevations[index];
            int best = DenseDrainageField.TERMINAL;
            double bestSlope = Double.NEGATIVE_INFINITY;

            for (int neighbor = 0; neighbor < DX.length; neighbor++) {
                int nx = x + DX[neighbor];
                int ny = y + DY[neighbor];
                if (nx < 0 || nx >= width || ny < 0 || ny >= height) {
                    continue;
                }
                int candidate = ny * width + nx;
                long candidateElevation = elevations[candidate];
                if (candidateElevation >= current) {
                    continue;
                }
                long drop = current - candidateElevation;
                boolean diagonal = DX[neighbor] != 0 && DY[neighbor] != 0;
                double slope = drop / (diagonal ? SQRT_TWO : 1.0);
                int comparison = Double.compare(slope, bestSlope);
                if (comparison > 0 || (comparison == 0 && candidate < best)) {
                    best = candidate;
                    bestSlope = slope;
                }
            }
            downstream[index] = best;
        }
    }

    /**
     * Resolves equal-elevation flats without boxed cell indices.
     *
     * <p>One primitive scratch array is reused first as component BFS storage and then as the
     * distance-propagation queue. The state array combines the old visited mask and distance field:
     * completed cells use a negative sentinel while the current component uses -1/0/positive
     * distances. This preserves the old deterministic neighbor and tie-breaking order while avoiding
     * {@code ArrayDeque<Integer>} and {@code List<Integer>} allocation.</p>
     */
    private static void resolveEqualElevationFlats(
            int width,
            int height,
            long[] elevations,
            int[] downstream,
            int[] work) {
        int[] state = new int[elevations.length];
        Arrays.fill(state, FLAT_UNSEEN);

        for (int start = 0; start < elevations.length; start++) {
            if (state[start] == FLAT_COMPLETE) {
                continue;
            }
            if (state[start] != FLAT_UNSEEN) {
                throw new IllegalStateException("flat drainage state leaked across components");
            }
            if (!hasEqualNeighbor(start, width, height, elevations)) {
                state[start] = FLAT_COMPLETE;
                continue;
            }

            int componentSize = collectFlatInto(
                    start, width, height, elevations, state, work);
            int minimumCell = work[0];
            int seedCount = 0;

            /*
             * Stable in-place compaction is safe because the write cursor never passes the read
             * cursor. It therefore preserves the old component-order outlet seeding exactly.
             */
            for (int read = 0; read < componentSize; read++) {
                int cell = work[read];
                minimumCell = Math.min(minimumCell, cell);
                if (downstream[cell] != DenseDrainageField.TERMINAL) {
                    state[cell] = 0;
                    work[seedCount++] = cell;
                }
            }

            if (seedCount == 0) {
                state[minimumCell] = 0;
                work[0] = minimumCell;
                seedCount = 1;
            }

            int head = 0;
            int tail = seedCount;
            while (head < tail) {
                int cell = work[head++];
                int x = cell % width;
                int y = cell / width;
                for (int neighbor = 0; neighbor < DX.length; neighbor++) {
                    int nx = x + DX[neighbor];
                    int ny = y + DY[neighbor];
                    if (nx < 0 || nx >= width || ny < 0 || ny >= height) {
                        continue;
                    }
                    int candidate = ny * width + nx;
                    if (elevations[candidate] != elevations[cell]
                            || state[candidate] != FLAT_DISCOVERED) {
                        continue;
                    }
                    state[candidate] = state[cell] + 1;
                    work[tail++] = candidate;
                }
            }

            if (tail != componentSize) {
                throw new IllegalStateException("flat drainage propagation did not cover component");
            }

            for (int index = 0; index < tail; index++) {
                int cell = work[index];
                if (state[cell] > 0) {
                    downstream[cell] = equalNeighborTowardOutlet(
                            cell, width, height, elevations, state);
                }
            }
            for (int index = 0; index < tail; index++) {
                state[work[index]] = FLAT_COMPLETE;
            }
        }
    }

    private static boolean hasEqualNeighbor(
            int cell,
            int width,
            int height,
            long[] elevations) {
        int x = cell % width;
        int y = cell / width;
        for (int neighbor = 0; neighbor < DX.length; neighbor++) {
            int nx = x + DX[neighbor];
            int ny = y + DY[neighbor];
            if (nx < 0 || nx >= width || ny < 0 || ny >= height) {
                continue;
            }
            if (elevations[ny * width + nx] == elevations[cell]) {
                return true;
            }
        }
        return false;
    }

    private static int collectFlatInto(
            int start,
            int width,
            int height,
            long[] elevations,
            int[] state,
            int[] work) {
        long flatElevation = elevations[start];
        int head = 0;
        int tail = 0;
        state[start] = FLAT_DISCOVERED;
        work[tail++] = start;

        while (head < tail) {
            int cell = work[head++];
            int x = cell % width;
            int y = cell / width;
            for (int neighbor = 0; neighbor < DX.length; neighbor++) {
                int nx = x + DX[neighbor];
                int ny = y + DY[neighbor];
                if (nx < 0 || nx >= width || ny < 0 || ny >= height) {
                    continue;
                }
                int candidate = ny * width + nx;
                if (state[candidate] == FLAT_UNSEEN
                        && elevations[candidate] == flatElevation) {
                    state[candidate] = FLAT_DISCOVERED;
                    work[tail++] = candidate;
                }
            }
        }
        return tail;
    }

    private static int equalNeighborTowardOutlet(
            int cell,
            int width,
            int height,
            long[] elevations,
            int[] distance) {
        int x = cell % width;
        int y = cell / width;
        int wantedDistance = distance[cell] - 1;
        int best = Integer.MAX_VALUE;

        for (int neighbor = 0; neighbor < DX.length; neighbor++) {
            int nx = x + DX[neighbor];
            int ny = y + DY[neighbor];
            if (nx < 0 || nx >= width || ny < 0 || ny >= height) {
                continue;
            }
            int candidate = ny * width + nx;
            if (elevations[candidate] == elevations[cell]
                    && distance[candidate] == wantedDistance
                    && candidate < best) {
                best = candidate;
            }
        }
        if (best == Integer.MAX_VALUE) {
            throw new IllegalStateException("flat drainage distance has no predecessor");
        }
        return best;
    }

    private static long[] accumulateContributingArea(int[] downstream, int[] queue) {
        int[] incoming = new int[downstream.length];
        long[] area = new long[downstream.length];
        Arrays.fill(area, 1L);
        for (int next : downstream) {
            if (next != DenseDrainageField.TERMINAL) {
                incoming[next]++;
            }
        }

        int head = 0;
        int tail = 0;
        for (int index = 0; index < incoming.length; index++) {
            if (incoming[index] == 0) {
                queue[tail++] = index;
            }
        }

        int processed = 0;
        while (head < tail) {
            int cell = queue[head++];
            processed++;
            int next = downstream[cell];
            if (next != DenseDrainageField.TERMINAL) {
                area[next] = Math.addExact(area[next], area[cell]);
                incoming[next]--;
                if (incoming[next] == 0) {
                    queue[tail++] = next;
                }
            }
        }
        if (processed != downstream.length) {
            throw new IllegalStateException("drainage topology contains a cycle");
        }
        return area;
    }

    private static int[] resolveTerminals(int[] downstream, int[] path) {
        int[] terminal = new int[downstream.length];
        Arrays.fill(terminal, DenseDrainageField.TERMINAL);
        for (int start = 0; start < downstream.length; start++) {
            if (terminal[start] != DenseDrainageField.TERMINAL) {
                continue;
            }
            int current = start;
            int pathLength = 0;
            while (downstream[current] != DenseDrainageField.TERMINAL
                    && terminal[current] == DenseDrainageField.TERMINAL) {
                path[pathLength++] = current;
                current = downstream[current];
            }
            int sink = terminal[current] != DenseDrainageField.TERMINAL
                    ? terminal[current]
                    : current;
            terminal[current] = sink;
            while (pathLength > 0) {
                terminal[path[--pathLength]] = sink;
            }
        }
        return terminal;
    }
}
