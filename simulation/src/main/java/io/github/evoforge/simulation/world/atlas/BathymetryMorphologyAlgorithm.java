package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.spatial.WorldBounds;

/**
 * Deterministic standing-water bathymetry over an already accepted submerged footprint.
 *
 * <p>The algorithm treats {@code elevation < 0} only as membership in an existing water body. Land
 * elevation is copied exactly. Submerged elevation is re-authored from body geometry so legacy
 * below-sea-level ranking does not become accidental bathymetric truth.</p>
 *
 * <p>Depth is driven by distance from the nearest shoreline and capped by both body width and the
 * calibrated world depth budget. A smooth quintic profile yields shallow littoral margins, a broad
 * transition slope and a flattened deep interior without generating Water or runtime Shapes.</p>
 */
public final class BathymetryMorphologyAlgorithm implements BathymetryElevationAlgorithm {
    private static final int PPM = NormalizedValue.SCALE;
    private static final int DISTANCE_SCALE = 1_000;
    private static final int CARDINAL_DISTANCE = DISTANCE_SCALE;
    private static final int DIAGONAL_DISTANCE = 1_414;
    private static final int INFINITE_DISTANCE = Integer.MAX_VALUE / 4;

    @Override
    public ElevationField generate(
            WorldGenesis genesis,
            ElevationField base,
            BathymetryCalibration calibration,
            BathymetryRecipe recipe) {
        if (genesis == null || base == null || calibration == null || recipe == null) {
            throw new IllegalArgumentException("bathymetry generation inputs must not be null");
        }

        WorldBounds bounds = genesis.spec().bounds();
        requireMatchingHorizontalBounds(bounds, base.bounds());
        int width = calibration.width();
        int height = calibration.height();
        if (width != horizontalWidth(bounds)
                || height != horizontalHeight(bounds)
                || calibration.area() != DenseElevationField.cellCount(bounds)) {
            throw new IllegalArgumentException("bathymetry calibration must match genesis horizontal bounds");
        }

        long[] elevation = copyBaseElevation(base, bounds, width, height);
        int[] shorelineDistance = shorelineDistance(elevation, width, height);
        boolean[] visited = new boolean[elevation.length];
        int[] component = new int[elevation.length];

        for (int cell = 0; cell < elevation.length; cell++) {
            if (elevation[cell] >= 0L || visited[cell]) continue;
            int componentSize = collectComponent(
                    cell,
                    elevation,
                    visited,
                    component,
                    width,
                    height);
            authorComponentBathymetry(
                    elevation,
                    shorelineDistance,
                    component,
                    componentSize,
                    width,
                    height,
                    calibration,
                    recipe);
        }

        return new DenseElevationField(bounds, elevation);
    }

    private static long[] copyBaseElevation(
            ElevationField base,
            WorldBounds targetBounds,
            int width,
            int height) {
        long[] elevation = new long[Math.multiplyExact(width, height)];
        int index = 0;
        for (int localY = 0; localY < height; localY++) {
            int y = targetBounds.minY() + localY;
            for (int localX = 0; localX < width; localX++) {
                int x = targetBounds.minX() + localX;
                elevation[index++] = base.elevationSubunitsAt(x, y);
            }
        }
        return elevation;
    }

    private static int[] shorelineDistance(long[] elevation, int width, int height) {
        int[] distance = new int[elevation.length];
        boolean hasLand = false;
        for (int cell = 0; cell < elevation.length; cell++) {
            if (elevation[cell] >= 0L) {
                distance[cell] = 0;
                hasLand = true;
            } else {
                distance[cell] = INFINITE_DISTANCE;
            }
        }
        if (!hasLand) return distance;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int cell = y * width + x;
                if (distance[cell] == 0) continue;
                int best = distance[cell];
                if (x > 0) best = Math.min(best, plus(distance[cell - 1], CARDINAL_DISTANCE));
                if (y > 0) best = Math.min(best, plus(distance[cell - width], CARDINAL_DISTANCE));
                if (x > 0 && y > 0) {
                    best = Math.min(best, plus(distance[cell - width - 1], DIAGONAL_DISTANCE));
                }
                if (x + 1 < width && y > 0) {
                    best = Math.min(best, plus(distance[cell - width + 1], DIAGONAL_DISTANCE));
                }
                distance[cell] = best;
            }
        }

        for (int y = height - 1; y >= 0; y--) {
            for (int x = width - 1; x >= 0; x--) {
                int cell = y * width + x;
                if (distance[cell] == 0) continue;
                int best = distance[cell];
                if (x + 1 < width) best = Math.min(best, plus(distance[cell + 1], CARDINAL_DISTANCE));
                if (y + 1 < height) best = Math.min(best, plus(distance[cell + width], CARDINAL_DISTANCE));
                if (x + 1 < width && y + 1 < height) {
                    best = Math.min(best, plus(distance[cell + width + 1], DIAGONAL_DISTANCE));
                }
                if (x > 0 && y + 1 < height) {
                    best = Math.min(best, plus(distance[cell + width - 1], DIAGONAL_DISTANCE));
                }
                distance[cell] = best;
            }
        }
        return distance;
    }

    private static int collectComponent(
            int start,
            long[] elevation,
            boolean[] visited,
            int[] queue,
            int width,
            int height) {
        int head = 0;
        int tail = 0;
        queue[tail++] = start;
        visited[start] = true;

        while (head < tail) {
            int cell = queue[head++];
            int x = cell % width;
            int y = cell / width;
            if (x > 0) tail = enqueueWater(cell - 1, elevation, visited, queue, tail);
            if (x + 1 < width) tail = enqueueWater(cell + 1, elevation, visited, queue, tail);
            if (y > 0) tail = enqueueWater(cell - width, elevation, visited, queue, tail);
            if (y + 1 < height) tail = enqueueWater(cell + width, elevation, visited, queue, tail);
        }
        return tail;
    }

    private static int enqueueWater(
            int cell,
            long[] elevation,
            boolean[] visited,
            int[] queue,
            int tail) {
        if (visited[cell] || elevation[cell] >= 0L) return tail;
        visited[cell] = true;
        queue[tail] = cell;
        return tail + 1;
    }

    private static void authorComponentBathymetry(
            long[] elevation,
            int[] shorelineDistance,
            int[] component,
            int componentSize,
            int width,
            int height,
            BathymetryCalibration calibration,
            BathymetryRecipe recipe) {
        int maximumDistance = 0;
        boolean finiteShore = false;
        for (int index = 0; index < componentSize; index++) {
            int distance = shorelineDistance[component[index]];
            if (distance >= INFINITE_DISTANCE) continue;
            finiteShore = true;
            maximumDistance = Math.max(maximumDistance, distance);
        }
        if (!finiteShore) {
            maximumDistance = Math.max(
                    DISTANCE_SCALE,
                    Math.min(width, height) * DISTANCE_SCALE / 2);
        }
        maximumDistance = Math.max(DISTANCE_SCALE, maximumDistance);

        long bodyDepthCap = bodyDepthCap(maximumDistance, calibration, recipe);
        long verticalCapacity = Math.negateExact(calibration.floorSubunits());
        for (int index = 0; index < componentSize; index++) {
            int cell = component[index];
            int distance = shorelineDistance[cell];
            if (distance >= INFINITE_DISTANCE) distance = maximumDistance;
            int coordinatePpm = (int) Math.min(
                    PPM,
                    (long) distance * PPM / maximumDistance);
            int profilePpm = smootherStepPpm(coordinatePpm);
            long depth = Math.max(1L, bodyDepthCap * profilePpm / PPM);
            depth = Math.min(verticalCapacity, depth);
            elevation[cell] = -depth;
        }
    }

    private static long bodyDepthCap(
            int maximumDistance,
            BathymetryCalibration calibration,
            BathymetryRecipe recipe) {
        long slopeSupported = Math.multiplyExact(
                (long) maximumDistance,
                calibration.maximumCardinalFallSubunits())
                / recipe.profileGradientBoundMilli();
        return Math.min(
                calibration.worldDepthCapSubunits(),
                Math.max(1L, slopeSupported));
    }

    /** Quintic smootherstep in normalized integer space: 6t^5 - 15t^4 + 10t^3. */
    private static int smootherStepPpm(int coordinatePpm) {
        long t = Math.max(0, Math.min(PPM, coordinatePpm));
        long t2 = t * t / PPM;
        long t3 = t2 * t / PPM;
        long t4 = t3 * t / PPM;
        long t5 = t4 * t / PPM;
        long value = 6L * t5 - 15L * t4 + 10L * t3;
        return (int) Math.max(0L, Math.min((long) PPM, value));
    }

    private static int plus(int distance, int increment) {
        if (distance >= INFINITE_DISTANCE - increment) return INFINITE_DISTANCE;
        return distance + increment;
    }

    private static void requireMatchingHorizontalBounds(WorldBounds expected, WorldBounds actual) {
        if (expected.minX() != actual.minX()
                || expected.maxX() != actual.maxX()
                || expected.minY() != actual.minY()
                || expected.maxY() != actual.maxY()) {
            throw new IllegalArgumentException("bathymetry base must share genesis horizontal bounds");
        }
    }

    private static int horizontalWidth(WorldBounds bounds) {
        return Math.toIntExact((long) bounds.maxX() - bounds.minX() + 1L);
    }

    private static int horizontalHeight(WorldBounds bounds) {
        return Math.toIntExact((long) bounds.maxY() - bounds.minY() + 1L);
    }
}
