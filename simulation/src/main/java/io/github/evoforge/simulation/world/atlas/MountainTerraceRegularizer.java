package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.PriorityQueue;

/**
 * Narrow voxel-readability correction for the composed V13 mountain surface.
 *
 * <p>The mountain morphology itself remains owned entirely by {@link MountainMorphologyAlgorithm}.
 * This pass does not smooth the V12 foundation, shave summits, inspect runtime Shapes, or replace the
 * accepted macro mountain profile. It only raises the lower side of an overly compressed cardinal
 * mountain slope. A small dry-land apron is available when the accepted mountain footprint ends too
 * early to widen a Z band without violating the original mountain-uplift slope budget.</p>
 */
final class MountainTerraceRegularizer {
    private static final long CELL = ElevationField.SUBUNITS_PER_CELL;

    /**
     * Two fifths of a cell per cardinal step leaves at least 2.5 horizontal cells per vertical level.
     * This is deliberately a surface-geometry rule rather than a contract with any concrete Shape.
     */
    static final long MAXIMUM_COMPOSED_CARDINAL_RISE = CELL * 2L / 5L;

    /**
     * A full Z level at the composed-rise budget spans three cardinal cells. The correction may borrow
     * no more land than that from around the already accepted mountain footprint, and only where the
     * slope constraints actually require positive uplift.
     */
    static final int MAXIMUM_LAND_APRON_CELLS = Math.toIntExact(
            Math.floorDiv(CELL + MAXIMUM_COMPOSED_CARDINAL_RISE - 1L, MAXIMUM_COMPOSED_CARDINAL_RISE));

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
        boolean[] originalMountain = new boolean[area];
        boolean anyMountain = false;
        long originalMaximumSurface = Long.MIN_VALUE;
        int cell = 0;
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                long baseHeight = base.elevationSubunitsAt(x, y);
                long generatedHeight = generated.elevationSubunitsAt(x, y);
                baseHeights[cell] = baseHeight;
                land[cell] = baseHeight > ElevationGenerationStage.SEA_LEVEL_SUBUNITS;
                originalMountain[cell] = land[cell] && generatedHeight > baseHeight;
                uplift[cell] = originalMountain[cell] ? generatedHeight - baseHeight : 0L;
                anyMountain |= originalMountain[cell];
                originalMaximumSurface = Math.max(originalMaximumSurface, generatedHeight);
                cell++;
            }
        }
        if (!anyMountain) return generated;

        boolean[] editable = editableLandApron(originalMountain, land, width, height);
        long[] upliftCaps = editableUpliftCaps(
                editable,
                land,
                width,
                height,
                maximumUpliftCardinalRise);
        for (cell = 0; cell < area; cell++) {
            if (!editable[cell]) continue;
            long summitCap = Math.max(0L, originalMaximumSurface - baseHeights[cell]);
            upliftCaps[cell] = Math.min(upliftCaps[cell], summitCap);
            if (originalMountain[cell] && uplift[cell] > upliftCaps[cell]) {
                throw new IllegalStateException("accepted mountain uplift exceeds terrace-preservation cap");
            }
        }

        PriorityQueue<SurfaceNode> queue = new PriorityQueue<>((first, second) -> {
            int bySurface = Long.compare(second.surfaceHeight(), first.surfaceHeight());
            return bySurface != 0 ? bySurface : Integer.compare(first.cell(), second.cell());
        });
        for (cell = 0; cell < area; cell++) {
            if (originalMountain[cell]) {
                queue.add(new SurfaceNode(cell, baseHeights[cell] + uplift[cell]));
            }
        }

        /*
         * Solve both monotone lower-bound constraints in one queue:
         *   1. the composed V12 + mountain surface should not spend a whole Z level in one cell;
         *   2. the original mountain-uplift Lipschitz budget remains authoritative.
         *
         * Every adjustment is upward and capped by the unchanged summit height. The small editable
         * apron is only activated by propagation from an existing mountain cell, so unrelated V12
         * relief remains bit-identical.
         */
        while (!queue.isEmpty()) {
            SurfaceNode node = queue.poll();
            cell = node.cell();
            long surfaceHeight = baseHeights[cell] + uplift[cell];
            if (node.surfaceHeight() != surfaceHeight) continue;

            int x = cell % width;
            int y = cell / width;
            if (x > 0) relaxNeighbour(
                    cell, cell - 1, baseHeights, uplift, editable, upliftCaps,
                    maximumUpliftCardinalRise, queue);
            if (x + 1 < width) relaxNeighbour(
                    cell, cell + 1, baseHeights, uplift, editable, upliftCaps,
                    maximumUpliftCardinalRise, queue);
            if (y > 0) relaxNeighbour(
                    cell, cell - width, baseHeights, uplift, editable, upliftCaps,
                    maximumUpliftCardinalRise, queue);
            if (y + 1 < height) relaxNeighbour(
                    cell, cell + width, baseHeights, uplift, editable, upliftCaps,
                    maximumUpliftCardinalRise, queue);
        }

        long[] surface = baseHeights.clone();
        for (cell = 0; cell < area; cell++) {
            if (uplift[cell] > 0L) surface[cell] = Math.addExact(baseHeights[cell], uplift[cell]);
        }
        return new DenseElevationField(bounds, surface);
    }

    private static void relaxNeighbour(
            int source,
            int target,
            long[] base,
            long[] uplift,
            boolean[] editable,
            long[] upliftCaps,
            long maximumUpliftRise,
            PriorityQueue<SurfaceNode> queue) {
        if (!editable[target]) return;

        long sourceSurface = base[source] + uplift[source];
        long requiredByComposedSurface = sourceSurface
                - MAXIMUM_COMPOSED_CARDINAL_RISE
                - base[target];
        long requiredByUpliftBudget = uplift[source] - maximumUpliftRise;
        long requiredUplift = Math.max(requiredByComposedSurface, requiredByUpliftBudget);
        long candidate = Math.min(upliftCaps[target], requiredUplift);
        if (candidate <= uplift[target] || candidate <= 0L) return;

        uplift[target] = candidate;
        queue.add(new SurfaceNode(target, base[target] + candidate));
    }

    private static boolean[] editableLandApron(
            boolean[] originalMountain,
            boolean[] land,
            int width,
            int height) {
        int[] distance = new int[originalMountain.length];
        Arrays.fill(distance, Integer.MAX_VALUE);
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int cell = 0; cell < originalMountain.length; cell++) {
            if (!originalMountain[cell]) continue;
            distance[cell] = 0;
            queue.addLast(cell);
        }

        while (!queue.isEmpty()) {
            int cell = queue.removeFirst();
            if (distance[cell] >= MAXIMUM_LAND_APRON_CELLS) continue;
            int nextDistance = distance[cell] + 1;
            int x = cell % width;
            int y = cell / width;
            if (x > 0) spreadLandDistance(cell - 1, nextDistance, land, distance, queue);
            if (x + 1 < width) spreadLandDistance(cell + 1, nextDistance, land, distance, queue);
            if (y > 0) spreadLandDistance(cell - width, nextDistance, land, distance, queue);
            if (y + 1 < height) spreadLandDistance(cell + width, nextDistance, land, distance, queue);
        }

        boolean[] editable = new boolean[originalMountain.length];
        for (int cell = 0; cell < editable.length; cell++) {
            editable[cell] = land[cell] && distance[cell] <= MAXIMUM_LAND_APRON_CELLS;
        }
        return editable;
    }

    private static void spreadLandDistance(
            int target,
            int candidateDistance,
            boolean[] land,
            int[] distance,
            ArrayDeque<Integer> queue) {
        if (!land[target] || candidateDistance >= distance[target]) return;
        distance[target] = candidateDistance;
        queue.addLast(target);
    }

    /**
     * Maximum uplift that can still fall to fixed zero-uplift land outside the editable apron at the
     * accepted mountain rise rate. Ocean cells are intentionally not anchors: the baseline mountain
     * model already owns its independent coastal-cliff policy.
     */
    private static long[] editableUpliftCaps(
            boolean[] editable,
            boolean[] land,
            int width,
            int height,
            long maximumUpliftRise) {
        int[] distance = new int[editable.length];
        Arrays.fill(distance, Integer.MAX_VALUE);
        ArrayDeque<Integer> queue = new ArrayDeque<>();

        for (int cell = 0; cell < editable.length; cell++) {
            if (!editable[cell]) continue;
            int x = cell % width;
            int y = cell / width;
            if ((x > 0 && land[cell - 1] && !editable[cell - 1])
                    || (x + 1 < width && land[cell + 1] && !editable[cell + 1])
                    || (y > 0 && land[cell - width] && !editable[cell - width])
                    || (y + 1 < height && land[cell + width] && !editable[cell + width])) {
                distance[cell] = 1;
                queue.addLast(cell);
            }
        }

        while (!queue.isEmpty()) {
            int cell = queue.removeFirst();
            int nextDistance = distance[cell] + 1;
            int x = cell % width;
            int y = cell / width;
            if (x > 0) spreadEditableDistance(cell - 1, nextDistance, editable, distance, queue);
            if (x + 1 < width) spreadEditableDistance(cell + 1, nextDistance, editable, distance, queue);
            if (y > 0) spreadEditableDistance(cell - width, nextDistance, editable, distance, queue);
            if (y + 1 < height) spreadEditableDistance(cell + width, nextDistance, editable, distance, queue);
        }

        long[] caps = new long[editable.length];
        for (int cell = 0; cell < caps.length; cell++) {
            if (!editable[cell]) {
                caps[cell] = 0L;
            } else if (distance[cell] == Integer.MAX_VALUE) {
                caps[cell] = Long.MAX_VALUE;
            } else {
                caps[cell] = Math.multiplyExact((long) distance[cell], maximumUpliftRise);
            }
        }
        return caps;
    }

    private static void spreadEditableDistance(
            int target,
            int candidateDistance,
            boolean[] editable,
            int[] distance,
            ArrayDeque<Integer> queue) {
        if (!editable[target] || candidateDistance >= distance[target]) return;
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
