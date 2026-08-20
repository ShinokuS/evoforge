package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.ArrayDeque;
import java.util.Arrays;

/**
 * Deepens broad inland standing-water bodies from their distance to dry shoreline.
 *
 * <p>The input already owns every wet/dry decision. This algorithm copies dry terrain and
 * boundary-connected water bit-exactly, then may only make existing inland-water cells deeper.
 * A smooth normalized profile keeps the first submerged ring shallow while allowing a substantial
 * core depth that scales with the body's own geometric radius rather than the world ocean scale.</p>
 */
final class DistanceProfileInlandLakeBathymetryAlgorithm implements InlandLakeBathymetryAlgorithm {
    static final DistanceProfileInlandLakeBathymetryAlgorithm INSTANCE =
            new DistanceProfileInlandLakeBathymetryAlgorithm();

    private static final int PROFILE_SCALE = 1_000_000;
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

        int[] shorelineDistance = distanceFromDry(water, width, height);
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
        if (maximumDistance < recipe.minimumSignificantRadiusCells()) return;

        int radiusScaledDepth = Math.toIntExact(
                (long) maximumDistance * recipe.radiusDepthNumerator() / recipe.radiusDepthDenominator());
        int targetDepthCells = Math.max(recipe.minimumSignificantDepthCells(), radiusScaledDepth);
        targetDepthCells = Math.min(recipe.maximumDepthCells(), targetDepthCells);
        targetDepthCells = Math.min(Math.negateExact(bounds.minZ()), targetDepthCells);
        if (targetDepthCells <= 0) return;

        long targetDepthSubunits = Math.multiplyExact(
                (long) targetDepthCells,
                ElevationField.SUBUNITS_PER_CELL);
        for (int i = 0; i < componentSize; i++) {
            int cell = component[i];
            int distance = shorelineDistance[cell];
            int coordinatePpm = maximumDistance <= 1
                    ? PROFILE_SCALE
                    : Math.toIntExact(
                            Math.min(
                                    (long) PROFILE_SCALE,
                                    (long) Math.max(0, distance - 1) * PROFILE_SCALE
                                            / Math.max(1, maximumDistance - 1)));
            int profilePpm = smootherStepPpm(coordinatePpm);
            long authoredDepth = ElevationField.SUBUNITS_PER_CELL
                    + Math.max(
                            0L,
                            (targetDepthSubunits - ElevationField.SUBUNITS_PER_CELL)
                                    * profilePpm / PROFILE_SCALE);
            long existingDepth = Math.negateExact(elevation[cell]);
            elevation[cell] = -Math.max(existingDepth, authoredDepth);
        }
    }

    private static int[] distanceFromDry(boolean[] water, int width, int height) {
        int[] distance = new int[water.length];
        Arrays.fill(distance, Integer.MAX_VALUE);
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int cell = 0; cell < water.length; cell++) {
            if (!water[cell]) {
                distance[cell] = 0;
                queue.addLast(cell);
            }
        }
        if (queue.isEmpty()) return distance;

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

    private static int smootherStepPpm(int coordinatePpm) {
        long t = Math.max(0, Math.min(PROFILE_SCALE, coordinatePpm));
        long t2 = t * t / PROFILE_SCALE;
        long t3 = t2 * t / PROFILE_SCALE;
        long t4 = t3 * t / PROFILE_SCALE;
        long t5 = t4 * t / PROFILE_SCALE;
        long value = 6L * t5 - 15L * t4 + 10L * t3;
        return (int) Math.max(0L, Math.min((long) PROFILE_SCALE, value));
    }

    private static boolean sameHorizontalBounds(WorldBounds first, WorldBounds second) {
        return first.minX() == second.minX()
                && first.maxX() == second.maxX()
                && first.minY() == second.minY()
                && first.maxY() == second.maxY();
    }
}
