package io.github.evoforge.simulation.world.terrain.field;

import io.github.evoforge.simulation.world.continuum.field.ContinuumSampleWindow;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPage;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPageSource;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.terrain.genesis.V15InlandLakeBathymetryRecipe;
import java.io.IOException;

/** Exact Continuum execution of the accepted V15 inland-only shoreline-distance depth refinement. */
public final class V15ExactInlandLakeBathymetryPageSource implements ContinuumScalarPageSource {
    private static final int DISTANCE_SCALE = 1_000;
    private static final int CARDINAL_DISTANCE = DISTANCE_SCALE;
    private static final int DIAGONAL_DISTANCE = 1_414;
    private static final int INFINITE_DISTANCE = Integer.MAX_VALUE / 4;
    private static final int ASYMMETRY_DIVISOR = 5;
    private static final int BASE_STAGING_ROWS = 256;
    private static final int[] AXIS_X = {1, 1, 0, -1, -1, -1, 0, 1};
    private static final int[] AXIS_Y = {0, 1, 1, 1, 0, -1, -1, -1};

    private final ContinuumWorldDomain domain;
    private final long seed;
    private final ContinuumScalarPageSource base;
    private final int minimumZCells;
    private final V15InlandLakeBathymetryRecipe recipe;

    public V15ExactInlandLakeBathymetryPageSource(
            ContinuumWorldDomain domain,
            long seed,
            ContinuumScalarPageSource base,
            int minimumZCells,
            V15InlandLakeBathymetryRecipe recipe) {
        if (domain == null || base == null || recipe == null) {
            throw new IllegalArgumentException("V15 inland bathymetry inputs must not be null");
        }
        if (!domain.equals(base.domain())) {
            throw new IllegalArgumentException("V15 inland bathymetry base must match its domain");
        }
        if (minimumZCells >= 0) {
            throw new IllegalArgumentException("V15 inland bathymetry requires negative Z headroom");
        }
        this.domain = domain;
        this.seed = seed;
        this.base = base;
        this.minimumZCells = minimumZCells;
        this.recipe = recipe;
    }

    @Override
    public ContinuumWorldDomain domain() {
        return domain;
    }

    @Override
    public ContinuumScalarPage materialize(ContinuumSampleWindow window) {
        validateWindow(window);
        int width = Math.toIntExact(domain.width());
        int height = Math.toIntExact(domain.height());
        int area = Math.multiplyExact(width, height);
        try (TemporaryTerrainWorkspace workspace = new TemporaryTerrainWorkspace()) {
            TemporaryTerrainWorkspace.LongGrid elevation = workspace.longGrid(area);
            materializeBase(elevation, width, height);
            TemporaryTerrainWorkspace.ByteGrid visited = workspace.byteGrid(area);
            TemporaryTerrainWorkspace.IntVector component = workspace.intVector(area);

            for (int start = 0; start < area; start++) {
                if (elevation.get(start) >= 0L || visited.getBoolean(start)) continue;
                ComponentGeometry geometry = collectComponent(
                        start,
                        elevation,
                        visited,
                        component,
                        width,
                        height);
                if (geometry.touchesBoundary()) continue;
                refineInlandComponent(
                        elevation,
                        component,
                        geometry,
                        width);
            }
            return readWindow(elevation, window, width);
        } catch (IOException exception) {
            throw new IllegalStateException("unable to execute exact V15 inland bathymetry", exception);
        }
    }

    private void refineInlandComponent(
            TemporaryTerrainWorkspace.LongGrid elevation,
            TemporaryTerrainWorkspace.IntVector component,
            ComponentGeometry geometry,
            int worldWidth) throws IOException {
        try (TemporaryTerrainWorkspace localWorkspace = new TemporaryTerrainWorkspace()) {
            int localWidth = geometry.maxX() - geometry.minX() + 3;
            int localHeight = geometry.maxY() - geometry.minY() + 3;
            int localArea = Math.multiplyExact(localWidth, localHeight);
            TemporaryTerrainWorkspace.ByteGrid inside = localWorkspace.byteGrid(localArea);
            for (int index = 0; index < geometry.size(); index++) {
                int cell = component.get(index);
                int x = cell % worldWidth;
                int y = cell / worldWidth;
                int localX = x - geometry.minX() + 1;
                int localY = y - geometry.minY() + 1;
                inside.setBoolean(localY * localWidth + localX, true);
            }
            TemporaryTerrainWorkspace.IntGrid distance = localWorkspace.intGrid(localArea);
            for (int cell = 0; cell < localArea; cell++) {
                distance.set(cell, inside.getBoolean(cell) ? INFINITE_DISTANCE : 0);
            }
            chamferPasses(distance, localWidth, localHeight);

            int maximumDistance = 0;
            long sumX = 0L;
            long sumY = 0L;
            for (int index = 0; index < geometry.size(); index++) {
                int cell = component.get(index);
                int x = cell % worldWidth;
                int y = cell / worldWidth;
                sumX += x;
                sumY += y;
                maximumDistance = Math.max(
                        maximumDistance,
                        localDistance(distance, localWidth, geometry, x, y));
            }

            int maximumRadiusCells = maximumDistance / DISTANCE_SCALE;
            if (maximumRadiusCells < recipe.minimumSignificantRadiusCells()) return;

            int radiusScaledDepth = Math.toIntExact(
                    (long) maximumRadiusCells
                            * recipe.radiusDepthNumerator()
                            / recipe.radiusDepthDenominator());
            int targetDepthCells = Math.max(recipe.minimumSignificantDepthCells(), radiusScaledDepth);
            targetDepthCells = Math.min(recipe.maximumDepthCells(), targetDepthCells);
            targetDepthCells = Math.min(Math.negateExact(minimumZCells), targetDepthCells);
            if (targetDepthCells <= 0) return;

            long bandWidthScaled = Math.max(
                    DISTANCE_SCALE,
                    (long) DISTANCE_SCALE
                            * recipe.radiusDepthDenominator()
                            / recipe.radiusDepthNumerator());
            long mixedSeed = mix64(seed
                    ^ ((long) geometry.anchor() << 32)
                    ^ geometry.size());
            int axis = (int) (mixedSeed & 7L);
            int axisX = AXIS_X[axis];
            int axisY = AXIS_Y[axis];
            int span = Math.max(
                    1,
                    Math.max(
                            geometry.maxX() - geometry.minX(),
                            geometry.maxY() - geometry.minY()));
            long projectionScale = Math.max(
                    1L,
                    (long) geometry.size()
                            * span
                            * (Math.abs(axisX) + Math.abs(axisY)));

            for (int index = 0; index < geometry.size(); index++) {
                int cell = component.get(index);
                int x = cell % worldWidth;
                int y = cell / worldWidth;
                int actualDistance = localDistance(distance, localWidth, geometry, x, y);
                long centeredX = (long) x * geometry.size() - sumX;
                long centeredY = (long) y * geometry.size() - sumY;
                long projection = centeredX * axisX + centeredY * axisY;
                long negativeProjection = Math.max(0L, -projection);
                long penalty = negativeProjection * maximumDistance
                        / Math.multiplyExact((long) ASYMMETRY_DIVISOR, projectionScale);
                int effectiveDistance = Math.max(
                        CARDINAL_DISTANCE,
                        actualDistance
                                - Math.toIntExact(Math.min(
                                        (long) actualDistance - CARDINAL_DISTANCE,
                                        penalty)));
                long inwardBeyondShore = Math.max(0L, effectiveDistance - CARDINAL_DISTANCE);
                int authoredLevel = 1 + Math.toIntExact(inwardBeyondShore / bandWidthScaled);
                authoredLevel = Math.min(targetDepthCells, authoredLevel);
                long authoredDepth = Math.multiplyExact(
                        (long) authoredLevel,
                        TerrainElevationField.SUBUNITS_PER_CELL);
                long existingDepth = Math.negateExact(elevation.get(cell));
                elevation.set(cell, -Math.max(existingDepth, authoredDepth));
            }
        }
    }

    private static ComponentGeometry collectComponent(
            int start,
            TemporaryTerrainWorkspace.LongGrid elevation,
            TemporaryTerrainWorkspace.ByteGrid visited,
            TemporaryTerrainWorkspace.IntVector component,
            int width,
            int height) {
        int head = 0;
        int tail = 0;
        component.set(tail++, start);
        visited.setBoolean(start, true);
        int startX = start % width;
        int startY = start / width;
        int minX = startX;
        int maxX = startX;
        int minY = startY;
        int maxY = startY;
        boolean touchesBoundary = startX == 0
                || startX == width - 1
                || startY == 0
                || startY == height - 1;

        while (head < tail) {
            int cell = component.get(head++);
            int x = cell % width;
            int y = cell / width;
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
            touchesBoundary |= x == 0 || x == width - 1 || y == 0 || y == height - 1;
            if (x > 0) tail = enqueueWater(cell - 1, elevation, visited, component, tail);
            if (x + 1 < width) tail = enqueueWater(cell + 1, elevation, visited, component, tail);
            if (y > 0) tail = enqueueWater(cell - width, elevation, visited, component, tail);
            if (y + 1 < height) tail = enqueueWater(cell + width, elevation, visited, component, tail);
        }
        return new ComponentGeometry(tail, minX, maxX, minY, maxY, start, touchesBoundary);
    }

    private static int enqueueWater(
            int cell,
            TemporaryTerrainWorkspace.LongGrid elevation,
            TemporaryTerrainWorkspace.ByteGrid visited,
            TemporaryTerrainWorkspace.IntVector component,
            int tail) {
        if (visited.getBoolean(cell) || elevation.get(cell) >= 0L) return tail;
        visited.setBoolean(cell, true);
        component.set(tail, cell);
        return tail + 1;
    }

    private static int localDistance(
            TemporaryTerrainWorkspace.IntGrid distance,
            int localWidth,
            ComponentGeometry geometry,
            int x,
            int y) {
        int localX = x - geometry.minX() + 1;
        int localY = y - geometry.minY() + 1;
        return distance.get(localY * localWidth + localX);
    }

    private static void chamferPasses(
            TemporaryTerrainWorkspace.IntGrid distance,
            int width,
            int height) {
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
    }

    private static int plus(int distance, int increment) {
        if (distance >= INFINITE_DISTANCE - increment) return INFINITE_DISTANCE;
        return distance + increment;
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

    private static ContinuumScalarPage readWindow(
            TemporaryTerrainWorkspace.LongGrid elevation,
            ContinuumSampleWindow window,
            int worldWidth) {
        double[] samples = new double[Math.multiplyExact(window.width(), window.height())];
        int cursor = 0;
        for (int sampleY = 0; sampleY < window.height(); sampleY++) {
            int y = Math.toIntExact(window.yAt(sampleY));
            for (int sampleX = 0; sampleX < window.width(); sampleX++) {
                int x = Math.toIntExact(window.xAt(sampleX));
                samples[cursor++] = elevation.get(y * worldWidth + x);
            }
        }
        return new ContinuumScalarPage(window, samples);
    }

    private void validateWindow(ContinuumSampleWindow window) {
        if (window == null) throw new IllegalArgumentException("window must not be null");
        long maxX = window.xAt(window.width() - 1);
        long maxY = window.yAt(window.height() - 1);
        if (!domain.contains(window.minX(), window.minY()) || !domain.contains(maxX, maxY)) {
            throw new IllegalArgumentException("window lies outside V15 inland bathymetry domain");
        }
    }

    private static long mix64(long value) {
        long mixed = value;
        mixed ^= mixed >>> 33;
        mixed *= 0xff51afd7ed558ccdl;
        mixed ^= mixed >>> 33;
        mixed *= 0xc4ceb9fe1a85ec53l;
        mixed ^= mixed >>> 33;
        return mixed;
    }

    private record ComponentGeometry(
            int size,
            int minX,
            int maxX,
            int minY,
            int maxY,
            int anchor,
            boolean touchesBoundary) {}
}
