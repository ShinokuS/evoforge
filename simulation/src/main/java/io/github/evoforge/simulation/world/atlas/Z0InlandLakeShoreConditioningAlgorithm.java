package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.ArrayDeque;
import java.util.Arrays;

/**
 * Materializes inland-water membership below Z=0 and caps only the immediate dry shore to the same
 * broad coast profile used by accepted V12 terrain.
 *
 * <p>The algorithm never invents a bowl. Lake shape is already owned by {@link InlandLakeDomain};
 * this stage only gives that domain the same Z=0 shoreline contract as existing standing water.</p>
 */
final class Z0InlandLakeShoreConditioningAlgorithm implements InlandLakeShoreConditioningAlgorithm {
    static final Z0InlandLakeShoreConditioningAlgorithm INSTANCE =
            new Z0InlandLakeShoreConditioningAlgorithm();

    private static final int PPM = NormalizedValue.SCALE;
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
        int[] lakeDistance = distanceFromLake(lakeDomain, width, height);
        long positiveAmplitude = Math.multiplyExact(
                (long) bounds.maxZ(), ElevationField.SUBUNITS_PER_CELL);

        int index = 0;
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                long original = continentalBase.elevationSubunitsAt(x, y);
                if (lakeDomain.isLakeIndex(index)) {
                    if (original <= ElevationGenerationStage.SEA_LEVEL_SUBUNITS) {
                        throw new IllegalStateException("inland lake domain overlapped existing standing water");
                    }
                    result[index++] = -1L;
                    continue;
                }
                if (original <= ElevationGenerationStage.SEA_LEVEL_SUBUNITS) {
                    result[index++] = original;
                    continue;
                }

                int distance = lakeDistance[index];
                if (distance <= 0 || distance > coastProfile.transitionCells()) {
                    result[index++] = original;
                    continue;
                }
                long coordinatePpm = (long) distance * PPM / coastProfile.transitionCells();
                int interiorityPpm = smoothStepPpm(coordinatePpm);
                int shoreCapPpm = coastProfile.baseHeightPpm()
                        + (int) ((long) interiorityPpm * coastProfile.interiorHeightPpm() / PPM);
                long shoreCap = positiveNormalizedHeight(shoreCapPpm, positiveAmplitude);
                result[index++] = Math.min(original, shoreCap);
            }
        }
        return new DenseElevationField(bounds, result);
    }

    private static int[] distanceFromLake(InlandLakeDomain lakeDomain, int width, int height) {
        int area = Math.multiplyExact(width, height);
        int[] distance = new int[area];
        Arrays.fill(distance, Integer.MAX_VALUE);
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int cell = 0; cell < area; cell++) {
            if (!lakeDomain.isLakeIndex(cell)) continue;
            distance[cell] = 0;
            queue.addLast(cell);
        }
        while (!queue.isEmpty()) {
            int cell = queue.removeFirst();
            int x = cell % width;
            int y = cell / width;
            int nextDistance = distance[cell] + 1;
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

    private static int smoothStepPpm(long coordinatePpm) {
        long coordinate = Math.max(0L, Math.min((long) PPM, coordinatePpm));
        long squared = coordinate * coordinate;
        return (int) (squared
                * (3L * PPM - 2L * coordinate)
                / ((long) PPM * PPM));
    }

    private static long positiveNormalizedHeight(int heightPpm, long amplitude) {
        if (amplitude <= 1L) return Math.max(1L, amplitude);
        return 1L + ((amplitude - 1L) * heightPpm) / PPM;
    }

    private static boolean sameHorizontalBounds(WorldBounds first, WorldBounds second) {
        return first.minX() == second.minX()
                && first.maxX() == second.maxX()
                && first.minY() == second.minY()
                && first.maxY() == second.maxY();
    }
}
