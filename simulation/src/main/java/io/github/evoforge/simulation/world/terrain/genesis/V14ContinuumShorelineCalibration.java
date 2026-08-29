package io.github.evoforge.simulation.world.terrain.genesis;

import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import java.util.Arrays;

/**
 * Fixed-budget real-coordinate shoreline landmarks for regional V14 bathymetry.
 *
 * <p>The retained points are actual sampled dry coordinates adjacent to sampled water, not a scaled
 * terrain raster. Unit-resolution requests still use their exact local chamfer field; these landmarks
 * are only the global fact needed when the nearest shore lies beyond the request halo and to estimate
 * the historical ocean component's maximum shoreline distance.</p>
 */
public final class V14ContinuumShorelineCalibration {
    private static final int SAMPLE_SIDE = 32;
    private static final int DISTANCE_SCALE = 1_000;
    private static final int DIAGONAL_DISTANCE = 1_414;

    private final ContinuumWorldDomain domain;
    private final long[] shoreX;
    private final long[] shoreY;
    private final int maximumShorelineDistance;

    private V14ContinuumShorelineCalibration(
            ContinuumWorldDomain domain,
            long[] shoreX,
            long[] shoreY,
            int maximumShorelineDistance) {
        this.domain = domain;
        this.shoreX = shoreX;
        this.shoreY = shoreY;
        this.maximumShorelineDistance = maximumShorelineDistance;
    }

    public static V14ContinuumShorelineCalibration prepare(
            ContinuumWorldDomain domain,
            V12LandRankPlan land) {
        if (domain == null || land == null) {
            throw new IllegalArgumentException("shoreline calibration inputs must not be null");
        }
        int columns = Math.toIntExact(Math.min(SAMPLE_SIDE, domain.width()));
        int rows = Math.toIntExact(Math.min(SAMPLE_SIDE, domain.height()));
        long[] xs = new long[columns];
        long[] ys = new long[rows];
        for (int x = 0; x < columns; x++) xs[x] = sampleCoordinate(domain.width(), x, columns);
        for (int y = 0; y < rows; y++) ys[y] = sampleCoordinate(domain.height(), y, rows);

        boolean[] dry = new boolean[Math.multiplyExact(columns, rows)];
        boolean[] oneCell = new boolean[1];
        boolean hasLand = false;
        boolean hasWater = false;
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < columns; x++) {
                // Scalar isLand() recursively re-evaluates the V14 two-pass coast stencil. The
                // one-cell window path is mathematically identical, but materializes the five-by-five
                // raw-score support once and applies the two relaxation passes explicitly. This keeps
                // shoreline calibration sparse while avoiding repeated recursive coast work.
                land.fillLandWindow(xs[x], ys[y], 1, 1, oneCell);
                boolean value = oneCell[0];
                dry[y * columns + x] = value;
                hasLand |= value;
                hasWater |= !value;
            }
        }
        if (!hasLand) {
            int fallback = scaledDistance(Math.max(1L, Math.min(domain.width(), domain.height()) / 2L));
            return new V14ContinuumShorelineCalibration(domain, new long[0], new long[0], fallback);
        }
        if (!hasWater) {
            return new V14ContinuumShorelineCalibration(domain, new long[0], new long[0], DISTANCE_SCALE);
        }

        long[] candidateX = new long[dry.length];
        long[] candidateY = new long[dry.length];
        int count = 0;
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < columns; x++) {
                int cell = y * columns + x;
                if (!dry[cell] || !touchesSampledWater(dry, x, y, columns, rows)) continue;
                candidateX[count] = xs[x];
                candidateY[count] = ys[y];
                count++;
            }
        }
        if (count == 0) {
            for (int y = 0; y < rows; y++) {
                for (int x = 0; x < columns; x++) {
                    if (!dry[y * columns + x]) continue;
                    candidateX[count] = xs[x];
                    candidateY[count] = ys[y];
                    count++;
                }
            }
        }
        long[] shoreX = Arrays.copyOf(candidateX, count);
        long[] shoreY = Arrays.copyOf(candidateY, count);

        int maximumDistance = DISTANCE_SCALE;
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < columns; x++) {
                if (dry[y * columns + x]) continue;
                maximumDistance = Math.max(
                        maximumDistance,
                        nearestDistance(xs[x], ys[y], shoreX, shoreY));
            }
        }
        return new V14ContinuumShorelineCalibration(
                domain,
                shoreX,
                shoreY,
                Math.max(DISTANCE_SCALE, maximumDistance));
    }

    public int maximumShorelineDistance() {
        return maximumShorelineDistance;
    }

    public int distanceMilliAt(long x, long y) {
        if (!domain.contains(x, y)) {
            throw new IllegalArgumentException("coordinate lies outside shoreline calibration domain");
        }
        if (shoreX.length == 0) return maximumShorelineDistance;
        return nearestDistance(x, y, shoreX, shoreY);
    }

    public int landmarkCount() {
        return shoreX.length;
    }

    private static boolean touchesSampledWater(
            boolean[] dry,
            int x,
            int y,
            int width,
            int height) {
        if (x > 0 && !dry[y * width + x - 1]) return true;
        if (x + 1 < width && !dry[y * width + x + 1]) return true;
        if (y > 0 && !dry[(y - 1) * width + x]) return true;
        return y + 1 < height && !dry[(y + 1) * width + x];
    }

    private static int nearestDistance(long x, long y, long[] shoreX, long[] shoreY) {
        long best = Integer.MAX_VALUE / 4L;
        for (int index = 0; index < shoreX.length; index++) {
            long dx = Math.abs(x - shoreX[index]);
            long dy = Math.abs(y - shoreY[index]);
            long diagonal = Math.min(dx, dy);
            long cardinal = Math.max(dx, dy) - diagonal;
            long distance = diagonal * DIAGONAL_DISTANCE + cardinal * DISTANCE_SCALE;
            if (distance < best) best = distance;
        }
        return Math.toIntExact(Math.min(Integer.MAX_VALUE / 4L, Math.max(1L, best)));
    }

    private static long sampleCoordinate(long extent, int bucket, int buckets) {
        if (buckets == extent) return bucket;
        long start = (long) bucket * extent / buckets;
        long endExclusive = (long) (bucket + 1) * extent / buckets;
        return Math.min(extent - 1L, start + Math.max(0L, (endExclusive - start - 1L) / 2L));
    }

    private static int scaledDistance(long cells) {
        return Math.toIntExact(Math.min(Integer.MAX_VALUE / 4L, cells * DISTANCE_SCALE));
    }
}
