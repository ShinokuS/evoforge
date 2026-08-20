package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.ArrayDeque;
import java.util.Arrays;

/**
 * Authors inland-lake depth from the geometry of the already accepted Z=0 water footprint.
 *
 * <p>The model deliberately mirrors the accepted ocean-bathymetry principle: shoreline distance
 * supplies the available depth envelope. Inland lakes do not receive independent pits, basin
 * centers or cell-scale interior noise. Dry terrain and boundary-connected water remain bit-exact.
 * The resulting discrete depth terraces are intentionally broad: approximately two cardinal cells
 * of inward room are required before another full Z level may appear.</p>
 */
final class DistanceProfileInlandLakeBathymetryAlgorithm implements InlandLakeBathymetryAlgorithm {
    static final DistanceProfileInlandLakeBathymetryAlgorithm INSTANCE =
            new DistanceProfileInlandLakeBathymetryAlgorithm();

    private static final int DISTANCE_SCALE = 1_000;
    private static final int CARDINAL_DISTANCE = DISTANCE_SCALE;
    private static final int DIAGONAL_DISTANCE = 1_414;
    private static final int INFINITE_DISTANCE = Integer.MAX_VALUE / 4;
    private static final int[] DX = {-1, 1, 0, 0};
    private static final int[] DY = {0, 0, -1, 1};

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

        int[] shorelineDistance = chamferDistanceFromDry(water, width, height);
        boolean[] visited = new boolean[area];
        int[] component = new int[area];
        for (int start = 0; start < area; start++) {
            if (!water[start] || visited[start]) continue;
            int componentSize = collectComponent(start, water, visited, component, width, height);
            if (touchesWorldBoundary(component, componentSize, width, height)) continue;
            refineInlandComponent(
                    result,
                    shorelineDistance,
                    component,
                    componentSize,
                    bathymetricTerrain.bounds(),
                    recipe);
        }
        return new DenseElevationField(bounds, result);
    }

    private static void refineInlandComponent(
            long[] elevation,
            int[] shorelineDistance,
            int[] component,
            int componentSize,
            WorldBounds bounds,
            InlandLakeBathymetryRecipe recipe) {
        int maximumDistance = 0;
        for (int i = 0; i < componentSize; i++) {
            maximumDistance = Math.max(maximumDistance, shorelineDistance[component[i]]);
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

        /*
         * Keep one full shallow Z next to shore. Afterwards each additional full Z requires the
         * same horizontal room implied by the recipe's radius/depth ratio. With balanced 1:2 this
         * means two cardinal cells per visible level; diagonal contours inherit the same metric via
         * the 1/sqrt(2)-aware chamfer field instead of Manhattan diamonds/rectangles.
         */
        long bandWidthScaled = Math.max(
                DISTANCE_SCALE,
                (long) DISTANCE_SCALE
                        * recipe.radiusDepthDenominator()
                        / recipe.radiusDepthNumerator());

        for (int i = 0; i < componentSize; i++) {
            int cell = component[i];
            long inwardBeyondShore = Math.max(0L, shorelineDistance[cell] - CARDINAL_DISTANCE);
            int authoredLevel = 1 + Math.toIntExact(inwardBeyondShore / bandWidthScaled);
            authoredLevel = Math.min(targetDepthCells, authoredLevel);
            long authoredDepth = Math.multiplyExact(
                    (long) authoredLevel,
                    ElevationField.SUBUNITS_PER_CELL);
            long existingDepth = Math.negateExact(elevation[cell]);
            elevation[cell] = -Math.max(existingDepth, authoredDepth);
        }
    }

    private static int[] chamferDistanceFromDry(boolean[] water, int width, int height) {
        int[] distance = new int[water.length];
        boolean hasDry = false;
        for (int cell = 0; cell < water.length; cell++) {
            if (water[cell]) {
                distance[cell] = INFINITE_DISTANCE;
            } else {
                distance[cell] = 0;
                hasDry = true;
            }
        }
        if (!hasDry) return distance;

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

    private static int plus(int distance, int increment) {
        if (distance >= INFINITE_DISTANCE - increment) return INFINITE_DISTANCE;
        return distance + increment;
    }

    private static int collectComponent(
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
        while (head < tail) {
            int cell = component[head++];
            int x = cell % width;
            int y = cell / width;
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
        return tail;
    }

    private static boolean touchesWorldBoundary(
            int[] component,
            int componentSize,
            int width,
            int height) {
        for (int i = 0; i < componentSize; i++) {
            int cell = component[i];
            int x = cell % width;
            int y = cell / width;
            if (x == 0 || x == width - 1 || y == 0 || y == height - 1) return true;
        }
        return false;
    }

    private static boolean sameHorizontalBounds(WorldBounds first, WorldBounds second) {
        return first.minX() == second.minX()
                && first.maxX() == second.maxX()
                && first.minY() == second.minY()
                && first.maxY() == second.maxY();
    }
}
