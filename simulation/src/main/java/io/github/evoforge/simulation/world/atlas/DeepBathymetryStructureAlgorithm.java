package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.ArrayList;
import java.util.List;

/**
 * Adds broad structural relief only to sufficiently large and deep water-body interiors.
 *
 * <p>This pass consumes already accepted coastal bathymetry. It never changes land or submerged
 * membership and keeps a world-scaled coastal band bit-identical. Shoreline distance is used only
 * as a clearance/envelope fact. Deep morphology comes from deterministic broad structural cores.
 * Each basin authors a slope-bounded minimum-depth surface composed with {@code max}; each high
 * authors a slope-bounded depth ceiling composed with {@code min}. Those operations preserve the
 * accepted slope bound instead of adding a second independent gradient to the existing seabed.</p>
 */
public final class DeepBathymetryStructureAlgorithm implements BathymetryElevationAlgorithm {
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
            throw new IllegalArgumentException("deep bathymetry inputs must not be null");
        }

        WorldBounds bounds = genesis.spec().bounds();
        requireMatchingHorizontalBounds(bounds, base.bounds());
        int width = calibration.width();
        int height = calibration.height();
        if (width != horizontalWidth(bounds)
                || height != horizontalHeight(bounds)
                || calibration.area() != DenseElevationField.cellCount(bounds)) {
            throw new IllegalArgumentException("deep bathymetry calibration must match genesis bounds");
        }

        long[] elevation = copyBaseElevation(base, bounds, width, height);
        int[] shorelineDistance = shorelineDistance(elevation, width, height);
        boolean[] visited = new boolean[elevation.length];
        int[] component = new int[elevation.length];

        for (int cell = 0; cell < elevation.length; cell++) {
            if (elevation[cell] >= 0L || visited[cell]) continue;
            int componentSize = collectComponent(cell, elevation, visited, component, width, height);
            authorDeepStructure(
                    elevation,
                    shorelineDistance,
                    component,
                    componentSize,
                    width,
                    calibration,
                    recipe);
        }

        return DenseElevationField.takeOwnership(bounds, elevation);
    }

    private static void authorDeepStructure(
            long[] elevation,
            int[] shorelineDistance,
            int[] component,
            int componentSize,
            int width,
            BathymetryCalibration calibration,
            BathymetryRecipe recipe) {
        BathymetryInteriorRecipe interior = recipe.interiorStructure();
        long depthCap = componentDepthCap(elevation, component, componentSize);
        long minimumDepth = Math.multiplyExact(
                (long) interior.minimumDepthCells(),
                ElevationField.SUBUNITS_PER_CELL);
        if (depthCap < minimumDepth) return;

        int maximumDistance = maximumFiniteDistance(shorelineDistance, component, componentSize);
        if (maximumDistance <= 0) return;

        int protectedBandCells = Math.max(
                interior.minimumCoreRadiusCells(),
                calibration.coastalContextRadiusCells());
        int protectedBandDistance = Math.multiplyExact(protectedBandCells, DISTANCE_SCALE);
        if (maximumDistance <= protectedBandDistance * 2L) return;

        long structureSlope = Math.max(
                1L,
                calibration.maximumCardinalFallSubunits()
                        * (long) interior.structuralSlopeUtilizationPpm()
                        / PPM);
        List<Core> cores = selectCores(
                elevation,
                component,
                componentSize,
                shorelineDistance,
                width,
                protectedBandDistance,
                depthCap,
                structureSlope,
                calibration,
                recipe);
        if (cores.size() < interior.minimumCoreCount()) return;

        long[] acceptedDepth = new long[elevation.length];
        for (int index = 0; index < componentSize; index++) {
            int cell = component[index];
            acceptedDepth[cell] = -elevation[cell];
        }
        long[] structuredDepth = acceptedDepth.clone();

        for (Core core : cores) {
            for (int index = 0; index < componentSize; index++) {
                int cell = component[index];
                if (shorelineDistance[cell] <= protectedBandDistance) continue;
                long radialDistance = octileDistanceMilli(cell, core.cell(), width);
                long radialChange = structureSlope * radialDistance / DISTANCE_SCALE;
                if (core.basin()) {
                    long surfaceDepth = core.targetDepthSubunits() - radialChange;
                    if (surfaceDepth > structuredDepth[cell]) {
                        structuredDepth[cell] = Math.min(depthCap, surfaceDepth);
                    }
                } else {
                    long ceilingDepth = core.targetDepthSubunits() + radialChange;
                    if (ceilingDepth < structuredDepth[cell]) {
                        structuredDepth[cell] = Math.max(1L, ceilingDepth);
                    }
                }
            }
        }

        for (int index = 0; index < componentSize; index++) {
            int cell = component[index];
            if (shorelineDistance[cell] <= protectedBandDistance) continue;
            elevation[cell] = -Math.max(1L, Math.min(depthCap, structuredDepth[cell]));
        }
    }

    private static List<Core> selectCores(
            long[] elevation,
            int[] component,
            int componentSize,
            int[] shorelineDistance,
            int width,
            int protectedBandDistance,
            long depthCap,
            long structureSlope,
            BathymetryCalibration calibration,
            BathymetryRecipe recipe) {
        BathymetryInteriorRecipe interior = recipe.interiorStructure();
        long minimumRadius = Math.multiplyExact(
                (long) Math.max(interior.minimumCoreRadiusCells(), calibration.coastalContextRadiusCells()),
                DISTANCE_SCALE);
        List<Core> cores = new ArrayList<>(interior.maximumCoreCount());

        for (int ordinal = 0; ordinal < interior.maximumCoreCount(); ordinal++) {
            int bestCell = -1;
            long bestRoom = -1L;
            for (int index = 0; index < componentSize; index++) {
                int cell = component[index];
                int shoreDistance = shorelineDistance[cell];
                if (shoreDistance >= INFINITE_DISTANCE) continue;
                long availableRoom = (long) shoreDistance - protectedBandDistance;
                if (availableRoom < minimumRadius) continue;
                for (Core existing : cores) {
                    availableRoom = Math.min(
                            availableRoom,
                            octileDistanceMilli(cell, existing.cell(), width) / 2L);
                }
                if (availableRoom > bestRoom || (availableRoom == bestRoom && cell < bestCell)) {
                    bestRoom = availableRoom;
                    bestCell = cell;
                }
            }
            if (bestCell < 0 || bestRoom < minimumRadius) break;

            long radius = bestRoom * interior.coreRadiusUtilizationPpm() / PPM;
            radius = Math.max(minimumRadius, Math.min(bestRoom, radius));
            boolean basin = ordinal % 3 != 0;
            long baseDepth = -elevation[bestCell];
            int strengthPpm = basin ? interior.basinStrengthPpm() : interior.highStrengthPpm();
            long strengthBudget = depthCap * (long) strengthPpm / PPM;
            long radiusBudget = structureSlope * radius / DISTANCE_SCALE;
            long amplitude = Math.max(1L, Math.min(strengthBudget, radiusBudget));

            long targetDepth;
            if (basin) {
                long desired = Math.min(depthCap, Math.addExact(baseDepth, amplitude));
                long coastalEnvelope = deepestSafeBasinTarget(
                        bestCell,
                        elevation,
                        component,
                        componentSize,
                        shorelineDistance,
                        width,
                        protectedBandDistance,
                        structureSlope,
                        depthCap);
                targetDepth = Math.min(desired, coastalEnvelope);
                if (targetDepth <= baseDepth) continue;
            } else {
                long desired = Math.max(1L, baseDepth - amplitude);
                long coastalEnvelope = shallowestSafeHighTarget(
                        bestCell,
                        elevation,
                        component,
                        componentSize,
                        shorelineDistance,
                        width,
                        protectedBandDistance,
                        structureSlope,
                        depthCap);
                targetDepth = Math.max(desired, coastalEnvelope);
                if (targetDepth >= baseDepth) continue;
            }
            cores.add(new Core(bestCell, Math.toIntExact(radius), basin, targetDepth));
        }
        return cores;
    }

    private static long deepestSafeBasinTarget(
            int coreCell,
            long[] elevation,
            int[] component,
            int componentSize,
            int[] shorelineDistance,
            int width,
            int protectedBandDistance,
            long structureSlope,
            long depthCap) {
        long limit = depthCap;
        for (int index = 0; index < componentSize; index++) {
            int cell = component[index];
            if (shorelineDistance[cell] > protectedBandDistance) continue;
            long boundaryDepth = -elevation[cell];
            long distance = octileDistanceMilli(coreCell, cell, width);
            long allowed = boundaryDepth + structureSlope * distance / DISTANCE_SCALE;
            limit = Math.min(limit, allowed);
        }
        return Math.max(1L, limit);
    }

    private static long shallowestSafeHighTarget(
            int coreCell,
            long[] elevation,
            int[] component,
            int componentSize,
            int[] shorelineDistance,
            int width,
            int protectedBandDistance,
            long structureSlope,
            long depthCap) {
        long limit = 1L;
        for (int index = 0; index < componentSize; index++) {
            int cell = component[index];
            if (shorelineDistance[cell] > protectedBandDistance) continue;
            long boundaryDepth = -elevation[cell];
            long distance = octileDistanceMilli(coreCell, cell, width);
            long allowed = boundaryDepth - structureSlope * distance / DISTANCE_SCALE;
            limit = Math.max(limit, allowed);
        }
        return Math.max(1L, Math.min(depthCap, limit));
    }

    private static long componentDepthCap(long[] elevation, int[] component, int componentSize) {
        long depthCap = 0L;
        for (int index = 0; index < componentSize; index++) {
            depthCap = Math.max(depthCap, -elevation[component[index]]);
        }
        return depthCap;
    }

    private static int maximumFiniteDistance(
            int[] shorelineDistance,
            int[] component,
            int componentSize) {
        int maximum = 0;
        for (int index = 0; index < componentSize; index++) {
            int distance = shorelineDistance[component[index]];
            if (distance < INFINITE_DISTANCE) maximum = Math.max(maximum, distance);
        }
        return maximum;
    }

    private static long octileDistanceMilli(int first, int second, int width) {
        int firstX = first % width;
        int firstY = first / width;
        int secondX = second % width;
        int secondY = second / width;
        long dx = Math.abs((long) firstX - secondX);
        long dy = Math.abs((long) firstY - secondY);
        long maximum = Math.max(dx, dy);
        long minimum = Math.min(dx, dy);
        return maximum * CARDINAL_DISTANCE + minimum * (DIAGONAL_DISTANCE - CARDINAL_DISTANCE);
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
                if (x > 0 && y > 0) best = Math.min(best, plus(distance[cell - width - 1], DIAGONAL_DISTANCE));
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

    private static int plus(int distance, int increment) {
        if (distance >= INFINITE_DISTANCE - increment) return INFINITE_DISTANCE;
        return distance + increment;
    }

    private static void requireMatchingHorizontalBounds(WorldBounds expected, WorldBounds actual) {
        if (expected.minX() != actual.minX()
                || expected.maxX() != actual.maxX()
                || expected.minY() != actual.minY()
                || expected.maxY() != actual.maxY()) {
            throw new IllegalArgumentException("deep bathymetry base must share genesis horizontal bounds");
        }
    }

    private static int horizontalWidth(WorldBounds bounds) {
        return Math.toIntExact((long) bounds.maxX() - bounds.minX() + 1L);
    }

    private static int horizontalHeight(WorldBounds bounds) {
        return Math.toIntExact((long) bounds.maxY() - bounds.minY() + 1L);
    }

    private record Core(int cell, int radiusMilli, boolean basin, long targetDepthSubunits) {
    }
}
