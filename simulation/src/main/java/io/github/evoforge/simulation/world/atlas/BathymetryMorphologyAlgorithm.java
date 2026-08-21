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
    private static final int RADIX = 256;
    private static final int RADIX_LEVELS = Long.BYTES;

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
        CoastalCharacterResult coastal = coastalCharacterField(
                elevation,
                shorelineDistance,
                width,
                height,
                calibration.coastalContextRadiusCells(),
                recipe);
        int[] coastalCharacter = coastal.character();
        int[] component = coastal.componentScratch();
        long[] visited = new long[Math.toIntExact(((long) elevation.length + 63L) >>> 6)];

        for (int cell = 0; cell < elevation.length; cell++) {
            if (elevation[cell] >= 0L || isMarked(visited, cell)) continue;
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

        return DenseElevationField.takeOwnership(bounds, elevation);
    }

    private static long[] copyBaseElevation(
            ElevationField base,
            WorldBounds targetBounds,
            int width,
            int height) {
        if (base instanceof DenseElevationField dense) {
            long[] storage = dense.readOnlyStorage();
            if (storage.length != Math.multiplyExact(width, height)) {
                throw new IllegalStateException("dense bathymetry base storage does not match world area");
            }
            return storage.clone();
        }

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
    private static CoastalCharacterResult coastalCharacterField(
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

        int blendRadius = Math.max(2, contextRadius / 2);
        DenseSlidingBoxSum.Workspace boxWorkspace = DenseSlidingBoxSum.workspace(
                width,
                height,
                Math.max(contextRadius, blendRadius));
        DenseSlidingBoxSum.sumInto(
                characterMass,
                width,
                height,
                contextRadius,
                characterMass,
                boxWorkspace);
        DenseSlidingBoxSum.sumInto(
                shorelineSupport,
                width,
                height,
                contextRadius,
                shorelineSupport,
                boxWorkspace);
        DenseSlidingBoxSum.sumInto(
                characterMass,
                width,
                height,
                blendRadius,
                characterMass,
                boxWorkspace);
        DenseSlidingBoxSum.sumInto(
                shorelineSupport,
                width,
                height,
                blendRadius,
                shorelineSupport,
                boxWorkspace);

        int[] nearshoreCharacter = new int[elevation.length];
        for (int cell = 0; cell < elevation.length; cell++) {
            if (shorelineSupport[cell] <= 0L || characterMass[cell] <= 0L) continue;
            nearshoreCharacter[cell] = (int) Math.min(PPM, characterMass[cell] / shorelineSupport[cell]);
        }

        int[] componentScratch = propagateCoastalCharacterInPlace(
                nearshoreCharacter,
                elevation,
                shorelineDistance,
                width,
                height);
        broadBlendConnectedWaterCharacterInPlace(
                nearshoreCharacter,
                elevation,
                width,
                height,
                contextRadius);
        return new CoastalCharacterResult(nearshoreCharacter, componentScratch);
    }

    /**
     * Propagates in exact (distance, cell-index) order without a 64-bit key array or comparison sort.
     *
     * <p>The existing nearshore array is safe to update in place because every dependency has a
     * strictly smaller shoreline distance. Before a cell is visited its entry still contains the
     * original nearshore fallback; afterwards it contains the propagated value. The primitive cell
     * order is retained and later reused as the component BFS workspace.</p>
     */
    private static int[] propagateCoastalCharacterInPlace(
            int[] character,
            long[] elevation,
            int[] shorelineDistance,
            int width,
            int height) {
        int waterCount = 0;
        int finiteCount = 0;
        for (int cell = 0; cell < elevation.length; cell++) {
            if (elevation[cell] >= 0L) continue;
            waterCount++;
            if (shorelineDistance[cell] < INFINITE_DISTANCE) finiteCount++;
        }

        int[] order = new int[waterCount];
        int orderIndex = 0;
        for (int cell = 0; cell < elevation.length; cell++) {
            if (elevation[cell] >= 0L || shorelineDistance[cell] >= INFINITE_DISTANCE) continue;
            order[orderIndex++] = cell;
        }
        if (orderIndex != finiteCount) {
            throw new IllegalStateException("bathymetry propagation count changed during materialization");
        }
        radixSortPropagationOrder(order, finiteCount, shorelineDistance);

        for (int orderPosition = 0; orderPosition < finiteCount; orderPosition++) {
            int cell = order[orderPosition];
            int distance = shorelineDistance[cell];
            if (distance <= DIAGONAL_DISTANCE) continue;

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
                    weightedCharacter += (long) character[neighbor] * weight;
                    totalWeight += weight;
                }
            }
            if (totalWeight > 0) {
                character[cell] = (int) (weightedCharacter / totalWeight);
            }
        }
        return order;
    }

    /** In-place MSD American-flag radix sort for the exact legacy 64-bit propagation key. */
    private static void radixSortPropagationOrder(
            int[] order,
            int length,
            int[] shorelineDistance) {
        if (length <= 1) return;
        int[][] counts = new int[RADIX_LEVELS][RADIX];
        int[][] starts = new int[RADIX_LEVELS][RADIX];
        int[][] next = new int[RADIX_LEVELS][RADIX];
        radixSortPropagationRange(
                order,
                0,
                length,
                56,
                0,
                shorelineDistance,
                counts,
                starts,
                next);
    }

    private static void radixSortPropagationRange(
            int[] order,
            int from,
            int to,
            int shift,
            int depth,
            int[] shorelineDistance,
            int[][] countsByDepth,
            int[][] startsByDepth,
            int[][] nextByDepth) {
        if (to - from <= 1 || shift < 0) return;

        int[] counts = countsByDepth[depth];
        int[] starts = startsByDepth[depth];
        int[] next = nextByDepth[depth];
        Arrays.fill(counts, 0);
        for (int index = from; index < to; index++) {
            counts[propagationKeyByte(order[index], shorelineDistance, shift)]++;
        }

        int position = from;
        for (int bucket = 0; bucket < RADIX; bucket++) {
            starts[bucket] = position;
            next[bucket] = position;
            position += counts[bucket];
        }

        for (int bucket = 0; bucket < RADIX; bucket++) {
            int end = starts[bucket] + counts[bucket];
            while (next[bucket] < end) {
                int positionInBucket = next[bucket];
                int value = order[positionInBucket];
                int target = propagationKeyByte(value, shorelineDistance, shift);
                if (target == bucket) {
                    next[bucket]++;
                    continue;
                }
                int targetPosition = next[target]++;
                int displaced = order[targetPosition];
                order[targetPosition] = value;
                order[positionInBucket] = displaced;
            }
        }

        for (int bucket = 0; bucket < RADIX; bucket++) {
            int bucketFrom = starts[bucket];
            int bucketTo = bucketFrom + counts[bucket];
            if (bucketTo - bucketFrom > 1) {
                radixSortPropagationRange(
                        order,
                        bucketFrom,
                        bucketTo,
                        shift - 8,
                        depth + 1,
                        shorelineDistance,
                        countsByDepth,
                        startsByDepth,
                        nextByDepth);
            }
        }
    }

    private static int propagationKeyByte(int cell, int[] shorelineDistance, int shift) {
        long key = ((long) shorelineDistance[cell] << 32) | (cell & 0xffff_ffffL);
        return (int) ((key >>> shift) & 0xffL);
    }

    /**
     * Applies the same connected-water square-window blend as before in linear work.
     *
     * <p>Within one contiguous water run, stopping at the first land cell is exactly equivalent to
     * clipping the radius window to that run. Prefix sums therefore replace the old per-cell
     * radius walk without changing integer averages. Two world-sized scratch arrays are reused for
     * both axis orders, and {@code source} becomes the final result in place.</p>
     */
    private static void broadBlendConnectedWaterCharacterInPlace(
            int[] source,
            long[] elevation,
            int width,
            int height,
            int radius) {
        int[] first = new int[source.length];
        int[] second = new int[source.length];
        long[] prefix = new long[Math.max(width, height) + 1];

        blendHorizontalInto(source, elevation, width, height, radius, first, prefix);
        blendVerticalInto(first, elevation, width, height, radius, second, prefix);

        blendVerticalInto(source, elevation, width, height, radius, first, prefix);
        blendHorizontalInto(first, elevation, width, height, radius, source, prefix);

        for (int cell = 0; cell < source.length; cell++) {
            if (elevation[cell] >= 0L) continue;
            source[cell] = (second[cell] + source[cell]) / 2;
        }
    }

    private static void blendHorizontalInto(
            int[] source,
            long[] elevation,
            int width,
            int height,
            int radius,
            int[] result,
            long[] prefix) {
        for (int y = 0; y < height; y++) {
            int row = y * width;
            int x = 0;
            while (x < width) {
                int cell = row + x;
                if (elevation[cell] >= 0L) {
                    result[cell] = source[cell];
                    x++;
                    continue;
                }

                int runStart = x;
                int runEnd = x + 1;
                while (runEnd < width && elevation[row + runEnd] < 0L) runEnd++;
                int runLength = runEnd - runStart;
                prefix[0] = 0L;
                for (int offset = 0; offset < runLength; offset++) {
                    prefix[offset + 1] = prefix[offset] + source[row + runStart + offset];
                }
                for (int offset = 0; offset < runLength; offset++) {
                    int left = Math.max(0, offset - radius);
                    int right = Math.min(runLength - 1, offset + radius);
                    long sum = prefix[right + 1] - prefix[left];
                    result[row + runStart + offset] = (int) (sum / (right - left + 1));
                }
                x = runEnd;
            }
        }
    }

    private static void blendVerticalInto(
            int[] source,
            long[] elevation,
            int width,
            int height,
            int radius,
            int[] result,
            long[] prefix) {
        for (int x = 0; x < width; x++) {
            int y = 0;
            while (y < height) {
                int cell = y * width + x;
                if (elevation[cell] >= 0L) {
                    result[cell] = source[cell];
                    y++;
                    continue;
                }

                int runStart = y;
                int runEnd = y + 1;
                while (runEnd < height && elevation[runEnd * width + x] < 0L) runEnd++;
                int runLength = runEnd - runStart;
                prefix[0] = 0L;
                for (int offset = 0; offset < runLength; offset++) {
                    prefix[offset + 1] = prefix[offset] + source[(runStart + offset) * width + x];
                }
                for (int offset = 0; offset < runLength; offset++) {
                    int top = Math.max(0, offset - radius);
                    int bottom = Math.min(runLength - 1, offset + radius);
                    long sum = prefix[bottom + 1] - prefix[top];
                    result[(runStart + offset) * width + x] = (int) (sum / (bottom - top + 1));
                }
                y = runEnd;
            }
        }
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

    private static int collectComponent(
            int start,
            long[] elevation,
            long[] visited,
            int[] queue,
            int width,
            int height) {
        int head = 0;
        int tail = 0;
        queue[tail++] = start;
        mark(visited, start);

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
            long[] visited,
            int[] queue,
            int tail) {
        if (isMarked(visited, cell) || elevation[cell] >= 0L) return tail;
        mark(visited, cell);
        queue[tail] = cell;
        return tail + 1;
    }

    private static boolean isMarked(long[] words, int index) {
        return (words[index >>> 6] & (1L << (index & 63))) != 0L;
    }

    private static void mark(long[] words, int index) {
        words[index >>> 6] |= 1L << (index & 63);
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

    private record CoastalCharacterResult(
            int[] character,
            int[] componentScratch) {
    }

    private record LandReliefIntegral(
            int stride,
            long[] positiveHeightSum,
            int[] positiveLandCount) {
    }
}
