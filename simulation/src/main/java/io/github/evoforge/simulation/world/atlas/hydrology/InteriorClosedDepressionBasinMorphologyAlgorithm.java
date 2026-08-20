package io.github.evoforge.simulation.world.atlas.hydrology;

import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Authors sparse broad inland depressions behind an untouched dry rim.
 *
 * <p>The algorithm is intentionally independent of landmass Fragmentation. It reads only the
 * already-generated terrain. Existing ocean cells are copied bit-identically and every dry cell
 * remains at or above sea level, so this owner cannot change continental membership. The closed
 * rim makes the result a real terrain basin rather than a synthetic water mask.
 */
public final class InteriorClosedDepressionBasinMorphologyAlgorithm
        implements LacustrineBasinMorphologyAlgorithm {
    private static final int[] DX = {-1, 1, 0, 0};
    private static final int[] DY = {0, 0, -1, 1};

    @Override
    public LacustrineBasinTerrain generate(
            ElevationField baseElevation,
            LacustrineBasinMorphologyRecipe recipe) {
        if (baseElevation == null || recipe == null) {
            throw new IllegalArgumentException("lacustrine basin morphology inputs must not be null");
        }

        WorldBounds bounds = baseElevation.bounds();
        int width = Math.toIntExact((long) bounds.maxX() - bounds.minX() + 1L);
        int height = Math.toIntExact((long) bounds.maxY() - bounds.minY() + 1L);
        int area = Math.multiplyExact(width, height);
        long[] elevations = new long[area];
        long landCells = 0L;
        for (int localY = 0; localY < height; localY++) {
            int y = bounds.minY() + localY;
            for (int localX = 0; localX < width; localX++) {
                int x = bounds.minX() + localX;
                int index = localY * width + localX;
                elevations[index] = baseElevation.elevationSubunitsAt(x, y);
                if (elevations[index] >= 0L) landCells++;
            }
        }

        if (landCells < 4L) {
            return new LacustrineBasinTerrain(
                    new DenseHydrologicElevationField(bounds, elevations),
                    0);
        }

        int[] oceanDistance = cardinalDistanceToOceanOrBoundary(width, height, elevations);
        int targetBasins = Math.min(
                recipe.maximumBasinCount(),
                Math.max(1, Math.toIntExact(Math.floorDiv(
                        landCells + recipe.targetLandCellsPerBasin() - 1L,
                        recipe.targetLandCellsPerBasin()))));

        List<Candidate> selected = new ArrayList<>();
        for (int ordinal = 0; ordinal < targetBasins; ordinal++) {
            Candidate best = bestCandidate(
                    width,
                    height,
                    elevations,
                    oceanDistance,
                    selected,
                    recipe);
            if (best == null) break;
            imprint(width, height, elevations, best, recipe);
            selected.add(best);
        }

        return new LacustrineBasinTerrain(
                new DenseHydrologicElevationField(bounds, elevations),
                selected.size());
    }

    private static Candidate bestCandidate(
            int width,
            int height,
            long[] elevations,
            int[] oceanDistance,
            List<Candidate> selected,
            LacustrineBasinMorphologyRecipe recipe) {
        Candidate best = null;
        for (int y = recipe.maximumRadiusCells(); y < height - recipe.maximumRadiusCells() - 1; y++) {
            for (int x = recipe.maximumRadiusCells(); x < width - recipe.maximumRadiusCells() - 1; x++) {
                int index = y * width + x;
                if (elevations[index] < 0L) continue;
                int radius = Math.max(
                        recipe.minimumRadiusCells(),
                        Math.min(recipe.maximumRadiusCells(), oceanDistance[index] / 2));
                if (oceanDistance[index] < radius + 1) continue;
                if (!separated(x, y, radius, selected)) continue;

                long rim = squareRimMinimum(width, height, elevations, x, y, radius);
                if (rim < recipe.minimumDepthSubunits()) continue;
                Candidate candidate = new Candidate(x, y, radius, rim, oceanDistance[index], elevations[index]);
                if (better(candidate, best)) best = candidate;
            }
        }
        return best;
    }

    private static boolean better(Candidate candidate, Candidate best) {
        if (best == null) return true;
        if (candidate.oceanDistance() != best.oceanDistance()) {
            return candidate.oceanDistance() > best.oceanDistance();
        }
        if (candidate.centerElevation() != best.centerElevation()) {
            return candidate.centerElevation() < best.centerElevation();
        }
        if (candidate.y() != best.y()) return candidate.y() < best.y();
        return candidate.x() < best.x();
    }

    private static boolean separated(
            int x,
            int y,
            int radius,
            List<Candidate> selected) {
        for (Candidate previous : selected) {
            long dx = (long) x - previous.x();
            long dy = (long) y - previous.y();
            long minimum = (long) radius + previous.radius() + 2L;
            if (dx * dx + dy * dy < minimum * minimum) return false;
        }
        return true;
    }

    private static long squareRimMinimum(
            int width,
            int height,
            long[] elevations,
            int centerX,
            int centerY,
            int radius) {
        long minimum = Long.MAX_VALUE;
        for (int y = centerY - radius; y <= centerY + radius + 1; y++) {
            for (int x = centerX - radius; x <= centerX + radius + 1; x++) {
                if (x < 0 || x >= width || y < 0 || y >= height) return Long.MIN_VALUE;
                int dx = x < centerX ? centerX - x : Math.max(0, x - (centerX + 1));
                int dy = y < centerY ? centerY - y : Math.max(0, y - (centerY + 1));
                if (Math.max(dx, dy) != radius) continue;
                long elevation = elevations[y * width + x];
                if (elevation < 0L) return Long.MIN_VALUE;
                minimum = Math.min(minimum, elevation);
            }
        }
        return minimum;
    }

    private static void imprint(
            int width,
            int height,
            long[] elevations,
            Candidate candidate,
            LacustrineBasinMorphologyRecipe recipe) {
        long depth = calibratedDepth(candidate.radius(), recipe);
        double support = Math.max(1.0, candidate.radius());
        for (int y = candidate.y() - candidate.radius() + 1;
                y <= candidate.y() + candidate.radius();
                y++) {
            for (int x = candidate.x() - candidate.radius() + 1;
                    x <= candidate.x() + candidate.radius();
                    x++) {
                if (x < 0 || x >= width || y < 0 || y >= height) continue;
                int index = y * width + x;
                if (elevations[index] < 0L) continue;

                int coreDx = x < candidate.x()
                        ? candidate.x() - x
                        : Math.max(0, x - (candidate.x() + 1));
                int coreDy = y < candidate.y()
                        ? candidate.y() - y
                        : Math.max(0, y - (candidate.y() + 1));
                double distance = StrictMath.sqrt((double) coreDx * coreDx + (double) coreDy * coreDy);
                double t = Math.max(0.0, 1.0 - distance / support);
                if (t <= 0.0) continue;
                double kernel = t * t * (3.0 - 2.0 * t);
                long localCut = Math.max(1L, Math.round(depth * kernel));
                long target = Math.max(0L, candidate.rimElevation() - localCut);
                elevations[index] = Math.min(elevations[index], target);
            }
        }
    }

    private static long calibratedDepth(
            int radius,
            LacustrineBasinMorphologyRecipe recipe) {
        if (recipe.maximumRadiusCells() == recipe.minimumRadiusCells()) {
            return recipe.minimumDepthSubunits();
        }
        long span = recipe.maximumDepthSubunits() - recipe.minimumDepthSubunits();
        long numerator = (long) radius - recipe.minimumRadiusCells();
        long denominator = (long) recipe.maximumRadiusCells() - recipe.minimumRadiusCells();
        return recipe.minimumDepthSubunits() + span * numerator / denominator;
    }

    private static int[] cardinalDistanceToOceanOrBoundary(
            int width,
            int height,
            long[] elevations) {
        int[] distance = new int[elevations.length];
        Arrays.fill(distance, Integer.MAX_VALUE);
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                if (elevations[index] < 0L || x == 0 || y == 0 || x == width - 1 || y == height - 1) {
                    distance[index] = 0;
                    queue.addLast(index);
                }
            }
        }
        while (!queue.isEmpty()) {
            int current = queue.removeFirst();
            int x = current % width;
            int y = current / width;
            for (int direction = 0; direction < DX.length; direction++) {
                int nx = x + DX[direction];
                int ny = y + DY[direction];
                if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue;
                int next = ny * width + nx;
                if (distance[next] <= distance[current] + 1) continue;
                distance[next] = distance[current] + 1;
                queue.addLast(next);
            }
        }
        return distance;
    }

    private record Candidate(
            int x,
            int y,
            int radius,
            long rimElevation,
            int oceanDistance,
            long centerElevation) {
    }
}
