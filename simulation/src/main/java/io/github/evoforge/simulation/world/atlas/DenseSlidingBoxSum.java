package io.github.evoforge.simulation.world.atlas;

import java.util.Arrays;

/**
 * Exact two-dimensional square-window sums with row-bounded scratch.
 *
 * <p>The kernel is separable but does not retain a full horizontal intermediate. Horizontal sums
 * are streamed into a ring containing at most {@code 2 * radius + 1} rows while one column-window
 * vector accumulates the vertical pass. Work is O(N) and temporary storage is O(width * radius),
 * rather than O(width * height).</p>
 *
 * <p>{@code result} may alias {@code source}. A result row is written only after every source row
 * that can contribute to it has already been consumed into the horizontal ring, so in-place
 * transformation is safe. One {@link Workspace} can be reused for multiple semantic fields and
 * radii up to its configured maximum.</p>
 */
final class DenseSlidingBoxSum {
    private DenseSlidingBoxSum() {
    }

    static Workspace workspace(int width, int height, int maximumRadius) {
        return new Workspace(width, height, maximumRadius);
    }

    static void sumInto(
            long[] source,
            int width,
            int height,
            int radius,
            long[] result,
            Workspace workspace) {
        requireValid(source, width, height, radius, result, workspace);
        workspace.sum(source, radius, result);
    }

    static final class Workspace {
        private final int width;
        private final int height;
        private final int maximumRadius;
        private final int maximumRingRows;
        private final long[] horizontalRing;
        private final long[] verticalWindow;

        private Workspace(int width, int height, int maximumRadius) {
            if (width <= 0 || height <= 0 || maximumRadius < 0) {
                throw new IllegalArgumentException("dense box-sum workspace dimensions/radius are invalid");
            }
            this.width = width;
            this.height = height;
            this.maximumRadius = maximumRadius;
            this.maximumRingRows = Math.min(
                    height,
                    Math.addExact(Math.multiplyExact(maximumRadius, 2), 1));
            this.horizontalRing = new long[Math.multiplyExact(width, maximumRingRows)];
            this.verticalWindow = new long[width];
        }

        private void sum(long[] source, int radius, long[] result) {
            Arrays.fill(verticalWindow, 0L);
            int activeRingRows = Math.min(
                    height,
                    Math.addExact(Math.multiplyExact(radius, 2), 1));
            int nextOutputY = 0;
            int currentTopY = 0;

            for (int sourceY = 0; sourceY < height; sourceY++) {
                int slot = sourceY % activeRingRows;
                int ringOffset = slot * width;
                int leavingY = sourceY - activeRingRows;
                if (leavingY >= 0) {
                    for (int x = 0; x < width; x++) {
                        verticalWindow[x] = Math.subtractExact(
                                verticalWindow[x],
                                horizontalRing[ringOffset + x]);
                    }
                    currentTopY = leavingY + 1;
                }

                horizontalRowInto(source, sourceY, radius, horizontalRing, ringOffset);
                for (int x = 0; x < width; x++) {
                    verticalWindow[x] = Math.addExact(
                            verticalWindow[x],
                            horizontalRing[ringOffset + x]);
                }

                if (sourceY >= radius) {
                    int outputY = sourceY - radius;
                    copyWindow(result, outputY);
                    nextOutputY = outputY + 1;
                }
            }

            for (int outputY = nextOutputY; outputY < height; outputY++) {
                int desiredTopY = Math.max(0, outputY - radius);
                while (currentTopY < desiredTopY) {
                    int slot = currentTopY % activeRingRows;
                    int ringOffset = slot * width;
                    for (int x = 0; x < width; x++) {
                        verticalWindow[x] = Math.subtractExact(
                                verticalWindow[x],
                                horizontalRing[ringOffset + x]);
                    }
                    currentTopY++;
                }
                copyWindow(result, outputY);
            }
        }

        private void horizontalRowInto(
                long[] source,
                int y,
                int radius,
                long[] result,
                int resultOffset) {
            int sourceOffset = y * width;
            long window = 0L;
            int initialRight = Math.min(width - 1, radius);
            for (int x = 0; x <= initialRight; x++) {
                window = Math.addExact(window, source[sourceOffset + x]);
            }

            for (int x = 0; x < width; x++) {
                result[resultOffset + x] = window;
                int leavingX = x - radius;
                if (leavingX >= 0) {
                    window = Math.subtractExact(window, source[sourceOffset + leavingX]);
                }
                int enteringX = x + radius + 1;
                if (enteringX < width) {
                    window = Math.addExact(window, source[sourceOffset + enteringX]);
                }
            }
        }

        private void copyWindow(long[] result, int outputY) {
            System.arraycopy(verticalWindow, 0, result, outputY * width, width);
        }
    }

    private static void requireValid(
            long[] source,
            int width,
            int height,
            int radius,
            long[] result,
            Workspace workspace) {
        if (source == null || result == null || workspace == null) {
            throw new IllegalArgumentException("dense box-sum inputs must not be null");
        }
        if (width <= 0 || height <= 0 || radius < 0) {
            throw new IllegalArgumentException("dense box-sum dimensions/radius are invalid");
        }
        int area = Math.multiplyExact(width, height);
        if (source.length != area || result.length != area) {
            throw new IllegalArgumentException("dense box-sum fields must match horizontal area");
        }
        if (workspace.width != width || workspace.height != height || radius > workspace.maximumRadius) {
            throw new IllegalArgumentException("dense box-sum workspace does not cover requested operation");
        }
    }
}
