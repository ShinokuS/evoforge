package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Finds lake domains as the lowest broad connected parts of real continental interiors.
 *
 * <p>No radial primitive, stamped basin or post-hoc water level is used. The algorithm reads the
 * accepted pre-mountain terrain, excludes coast-adjacent and already submerged cells, smooths only
 * for placement decisions, then takes a low quantile of that actual terrain. Connected components
 * of the lowland quantile become lake domains only when they have geographic width. Fragmentation
 * is never read directly: fragmented worlds simply contain less deep continental interior.</p>
 */
final class TerrainLowlandInlandLakeDomainAlgorithm implements InlandLakeDomainAlgorithm {
    static final TerrainLowlandInlandLakeDomainAlgorithm INSTANCE =
            new TerrainLowlandInlandLakeDomainAlgorithm();

    private static final int PPM = NormalizedValue.SCALE;
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

        long[] elevation = new long[area];
        boolean[] dry = new boolean[area];
        int index = 0;
        for (int localY = 0; localY < height; localY++) {
            int y = bounds.minY() + localY;
            for (int localX = 0; localX < width; localX++) {
                int x = bounds.minX() + localX;
                long value = continentalBase.elevationSubunitsAt(x, y);
                elevation[index] = value;
                dry[index] = value > ElevationGenerationStage.SEA_LEVEL_SUBUNITS;
                index++;
            }
        }

        int[] coastDistance = distanceFromExistingWater(dry, width, height);
        long[] broadElevation = broadDryElevation(
                elevation,
                dry,
                width,
                height,
                calibration.smoothingRadiusCells());

        long[] eligibleHeights = new long[area];
        boolean[] eligible = new boolean[area];
        int eligibleCount = 0;
        for (int cell = 0; cell < area; cell++) {
            if (!dry[cell]
                    || coastDistance[cell] < calibration.minimumInteriorClearanceCells()
                    || elevation[cell] > calibration.maximumSourceElevationSubunits()) {
                continue;
            }
            eligible[cell] = true;
            eligibleHeights[eligibleCount++] = broadElevation[cell];
        }

        int interiorCapacity = Math.toIntExact(
                (long) eligibleCount * recipe.maximumInteriorOccupancyPpm() / PPM);
        int desiredLakeCells = Math.min(calibration.targetLakeCells(), interiorCapacity);
        if (desiredLakeCells < calibration.minimumComponentCells() || eligibleCount == 0) {
            return InlandLakeDomain.empty(bounds);
        }

        Arrays.sort(eligibleHeights, 0, eligibleCount);
        long threshold = eligibleHeights[Math.min(eligibleCount - 1, desiredLakeCells - 1)];
        boolean[] candidate = new boolean[area];
        for (int cell = 0; cell < area; cell++) {
            candidate[cell] = eligible[cell] && broadElevation[cell] <= threshold;
        }

        int[] componentIds = new int[area];
        Arrays.fill(componentIds, -1);
        List<Component> components = collectComponents(
                candidate,
                broadElevation,
                componentIds,
                width,
                height);
        if (components.isEmpty()) return InlandLakeDomain.empty(bounds);

        boolean[] hasInteriorCore = interiorCoreFlags(componentIds, components.size(), width, height);
        List<Component> valid = new ArrayList<>();
        for (Component component : components) {
            int spanX = component.maxX() - component.minX() + 1;
            int spanY = component.maxY() - component.minY() + 1;
            if (component.cellCount() < calibration.minimumComponentCells()
                    || spanX < calibration.minimumComponentSpanCells()
                    || spanY < calibration.minimumComponentSpanCells()
                    || !hasInteriorCore[component.id()]) {
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

        boolean[] lake = new boolean[area];
        int lakeCellCount = 0;
        for (int cell = 0; cell < area; cell++) {
            int componentId = componentIds[cell];
            if (componentId < 0 || !selectedComponent[componentId]) continue;
            lake[cell] = true;
            lakeCellCount++;
        }
        return new InlandLakeDomain(bounds, lake, lakeCellCount);
    }

    private static int[] distanceFromExistingWater(boolean[] dry, int width, int height) {
        int area = dry.length;
        int[] distance = new int[area];
        Arrays.fill(distance, Integer.MAX_VALUE);
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        boolean hasWater = false;
        for (int cell = 0; cell < area; cell++) {
            if (dry[cell]) continue;
            distance[cell] = 0;
            queue.addLast(cell);
            hasWater = true;
        }
        if (!hasWater) {
            for (int x = 0; x < width; x++) {
                seedBoundary(distance, queue, x);
                seedBoundary(distance, queue, (height - 1) * width + x);
            }
            for (int y = 1; y + 1 < height; y++) {
                seedBoundary(distance, queue, y * width);
                seedBoundary(distance, queue, y * width + width - 1);
            }
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

    private static void seedBoundary(int[] distance, ArrayDeque<Integer> queue, int cell) {
        if (distance[cell] == 0) return;
        distance[cell] = 0;
        queue.addLast(cell);
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
                int windowCount = Math.toIntExact(
                        rectangle(count, integralWidth, minX, minY, maxX, maxY));
                if (windowCount > 0) broad[cell] = windowSum / windowCount;
            }
        }
        return broad;
    }

    private static long rectangle(
            long[] integral,
            int stride,
            int minX,
            int minY,
            int maxX,
            int maxY) {
        int x0 = minX;
        int y0 = minY;
        int x1 = maxX + 1;
        int y1 = maxY + 1;
        return integral[y1 * stride + x1]
                - integral[y0 * stride + x1]
                - integral[y1 * stride + x0]
                + integral[y0 * stride + x0];
    }

    private static long rectangle(
            int[] integral,
            int stride,
            int minX,
            int minY,
            int maxX,
            int maxY) {
        int x0 = minX;
        int y0 = minY;
        int x1 = maxX + 1;
        int y1 = maxY + 1;
        return (long) integral[y1 * stride + x1]
                - integral[y0 * stride + x1]
                - integral[y1 * stride + x0]
                + integral[y0 * stride + x0];
    }

    private static List<Component> collectComponents(
            boolean[] candidate,
            long[] broadElevation,
            int[] componentIds,
            int width,
            int height) {
        List<Component> components = new ArrayList<>();
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int start = 0; start < candidate.length; start++) {
            if (!candidate[start] || componentIds[start] >= 0) continue;
            int id = components.size();
            componentIds[start] = id;
            queue.addLast(start);
            int cellCount = 0;
            long sumBroad = 0L;
            int startX = start % width;
            int startY = start / width;
            int minX = startX;
            int maxX = startX;
            int minY = startY;
            int maxY = startY;

            while (!queue.isEmpty()) {
                int cell = queue.removeFirst();
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
                    queue.addLast(next);
                }
            }
            components.add(new Component(id, cellCount, sumBroad, minX, maxX, minY, maxY));
        }
        return components;
    }

    private static boolean[] interiorCoreFlags(
            int[] componentIds,
            int componentCount,
            int width,
            int height) {
        boolean[] result = new boolean[componentCount];
        for (int y = 1; y + 1 < height; y++) {
            for (int x = 1; x + 1 < width; x++) {
                int cell = y * width + x;
                int id = componentIds[cell];
                if (id < 0 || result[id]) continue;
                if (componentIds[cell - 1] == id
                        && componentIds[cell + 1] == id
                        && componentIds[cell - width] == id
                        && componentIds[cell + width] == id) {
                    result[id] = true;
                }
            }
        }
        return result;
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
