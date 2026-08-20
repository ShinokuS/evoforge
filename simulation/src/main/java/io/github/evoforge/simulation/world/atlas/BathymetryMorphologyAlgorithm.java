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
 * <p>The accepted smooth shoreline-distance profile remains the universal base morphology. For
 * boundary-connected seas/oceans only, a broad land-side relief context may causally permit a
 * locally faster coastal descent. Coastal character is sampled at the nearest land source rather
 * than independently from each water cell, so one broad landform authors one broad coastal segment
 * instead of a noisy per-cell field. The causal contribution is monotone and fades automatically as
 * the accepted baseline approaches the body's depth cap; narrow bays therefore remain shallow when
 * they do not have enough horizontal room.</p>
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
        ShorelineField shoreline = shorelineField(elevation, width, height);
        LandReliefIntegral landRelief = landReliefIntegral(elevation, width, height);
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
                    shoreline,
                    landRelief,
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

    /**
     * Approximate Euclidean shoreline distance plus the deterministic nearest-land source that won
     * the same chamfer transform. Keeping source provenance lets broad land morphology continue into
     * water without re-sampling a shrinking/disappearing land window at every submerged cell.
     */
    private static ShorelineField shorelineField(long[] elevation, int width, int height) {
        int[] distance = new int[elevation.length];
        int[] nearestLand = new int[elevation.length];
        boolean hasLand = false;
        for (int cell = 0; cell < elevation.length; cell++) {
            if (elevation[cell] >= 0L) {
                distance[cell] = 0;
                nearestLand[cell] = cell;
                hasLand = true;
            } else {
                distance[cell] = INFINITE_DISTANCE;
                nearestLand[cell] = -1;
            }
        }
        if (!hasLand) return new ShorelineField(distance, nearestLand);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int cell = y * width + x;
                if (distance[cell] == 0) continue;
                int best = distance[cell];
                int source = nearestLand[cell];

                if (x > 0) {
                    int neighbor = cell - 1;
                    int candidate = plus(distance[neighbor], CARDINAL_DISTANCE);
                    if (candidate < best) {
                        best = candidate;
                        source = nearestLand[neighbor];
                    }
                }
                if (y > 0) {
                    int neighbor = cell - width;
                    int candidate = plus(distance[neighbor], CARDINAL_DISTANCE);
                    if (candidate < best) {
                        best = candidate;
                        source = nearestLand[neighbor];
                    }
                }
                if (x > 0 && y > 0) {
                    int neighbor = cell - width - 1;
                    int candidate = plus(distance[neighbor], DIAGONAL_DISTANCE);
                    if (candidate < best) {
                        best = candidate;
                        source = nearestLand[neighbor];
                    }
                }
                if (x + 1 < width && y > 0) {
                    int neighbor = cell - width + 1;
                    int candidate = plus(distance[neighbor], DIAGONAL_DISTANCE);
                    if (candidate < best) {
                        best = candidate;
                        source = nearestLand[neighbor];
                    }
                }
                distance[cell] = best;
                nearestLand[cell] = source;
            }
        }

        for (int y = height - 1; y >= 0; y--) {
            for (int x = width - 1; x >= 0; x--) {
                int cell = y * width + x;
                if (distance[cell] == 0) continue;
                int best = distance[cell];
                int source = nearestLand[cell];

                if (x + 1 < width) {
                    int neighbor = cell + 1;
                    int candidate = plus(distance[neighbor], CARDINAL_DISTANCE);
                    if (candidate < best) {
                        best = candidate;
                        source = nearestLand[neighbor];
                    }
                }
                if (y + 1 < height) {
                    int neighbor = cell + width;
                    int candidate = plus(distance[neighbor], CARDINAL_DISTANCE);
                    if (candidate < best) {
                        best = candidate;
                        source = nearestLand[neighbor];
                    }
                }
                if (x + 1 < width && y + 1 < height) {
                    int neighbor = cell + width + 1;
                    int candidate = plus(distance[neighbor], DIAGONAL_DISTANCE);
                    if (candidate < best) {
                        best = candidate;
                        source = nearestLand[neighbor];
                    }
                }
                if (x > 0 && y + 1 < height) {
                    int neighbor = cell + width - 1;
                    int candidate = plus(distance[neighbor], DIAGONAL_DISTANCE);
                    if (candidate < best) {
                        best = candidate;
                        source = nearestLand[neighbor];
                    }
                }
                distance[cell] = best;
                nearestLand[cell] = source;
            }
        }
        return new ShorelineField(distance, nearestLand);
    }

    private static LandReliefIntegral landReliefIntegral(long[] elevation, int width, int height) {
        int stride = width + 1;
        int cells = Math.multiplyExact(stride, height + 1);
        long[] positiveHeightSum = new long[cells];
        int[] positiveLandCount = new int[cells];

        for (int y = 1; y <= height; y++) {
            for (int x = 1; x <= width; x++) {
                long value = elevation[(y - 1) * width + (x - 1)];
                long positiveHeight = Math.max(0L, value);
                int positiveLand = value > 0L ? 1 : 0;
                int cell = y * stride + x;
                int above = cell - stride;
                int left = cell - 1;
                int diagonal = above - 1;
                positiveHeightSum[cell] = Math.addExact(
                        positiveHeight,
                        positiveHeightSum[above] + positiveHeightSum[left] - positiveHeightSum[diagonal]);
                positiveLandCount[cell] = positiveLand
                        + positiveLandCount[above]
                        + positiveLandCount[left]
                        - positiveLandCount[diagonal];
            }
        }
        return new LandReliefIntegral(stride, positiveHeightSum, positiveLandCount);
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
            ShorelineField shoreline,
            LandReliefIntegral landRelief,
            int[] component,
            int componentSize,
            int width,
            int height,
            BathymetryCalibration calibration,
            BathymetryRecipe recipe) {
        int maximumDistance = maximumShorelineDistance(
                shoreline.distance(),
                component,
                componentSize,
                width,
                height);
        long bodyDepthCap = bodyDepthCap(maximumDistance, calibration, recipe);
        long verticalCapacity = Math.negateExact(calibration.floorSubunits());
        boolean oceanConnected = touchesWorldBoundary(component, componentSize, width, height);

        for (int index = 0; index < componentSize; index++) {
            int cell = component[index];
            int distance = shoreline.distance()[cell];
            if (distance >= INFINITE_DISTANCE) distance = maximumDistance;
            long baselineDepth = baselineDepth(distance, maximumDistance, bodyDepthCap);
            long depth = baselineDepth;

            if (oceanConnected) {
                long coastalDepth = causalCoastalDepth(
                        cell,
                        distance,
                        shoreline.nearestLand()[cell],
                        width,
                        height,
                        landRelief,
                        baselineDepth,
                        bodyDepthCap,
                        calibration,
                        recipe);
                depth = Math.max(depth, coastalDepth);
            }

            depth = Math.min(bodyDepthCap, depth);
            depth = Math.min(verticalCapacity, depth);
            elevation[cell] = -Math.max(1L, depth);
        }
    }

    private static int maximumShorelineDistance(
            int[] shorelineDistance,
            int[] component,
            int componentSize,
            int width,
            int height) {
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
        return Math.max(DISTANCE_SCALE, maximumDistance);
    }

    private static boolean touchesWorldBoundary(
            int[] component,
            int componentSize,
            int width,
            int height) {
        for (int index = 0; index < componentSize; index++) {
            int cell = component[index];
            int x = cell % width;
            int y = cell / width;
            if (x == 0 || x == width - 1 || y == 0 || y == height - 1) return true;
        }
        return false;
    }

    private static long baselineDepth(int distance, int maximumDistance, long bodyDepthCap) {
        int coordinatePpm = (int) Math.min(
                PPM,
                (long) distance * PPM / maximumDistance);
        int profilePpm = smootherStepPpm(coordinatePpm);
        return Math.max(1L, bodyDepthCap * profilePpm / PPM);
    }

    /**
     * Builds one monotone coastal continuation from the broad relief around the nearest land source.
     *
     * <p>The first term is the fastest depth that the causal coast may geometrically support. The
     * second term limits how far that coast may depart from the accepted baseline. That extra budget
     * shrinks in direct proportion to remaining body-depth headroom, so the coastal contribution
     * returns continuously to zero as the baseline reaches its deep interior cap.</p>
     */
    private static long causalCoastalDepth(
            int cell,
            int shorelineDistance,
            int nearestLand,
            int width,
            int height,
            LandReliefIntegral landRelief,
            long baselineDepth,
            long bodyDepthCap,
            BathymetryCalibration calibration,
            BathymetryRecipe recipe) {
        if (nearestLand < 0 || shorelineDistance <= 0) return 0L;

        int sourceX = nearestLand % width;
        int sourceY = nearestLand / width;
        int reliefPpm = coastalLandReliefPpm(
                sourceX,
                sourceY,
                width,
                height,
                landRelief,
                calibration.coastalContextRadiusCells());
        if (reliefPpm <= 0) return 0L;

        int reliefCoordinatePpm = (int) Math.min(
                PPM,
                (long) reliefPpm * PPM / recipe.coastalReliefFullScalePpm());
        int reliefCharacterPpm = smootherStepPpm(reliefCoordinatePpm);
        long localFall = calibration.coastalMinimumFallSubunits()
                + (calibration.coastalMaximumFallSubunits()
                                - calibration.coastalMinimumFallSubunits())
                        * reliefCharacterPpm
                        / PPM;
        if (localFall <= 0L) return 0L;

        long geometricDepth = (long) shorelineDistance * localFall / DISTANCE_SCALE;
        int supportedSteps = Math.max(2, calibration.coastalContextRadiusCells() / 2);
        long requestedExtra = Math.multiplyExact(localFall, (long) supportedSteps);
        long maximumExtra = Math.min(bodyDepthCap, requestedExtra);
        long remainingDepth = Math.max(0L, bodyDepthCap - baselineDepth);
        long fadedExtra = bodyDepthCap <= 0L
                ? 0L
                : maximumExtra * remainingDepth / bodyDepthCap;
        long parallelDepth = Math.addExact(baselineDepth, fadedExtra);

        return Math.min(bodyDepthCap, Math.min(geometricDepth, parallelDepth));
    }

    private static int coastalLandReliefPpm(
            int x,
            int y,
            int width,
            int height,
            LandReliefIntegral integral,
            int radius) {
        int minX = Math.max(0, x - radius);
        int maxX = Math.min(width - 1, x + radius);
        int minY = Math.max(0, y - radius);
        int maxY = Math.min(height - 1, y + radius);
        long sum = rectangleSum(integral.positiveHeightSum(), integral.stride(), minX, minY, maxX, maxY);
        int count = rectangleSum(integral.positiveLandCount(), integral.stride(), minX, minY, maxX, maxY);
        if (count <= 0 || sum <= 0L) return 0;

        long averageLandHeight = sum / count;
        long horizontalReference = Math.multiplyExact(
                (long) radius,
                ElevationField.SUBUNITS_PER_CELL);
        return (int) Math.min(
                PPM,
                averageLandHeight * PPM / horizontalReference);
    }

    private static long rectangleSum(
            long[] integral,
            int stride,
            int minX,
            int minY,
            int maxX,
            int maxY) {
        int left = minX;
        int top = minY;
        int right = maxX + 1;
        int bottom = maxY + 1;
        return integral[bottom * stride + right]
                - integral[top * stride + right]
                - integral[bottom * stride + left]
                + integral[top * stride + left];
    }

    private static int rectangleSum(
            int[] integral,
            int stride,
            int minX,
            int minY,
            int maxX,
            int maxY) {
        int left = minX;
        int top = minY;
        int right = maxX + 1;
        int bottom = maxY + 1;
        return integral[bottom * stride + right]
                - integral[top * stride + right]
                - integral[bottom * stride + left]
                + integral[top * stride + left];
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

    private record ShorelineField(int[] distance, int[] nearestLand) {
    }

    private record LandReliefIntegral(
            int stride,
            long[] positiveHeightSum,
            int[] positiveLandCount) {
    }
}
