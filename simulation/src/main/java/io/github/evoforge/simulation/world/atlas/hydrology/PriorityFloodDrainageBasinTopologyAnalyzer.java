package io.github.evoforge.simulation.world.atlas.hydrology;

import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Priority-Flood depression analysis over final generated terrain.
 *
 * <p>Existing accepted standing water acts only as a drainage outlet. Dry terrain is never changed.
 * The resulting fill elevation is an analytical spill level: cells whose minimum drainage level is
 * above their terrain elevation form closed depression basins. Lake formation remains a separate
 * downstream responsibility.
 */
public final class PriorityFloodDrainageBasinTopologyAnalyzer
        implements DrainageBasinTopologyAnalyzer {
    private static final int[] DX = {-1, 1, 0, 0};
    private static final int[] DY = {0, 0, -1, 1};

    private static final Comparator<FloodEntry> FLOOD_ORDER =
            Comparator.comparingLong(FloodEntry::level)
                    .thenComparingInt(FloodEntry::index);

    @Override
    public DrainageBasinTopology analyze(
            ElevationField elevation,
            StandingWaterTopology drainageOutlets) {
        if (elevation == null || drainageOutlets == null) {
            throw new IllegalArgumentException("drainage basin inputs must not be null");
        }
        WorldBounds bounds = elevation.bounds();
        if (!bounds.equals(drainageOutlets.bounds())) {
            throw new IllegalArgumentException("drainage basin inputs must share world bounds");
        }

        int width = Math.toIntExact((long) bounds.maxX() - bounds.minX() + 1L);
        int height = Math.toIntExact((long) bounds.maxY() - bounds.minY() + 1L);
        int area = Math.multiplyExact(width, height);
        long[] fillLevel = new long[area];
        boolean[] fixedOutlet = new boolean[area];
        Arrays.fill(fillLevel, Long.MAX_VALUE);
        PriorityQueue<FloodEntry> frontier = new PriorityQueue<>(FLOOD_ORDER);

        for (int localY = 0; localY < height; localY++) {
            int y = bounds.minY() + localY;
            for (int localX = 0; localX < width; localX++) {
                int x = bounds.minX() + localX;
                int index = localY * width + localX;
                if (drainageOutlets.isStandingWaterAt(x, y)) {
                    seed(frontier, fillLevel, fixedOutlet, index, 0L);
                } else if (localX == 0 || localX == width - 1
                        || localY == 0 || localY == height - 1) {
                    long boundaryLevel = Math.max(0L, elevation.elevationSubunitsAt(x, y));
                    seed(frontier, fillLevel, fixedOutlet, index, boundaryLevel);
                }
            }
        }

        while (!frontier.isEmpty()) {
            FloodEntry current = frontier.remove();
            if (fillLevel[current.index()] != current.level()) continue;

            int localX = current.index() % width;
            int localY = current.index() / width;
            for (int direction = 0; direction < DX.length; direction++) {
                int nx = localX + DX[direction];
                int ny = localY + DY[direction];
                if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue;
                int next = ny * width + nx;
                if (fixedOutlet[next]) continue;

                long terrain = elevation.elevationSubunitsAt(
                        bounds.minX() + nx,
                        bounds.minY() + ny);
                long candidate = Math.max(current.level(), terrain);
                if (candidate < fillLevel[next]) {
                    fillLevel[next] = candidate;
                    frontier.add(new FloodEntry(candidate, next));
                }
            }
        }

        int[] basinIds = new int[area];
        Arrays.fill(basinIds, DrainageBasinTopology.NO_BASIN);
        List<DrainageBasin> basins = new ArrayList<>();
        ArrayDeque<Integer> queue = new ArrayDeque<>();

        for (int start = 0; start < area; start++) {
            if (basinIds[start] != DrainageBasinTopology.NO_BASIN
                    || !isDepressionCell(start, width, bounds, elevation, drainageOutlets, fillLevel)) {
                continue;
            }

            int basinId = basins.size();
            long spillLevel = fillLevel[start];
            basinIds[start] = basinId;
            queue.addLast(start);

            long cellCount = 0L;
            long maximumDepth = 0L;
            int startLocalX = start % width;
            int startLocalY = start / width;
            int minX = bounds.minX() + startLocalX;
            int maxX = minX;
            int minY = bounds.minY() + startLocalY;
            int maxY = minY;

            while (!queue.isEmpty()) {
                int cell = queue.removeFirst();
                int localX = cell % width;
                int localY = cell / width;
                int x = bounds.minX() + localX;
                int y = bounds.minY() + localY;
                long terrain = elevation.elevationSubunitsAt(x, y);

                cellCount++;
                maximumDepth = Math.max(maximumDepth, spillLevel - terrain);
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);

                for (int direction = 0; direction < DX.length; direction++) {
                    int nx = localX + DX[direction];
                    int ny = localY + DY[direction];
                    if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue;
                    int next = ny * width + nx;
                    if (basinIds[next] != DrainageBasinTopology.NO_BASIN
                            || fillLevel[next] != spillLevel
                            || !isDepressionCell(
                                    next,
                                    width,
                                    bounds,
                                    elevation,
                                    drainageOutlets,
                                    fillLevel)) {
                        continue;
                    }
                    basinIds[next] = basinId;
                    queue.addLast(next);
                }
            }

            basins.add(new DrainageBasin(
                    basinId,
                    cellCount,
                    spillLevel,
                    maximumDepth,
                    minX,
                    maxX,
                    minY,
                    maxY));
        }

        return new DenseDrainageBasinTopology(bounds, basinIds, basins);
    }

    private static void seed(
            PriorityQueue<FloodEntry> frontier,
            long[] fillLevel,
            boolean[] fixedOutlet,
            int index,
            long level) {
        if (level < fillLevel[index]) {
            fillLevel[index] = level;
            frontier.add(new FloodEntry(level, index));
        }
        fixedOutlet[index] = true;
    }

    private static boolean isDepressionCell(
            int index,
            int width,
            WorldBounds bounds,
            ElevationField elevation,
            StandingWaterTopology drainageOutlets,
            long[] fillLevel) {
        int localX = index % width;
        int localY = index / width;
        int x = bounds.minX() + localX;
        int y = bounds.minY() + localY;
        if (drainageOutlets.isStandingWaterAt(x, y)) return false;
        long terrain = elevation.elevationSubunitsAt(x, y);
        return terrain >= 0L
                && fillLevel[index] != Long.MAX_VALUE
                && fillLevel[index] > terrain;
    }

    private record FloodEntry(long level, int index) {
    }
}
