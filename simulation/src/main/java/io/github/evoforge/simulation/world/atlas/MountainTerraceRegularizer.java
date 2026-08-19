package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.spatial.WorldBounds;
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

    static ElevationField widenNarrowLevels(ElevationField base, ElevationField generated) {
        if (base == null || generated == null) {
            throw new IllegalArgumentException("mountain terrace inputs must not be null");
        }
        if (!sameHorizontalBounds(base.bounds(), generated.bounds())) {
            throw new IllegalArgumentException("base and generated surfaces must share horizontal bounds");
        }

        WorldBounds bounds = generated.bounds();
        int width = Math.toIntExact((long) bounds.maxX() - bounds.minX() + 1L);
        int height = Math.toIntExact((long) bounds.maxY() - bounds.minY() + 1L);
        int area = Math.toIntExact(Math.multiplyExact((long) width, height));

        long[] surface = new long[area];
        boolean[] mountain = new boolean[area];
        boolean anyMountain = false;
        int cell = 0;
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                long baseHeight = base.elevationSubunitsAt(x, y);
                long generatedHeight = generated.elevationSubunitsAt(x, y);
                surface[cell] = generatedHeight;
                mountain[cell] = baseHeight > ElevationGenerationStage.SEA_LEVEL_SUBUNITS
                        && generatedHeight > baseHeight;
                anyMountain |= mountain[cell];
                cell++;
            }
        }
        if (!anyMountain) return generated;

        PriorityQueue<SurfaceNode> queue = new PriorityQueue<>((first, second) -> {
            int byHeight = Long.compare(second.height(), first.height());
            return byHeight != 0 ? byHeight : Integer.compare(first.cell(), second.cell());
        });
        for (cell = 0; cell < area; cell++) {
            if (mountain[cell]) queue.add(new SurfaceNode(cell, surface[cell]));
        }

        // Monotone propagation can only raise lower cells. It therefore converges without the
        // oscillation risk of a bidirectional smoothing pass and cannot shave or move a summit.
        while (!queue.isEmpty()) {
            SurfaceNode node = queue.poll();
            cell = node.cell();
            if (node.height() != surface[cell]) continue;

            long minimumNeighbourHeight = surface[cell] - MAXIMUM_COMPOSED_CARDINAL_RISE;
            int x = cell % width;
            int y = cell / width;
            if (x > 0) propagate(cell - 1, minimumNeighbourHeight, surface, mountain, queue);
            if (x + 1 < width) propagate(cell + 1, minimumNeighbourHeight, surface, mountain, queue);
            if (y > 0) propagate(cell - width, minimumNeighbourHeight, surface, mountain, queue);
            if (y + 1 < height) propagate(cell + width, minimumNeighbourHeight, surface, mountain, queue);
        }

        return new DenseElevationField(bounds, surface);
    }

    private static void propagate(
            int target,
            long minimumHeight,
            long[] surface,
            boolean[] mountain,
            PriorityQueue<SurfaceNode> queue) {
        if (!mountain[target] || minimumHeight <= surface[target]) return;
        surface[target] = minimumHeight;
        queue.add(new SurfaceNode(target, minimumHeight));
    }

    private static boolean sameHorizontalBounds(WorldBounds first, WorldBounds second) {
        return first.minX() == second.minX()
                && first.maxX() == second.maxX()
                && first.minY() == second.minY()
                && first.maxY() == second.maxY();
    }

    private record SurfaceNode(int cell, long height) {
    }
}
