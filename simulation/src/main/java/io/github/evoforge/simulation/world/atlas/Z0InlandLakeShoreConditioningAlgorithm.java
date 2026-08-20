package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.ArrayDeque;
import java.util.Arrays;

/**
 * Materializes inland-water membership below Z=0 and grades only a narrow adjacent dry collar.
 *
 * <p>The lake domain still owns the shoreline footprint. Conditioning never expands water and never
 * raises terrain. Nearby dry cells may only be lowered enough to respect a one-Z-per-cardinal-step
 * approach to the Z=0 shore; terrain outside the coast-derived collar remains bit-identical.</p>
 */
final class Z0InlandLakeShoreConditioningAlgorithm implements InlandLakeShoreConditioningAlgorithm {
    static final Z0InlandLakeShoreConditioningAlgorithm INSTANCE =
            new Z0InlandLakeShoreConditioningAlgorithm();

    private static final int[] DX = {-1, 1, 0, 0};
    private static final int[] DY = {0, 0, -1, 1};

    private Z0InlandLakeShoreConditioningAlgorithm() {
    }

    @Override
    public ElevationField condition(
            ElevationField continentalBase,
            InlandLakeDomain lakeDomain,
            V12LandformRecipe.CoastProfile coastProfile) {
        if (continentalBase == null || lakeDomain == null || coastProfile == null) {
            throw new IllegalArgumentException("lake shoreline inputs must not be null");
        }
        WorldBounds bounds = continentalBase.bounds();
        if (!sameHorizontalBounds(bounds, lakeDomain.bounds())) {
            throw new IllegalArgumentException("lake domain must match continental base horizontal bounds");
        }
        if (bounds.minZ() >= 0 || bounds.maxZ() <= 0) {
            throw new IllegalArgumentException("Z=0 inland lakes require elevation headroom below and above sea level");
        }

        int width = Math.toIntExact((long) bounds.maxX() - bounds.minX() + 1L);
        int height = Math.toIntExact((long) bounds.maxY() - bounds.minY() + 1L);
        int area = Math.multiplyExact(width, height);
        long[] result = new long[area];
        boolean[] lake = new boolean[area];
        int index = 0;
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                long original = continentalBase.elevationSubunitsAt(x, y);
                if (lakeDomain.isLakeIndex(index)) {
                    if (original <= ElevationGenerationStage.SEA_LEVEL_SUBUNITS) {
                        throw new IllegalStateException("inland lake domain overlapped existing standing water");
                    }
                    lake[index] = true;
                    result[index] = -1L;
                } else {
                    result[index] = original;
                }
                index++;
            }
        }

        int collarRadius = Math.max(1, coastProfile.transitionCells() / 4);
        int[] distance = cardinalDistanceFromLake(lake, width, height, collarRadius);
        for (int cell = 0; cell < area; cell++) {
            int steps = distance[cell];
            if (lake[cell] || steps <= 0 || steps > collarRadius || result[cell] <= 0L) continue;
            long maximumShoreCompatibleHeight = Math.multiplyExact(
                    (long) steps,
                    ElevationField.SUBUNITS_PER_CELL);
            maximumShoreCompatibleHeight = Math.max(1L, maximumShoreCompatibleHeight - 1L);
            result[cell] = Math.min(result[cell], maximumShoreCompatibleHeight);
        }

        return new DenseElevationField(bounds, result);
    }

    private static int[] cardinalDistanceFromLake(
            boolean[] lake,
            int width,
            int height,
            int maximumDistance) {
        int[] distance = new int[lake.length];
        Arrays.fill(distance, Integer.MAX_VALUE);
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int cell = 0; cell < lake.length; cell++) {
            if (!lake[cell]) continue;
            distance[cell] = 0;
            queue.addLast(cell);
        }
        while (!queue.isEmpty()) {
            int cell = queue.removeFirst();
            int nextDistance = distance[cell] + 1;
            if (nextDistance > maximumDistance) continue;
            int x = cell % width;
            int y = cell / width;
            for (int direction = 0; direction < DX.length; direction++) {
                int nx = x + DX[direction];
                int ny = y + DY[direction];
                if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue;
                int next = ny * width + nx;
                if (nextDistance >= distance[next]) continue;
                distance[next] = nextDistance;
                queue.addLast(next);
            }
        }
        return distance;
    }

    private static boolean sameHorizontalBounds(WorldBounds first, WorldBounds second) {
        return first.minX() == second.minX()
                && first.maxX() == second.maxX()
                && first.minY() == second.minY()
                && first.maxY() == second.maxY();
    }
}
