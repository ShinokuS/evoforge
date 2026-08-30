package io.github.evoforge.simulation.world.terrain.field;

import io.github.evoforge.simulation.world.continuum.field.ContinuumSampleWindow;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPage;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPageSource;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.terrain.genesis.V14BathymetryCalibration;
import io.github.evoforge.simulation.world.terrain.genesis.V14BathymetryRecipe;
import java.io.IOException;

/**
 * Exact Continuum execution of the historical V14 {@code BathymetryMorphologyAlgorithm}.
 *
 * <p>Every mathematical operation and iteration/tie order is the accepted V14 one. Only primitive
 * storage changes: world-sized historical arrays are disposable file-backed grids/vectors. Base
 * terrain is pulled in bounded row bands, and only the requested output window is returned.</p>
 */
public final class V14ExactCoastalBathymetryPageSource implements ContinuumScalarPageSource {
    private static final int PPM = 1_000_000;
    private static final int DISTANCE_SCALE = 1_000;
    private static final int CARDINAL_DISTANCE = DISTANCE_SCALE;
    private static final int DIAGONAL_DISTANCE = 1_414;
    private static final int CARDINAL_CHARACTER_WEIGHT = 1_000;
    private static final int DIAGONAL_CHARACTER_WEIGHT = 707;
    private static final int INFINITE_DISTANCE = Integer.MAX_VALUE / 4;
    private static final int BASE_STAGING_ROWS = 64;

    private final ContinuumWorldDomain domain;
    private final ContinuumScalarPageSource base;
    private final V14BathymetryCalibration calibration;
    private final V14BathymetryRecipe recipe;

    public V14ExactCoastalBathymetryPageSource(
            ContinuumWorldDomain domain,
            ContinuumScalarPageSource base,
            V14BathymetryCalibration calibration,
            V14BathymetryRecipe recipe) {
        if (domain == null || base == null || calibration == null || recipe == null) {
            throw new IllegalArgumentException("V14 coastal bathymetry inputs must not be null");
        }
        if (!domain.equals(base.domain())
                || domain.width() != calibration.width()
                || domain.height() != calibration.height()) {
            throw new IllegalArgumentException("V14 coastal bathymetry dependencies must share one domain");
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
            TemporaryTerrainWorkspace.IntGrid coastalCharacter = coastalCharacterField(
                    workspace,
                    elevation,
                    shorelineDistance,
                    width,
                    height,
                    calibration.coastalContextRadiusCells());
            TemporaryTerrainWorkspace.ByteGrid visited = workspace.byteGrid(area);
            TemporaryTerrainWorkspace.IntVector component = workspace.intVector(area);

            for (int cell = 0; cell < area; cell++) {
                if (elevation.get(cell) >= 0L || visited.getBoolean(cell)) continue;
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
                        height);
            }
            return readWindow(elevation, window, width);
        } catch (IOException exception) {
            throw new IllegalStateException("unable to execute exact V14 coastal bathymetry", exception);
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

    private TemporaryTerrainWorkspace.IntGrid coastalCharacterField(
            TemporaryTerrainWorkspace workspace,
            TemporaryTerrainWorkspace.LongGrid elevation,
            TemporaryTerrainWorkspace.IntGrid shorelineDistance,
            int width,
            int height,
            int contextRadius) throws IOException {
        LandReliefIntegral landRelief = landReliefIntegral(workspace, elevation, width, height);
        int area = Math.multiplyExact(width, height);
        TemporaryTerrainWorkspace.LongGrid characterMass = workspace.longGrid(area);
        TemporaryTerrainWorkspace.LongGrid shorelineSupport = workspace.longGrid(area);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int cell = y * width + x;
                if (elevation.get(cell) < 0L || !touchesWater(cell, elevation, width, height)) continue;
                shorelineSupport.set(cell, 1L);
                characterMass.set(
                        cell,
                        coastalLandReliefPpm(
                                x,
                                y,
                                landRelief,
                                contextRadius));
            }
        }

        TemporaryTerrainWorkspace.LongGrid firstMass = boxSumField(
                workspace, characterMass, width, height, contextRadius);
        TemporaryTerrainWorkspace.LongGrid firstSupport = boxSumField(
                workspace, shorelineSupport, width, height, contextRadius);
        int blendRadius = Math.max(2, contextRadius / 2);
        TemporaryTerrainWorkspace.LongGrid blendedMass = boxSumField(
                workspace, firstMass, width, height, blendRadius);
        TemporaryTerrainWorkspace.LongGrid blendedSupport = boxSumField(
                workspace, firstSupport, width, height, blendRadius);

        TemporaryTerrainWorkspace.IntGrid nearshoreCharacter = workspace.intGrid(area);
        for (int cell = 0; cell < area; cell++) {
            long support = blendedSupport.get(cell);
            long mass = blendedMass.get(cell);
            if (support <= 0L || mass <= 0L) continue;
            nearshoreCharacter.set(cell, (int) Math.min(PPM, mass / support));
        }

        TemporaryTerrainWorkspace.IntGrid propagated = propagateCoastalCharacter(
                workspace,
                nearshoreCharacter,
                elevation,
                shorelineDistance,
                width,
                height);
        return broadBlendConnectedWaterCharacter(
                workspace,
                propagated,
                elevation,
                width,
                height,
                contextRadius);
    }

    private static TemporaryTerrainWorkspace.IntGrid propagateCoastalCharacter(
            TemporaryTerrainWorkspace workspace,
            TemporaryTerrainWorkspace.IntGrid nearshoreCharacter,
            TemporaryTerrainWorkspace.LongGrid elevation,
            TemporaryTerrainWorkspace.IntGrid shorelineDistance,
            int width,
            int height) throws IOException {
        int area = Math.multiplyExact(width, height);
        int waterCount = 0;
        for (int cell = 0; cell < area; cell++) {
            if (elevation.get(cell) < 0L && shorelineDistance.get(cell) < INFINITE_DISTANCE) waterCount++;
        }

        TemporaryTerrainWorkspace.IntGrid propagated = workspace.intGrid(area);
        if (waterCount == 0) return propagated;
        TemporaryTerrainWorkspace.LongVector order = workspace.longVector(waterCount);
        int orderIndex = 0;
        for (int cell = 0; cell < area; cell++) {
            if (elevation.get(cell) >= 0L || shorelineDistance.get(cell) >= INFINITE_DISTANCE) continue;
            order.set(
                    orderIndex++,
                    ((long) shorelineDistance.get(cell) << 32) | (cell & 0xffff_ffffL));
        }
        heapSort(order, waterCount);

        for (int orderCell = 0; orderCell < waterCount; orderCell++) {
            long key = order.get(orderCell);
            int cell = (int) key;
            int distance = shorelineDistance.get(cell);
            if (distance <= DIAGONAL_DISTANCE) {
                propagated.set(cell, nearshoreCharacter.get(cell));
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
                    if (elevation.get(neighbor) >= 0L
                            || shorelineDistance.get(neighbor) >= distance) continue;
                    int weight = dx == 0 || dy == 0
                            ? CARDINAL_CHARACTER_WEIGHT
                            : DIAGONAL_CHARACTER_WEIGHT;
                    weightedCharacter += (long) propagated.get(neighbor) * weight;
                    totalWeight += weight;
                }
            }
            propagated.set(
                    cell,
                    totalWeight > 0
                            ? (int) (weightedCharacter / totalWeight)
                            : nearshoreCharacter.get(cell));
        }
        return propagated;
    }

    private static TemporaryTerrainWorkspace.IntGrid broadBlendConnectedWaterCharacter(
            TemporaryTerrainWorkspace workspace,
            TemporaryTerrainWorkspace.IntGrid source,
            TemporaryTerrainWorkspace.LongGrid elevation,
            int width,
            int height,
            int radius) throws IOException {
        TemporaryTerrainWorkspace.IntGrid horizontal = blendHorizontal(
                workspace, source, elevation, width, height, radius);
        TemporaryTerrainWorkspace.IntGrid horizontalThenVertical = blendVertical(
                workspace, horizontal, elevation, width, height, radius);
        TemporaryTerrainWorkspace.IntGrid vertical = blendVertical(
                workspace, source, elevation, width, height, radius);
        TemporaryTerrainWorkspace.IntGrid verticalThenHorizontal = blendHorizontal(
                workspace, vertical, elevation, width, height, radius);
        TemporaryTerrainWorkspace.IntGrid result = workspace.intGrid(Math.multiplyExact(width, height));
        result.copyFrom(source);
        for (int cell = 0; cell < result.size(); cell++) {
            if (elevation.get(cell) >= 0L) continue;
            result.set(cell, (horizontalThenVertical.get(cell) + verticalThenHorizontal.get(cell)) / 2);
        }
        return result;
    }

    private static TemporaryTerrainWorkspace.IntGrid blendHorizontal(
            TemporaryTerrainWorkspace workspace,
            TemporaryTerrainWorkspace.IntGrid source,
            TemporaryTerrainWorkspace.LongGrid elevation,
            int width,
            int height,
            int radius) throws IOException {
        TemporaryTerrainWorkspace.IntGrid result = workspace.intGrid(Math.multiplyExact(width, height));
        result.copyFrom(source);
        for (int y = 0; y < height; y++) {
            int row = y * width;
            for (int x = 0; x < width; x++) {
                int cell = row + x;
                if (elevation.get(cell) >= 0L) continue;
                long sum = source.get(cell);
                int count = 1;
                boolean leftOpen = true;
                boolean rightOpen = true;
                for (int step = 1; step <= radius && (leftOpen || rightOpen); step++) {
                    if (leftOpen) {
                        int leftX = x - step;
                        if (leftX >= 0 && elevation.get(row + leftX) < 0L) {
                            sum += source.get(row + leftX);
                            count++;
                        } else {
                            leftOpen = false;
                        }
                    }
                    if (rightOpen) {
                        int rightX = x + step;
                        if (rightX < width && elevation.get(row + rightX) < 0L) {
                            sum += source.get(row + rightX);
                            count++;
                        } else {
                            rightOpen = false;
                        }
                    }
                }
                result.set(cell, (int) (sum / count));
            }
        }
        return result;
    }

    private static TemporaryTerrainWorkspace.IntGrid blendVertical(
            TemporaryTerrainWorkspace workspace,
            TemporaryTerrainWorkspace.IntGrid source,
            TemporaryTerrainWorkspace.LongGrid elevation,
            int width,
            int height,
            int radius) throws IOException {
        TemporaryTerrainWorkspace.IntGrid result = workspace.intGrid(Math.multiplyExact(width, height));
        result.copyFrom(source);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int cell = y * width + x;
                if (elevation.get(cell) >= 0L) continue;
                long sum = source.get(cell);
                int count = 1;
                boolean upOpen = true;
                boolean downOpen = true;
                for (int step = 1; step <= radius && (upOpen || downOpen); step++) {
                    if (upOpen) {
                        int upY = y - step;
                        if (upY >= 0 && elevation.get(upY * width + x) < 0L) {
                            sum += source.get(upY * width + x);
                            count++;
                        } else {
                            upOpen = false;
                        }
                    }
                    if (downOpen) {
                        int downY = y + step;
                        if (downY < height && elevation.get(downY * width + x) < 0L) {
                            sum += source.get(downY * width + x);
                            count++;
                        } else {
                            downOpen = false;
                        }
                    }
                }
                result.set(cell, (int) (sum / count));
            }
        }
        return result;
    }

    private static boolean touchesWater(
            int cell,
            TemporaryTerrainWorkspace.LongGrid elevation,
            int width,
            int height) {
        int x = cell % width;
        int y = cell / width;
        if (x > 0 && elevation.get(cell - 1) < 0L) return true;
        if (x + 1 < width && elevation.get(cell + 1) < 0L) return true;
        if (y > 0 && elevation.get(cell - width) < 0L) return true;
        return y + 1 < height && elevation.get(cell + width) < 0L;
    }

    private static LandReliefIntegral landReliefIntegral(
            TemporaryTerrainWorkspace workspace,
            TemporaryTerrainWorkspace.LongGrid elevation,
            int width,
            int height) throws IOException {
        int stride = width + 1;
        int cells = Math.multiplyExact(stride, height + 1);
        TemporaryTerrainWorkspace.LongGrid positiveHeightSum = workspace.longGrid(cells);
        TemporaryTerrainWorkspace.IntGrid positiveLandCount = workspace.intGrid(cells);
        for (int y = 1; y <= height; y++) {
            for (int x = 1; x <= width; x++) {
                long value = elevation.get((y - 1) * width + (x - 1));
                long positiveHeight = Math.max(0L, value);
                int positiveLand = value > 0L ? 1 : 0;
                int cell = y * stride + x;
                int above = cell - stride;
                int left = cell - 1;
                int diagonal = above - 1;
                positiveHeightSum.set(
                        cell,
                        Math.addExact(
                                positiveHeight,
                                positiveHeightSum.get(above)
                                        + positiveHeightSum.get(left)
                                        - positiveHeightSum.get(diagonal)));
                positiveLandCount.set(
                        cell,
                        positiveLand
                                + positiveLandCount.get(above)
                                + positiveLandCount.get(left)
                                - positiveLandCount.get(diagonal));
            }
        }
        return new LandReliefIntegral(stride, positiveHeightSum, positiveLandCount);
    }

    private static TemporaryTerrainWorkspace.LongGrid boxSumField(
            TemporaryTerrainWorkspace workspace,
            TemporaryTerrainWorkspace.LongGrid values,
            int width,
            int height,
            int radius) throws IOException {
        int stride = width + 1;
        TemporaryTerrainWorkspace.LongGrid integral = workspace.longGrid(
                Math.multiplyExact(stride, height + 1));
        for (int y = 1; y <= height; y++) {
            for (int x = 1; x <= width; x++) {
                int cell = y * stride + x;
                int above = cell - stride;
                int left = cell - 1;
                int diagonal = above - 1;
                integral.set(
                        cell,
                        Math.addExact(
                                values.get((y - 1) * width + (x - 1)),
                                integral.get(above) + integral.get(left) - integral.get(diagonal)));
            }
        }

        TemporaryTerrainWorkspace.LongGrid result = workspace.longGrid(Math.multiplyExact(width, height));
        for (int y = 0; y < height; y++) {
            int minY = Math.max(0, y - radius);
            int maxY = Math.min(height - 1, y + radius);
            for (int x = 0; x < width; x++) {
                int minX = Math.max(0, x - radius);
                int maxX = Math.min(width - 1, x + radius);
                result.set(
                        y * width + x,
                        rectangleSum(integral, stride, minX, minY, maxX, maxY));
            }
        }
        return result;
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

    private void authorComponentBathymetry(
            TemporaryTerrainWorkspace.LongGrid elevation,
            TemporaryTerrainWorkspace.IntGrid shorelineDistance,
            TemporaryTerrainWorkspace.IntGrid coastalCharacter,
            TemporaryTerrainWorkspace.IntVector component,
            int componentSize,
            int width,
            int height) {
        int maximumDistance = maximumShorelineDistance(
                shorelineDistance, component, componentSize, width, height);
        long bodyDepthCap = bodyDepthCap(maximumDistance);
        long verticalCapacity = Math.negateExact(calibration.floorSubunits());
        boolean oceanConnected = touchesWorldBoundary(component, componentSize, width, height);
        for (int index = 0; index < componentSize; index++) {
            int cell = component.get(index);
            int distance = shorelineDistance.get(cell);
            if (distance >= INFINITE_DISTANCE) distance = maximumDistance;
            long baselineDepth = baselineDepth(distance, maximumDistance, bodyDepthCap);
            long depth = baselineDepth;
            if (oceanConnected) {
                long coastalDepth = causalCoastalDepth(
                        distance,
                        coastalCharacter.get(cell),
                        baselineDepth,
                        bodyDepthCap);
                depth = Math.max(depth, coastalDepth);
            }
            depth = Math.min(bodyDepthCap, depth);
            depth = Math.min(verticalCapacity, depth);
            elevation.set(cell, -Math.max(1L, depth));
        }
    }

    private static int maximumShorelineDistance(
            TemporaryTerrainWorkspace.IntGrid shorelineDistance,
            TemporaryTerrainWorkspace.IntVector component,
            int componentSize,
            int width,
            int height) {
        int maximumDistance = 0;
        boolean finiteShore = false;
        for (int index = 0; index < componentSize; index++) {
            int distance = shorelineDistance.get(component.get(index));
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
            TemporaryTerrainWorkspace.IntVector component,
            int componentSize,
            int width,
            int height) {
        for (int index = 0; index < componentSize; index++) {
            int cell = component.get(index);
            int x = cell % width;
            int y = cell / width;
            if (x == 0 || x == width - 1 || y == 0 || y == height - 1) return true;
        }
        return false;
    }

    private static long baselineDepth(int distance, int maximumDistance, long bodyDepthCap) {
        int coordinatePpm = (int) Math.min(PPM, (long) distance * PPM / maximumDistance);
        int profilePpm = smootherStepPpm(coordinatePpm);
        return Math.max(1L, bodyDepthCap * profilePpm / PPM);
    }

    private long causalCoastalDepth(
            int shorelineDistance,
            int coastalCharacterPpm,
            long baselineDepth,
            long bodyDepthCap) {
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

    private int coastalLandReliefPpm(
            int x,
            int y,
            LandReliefIntegral integral,
            int radius) {
        int minX = Math.max(0, x - radius);
        int maxX = Math.min(calibration.width() - 1, x + radius);
        int minY = Math.max(0, y - radius);
        int maxY = Math.min(calibration.height() - 1, y + radius);
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
                TerrainElevationField.SUBUNITS_PER_CELL);
        long reliefPpm = averageLandHeight * PPM / horizontalReference;
        return (int) Math.min(recipe.coastalReliefFullScalePpm(), Math.max(0L, reliefPpm));
    }

    private long bodyDepthCap(int maximumDistance) {
        long slopeSupported = Math.multiplyExact(
                (long) maximumDistance,
                calibration.maximumCardinalFallSubunits())
                / recipe.profileGradientBoundMilli();
        return Math.min(calibration.worldDepthCapSubunits(), Math.max(1L, slopeSupported));
    }

    private static long rectangleSum(
            TemporaryTerrainWorkspace.LongGrid integral,
            int stride,
            int minX,
            int minY,
            int maxX,
            int maxY) {
        int left = minX;
        int top = minY;
        int right = maxX + 1;
        int bottom = maxY + 1;
        return integral.get(bottom * stride + right)
                - integral.get(top * stride + right)
                - integral.get(bottom * stride + left)
                + integral.get(top * stride + left);
    }

    private static int rectangleSum(
            TemporaryTerrainWorkspace.IntGrid integral,
            int stride,
            int minX,
            int minY,
            int maxX,
            int maxY) {
        int left = minX;
        int top = minY;
        int right = maxX + 1;
        int bottom = maxY + 1;
        return integral.get(bottom * stride + right)
                - integral.get(top * stride + right)
                - integral.get(bottom * stride + left)
                + integral.get(top * stride + left);
    }

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

    private static void heapSort(TemporaryTerrainWorkspace.LongVector values, int size) {
        for (int start = size / 2 - 1; start >= 0; start--) siftDown(values, start, size);
        for (int end = size - 1; end > 0; end--) {
            swap(values, 0, end);
            siftDown(values, 0, end);
        }
    }

    private static void siftDown(TemporaryTerrainWorkspace.LongVector values, int root, int size) {
        int current = root;
        while (true) {
            int child = current * 2 + 1;
            if (child >= size) return;
            int larger = child;
            if (child + 1 < size && values.get(child + 1) > values.get(child)) larger = child + 1;
            if (values.get(current) >= values.get(larger)) return;
            swap(values, current, larger);
            current = larger;
        }
    }

    private static void swap(TemporaryTerrainWorkspace.LongVector values, int first, int second) {
        long value = values.get(first);
        values.set(first, values.get(second));
        values.set(second, value);
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
            throw new IllegalArgumentException("window lies outside V14 bathymetry domain");
        }
    }

    private record LandReliefIntegral(
            int stride,
            TemporaryTerrainWorkspace.LongGrid positiveHeightSum,
            TemporaryTerrainWorkspace.IntGrid positiveLandCount) {}
}
