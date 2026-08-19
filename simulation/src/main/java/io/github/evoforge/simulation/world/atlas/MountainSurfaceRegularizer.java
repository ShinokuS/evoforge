package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.spatial.WorldBounds;

/**
 * Geometry-only cleanup for voxel-scale readability of the final V13 mountain surface.
 *
 * <p>This class deliberately knows nothing about runtime Shapes. It only observes authoritative
 * elevation and discrete vertical cell levels. A one-cell mountain level squeezed between the same
 * neighbouring level is treated as contour noise and replaced by the local opposite-neighbour
 * surface. Wider bands are preserved for the generic shape fitter to interpret later.</p>
 */
final class MountainSurfaceRegularizer {
    private static final long CELL = ElevationField.SUBUNITS_PER_CELL;
    private static final long NO_SUGGESTION = Long.MIN_VALUE;

    private MountainSurfaceRegularizer() {
    }

    static ElevationField removeIsolatedSingleCellLevels(
            ElevationField base,
            ElevationField generated,
            int passes) {
        if (base == null || generated == null) {
            throw new IllegalArgumentException("mountain surface inputs must not be null");
        }
        if (passes < 0) throw new IllegalArgumentException("cleanup passes must be non-negative");
        if (!sameHorizontalBounds(base.bounds(), generated.bounds())) {
            throw new IllegalArgumentException("base and generated surfaces must share horizontal bounds");
        }
        if (passes == 0) return generated;

        WorldBounds bounds = generated.bounds();
        int width = Math.toIntExact((long) bounds.maxX() - bounds.minX() + 1L);
        int height = Math.toIntExact((long) bounds.maxY() - bounds.minY() + 1L);
        int area = Math.toIntExact(Math.multiplyExact((long) width, height));
        long ceiling = Math.multiplyExact((long) bounds.maxZ(), CELL);

        long[] surface = new long[area];
        boolean[] land = new boolean[area];
        boolean[] mountainInfluence = new boolean[area];
        int index = 0;
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                long value = generated.elevationSubunitsAt(x, y);
                long baseValue = base.elevationSubunitsAt(x, y);
                surface[index] = value;
                land[index] = value > ElevationGenerationStage.SEA_LEVEL_SUBUNITS;
                mountainInfluence[index] = land[index] && value != baseValue;
                index++;
            }
        }

        long[] scratch = new long[area];
        for (int pass = 0; pass < passes; pass++) {
            System.arraycopy(surface, 0, scratch, 0, area);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int cell = y * width + x;
                    if (!land[cell] || !mountainInfluence[cell]) continue;

                    long horizontal = oppositeNeighbourSuggestion(
                            surface,
                            land,
                            width,
                            height,
                            x,
                            y,
                            -1,
                            0,
                            1,
                            0);
                    long vertical = oppositeNeighbourSuggestion(
                            surface,
                            land,
                            width,
                            height,
                            x,
                            y,
                            0,
                            -1,
                            0,
                            1);

                    long replacement;
                    if (horizontal != NO_SUGGESTION && vertical != NO_SUGGESTION) {
                        replacement = midpoint(horizontal, vertical);
                    } else if (horizontal != NO_SUGGESTION) {
                        replacement = horizontal;
                    } else if (vertical != NO_SUGGESTION) {
                        replacement = vertical;
                    } else {
                        continue;
                    }
                    scratch[cell] = Math.max(1L, Math.min(ceiling, replacement));
                }
            }
            long[] swap = surface;
            surface = scratch;
            scratch = swap;
        }
        return new DenseElevationField(bounds, surface);
    }

    private static long oppositeNeighbourSuggestion(
            long[] surface,
            boolean[] land,
            int width,
            int height,
            int x,
            int y,
            int firstDx,
            int firstDy,
            int secondDx,
            int secondDy) {
        int firstX = x + firstDx;
        int firstY = y + firstDy;
        int secondX = x + secondDx;
        int secondY = y + secondDy;
        if (firstX < 0 || firstX >= width || firstY < 0 || firstY >= height
                || secondX < 0 || secondX >= width || secondY < 0 || secondY >= height) {
            return NO_SUGGESTION;
        }

        int center = y * width + x;
        int first = firstY * width + firstX;
        int second = secondY * width + secondX;
        if (!land[first] || !land[second]) return NO_SUGGESTION;

        long centerLayer = Math.floorDiv(surface[center], CELL);
        long firstLayer = Math.floorDiv(surface[first], CELL);
        long secondLayer = Math.floorDiv(surface[second], CELL);
        if (firstLayer != secondLayer || firstLayer == centerLayer) return NO_SUGGESTION;
        if (Math.abs(firstLayer - centerLayer) != 1L) return NO_SUGGESTION;
        return midpoint(surface[first], surface[second]);
    }

    private static long midpoint(long first, long second) {
        return first / 2L + second / 2L + (first % 2L + second % 2L) / 2L;
    }

    private static boolean sameHorizontalBounds(WorldBounds first, WorldBounds second) {
        return first.minX() == second.minX()
                && first.maxX() == second.maxX()
                && first.minY() == second.minY()
                && first.maxY() == second.maxY();
    }
}
