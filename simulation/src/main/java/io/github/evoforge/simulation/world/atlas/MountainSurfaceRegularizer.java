package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.spatial.WorldBounds;

/**
 * Geometry-only cleanup for voxel-scale readability of the final V13 mountain surface.
 *
 * <p>This class deliberately knows nothing about runtime Shapes. It only observes authoritative
 * elevation, horizontal adjacency and discrete vertical cell levels. Dedicated mountains are given
 * a small transition halo in which the composed surface may absorb V12-scale bumps or dips until it
 * obeys the same abstract cardinal-rise budget used by mountain calibration. One-cell contour noise
 * is then removed where doing so remains inside that budget.</p>
 */
final class MountainSurfaceRegularizer {
    private static final long CELL = ElevationField.SUBUNITS_PER_CELL;
    private static final long NO_SUGGESTION = Long.MIN_VALUE;
    private static final int TRANSITION_HALO_CELLS = 4;
    private static final int MAX_SURFACE_RELAXATION_PASSES = 128;

    private MountainSurfaceRegularizer() {
    }

    static ElevationField removeIsolatedSingleCellLevels(
            ElevationField base,
            ElevationField generated,
            long maximumCardinalRise,
            int passes) {
        if (base == null || generated == null) {
            throw new IllegalArgumentException("mountain surface inputs must not be null");
        }
        if (maximumCardinalRise <= 0L) {
            throw new IllegalArgumentException("maximumCardinalRise must be positive");
        }
        if (passes < 0) throw new IllegalArgumentException("cleanup passes must be non-negative");
        if (!sameHorizontalBounds(base.bounds(), generated.bounds())) {
            throw new IllegalArgumentException("base and generated surfaces must share horizontal bounds");
        }

        WorldBounds bounds = generated.bounds();
        int width = Math.toIntExact((long) bounds.maxX() - bounds.minX() + 1L);
        int height = Math.toIntExact((long) bounds.maxY() - bounds.minY() + 1L);
        int area = Math.toIntExact(Math.multiplyExact((long) width, height));
        long ceiling = Math.multiplyExact((long) bounds.maxZ(), CELL);

        long[] surface = new long[area];
        boolean[] land = new boolean[area];
        boolean[] mountainInfluence = new boolean[area];
        boolean anyMountainInfluence = false;
        int index = 0;
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                long value = generated.elevationSubunitsAt(x, y);
                long baseValue = base.elevationSubunitsAt(x, y);
                surface[index] = value;
                land[index] = value > ElevationGenerationStage.SEA_LEVEL_SUBUNITS;
                mountainInfluence[index] = land[index] && value != baseValue;
                anyMountainInfluence |= mountainInfluence[index];
                index++;
            }
        }
        if (!anyMountainInfluence) return generated;

        // The generic coherent surface fitter requires local support across several cells. A four
        // cell halo lets a dedicated mountain absorb the last small V12 undulations at its boundary
        // instead of creating a one-cell seam where the two surfaces meet.
        dilateInfluence(
                mountainInfluence,
                land,
                width,
                height,
                TRANSITION_HALO_CELLS);
        relaxFinalSurface(
                surface,
                land,
                mountainInfluence,
                width,
                height,
                maximumCardinalRise,
                ceiling,
                MAX_SURFACE_RELAXATION_PASSES);

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
                    replacement = Math.max(1L, Math.min(ceiling, replacement));
                    if (fitsCardinalRiseBudget(
                            surface,
                            land,
                            width,
                            height,
                            x,
                            y,
                            replacement,
                            maximumCardinalRise)) {
                        scratch[cell] = replacement;
                    }
                }
            }
            long[] swap = surface;
            surface = scratch;
            scratch = swap;
        }

        // Cleanup proposals are evaluated against one immutable pass snapshot. Two neighbouring
        // proposals may therefore both be locally legal yet move apart when committed together.
        // Project the cleaned result onto the same geometry-only rise budget once more so the final
        // authoritative surface, not merely each intermediate proposal, owns the contract.
        relaxFinalSurface(
                surface,
                land,
                mountainInfluence,
                width,
                height,
                maximumCardinalRise,
                ceiling,
                MAX_SURFACE_RELAXATION_PASSES);
        return new DenseElevationField(bounds, surface);
    }

    private static void dilateInfluence(
            boolean[] influence,
            boolean[] land,
            int width,
            int height,
            int cells) {
        if (cells <= 0) return;
        boolean[] source = influence.clone();
        boolean[] target = new boolean[influence.length];
        for (int pass = 0; pass < cells; pass++) {
            System.arraycopy(source, 0, target, 0, source.length);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int cell = y * width + x;
                    if (!land[cell] || source[cell]) continue;
                    if ((x > 0 && source[cell - 1])
                            || (x + 1 < width && source[cell + 1])
                            || (y > 0 && source[cell - width])
                            || (y + 1 < height && source[cell + width])) {
                        target[cell] = true;
                    }
                }
            }
            boolean[] swap = source;
            source = target;
            target = swap;
        }
        System.arraycopy(source, 0, influence, 0, influence.length);
    }

    /**
     * Bounded bidirectional projection of the final land surface onto the cardinal rise constraint.
     * Both cells move when both belong to the mountain transition area; at its outer boundary only
     * the mountain-owned side changes and untouched V12 terrain remains authoritative.
     */
    private static void relaxFinalSurface(
            long[] surface,
            boolean[] land,
            boolean[] adjustable,
            int width,
            int height,
            long maximumRise,
            long ceiling,
            int passes) {
        for (int pass = 0; pass < passes; pass++) {
            boolean changed = false;
            if ((pass & 1) == 0) {
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int cell = y * width + x;
                        if (!land[cell]) continue;
                        if (x + 1 < width) {
                            changed |= relaxPair(
                                    cell,
                                    cell + 1,
                                    surface,
                                    land,
                                    adjustable,
                                    maximumRise,
                                    ceiling);
                        }
                        if (y + 1 < height) {
                            changed |= relaxPair(
                                    cell,
                                    cell + width,
                                    surface,
                                    land,
                                    adjustable,
                                    maximumRise,
                                    ceiling);
                        }
                    }
                }
            } else {
                for (int y = height - 1; y >= 0; y--) {
                    for (int x = width - 1; x >= 0; x--) {
                        int cell = y * width + x;
                        if (!land[cell]) continue;
                        if (x > 0) {
                            changed |= relaxPair(
                                    cell,
                                    cell - 1,
                                    surface,
                                    land,
                                    adjustable,
                                    maximumRise,
                                    ceiling);
                        }
                        if (y > 0) {
                            changed |= relaxPair(
                                    cell,
                                    cell - width,
                                    surface,
                                    land,
                                    adjustable,
                                    maximumRise,
                                    ceiling);
                        }
                    }
                }
            }
            if (!changed) return;
        }
    }

    private static boolean relaxPair(
            int first,
            int second,
            long[] surface,
            boolean[] land,
            boolean[] adjustable,
            long maximumRise,
            long ceiling) {
        if (!land[first] || !land[second]) return false;
        long difference = surface[first] - surface[second];
        if (absolute(difference) <= maximumRise) return false;

        int high = difference > 0L ? first : second;
        int low = difference > 0L ? second : first;
        boolean highAdjustable = adjustable[high];
        boolean lowAdjustable = adjustable[low];
        if (!highAdjustable && !lowAdjustable) return false;

        long excess = surface[high] - surface[low] - maximumRise;
        long highDownCapacity = highAdjustable ? Math.max(0L, surface[high] - 1L) : 0L;
        long lowUpCapacity = lowAdjustable ? Math.max(0L, ceiling - surface[low]) : 0L;
        if (highDownCapacity + lowUpCapacity <= 0L) return false;

        long down = 0L;
        long up = 0L;
        if (highAdjustable && lowAdjustable) {
            down = Math.min(highDownCapacity, (excess + 1L) / 2L);
            up = Math.min(lowUpCapacity, excess - down);
            long remaining = excess - down - up;
            if (remaining > 0L) {
                long extraDown = Math.min(highDownCapacity - down, remaining);
                down += extraDown;
                remaining -= extraDown;
            }
            if (remaining > 0L) {
                up += Math.min(lowUpCapacity - up, remaining);
            }
        } else if (highAdjustable) {
            down = Math.min(highDownCapacity, excess);
        } else {
            up = Math.min(lowUpCapacity, excess);
        }

        if (down <= 0L && up <= 0L) return false;
        surface[high] -= down;
        surface[low] += up;
        return true;
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

    private static boolean fitsCardinalRiseBudget(
            long[] surface,
            boolean[] land,
            int width,
            int height,
            int x,
            int y,
            long replacement,
            long maximumRise) {
        int cell = y * width + x;
        if (x > 0 && land[cell - 1]
                && absolute(replacement - surface[cell - 1]) > maximumRise) return false;
        if (x + 1 < width && land[cell + 1]
                && absolute(replacement - surface[cell + 1]) > maximumRise) return false;
        if (y > 0 && land[cell - width]
                && absolute(replacement - surface[cell - width]) > maximumRise) return false;
        return y + 1 >= height
                || !land[cell + width]
                || absolute(replacement - surface[cell + width]) <= maximumRise;
    }

    private static long midpoint(long first, long second) {
        return first / 2L + second / 2L + (first % 2L + second % 2L) / 2L;
    }

    private static long absolute(long value) {
        if (value == Long.MIN_VALUE) {
            throw new ArithmeticException("surface difference exceeds signed range");
        }
        return Math.abs(value);
    }

    private static boolean sameHorizontalBounds(WorldBounds first, WorldBounds second) {
        return first.minX() == second.minX()
                && first.maxX() == second.maxX()
                && first.minY() == second.minY()
                && first.maxY() == second.maxY();
    }
}
