package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.spatial.WorldBounds;

/**
 * Authors inland-lake depth from the geometry of the already accepted Z=0 water footprint.
 *
 * <p>The model mirrors the accepted ocean-bathymetry principle: shoreline distance supplies the
 * available depth envelope. Inland lakes receive no authored pits, basin centers or cell-scale
 * interior noise. A deterministic broad directional bias may only make one side of a lake shallower,
 * which shifts the apparent deep core without ever inventing depth unsupported by shoreline room.
 * Each inland component is processed only inside its padded bounding box so world-scale oceans do
 * not force a second full-world distance transform.</p>
 */
final class DistanceProfileInlandLakeBathymetryAlgorithm implements InlandLakeBathymetryAlgorithm {
    static final DistanceProfileInlandLakeBathymetryAlgorithm INSTANCE =
            new DistanceProfileInlandLakeBathymetryAlgorithm();

    private static final int DISTANCE_SCALE = 1_000;
    private static final int CARDINAL_DISTANCE = DISTANCE_SCALE;
    private static final int DIAGONAL_DISTANCE = 1_414;
    private static final int INFINITE_DISTANCE = Integer.MAX_VALUE / 4;
    private static final int ASYMMETRY_DIVISOR = 5;
    private static final int[] DX = {-1, 1, 0, 0};
    private static final int[] DY = {0, 0, -1, 1};
    private static final int[] AXIS_X = {1, 1, 0, -1, -1, -1, 0, 1};
    private static final int[] AXIS_Y = {0, 1, 1, 1, 0, -1, -1, -1};

    private DistanceProfileInlandLakeBathymetryAlgorithm() {
    }

    @Override
    public ElevationField generate(
            WorldGenesis genesis,
            ElevationField bathymetricTerrain,
            InlandLakeBathymetryRecipe recipe) {
        if (genesis == null || bathymetricTerrain == null || recipe == null) {
            throw new IllegalArgumentException("inland lake bathymetry inputs must not be null");
        }
        WorldBounds bounds = genesis.spec().bounds();
        if (!sameHorizontalBounds(bounds, bathymetricTerrain.bounds())) {
            throw new IllegalArgumentException("bathymetric terrain must match genesis horizontal bounds");
        }

        int width = Math.toIntExact((long) bounds.maxX() - bounds.minX() + 1L);
        int height = Math.toIntExact((long) bounds.maxY() - bounds.minY() + 1L);
        int area = Math.multiplyExact(width, height);
        long[] result = new long[area];
        boolean[] water = new boolean[area];
        int index = 0;
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                long value = bathymetricTerrain.elevationSubunitsAt(x, y);
                result[index] = value;
                water[index] = value < ElevationGenerationStage.SEA_LEVEL_SUBUNITS;
                index++;
            }
        }

        boolean[] visited = new boolean[area];
        int[] component = new int[area];
        for (int start = 0; start < area; start++) {
            if (!water[start] || visited[start]) continue;
            ComponentGeometry geometry = collectComponent(
                    start, water, visited, component, width, height);
            if (geometry.touchesBoundary()) continue;
            refineInlandComponent(
                    genesis,
                    result,
                    component,
                    geometry,
                    width,
                    bounds,
                    recipe);
        }
        return DenseElevationField.takeOwnership(bounds, result);
    }

    private static void refineInlandComponent(
            WorldGenesis genesis,
            long[] elevation,
            int[] component,
            ComponentGeometry geometry,
            int worldWidth,
            WorldBounds bounds,
            InlandLakeBathymetryRecipe recipe) {
        LocalDistanceField local = localShorelineDistance(component, geometry, worldWidth);
        int maximumDistance = 0;
        long sumX = 0L;
        long sumY = 0L;
        for (int i = 0; i < geometry.size(); i++) {
            int cell = component[i];
            int x = cell % worldWidth;
            int y = cell / worldWidth;
            sumX += x;
            sumY += y;
            maximumDistance = Math.max(maximumDistance, local.distanceAtWorld(x, y));
        }

        int maximumRadiusCells = maximumDistance / DISTANCE_SCALE;
        if (maximumRadiusCells < recipe.minimumSignificantRadiusCells()) return;

        int radiusScaledDepth = Math.toIntExact(
                (long) maximumRadiusCells
                        * recipe.radiusDepthNumerator()
                        / recipe.radiusDepthDenominator());
        int targetDepthCells = Math.max(recipe.minimumSignificantDepthCells(), radiusScaledDepth);
        targetDepthCells = Math.min(recipe.maximumDepthCells(), targetDepthCells);
        targetDepthCells = Math.min(Math.negateExact(bounds.minZ()), targetDepthCells);
        if (targetDepthCells <= 0) return;

        long bandWidthScaled = Math.max(
                DISTANCE_SCALE,
                (long) DISTANCE_SCALE
                        * recipe.radiusDepthDenominator()
                        / recipe.radiusDepthNumerator());

        long seed = mix64(genesis.masterSeed()
                ^ ((long) geometry.anchor() << 32)
                ^ geometry.size());
        int axis = (int) (seed & 7L);
        int axisX = AXIS_X[axis];
        int axisY = AXIS_Y[axis];
        int span = Math.max(1, Math.max(geometry.maxX() - geometry.minX(), geometry.maxY() - geometry.minY()));
        long projectionScale = Math.max(1L, (long) geometry.size() * span * (Math.abs(axisX) + Math.abs(axisY)));

        for (int i = 0; i < geometry.size(); i++) {
            int cell = component[i];
            int x = cell % worldWidth;
            int y = cell / worldWidth;
            int actualDistance = local.distanceAtWorld(x, y);

            /*
             * The directional term can only subtract available distance. It therefore cannot create
             * a pit or make a near-shore cell deeper than its real geometric room permits. Its broad
             * linear variation shifts the visual deep side without adding cell-scale noise.
             */
            long centeredX = (long) x * geometry.size() - sumX;
            long centeredY = (long) y * geometry.size() - sumY;
            long projection = centeredX * axisX + centeredY * axisY;
            long negativeProjection = Math.max(0L, -projection);
            long penalty = negativeProjection * maximumDistance
                    / Math.multiplyExact((long) ASYMMETRY_DIVISOR, projectionScale);
            int effectiveDistance = Math.max(
                    CARDINAL_DISTANCE,
                    actualDistance - Math.toIntExact(Math.min((long) actualDistance - CARDINAL_DISTANCE, penalty)));

            long inwardBeyondShore = Math.max(0L, effectiveDistance - CARDINAL_DISTANCE);
            int authoredLevel = 1 + Math.toIntExact(inwardBeyondShore / bandWidthScaled);
            authoredLevel = Math.min(targetDepthCells, authoredLevel);
            long authoredDepth = Math.multiplyExact(
                    (long) authoredLevel,
                    ElevationField.SUBUNITS_PER_CELL);
            long existingDepth = Math.negateExact(elevation[cell]);
            elevation[cell] = -Math.max(existingDepth, authoredDepth);
        }
    }

    private static LocalDistanceField localShorelineDistance(
            int[] component,
            ComponentGeometry geometry,
            int worldWidth) {
        int localWidth = geometry.maxX() - geometry.minX() + 3;
        int localHeight = geometry.maxY() - geometry.minY() + 3;
        boolean[] inside = new boolean[Math.multiplyExact(localWidth, localHeight)];
        for (int i = 0; i < geometry.size(); i++) {
            int cell = component[i];
            int x = cell % worldWidth;
            int y = cell / worldWidth;
            int localX = x - geometry.minX() + 1;
            int localY = y - geometry.minY() + 1;
            inside[localY * localWidth + localX] = true;
        }
        return new LocalDistanceField(
                geometry.minX() - 1,
                geometry.minY() - 1,
                localWidth,
                chamferDistanceInside(inside, localWidth, localHeight));
    }

    private static int[] chamferDistanceInside(boolean[] inside, int width, int height) {
        int[] distance = new int[inside.length];
        for (int cell = 0; cell < inside.length; cell++) {
            distance[cell] = inside[cell] ? INFINITE_DISTANCE : 0;
        }
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

    private static ComponentGeometry collectComponent(
            int start,
            boolean[] water,
            boolean[] visited,
            int[] component,
            int width,
            int height) {
        int head = 0;
        int tail = 0;
        component[tail++] = start;
        visited[start] = true;
        int startX = start % width;
        int startY = start / width;
        int minX = startX;
        int maxX = startX;
        int minY = startY;
        int maxY = startY;
        boolean touchesBoundary = startX == 0 || startX == width - 1 || startY == 0 || startY == height - 1;

        while (head < tail) {
            int cell = component[head++];
            int x = cell % width;
            int y = cell / width;
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
            touchesBoundary |= x == 0 || x == width - 1 || y == 0 || y == height - 1;
            for (int direction = 0; direction < DX.length; direction++) {
                int nx = x + DX[direction];
                int ny = y + DY[direction];
                if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue;
                int next = ny * width + nx;
                if (!water[next] || visited[next]) continue;
                visited[next] = true;
                component[tail++] = next;
            }
        }
        return new ComponentGeometry(tail, minX, maxX, minY, maxY, start, touchesBoundary);
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

    private static boolean sameHorizontalBounds(WorldBounds first, WorldBounds second) {
        return first.minX() == second.minX()
                && first.maxX() == second.maxX()
                && first.minY() == second.minY()
                && first.maxY() == second.maxY();
    }

    private record ComponentGeometry(
            int size,
            int minX,
            int maxX,
            int minY,
            int maxY,
            int anchor,
            boolean touchesBoundary) {
    }

    private record LocalDistanceField(
            int minX,
            int minY,
            int width,
            int[] distance) {
        int distanceAtWorld(int x, int y) {
            return distance[(y - minY) * width + (x - minX)];
        }
    }
}
