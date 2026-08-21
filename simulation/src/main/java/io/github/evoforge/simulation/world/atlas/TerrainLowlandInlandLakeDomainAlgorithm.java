package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Selects broad inland-water domains from real continental lowlands.
 *
 * <p>The terrain still decides where a lake may exist. Shape regularization only rejects narrow
 * support and rounds the accepted lowland footprint in an approximately Euclidean metric. It does
 * not carve arbitrary water through high ground and it never reads Fragmentation directly.</p>
 */
final class TerrainLowlandInlandLakeDomainAlgorithm implements InlandLakeDomainAlgorithm {
    static final TerrainLowlandInlandLakeDomainAlgorithm INSTANCE =
            new TerrainLowlandInlandLakeDomainAlgorithm();

    private static final int PPM = NormalizedValue.SCALE;
    private static final int DISTANCE_SCALE = 1_000;
    private static final int CARDINAL_DISTANCE = DISTANCE_SCALE;
    private static final int DIAGONAL_DISTANCE = 1_414;
    private static final int INFINITE_DISTANCE = Integer.MAX_VALUE / 4;
    private static final int[] DX = {-1, 1, 0, 0};
    private static final int[] DY = {0, 0, -1, 1};

    private TerrainLowlandInlandLakeDomainAlgorithm() {
    }

    @Override
    public InlandLakeDomain generate(
            WorldGenesis genesis,
            ElevationField continentalBase,
            InlandLakeDomainCalibration calibration,
            InlandLakeDomainRecipe recipe) {
        if (genesis == null || continentalBase == null || calibration == null || recipe == null) {
            throw new IllegalArgumentException("inland lake domain inputs must not be null");
        }
        WorldBounds bounds = genesis.spec().bounds();
        if (!sameHorizontalBounds(bounds, continentalBase.bounds())) {
            throw new IllegalArgumentException("continental base must match lake-domain horizontal bounds");
        }

        int width = calibration.width();
        int height = calibration.height();
        int area = calibration.area();
        if (area != Math.multiplyExact(width, height)) {
            throw new IllegalArgumentException("lake calibration must match its dimensions");
        }
        if (calibration.targetLakeCells() == 0 || calibration.dryLandCells() == 0) {
            return InlandLakeDomain.empty(bounds);
        }

        long[] elevation = denseOrMaterializedElevation(
                continentalBase, bounds, width, height, area);
        boolean[] dry = new boolean[area];
        for (int cell = 0; cell < area; cell++) {
            dry[cell] = elevation[cell] > ElevationGenerationStage.SEA_LEVEL_SUBUNITS;
        }

        int[] coastDistance = chamferDistanceInside(dry, width, height);
        long[] broadElevation = broadDryElevation(
                elevation,
                dry,
                width,
                height,
                calibration.smoothingRadiusCells());

        /* dry is dead after broad smoothing, so the same mask now owns eligibility/support. */
        boolean[] eligible = dry;
        int eligibleCount = 0;
        for (int cell = 0; cell < area; cell++) {
            boolean accepted = eligible[cell]
                    && coastDistance[cell] >= calibration.minimumInteriorClearanceCells() * DISTANCE_SCALE
                    && elevation[cell] <= calibration.maximumSourceElevationSubunits();
            eligible[cell] = accepted;
            if (accepted) eligibleCount++;
        }
        if (eligibleCount == 0) return InlandLakeDomain.empty(bounds);

        int interiorCapacity = Math.toIntExact(
                (long) eligibleCount * recipe.maximumInteriorOccupancyPpm() / PPM);
        int desiredLakeCells = Math.min(calibration.targetLakeCells(), interiorCapacity);
        if (desiredLakeCells < calibration.minimumComponentCells()) {
            return InlandLakeDomain.empty(bounds);
        }

        int supportTarget = Math.min(
                eligibleCount,
                Math.max(desiredLakeCells, Math.multiplyExact(desiredLakeCells, 3)));
        long threshold = selectEligibleThreshold(
                broadElevation,
                eligible,
                eligibleCount,
                Math.max(0, supportTarget - 1));
        for (int cell = 0; cell < area; cell++) {
            eligible[cell] = eligible[cell] && broadElevation[cell] <= threshold;
        }
        boolean[] support = eligible;

        int[] supportWidth = chamferDistanceInside(support, width, height);

        /*
         * A valid lake needs real geometric room for its depth profile, not merely enough area to
         * draw a water patch. Balanced policy uses a 20-cell minimum span and the lake bathymetry
         * consumes roughly two inward cells per full Z, so requiring half that span as an actual
         * interior radius guarantees room for the five-Z minimum without forcing a trench later.
         */
        int requiredHalfWidth = Math.max(
                2,
                (calibration.minimumComponentSpanCells() + 1) / 2);
        int requiredHalfWidthScaled = requiredHalfWidth * DISTANCE_SCALE;
        boolean[] broadCore = new boolean[area];
        for (int cell = 0; cell < area; cell++) {
            broadCore[cell] = support[cell] && supportWidth[cell] > requiredHalfWidthScaled;
        }

        int[] distanceToCore = chamferDistanceFromTrue(broadCore, width, height);

        /* broadCore is dead once core distance exists, so reuse it as the regularized/final mask. */
        boolean[] regularized = broadCore;
        for (int cell = 0; cell < area; cell++) {
            regularized[cell] = support[cell]
                    && supportWidth[cell] > DISTANCE_SCALE
                    && distanceToCore[cell] <= requiredHalfWidthScaled;
        }

        /*
         * Both morphology distance fields are dead now. Reuse their int storage as component labels
         * and the primitive BFS queue instead of allocating another two world-sized buffers or
         * boxing cell indices in ArrayDeque<Integer>.
         */
        int[] componentIds = supportWidth;
        Arrays.fill(componentIds, -1);
        int[] componentQueue = distanceToCore;
        List<Component> components = collectComponents(
                regularized,
                broadElevation,
                componentIds,
                componentQueue,
                width,
                height);
        if (components.isEmpty()) return InlandLakeDomain.empty(bounds);

        List<Component> valid = new ArrayList<>();
        for (Component component : components) {
            int spanX = component.maxX() - component.minX() + 1;
            int spanY = component.maxY() - component.minY() + 1;
            if (component.cellCount() < calibration.minimumComponentCells()
                    || spanX < calibration.minimumComponentSpanCells()
                    || spanY < calibration.minimumComponentSpanCells()) {
                continue;
            }
            valid.add(component);
        }
        if (valid.isEmpty()) return InlandLakeDomain.empty(bounds);

        valid.sort(Comparator
                .comparingDouble(Component::meanBroadElevation)
                .thenComparing(Comparator.comparingInt(Component::cellCount).reversed())
                .thenComparingInt(Component::id));

        boolean[] selectedComponent = new boolean[components.size()];
        int selectedBodies = 0;
        int selectedCells = 0;
        for (Component component : valid) {
            if (selectedBodies >= calibration.maximumLakeBodies()) break;
            if (selectedCells >= desiredLakeCells && selectedBodies > 0) break;
            selectedComponent[component.id()] = true;
            selectedBodies++;
            selectedCells += component.cellCount();
        }

        /* Component membership has been captured in componentIds; recycle regularized as lake. */
        boolean[] lake = regularized;
        Arrays.fill(lake, false);
        int lakeCellCount = 0;
        for (int cell = 0; cell < area; cell++) {
            int componentId = componentIds[cell];
            if (componentId < 0 || !selectedComponent[componentId]) continue;
            lake[cell] = true;
            lakeCellCount++;
        }
        return lakeCellCount == 0
                ? InlandLakeDomain.empty(bounds)
                : new InlandLakeDomain(bounds, lake, lakeCellCount);
    }

    /** Borrows immutable dense storage and only materializes generic elevation implementations. */
    private static long[] denseOrMaterializedElevation(
            ElevationField elevation,
            WorldBounds bounds,
            int width,
            int height,
            int area) {
        if (elevation instanceof DenseElevationField dense) {
            long[] storage = dense.readOnlyStorage();
            if (storage.length != area) {
                throw new IllegalStateException("dense elevation storage does not match lake-domain area");
            }
            return storage;
        }
        long[] values = new long[area];
        for (int localY = 0; localY < height; localY++) {
            int y = bounds.minY() + localY;
            for (int localX = 0; localX < width; localX++) {
                int x = bounds.minX() + localX;
                values[localY * width + localX] = elevation.elevationSubunitsAt(x, y);
            }
        }
        return values;
    }

    /**
     * Selects the exact zero-based kth signed-long value among eligible cells without materializing
     * or sorting an eligible-height array.
     *
     * <p>Eight stable radix decisions identify the target value. XOR with Long.MIN_VALUE maps Java's
     * signed long order to unsigned lexicographic bit order. Each pass only counts 256 buckets, so
     * auxiliary storage is constant and work is O(8N).</p>
     */
    private static long selectEligibleThreshold(
            long[] values,
            boolean[] eligible,
            int eligibleCount,
            int rank) {
        if (values.length != eligible.length || eligibleCount <= 0 || rank < 0 || rank >= eligibleCount) {
            throw new IllegalArgumentException("eligible threshold selection inputs are inconsistent");
        }

        long prefix = 0L;
        long prefixMask = 0L;
        int remainingRank = rank;
        int[] counts = new int[256];

        for (int shift = 56; shift >= 0; shift -= 8) {
            Arrays.fill(counts, 0);
            for (int cell = 0; cell < values.length; cell++) {
                if (!eligible[cell]) continue;
                long sortable = values[cell] ^ Long.MIN_VALUE;
                if ((sortable & prefixMask) != prefix) continue;
                counts[(int) ((sortable >>> shift) & 0xffL)]++;
            }

            int chosen = -1;
            for (int bucket = 0; bucket < counts.length; bucket++) {
                int count = counts[bucket];
                if (remainingRank < count) {
                    chosen = bucket;
                    break;
                }
                remainingRank -= count;
            }
            if (chosen < 0) {
                throw new IllegalStateException("radix threshold selection lost the requested rank");
            }
            prefix |= (long) chosen << shift;
            prefixMask |= 0xffL << shift;
        }
        return prefix ^ Long.MIN_VALUE;
    }

    private static int[] chamferDistanceInside(boolean[] inside, int width, int height) {
        int[] distance = new int[inside.length];
        for (int cell = 0; cell < inside.length; cell++) {
            int x = cell % width;
            int y = cell / width;
            distance[cell] = inside[cell]
                    && x > 0 && x + 1 < width && y > 0 && y + 1 < height
                    ? INFINITE_DISTANCE
                    : 0;
        }
        chamferPasses(distance, width, height);
        return distance;
    }

    private static int[] chamferDistanceFromTrue(boolean[] source, int width, int height) {
        int[] distance = new int[source.length];
        boolean any = false;
        for (int cell = 0; cell < source.length; cell++) {
            if (source[cell]) {
                distance[cell] = 0;
                any = true;
            } else {
                distance[cell] = INFINITE_DISTANCE;
            }
        }
        if (!any) return distance;
        chamferPasses(distance, width, height);
        return distance;
    }

    private static void chamferPasses(int[] distance, int width, int height) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int cell = y * width + x;
                if (distance[cell] == 0) continue;
                int best = distance[cell];
                if (x > 0) best = Math.min(best, plus(distance[cell - 1], CARDINAL_DISTANCE));
                if (y > 0) best = Math.min(best, plus(distance[cell - width], CARDINAL_DISTANCE));
                if (x > 0 && y > 0) best = Math.min(best, plus(distance[cell - width - 1], DIAGONAL_DISTANCE));
                if (x + 1 < width && y > 0) best = Math.min(best, plus(distance[cell - width + 1], DIAGONAL_DISTANCE));
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
                if (x + 1 < width && y + 1 < height) best = Math.min(best, plus(distance[cell + width + 1], DIAGONAL_DISTANCE));
                if (x > 0 && y + 1 < height) best = Math.min(best, plus(distance[cell + width - 1], DIAGONAL_DISTANCE));
                distance[cell] = best;
            }
        }
    }

    private static int plus(int distance, int increment) {
        if (distance >= INFINITE_DISTANCE - increment) return INFINITE_DISTANCE;
        return distance + increment;
    }

    private static long[] broadDryElevation(
            long[] elevation,
            boolean[] dry,
            int width,
            int height,
            int radius) {
        int integralWidth = width + 1;
        long[] sum = new long[Math.multiplyExact(integralWidth, height + 1)];
        int[] count = new int[sum.length];
        for (int y = 0; y < height; y++) {
            long rowSum = 0L;
            int rowCount = 0;
            for (int x = 0; x < width; x++) {
                int cell = y * width + x;
                if (dry[cell]) {
                    rowSum += elevation[cell];
                    rowCount++;
                }
                int integral = (y + 1) * integralWidth + x + 1;
                sum[integral] = sum[y * integralWidth + x + 1] + rowSum;
                count[integral] = count[y * integralWidth + x + 1] + rowCount;
            }
        }

        long[] broad = elevation.clone();
        for (int y = 0; y < height; y++) {
            int minY = Math.max(0, y - radius);
            int maxY = Math.min(height - 1, y + radius);
            for (int x = 0; x < width; x++) {
                int cell = y * width + x;
                if (!dry[cell]) continue;
                int minX = Math.max(0, x - radius);
                int maxX = Math.min(width - 1, x + radius);
                long windowSum = rectangle(sum, integralWidth, minX, minY, maxX, maxY);
                int windowCount = Math.toIntExact(rectangle(count, integralWidth, minX, minY, maxX, maxY));
                if (windowCount > 0) broad[cell] = windowSum / windowCount;
            }
        }
        return broad;
    }

    private static long rectangle(long[] integral, int stride, int minX, int minY, int maxX, int maxY) {
        int x1 = maxX + 1;
        int y1 = maxY + 1;
        return integral[y1 * stride + x1]
                - integral[minY * stride + x1]
                - integral[y1 * stride + minX]
                + integral[minY * stride + minX];
    }

    private static long rectangle(int[] integral, int stride, int minX, int minY, int maxX, int maxY) {
        int x1 = maxX + 1;
        int y1 = maxY + 1;
        return (long) integral[y1 * stride + x1]
                - integral[minY * stride + x1]
                - integral[y1 * stride + minX]
                + integral[minY * stride + minX];
    }

    private static List<Component> collectComponents(
            boolean[] candidate,
            long[] broadElevation,
            int[] componentIds,
            int[] queue,
            int width,
            int height) {
        List<Component> components = new ArrayList<>();
        for (int start = 0; start < candidate.length; start++) {
            if (!candidate[start] || componentIds[start] >= 0) continue;
            int id = components.size();
            componentIds[start] = id;
            int head = 0;
            int tail = 0;
            queue[tail++] = start;
            int cellCount = 0;
            long sumBroad = 0L;
            int startX = start % width;
            int startY = start / width;
            int minX = startX;
            int maxX = startX;
            int minY = startY;
            int maxY = startY;
            while (head < tail) {
                int cell = queue[head++];
                int x = cell % width;
                int y = cell / width;
                cellCount++;
                sumBroad += broadElevation[cell];
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
                for (int direction = 0; direction < DX.length; direction++) {
                    int nx = x + DX[direction];
                    int ny = y + DY[direction];
                    if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue;
                    int next = ny * width + nx;
                    if (!candidate[next] || componentIds[next] >= 0) continue;
                    componentIds[next] = id;
                    queue[tail++] = next;
                }
            }
            components.add(new Component(id, cellCount, sumBroad, minX, maxX, minY, maxY));
        }
        return components;
    }

    private static boolean sameHorizontalBounds(WorldBounds first, WorldBounds second) {
        return first.minX() == second.minX()
                && first.maxX() == second.maxX()
                && first.minY() == second.minY()
                && first.maxY() == second.maxY();
    }

    private record Component(
            int id,
            int cellCount,
            long sumBroadElevation,
            int minX,
            int maxX,
            int minY,
            int maxY) {
        double meanBroadElevation() {
            return sumBroadElevation / (double) cellCount;
        }
    }
}
