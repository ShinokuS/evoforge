package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.Arrays;

/**
 * Deterministic standing-water bathymetry over an already accepted submerged footprint.
 *
 * <p>The algorithm treats {@code elevation < 0} only as membership in an existing water body. Land
 * elevation is copied exactly. Submerged elevation is re-authored from body geometry so legacy
 * below-sea-level ranking does not become accidental bathymetric truth.</p>
 *
 * <p>The accepted smooth shoreline-distance profile remains the universal base morphology. For
 * boundary-connected seas/oceans only, broad land-side relief may causally permit a locally faster
 * coastal descent. Coastal character is blended from many nearby shoreline cells, carried offshore
 * through the shoreline-distance gradient, then mixed over a world-scaled connected-water window.
 * Competing shore fronts therefore meet as a broad transition rather than a Voronoi-like wedge.
 * Final seafloor elevation is never post-smoothed.</p>
 */
public final class BathymetryMorphologyAlgorithm implements BathymetryElevationAlgorithm {
    private static final int PPM = NormalizedValue.SCALE;
    private static final int DISTANCE_SCALE = 1_000;
    private static final int CARDINAL_DISTANCE = DISTANCE_SCALE;
    private static final int DIAGONAL_DISTANCE = 1_414;
    private static final int CARDINAL_CHARACTER_WEIGHT = 1_000;
    private static final int DIAGONAL_CHARACTER_WEIGHT = 707;
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
        int[] coastalCharacter = coastalCharacterField(
                elevation,
                shorelineDistance,
                width,
                height,
                calibration.coastalContextRadiusCells(),
                recipe);
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
                    coastalCharacter,
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

    /**
     * Produces one broad causal coastal-character field before underwater depth is authored.
     *
     * <p>Shoreline land cells first derive character from a broad land-only relief context. Their
     * character is blended over a wide source neighborhood, then the first submerged ring seeds an
     * inward propagation ordered by shoreline distance. The propagated fact is finally averaged over
     * a connected-water window whose radius is the same world-scaled coastal context. Horizontal and
     * vertical passes are evaluated in both orders and averaged so the blend has no preferred axis.
     * None of these operations touches final elevation.</p>
     */
    private static int[] coastalCharacterField(
            long[] elevation,
            int[] shorelineDistance,
            int width,
            int height,
            int contextRadius,
            BathymetryRecipe recipe) {
        LandReliefIntegral landRelief = landReliefIntegral(elevation, width, height);
        long[] characterMass = new long[elevation.length];
        long[] shorelineSupport = new long[elevation.length];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int cell = y * width + x;
                if (elevation[cell] < 0L || !touchesWater(cell, elevation, width, height)) continue;
                shorelineSupport[cell] = 1L;
                characterMass[cell] = coastalLandReliefPpm(
                        x,
                        y,
                        width,
                        height,
                        landRelief,
                        contextRadius,
                        recipe);
            }
        }

        long[] firstMass = boxSumField(characterMass, width, height, contextRadius);
        long[] firstSupport = boxSumField(shorelineSupport, width, height, contextRadius);
        int blendRadius = Math.max(2, contextRadius / 2);
        long[] blendedMass = boxSumField(firstMass, width, height, blendRadius);
        long[] blendedSupport = boxSumField(firstSupport, width, height, blendRadius);

        int[] nearshoreCharacter = new int[elevation.length];
        for (int cell = 0; cell < elevation.length; cell++) {
            if (blendedSupport[cell] <= 0L || blendedMass[cell] <= 0L) continue;
            nearshoreCharacter[cell] = (int) Math.min(PPM, blendedMass[cell] / blendedSupport[cell]);
        }

        int[] propagated = propagateCoastalCharacter(
                nearshoreCharacter,
                elevation,
                shorelineDistance,
                width,
                height);
        return broadBlendConnectedWaterCharacter(
                propagated,
                elevation,
                width,
                height,
                contextRadius);
    }

    private static int[] propagateCoastalCharacter(
            int[] nearshoreCharacter,
            long[] elevation,
            int[] shorelineDistance,
            int width,
            int height) {
        int waterCount = 0;
        for (int cell = 0; cell < elevation.length; cell++) {
            if (elevation[cell] < 0L && shorelineDistance[cell] < INFINITE_DISTANCE) waterCount++;
        }

        long[] order = new long[waterCount];
        int orderIndex = 0;
        for (int cell = 0; cell < elevation.length; cell++) {
            if (elevation[cell] >= 0L || shorelineDistance[cell] >= INFINITE_DISTANCE) continue;
            order[orderIndex++] = ((long) shorelineDistance[cell] << 32)
                    | (cell & 0xffff_ffffL);
        }
        Arrays.sort(order);

        int[] propagated = new int[elevation.length];
        for (long key : order) {
            int cell = (int) key;
            int distance = shorelineDistance[cell];
            if (distance <= DIAGONAL_DISTANCE) {
                propagated[cell] = nearshoreCharacter[cell];
                continue;
            }

            int x = cell % width;
            int y = cell / width;
            long weightedCharacter = 0L;
            int totalWeight = 0;
            for (int dy = -1; dy <= 1; dy++) {
                int neighborY = y + dy;
                if (neighborY < 0 || neighborY >= height) continue;
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dy == 0) continue;
                    int neighborX = x + dx;
                    if (neighborX < 0 || neighborX >= width) continue;
                    int neighbor = neighborY * width + neighborX;
                    if (elevation[neighbor] >= 0L || shorelineDistance[neighbor] >= distance) continue;
                    int weight = dx == 0 || dy == 0
                            ? CARDINAL_CHARACTER_WEIGHT
                            : DIAGONAL_CHARACTER_WEIGHT;
                    weightedCharacter += (long) propagated[neighbor] * weight;
                    totalWeight += weight;
                }
            }
            propagated[cell] = totalWeight > 0
                    ? (int) (weightedCharacter / totalWeight)
                    : nearshoreCharacter[cell];
        }
        return propagated;
    }

    private static int[] broadBlendConnectedWaterCharacter(
            int[] source,
            long[] elevation,
            int width,
            int height,
            int radius) {
        int[] horizontalThenVertical = blendVertical(
                blendHorizontal(source, elevation, width, height, radius),
                elevation,
                width,
                height,
                radius);
        int[] verticalThenHorizontal = blendHorizontal(
                blendVertical(source, elevation, width, height, radius),
                elevation,
                width,
                height,
                radius);

        int[] result = source.clone();
        for (int cell = 0; cell < result.length; cell++) {
            if (elevation[cell] >= 0L) continue;
            result[cell] = (horizontalThenVertical[cell] + verticalThenHorizontal[cell]) / 2;
        }
        return result;
    }

    private static int[] blendHorizontal(
            int[] source,
            long[] elevation,
            int width,
            int height,
            int radius) {
        int[] result = source.clone();
        for (int y = 0; y < height; y++) {
            int row = y * width;
            for (int x = 0; x < width; x++) {
                int cell = row + x;
                if (elevation[cell] >= 0L) continue;
                long sum = source[cell];
                int count = 1;
                boolean leftOpen = true;
                boolean rightOpen = true;
                for (int step = 1; step <= radius && (leftOpen || rightOpen); step++) {
                    if (leftOpen) {
                        int leftX = x - step;
                        if (leftX >= 0 && elevation[row + leftX] < 0L) {
                            sum += source[row + leftX];
                            count++;
                        } else {
                            leftOpen = false;
                        }
                    }
                    if (rightOpen) {
                        int rightX = x + step;
                        if (rightX < width && elevation[row + rightX] < 0L) {
                            sum += source[row + rightX];
                            count++;
                        } else {
                            rightOpen = false;
                        }
                    }
                }
                result[cell] = (int) (sum / count);
            }
        }
        return result;
    }

    private static int[] blendVertical(
            int[] source,
            long[] elevation,
            int width,
            int height,
            int radius) {
        int[] result = source.clone();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int cell = y * width + x;
                if (elevation[cell] >= 0L) continue;
                long sum = source[cell];
                int count = 1;
                boolean upOpen = true;
                boolean downOpen = true;
                for (int step = 1; step <= radius && (upOpen || downOpen); step++) {
                    if (upOpen) {
                        int upY = y - step;
                        if (upY >= 0 && elevation[upY * width + x] < 0L) {
                            sum += source[upY * width + x];
                            count++;
                        } else {
                            upOpen = false;
                        }
                    }
                    if (downOpen) {
                        int downY = y + step;
                        if (downY < height && elevation[downY * width + x] < 0L) {
                            sum += source[downY * width + x];
                            count++;
                        } else {
                            downOpen = false;
                        }
                    }
                }
                result[cell] = (int) (sum / count);
            }
        }
        return result;
    }

    private static boolean touchesWater(
            int cell,
            long[] elevation,
            int width,
            int height) {
        int x = cell % width;
        int y = cell / width;
        if (x > 0 && elevation[cell - 1] < 0L) return true;
        if (x + 1 < width && elevation[cell + 1] < 0L) return true;
        if (y > 0 && elevation[cell - width] < 0L) return true;
        return y + 1 < height && elevation[cell + width] < 0L;
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

    private static long[] boxSumField(
            long[] values,
            int width,
            int height,
            int radius) {
        int stride = width + 1;
        long[] integral = new long[Math.multiplyExact(stride, height + 1)];
        for (int y = 1; y <= height; y++) {
            for (int x = 1; x <= width; x++) {
                int cell = y * stride + x;
                int above = cell - stride;
                int left = cell - 1;
                int diagonal = above - 1;
                integral[cell] = Math.addExact(
                        values[(y - 1) * width + (x - 1)],
                        integral[above] + integral[left] - integral[diagonal]);
            }
        }

        long[] result = new long[values.length];
        for (int y = 0; y < height; y++) {
            int minY = Math.max(0, y - radius);
            int maxY = Math.min(height - 1, y + radius);
            for (int x = 0; x < width; x++) {
                int minX = Math.max(0, x - radius);
                int maxX = Math.min(width - 1, x + radius);
                result[y * width + x] = rectangleSum(
                        integral,
                        stride,
                        minX,
                        minY,
                        maxX,
                        maxY);
            }
        }
        return result;
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
            int[] coastalCharacter,
            int[] component,
            int componentSize,
            int width,
            int height,
            BathymetryCalibration calibration,
            BathymetryRecipe recipe) {
        int maximumDistance = maximumShorelineDistance(
                shorelineDistance,
                component,
                componentSize,
                width,
                height);
        long bodyDepthCap = bodyDepthCap(maximumDistance, calibration, recipe);
        long verticalCapacity = Math.negateExact(calibration.floorSubunits());
        boolean oceanConnected = touchesWorldBoundary(component, componentSize, width, height);

        for (int index = 0; index < componentSize; index++) {
            int cell = component[index];
            int distance = shorelineDistance[cell];
            if (distance >= INFINITE_DISTANCE) distance = maximumDistance;
            long baselineDepth = baselineDepth(distance, maximumDistance, bodyDepthCap);
            long depth = baselineDepth;

            if (oceanConnected) {
                long coastalDepth = causalCoastalDepth(
                        distance,
                        coastalCharacter[cell],
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

    private static long causalCoastalDepth(
            int shorelineDistance,
            int coastalCharacterPpm,
            long baselineDepth,
            long bodyDepthCap,
            BathymetryCalibration calibration,
            BathymetryRecipe recipe) {
        if (coastalCharacterPpm <= 0 || shorelineDistance <= 0) return 0L;

        int reliefCoordinatePpm = (int) Math.min(
                PPM,
                (long) coastalCharacterPpm * PPM / recipe.coastalReliefFullScalePpm());
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
            int radius,
            BathymetryRecipe recipe) {
        int minX = Math.max(0, x - radius);
        int maxX = Math.min(width - 1, x + radius);
        int minY = Math.max(0, y - radius);
        int maxY = Math.min(height - 1, y + radius);
        long sum = rectangleSum(
                integral.positiveHeightSum(),
                integral.stride(),
                minX,
                minY,
                maxX,
                maxY);
        int count = rectangleSum(
                integral.positiveLandCount(),
                integral.stride(),
                minX,
                minY,
                maxX,
                maxY);
        if (count <= 0 || sum <= 0L) return 0;

        long averageLandHeight = sum / count;
        long horizontalReference = Math.multiplyExact(
                (long) radius,
                ElevationField.SUBUNITS_PER_CELL);
        long reliefPpm = averageLandHeight * PPM / horizontalReference;
        return (int) Math.min(
                recipe.coastalReliefFullScalePpm(),
                Math.max(0L, reliefPpm));
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

    private record LandReliefIntegral(
            int stride,
            long[] positiveHeightSum,
            int[] positiveLandCount) {
    }
}
