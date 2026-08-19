package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.PriorityQueue;

/**
 * Narrow voxel-readability correction for the composed V13 mountain surface.
 *
 * <p>The mountain morphology itself remains owned entirely by {@link MountainMorphologyAlgorithm}.
 * This pass does not reshape peaks, smooth the V12 foundation, grow the mountain footprint, inspect
 * runtime Shapes, or change terrain outside cells that already received dedicated mountain uplift.
 * It only raises the lower side of an overly compressed cardinal slope until one vertical level has
 * enough horizontal room to remain readable after voxel quantization.</p>
 */
final class MountainTerraceRegularizer {
    private static final long CELL = ElevationField.SUBUNITS_PER_CELL;

    /**
     * Two fifths of a cell per cardinal step leaves at least 2.5 horizontal cells per vertical level.
     * This is deliberately a surface-geometry rule rather than a contract with any concrete Shape.
     */
    static final long MAXIMUM_COMPOSED_CARDINAL_RISE = CELL * 2L / 5L;

    private MountainTerraceRegularizer() {
    }

    static ElevationField widenNarrowLevels(
            ElevationField base,
            ElevationField generated,
            long maximumUpliftCardinalRise) {
        if (base == null || generated == null) {
            throw new IllegalArgumentException("mountain terrace inputs must not be null");
        }
        if (maximumUpliftCardinalRise <= 0L) {
            throw new IllegalArgumentException("maximum mountain uplift rise must be positive");
        }
        if (!sameHorizontalBounds(base.bounds(), generated.bounds())) {
            throw new IllegalArgumentException("base and generated surfaces must share horizontal bounds");
        }

        WorldBounds bounds = generated.bounds();
        int width = Math.toIntExact((long) bounds.maxX() - bounds.minX() + 1L);
        int height = Math.toIntExact((long) bounds.maxY() - bounds.minY() + 1L);
        int area = Math.toIntExact(Math.multiplyExact((long) width, height));

        long[] baseHeights = new long[area];
        long[] uplift = new long[area];
        boolean[] land = new boolean[area];
        boolean[] mountain = new boolean[area];
        boolean anyMountain = false;
        long originalMaximumSurface = Long.MIN_VALUE;
        int cell = 0;
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                long baseHeight = base.elevationSubunitsAt(x, y);
                long generatedHeight = generated.elevationSubunitsAt(x, y);
                baseHeights[cell] = baseHeight;
                land[cell] = baseHeight > ElevationGenerationStage.SEA_LEVEL_SUBUNITS;
                mountain[cell] = land[cell] && generatedHeight > baseHeight;
                uplift[cell] = mountain[cell] ? generatedHeight - baseHeight : 0L;
                anyMountain |= mountain[cell];
                originalMaximumSurface = Math.max(originalMaximumSurface, generatedHeight);
                cell++;
            }
        }
        if (!anyMountain) return generated;

        long[] upliftCaps = footprintUpliftCaps(
                mountain,
                land,
                width,
                height,
                maximumUpliftCardinalRise);
        for (cell = 0; cell < area; cell++) {
            if (!mountain[cell]) continue;
            long summitCap = originalMaximumSurface - baseHeights[cell];
            upliftCaps[cell] = Math.min(upliftCaps[cell], summitCap);
            if (uplift[cell] > upliftCaps[cell]) {
                throw new IllegalStateException("accepted mountain uplift already exceeds terrace-preservation cap");
            }
        }

        PriorityQueue<SurfaceNode> queue = new PriorityQueue<>((first, second) -> {
            int bySurface = Long.compare(second.surfaceHeight(), first.surfaceHeight());
            return bySurface != 0 ? bySurface : Integer.compare(first.cell(), second.cell());
        });
        for (cell = 0; cell < area; cell++) {
            if (mountain[cell]) {
                queue.add(new SurfaceNode(cell, baseHeights[cell] + uplift[cell]));
            }
        }

        /*
         * Solve both monotone lower-bound constraints in one queue:
         *   1. the composed V12 + mountain surface should not spend a whole Z level in one cell;
         *   2. the original mountain-uplift Lipschitz budget remains authoritative.
         *
         * Every adjustment is upward, inside the original mountain footprint, and capped by both
         * the unchanged summit height and the amount of uplift that can taper back to zero before
         * leaving that footprint. Consequently this corrects compressed terraces without replacing
         * the accepted macro mountain with a newly smoothed surface.
         */
        while (!queue.isEmpty()) {
            SurfaceNode node = queue.poll();
            cell = node.cell();
            long surfaceHeight = baseHeights[cell] + uplift[cell];
            if (node.surfaceHeight() != surfaceHeight) continue;

            int x = cell % width;
            int y = cell / width;
            if (x > 0) relaxNeighbour(
                    cell, cell - 1, baseHeights, uplift, mountain, upliftCaps,
                    maximumUpliftCardinalRise, queue);
            if (x + 1 < width) relaxNeighbour(
                    cell, cell + 1, baseHeights, uplift, mountain, upliftCaps,
                    maximumUpliftCardinalRise, queue);
            if (y > 0) relaxNeighbour(
                    cell, cell - width, baseHeights, uplift, mountain, upliftCaps,
                    maximumUpliftCardinalRise, queue);
            if (y + 1 < height) relaxNeighbour(
                    cell, cell + width, baseHeights, uplift, mountain, upliftCaps,
                    maximumUpliftCardinalRise, queue);
        }

        long[] surface = baseHeights.clone();
        for (cell = 0; cell < area; cell++) {
            if (mountain[cell]) surface[cell] = Math.addExact(baseHeights[cell], uplift[cell]);
        }
        return new DenseElevationField(bounds, surface);
    }

    private static void relaxNeighbour(
            int source,
            int target,
            long[] base,
            long[] uplift,
            boolean[] mountain,
            long[] upliftCaps,
            long maximumUpliftRise,
            PriorityQueue<SurfaceNode> queue) {
        if (!mountain[target]) return;

        long sourceSurface = base[source] + uplift[source];
        long requiredByComposedSurface = sourceSurface
                - MAXIMUM_COMPOSED_CARDINAL_RISE
                - base[target];
        long requiredByUpliftBudget = uplift[source] - maximumUpliftRise;
        long requiredUplift = Math.max(requiredByComposedSurface, requiredByUpliftBudget);
        long candidate = Math.min(upliftCaps[target], requiredUplift);
        if (candidate <= uplift[target]) return;

        uplift[target] = candidate;
        queue.add(new SurfaceNode(target, base[target] + candidate));
    }

    /**
     * Maximum uplift that can still fall to the fixed zero-uplift land outside the original mountain
     * footprint at the original mountain rise rate. Ocean cells are intentionally not anchors: the
     * accepted mountain model already owns its independent coastal-cliff policy.
     */
    private static long[] footprintUpliftCaps(
            boolean[] mountain,
            boolean[] land,
            int width,
            int height,
            long maximumUpliftRise) {
        int[] distance = new int[mountain.length];
        Arrays.fill(distance, Integer.MAX_VALUE);
        ArrayDeque<Integer> queue = new ArrayDeque<>();

        for (int cell = 0; cell < mountain.length; cell++) {
            if (!mountain[cell]) continue;
            int x = cell % width;
            int y = cell / width;
            if ((x > 0 && land[cell - 1] && !mountain[cell - 1])
                    || (x + 1 < width && land[cell + 1] && !mountain[cell + 1])
                    || (y > 0 && land[cell - width] && !mountain[cell - width])
                    || (y + 1 < height && land[cell + width] && !mountain[cell + width])) {
                distance[cell] = 1;
                queue.add(cell);
            }
        }

        while (!queue.isEmpty()) {
            cellLoop:
            {
                int cell = queue.removeFirst();
                int nextDistance = distance[cell] + 1;
                int x = cell % width;
                int y = cell / width;
                if (x > 0) spreadDistance(cell - 1, nextDistance, mountain, distance, queue);
                if (x + 1 < width) spreadDistance(cell + 1, nextDistance, mountain, distance, queue);
                if (y > 0) spreadDistance(cell - width, nextDistance, mountain, distance, queue);
                if (y + 1 < height) spreadDistance(cell + width, nextDistance, mountain, distance, queue);
                break cellLoop;
            }
        }

        long[] caps = new long[mountain.length];
        for (int cell = 0; cell < caps.length; cell++) {
            if (!mountain[cell]) {
                caps[cell] = 0L;
            } else if (distance[cell] == Integer.MAX_VALUE) {
                caps[cell] = Long.MAX_VALUE;
            } else {
                caps[cell] = Math.multiplyExact((long) distance[cell], maximumUpliftRise);
            }
        }
        return caps;
    }

    private static void spreadDistance(
            int target,
            int candidateDistance,
            boolean[] mountain,
            int[] distance,
            ArrayDeque<Integer> queue) {
        if (!mountain[target] || candidateDistance >= distance[target]) return;
        distance[target] = candidateDistance;
        queue.addLast(target);
    }

    private static boolean sameHorizontalBounds(WorldBounds first, WorldBounds second) {
        return first.minX() == second.minX()
                && first.maxX() == second.maxX()
                && first.minY() == second.minY()
                && first.maxY() == second.maxY();
    }

    private record SurfaceNode(int cell, long surfaceHeight) {
    }
}
