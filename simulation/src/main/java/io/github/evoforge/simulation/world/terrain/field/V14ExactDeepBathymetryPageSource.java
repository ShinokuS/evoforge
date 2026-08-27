package io.github.evoforge.simulation.world.terrain.field;

import io.github.evoforge.simulation.world.continuum.field.ContinuumSampleWindow;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPage;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPageSource;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.terrain.genesis.V14BathymetryCalibration;
import io.github.evoforge.simulation.world.terrain.genesis.V14BathymetryInteriorRecipe;
import io.github.evoforge.simulation.world.terrain.genesis.V14BathymetryRecipe;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Exact Continuum execution of the historical V14 {@code DeepBathymetryStructureAlgorithm}.
 *
 * <p>Shoreline distance, water-component traversal, deterministic core selection and basin/high
 * envelope composition are preserved operation-for-operation. World-sized primitive state is kept
 * only in disposable file-backed workspace grids while one bounded request is evaluated.</p>
 */
public final class V14ExactDeepBathymetryPageSource implements ContinuumScalarPageSource {
    private static final int PPM = 1_000_000;
    private static final int DISTANCE_SCALE = 1_000;
    private static final int CARDINAL_DISTANCE = DISTANCE_SCALE;
    private static final int DIAGONAL_DISTANCE = 1_414;
    private static final int INFINITE_DISTANCE = Integer.MAX_VALUE / 4;
    private static final int BASE_STAGING_ROWS = 256;

    private final ContinuumWorldDomain domain;
    private final ContinuumScalarPageSource base;
    private final V14BathymetryCalibration calibration;
    private final V14BathymetryRecipe recipe;

    public V14ExactDeepBathymetryPageSource(
            ContinuumWorldDomain domain,
            ContinuumScalarPageSource base,
            V14BathymetryCalibration calibration,
            V14BathymetryRecipe recipe) {
        if (domain == null || base == null || calibration == null || recipe == null) {
            throw new IllegalArgumentException("V14 deep bathymetry inputs must not be null");
        }
        if (!domain.equals(base.domain())
                || domain.width() != calibration.width()
                || domain.height() != calibration.height()) {
            throw new IllegalArgumentException("V14 deep bathymetry dependencies must share one domain");
        }
        this.domain = domain;
        this.base = base;
        this.calibration = calibration;
        this.recipe = recipe;
    }

    @Override
    public ContinuumWorldDomain domain() {
        return domain;
    }

    @Override
    public ContinuumScalarPage materialize(ContinuumSampleWindow window) {
        validateWindow(window);
        try (TemporaryTerrainWorkspace workspace = new TemporaryTerrainWorkspace()) {
            int width = calibration.width();
            int height = calibration.height();
            int area = calibration.area();
            TemporaryTerrainWorkspace.LongGrid elevation = workspace.longGrid(area);
            materializeBase(elevation, width, height);
            TemporaryTerrainWorkspace.IntGrid shorelineDistance = shorelineDistance(
                    workspace, elevation, width, height);
            TemporaryTerrainWorkspace.ByteGrid visited = workspace.byteGrid(area);
            TemporaryTerrainWorkspace.IntVector component = workspace.intVector(area);
            TemporaryTerrainWorkspace.LongGrid structuredDepth = workspace.longGrid(area);

            for (int cell = 0; cell < area; cell++) {
                if (elevation.get(cell) >= 0L || visited.getBoolean(cell)) continue;
                int componentSize = collectComponent(
                        cell,
                        elevation,
                        visited,
                        component,
                        width,
                        height);
                authorDeepStructure(
                        elevation,
                        shorelineDistance,
                        component,
                        componentSize,
                        structuredDepth,
                        width);
            }
            return readWindow(elevation, window, width);
        } catch (IOException exception) {
            throw new IllegalStateException("unable to execute exact V14 deep bathymetry", exception);
        }
    }

    private void materializeBase(
            TemporaryTerrainWorkspace.LongGrid elevation,
            int width,
            int height) {
        for (int minY = 0; minY < height; minY += BASE_STAGING_ROWS) {
            int rows = Math.min(BASE_STAGING_ROWS, height - minY);
            ContinuumScalarPage page = base.materialize(
                    new ContinuumSampleWindow(0L, minY, width, rows, 1L));
            for (int localY = 0; localY < rows; localY++) {
                int row = (minY + localY) * width;
                for (int x = 0; x < width; x++) {
                    elevation.set(row + x, Math.round(page.sample(x, localY)));
                }
            }
        }
    }

    private static TemporaryTerrainWorkspace.IntGrid shorelineDistance(
            TemporaryTerrainWorkspace workspace,
            TemporaryTerrainWorkspace.LongGrid elevation,
            int width,
            int height) throws IOException {
        int area = Math.multiplyExact(width, height);
        TemporaryTerrainWorkspace.IntGrid distance = workspace.intGrid(area);
        boolean hasLand = false;
        for (int cell = 0; cell < area; cell++) {
            if (elevation.get(cell) >= 0L) {
                distance.set(cell, 0);
                hasLand = true;
            } else {
                distance.set(cell, INFINITE_DISTANCE);
            }
        }
        if (!hasLand) return distance;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int cell = y * width + x;
                if (distance.get(cell) == 0) continue;
                int best = distance.get(cell);
                if (x > 0) best = Math.min(best, plus(distance.get(cell - 1), CARDINAL_DISTANCE));
                if (y > 0) best = Math.min(best, plus(distance.get(cell - width), CARDINAL_DISTANCE));
                if (x > 0 && y > 0) {
                    best = Math.min(best, plus(distance.get(cell - width - 1), DIAGONAL_DISTANCE));
                }
                if (x + 1 < width && y > 0) {
                    best = Math.min(best, plus(distance.get(cell - width + 1), DIAGONAL_DISTANCE));
                }
                distance.set(cell, best);
            }
        }
        for (int y = height - 1; y >= 0; y--) {
            for (int x = width - 1; x >= 0; x--) {
                int cell = y * width + x;
                if (distance.get(cell) == 0) continue;
                int best = distance.get(cell);
                if (x + 1 < width) best = Math.min(best, plus(distance.get(cell + 1), CARDINAL_DISTANCE));
                if (y + 1 < height) best = Math.min(best, plus(distance.get(cell + width), CARDINAL_DISTANCE));
                if (x + 1 < width && y + 1 < height) {
                    best = Math.min(best, plus(distance.get(cell + width + 1), DIAGONAL_DISTANCE));
                }
                if (x > 0 && y + 1 < height) {
                    best = Math.min(best, plus(distance.get(cell + width - 1), DIAGONAL_DISTANCE));
                }
                distance.set(cell, best);
            }
        }
        return distance;
    }

    private void authorDeepStructure(
            TemporaryTerrainWorkspace.LongGrid elevation,
            TemporaryTerrainWorkspace.IntGrid shorelineDistance,
            TemporaryTerrainWorkspace.IntVector component,
            int componentSize,
            TemporaryTerrainWorkspace.LongGrid structuredDepth,
            int width) {
        V14BathymetryInteriorRecipe interior = recipe.interiorStructure();
        long depthCap = componentDepthCap(elevation, component, componentSize);
        long minimumDepth = Math.multiplyExact(
                (long) interior.minimumDepthCells(),
                TerrainElevationField.SUBUNITS_PER_CELL);
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
                structureSlope);
        if (cores.size() < interior.minimumCoreCount()) return;

        for (int index = 0; index < componentSize; index++) {
            int cell = component.get(index);
            structuredDepth.set(cell, -elevation.get(cell));
        }
        for (Core core : cores) {
            for (int index = 0; index < componentSize; index++) {
                int cell = component.get(index);
                if (shorelineDistance.get(cell) <= protectedBandDistance) continue;
                long radialDistance = octileDistanceMilli(cell, core.cell(), width);
                long radialChange = structureSlope * radialDistance / DISTANCE_SCALE;
                long current = structuredDepth.get(cell);
                if (core.basin()) {
                    long surfaceDepth = core.targetDepthSubunits() - radialChange;
                    if (surfaceDepth > current) {
                        structuredDepth.set(cell, Math.min(depthCap, surfaceDepth));
                    }
                } else {
                    long ceilingDepth = core.targetDepthSubunits() + radialChange;
                    if (ceilingDepth < current) {
                        structuredDepth.set(cell, Math.max(1L, ceilingDepth));
                    }
                }
            }
        }

        for (int index = 0; index < componentSize; index++) {
            int cell = component.get(index);
            if (shorelineDistance.get(cell) <= protectedBandDistance) continue;
            elevation.set(
                    cell,
                    -Math.max(1L, Math.min(depthCap, structuredDepth.get(cell))));
        }
    }

    private List<Core> selectCores(
            TemporaryTerrainWorkspace.LongGrid elevation,
            TemporaryTerrainWorkspace.IntVector component,
            int componentSize,
            TemporaryTerrainWorkspace.IntGrid shorelineDistance,
            int width,
            int protectedBandDistance,
            long depthCap,
            long structureSlope) {
        V14BathymetryInteriorRecipe interior = recipe.interiorStructure();
        long minimumRadius = Math.multiplyExact(
                (long) Math.max(
                        interior.minimumCoreRadiusCells(),
                        calibration.coastalContextRadiusCells()),
                DISTANCE_SCALE);
        List<Core> cores = new ArrayList<>(interior.maximumCoreCount());
        for (int ordinal = 0; ordinal < interior.maximumCoreCount(); ordinal++) {
            int bestCell = -1;
            long bestRoom = -1L;
            for (int index = 0; index < componentSize; index++) {
                int cell = component.get(index);
                int shoreDistance = shorelineDistance.get(cell);
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
            long baseDepth = -elevation.get(bestCell);
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
            TemporaryTerrainWorkspace.LongGrid elevation,
            TemporaryTerrainWorkspace.IntVector component,
            int componentSize,
            TemporaryTerrainWorkspace.IntGrid shorelineDistance,
            int width,
            int protectedBandDistance,
            long structureSlope,
            long depthCap) {
        long limit = depthCap;
        for (int index = 0; index < componentSize; index++) {
            int cell = component.get(index);
            if (shorelineDistance.get(cell) > protectedBandDistance) continue;
            long boundaryDepth = -elevation.get(cell);
            long distance = octileDistanceMilli(coreCell, cell, width);
            long allowed = boundaryDepth + structureSlope * distance / DISTANCE_SCALE;
            limit = Math.min(limit, allowed);
        }
        return Math.max(1L, limit);
    }

    private static long shallowestSafeHighTarget(
            int coreCell,
            TemporaryTerrainWorkspace.LongGrid elevation,
            TemporaryTerrainWorkspace.IntVector component,
            int componentSize,
            TemporaryTerrainWorkspace.IntGrid shorelineDistance,
            int width,
            int protectedBandDistance,
            long structureSlope,
            long depthCap) {
        long limit = 1L;
        for (int index = 0; index < componentSize; index++) {
            int cell = component.get(index);
            if (shorelineDistance.get(cell) > protectedBandDistance) continue;
            long boundaryDepth = -elevation.get(cell);
            long distance = octileDistanceMilli(coreCell, cell, width);
            long allowed = boundaryDepth - structureSlope * distance / DISTANCE_SCALE;
            limit = Math.max(limit, allowed);
        }
        return Math.max(1L, Math.min(depthCap, limit));
    }

    private static long componentDepthCap(
            TemporaryTerrainWorkspace.LongGrid elevation,
            TemporaryTerrainWorkspace.IntVector component,
            int componentSize) {
        long depthCap = 0L;
        for (int index = 0; index < componentSize; index++) {
            depthCap = Math.max(depthCap, -elevation.get(component.get(index)));
        }
        return depthCap;
    }

    private static int maximumFiniteDistance(
            TemporaryTerrainWorkspace.IntGrid shorelineDistance,
            TemporaryTerrainWorkspace.IntVector component,
            int componentSize) {
        int maximum = 0;
        for (int index = 0; index < componentSize; index++) {
            int distance = shorelineDistance.get(component.get(index));
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

    private static int collectComponent(
            int start,
            TemporaryTerrainWorkspace.LongGrid elevation,
            TemporaryTerrainWorkspace.ByteGrid visited,
            TemporaryTerrainWorkspace.IntVector queue,
            int width,
            int height) {
        int head = 0;
        int tail = 0;
        queue.set(tail++, start);
        visited.setBoolean(start, true);
        while (head < tail) {
            int cell = queue.get(head++);
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
            TemporaryTerrainWorkspace.LongGrid elevation,
            TemporaryTerrainWorkspace.ByteGrid visited,
            TemporaryTerrainWorkspace.IntVector queue,
            int tail) {
        if (visited.getBoolean(cell) || elevation.get(cell) >= 0L) return tail;
        visited.setBoolean(cell, true);
        queue.set(tail, cell);
        return tail + 1;
    }

    private static int plus(int distance, int increment) {
        if (distance >= INFINITE_DISTANCE - increment) return INFINITE_DISTANCE;
        return distance + increment;
    }

    private static ContinuumScalarPage readWindow(
            TemporaryTerrainWorkspace.LongGrid elevation,
            ContinuumSampleWindow window,
            int worldWidth) {
        double[] samples = new double[Math.multiplyExact(window.width(), window.height())];
        int cursor = 0;
        for (int y = 0; y < window.height(); y++) {
            int worldY = Math.toIntExact(window.yAt(y));
            for (int x = 0; x < window.width(); x++) {
                int worldX = Math.toIntExact(window.xAt(x));
                samples[cursor++] = elevation.get(worldY * worldWidth + worldX);
            }
        }
        return new ContinuumScalarPage(window, samples);
    }

    private void validateWindow(ContinuumSampleWindow window) {
        if (window == null) throw new IllegalArgumentException("window must not be null");
        long maxX = window.xAt(window.width() - 1);
        long maxY = window.yAt(window.height() - 1);
        if (!domain.contains(window.minX(), window.minY()) || !domain.contains(maxX, maxY)) {
            throw new IllegalArgumentException("window lies outside V14 deep bathymetry domain");
        }
    }

    private record Core(int cell, int radiusMilli, boolean basin, long targetDepthSubunits) {}
}
