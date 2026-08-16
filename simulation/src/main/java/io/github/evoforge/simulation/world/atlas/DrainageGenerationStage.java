package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Deterministic D8-style closed-world drainage over precise Atlas elevation. */
public final class DrainageGenerationStage implements DrainageGenerator {
    private static final int[] DX = {-1, 0, 1, -1, 1, -1, 0, 1};
    private static final int[] DY = {-1, -1, -1, 0, 0, 1, 1, 1};
    private static final double SQRT_TWO = StrictMath.sqrt(2.0);

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
        resolveEqualElevationFlats(width, height, heights, downstream);

        long[] contributingArea = accumulateContributingArea(downstream);
        int[] terminal = resolveTerminals(downstream);
        return new DenseDrainageField(bounds, downstream, contributingArea, terminal);
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

    private static void resolveEqualElevationFlats(
            int width,
            int height,
            long[] elevations,
            int[] downstream) {
        boolean[] visited = new boolean[elevations.length];
        int[] distance = new int[elevations.length];
        Arrays.fill(distance, -1);

        for (int start = 0; start < elevations.length; start++) {
            if (visited[start]) {
                continue;
            }
            if (!hasEqualNeighbor(start, width, height, elevations)) {
                visited[start] = true;
                continue;
            }

            List<Integer> component = collectFlat(
                    start, width, height, elevations, visited);
            ArrayDeque<Integer> frontier = new ArrayDeque<>();
            boolean hasOutlet = false;

            for (int cell : component) {
                if (downstream[cell] != DenseDrainageField.TERMINAL) {
                    distance[cell] = 0;
                    frontier.addLast(cell);
                    hasOutlet = true;
                }
            }

            if (!hasOutlet) {
                int terminal = component.get(0);
                for (int cell : component) {
                    terminal = Math.min(terminal, cell);
                }
                distance[terminal] = 0;
                frontier.addLast(terminal);
            }

            while (!frontier.isEmpty()) {
                int cell = frontier.removeFirst();
                int x = cell % width;
                int y = cell / width;
                for (int neighbor = 0; neighbor < DX.length; neighbor++) {
                    int nx = x + DX[neighbor];
                    int ny = y + DY[neighbor];
                    if (nx < 0 || nx >= width || ny < 0 || ny >= height) {
                        continue;
                    }
                    int candidate = ny * width + nx;
                    if (elevations[candidate] != elevations[cell] || distance[candidate] >= 0) {
                        continue;
                    }
                    distance[candidate] = distance[cell] + 1;
                    frontier.addLast(candidate);
                }
            }

            for (int cell : component) {
                if (distance[cell] > 0) {
                    downstream[cell] = equalNeighborTowardOutlet(
                            cell, width, height, elevations, distance);
                }
            }
            for (int cell : component) {
                distance[cell] = -1;
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

    private static List<Integer> collectFlat(
            int start,
            int width,
            int height,
            long[] elevations,
            boolean[] visited) {
        List<Integer> component = new ArrayList<>();
        ArrayDeque<Integer> frontier = new ArrayDeque<>();
        long flatElevation = elevations[start];
        visited[start] = true;
        frontier.addLast(start);

        while (!frontier.isEmpty()) {
            int cell = frontier.removeFirst();
            component.add(cell);
            int x = cell % width;
            int y = cell / width;
            for (int neighbor = 0; neighbor < DX.length; neighbor++) {
                int nx = x + DX[neighbor];
                int ny = y + DY[neighbor];
                if (nx < 0 || nx >= width || ny < 0 || ny >= height) {
                    continue;
                }
                int candidate = ny * width + nx;
                if (!visited[candidate] && elevations[candidate] == flatElevation) {
                    visited[candidate] = true;
                    frontier.addLast(candidate);
                }
            }
        }
        return component;
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

    private static long[] accumulateContributingArea(int[] downstream) {
        int[] incoming = new int[downstream.length];
        long[] area = new long[downstream.length];
        Arrays.fill(area, 1L);
        for (int next : downstream) {
            if (next != DenseDrainageField.TERMINAL) {
                incoming[next]++;
            }
        }

        ArrayDeque<Integer> ready = new ArrayDeque<>();
        for (int index = 0; index < incoming.length; index++) {
            if (incoming[index] == 0) {
                ready.addLast(index);
            }
        }

        int processed = 0;
        while (!ready.isEmpty()) {
            int cell = ready.removeFirst();
            processed++;
            int next = downstream[cell];
            if (next != DenseDrainageField.TERMINAL) {
                area[next] = Math.addExact(area[next], area[cell]);
                incoming[next]--;
                if (incoming[next] == 0) {
                    ready.addLast(next);
                }
            }
        }
        if (processed != downstream.length) {
            throw new IllegalStateException("drainage topology contains a cycle");
        }
        return area;
    }

    private static int[] resolveTerminals(int[] downstream) {
        int[] terminal = new int[downstream.length];
        Arrays.fill(terminal, DenseDrainageField.TERMINAL);
        for (int start = 0; start < downstream.length; start++) {
            if (terminal[start] != DenseDrainageField.TERMINAL) {
                continue;
            }
            int current = start;
            ArrayDeque<Integer> path = new ArrayDeque<>();
            while (downstream[current] != DenseDrainageField.TERMINAL
                    && terminal[current] == DenseDrainageField.TERMINAL) {
                path.addLast(current);
                current = downstream[current];
            }
            int sink = terminal[current] != DenseDrainageField.TERMINAL
                    ? terminal[current]
                    : current;
            terminal[current] = sink;
            while (!path.isEmpty()) {
                terminal[path.removeLast()] = sink;
            }
        }
        return terminal;
    }
}
