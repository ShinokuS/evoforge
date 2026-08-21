package io.github.evoforge.simulation.world.atlas;

/**
 * Allocation-free two-dimensional square-window sums over dense long fields.
 *
 * <p>The caller owns both full-field buffers so a generation stage can explicitly reuse scratch
 * whose lifetime does not overlap with another fact. The kernel performs one horizontal and one
 * vertical sliding pass, making work O(N) independently of the requested radius.</p>
 */
final class DenseSlidingBoxSum {
    private DenseSlidingBoxSum() {
    }

    static void sumInto(
            long[] source,
            int width,
            int height,
            int radius,
            long[] horizontalScratch,
            long[] result) {
        requireValid(source, width, height, radius, horizontalScratch, result);
        horizontalInto(source, width, height, radius, horizontalScratch);
        verticalInto(horizontalScratch, width, height, radius, result);
    }

    private static void horizontalInto(
            long[] source,
            int width,
            int height,
            int radius,
            long[] result) {
        for (int y = 0; y < height; y++) {
            int row = y * width;
            long window = 0L;
            int initialRight = Math.min(width - 1, radius);
            for (int x = 0; x <= initialRight; x++) {
                window = Math.addExact(window, source[row + x]);
            }

            for (int x = 0; x < width; x++) {
                result[row + x] = window;
                int leaving = x - radius;
                if (leaving >= 0) {
                    window = Math.subtractExact(window, source[row + leaving]);
                }
                int entering = x + radius + 1;
                if (entering < width) {
                    window = Math.addExact(window, source[row + entering]);
                }
            }
        }
    }

    private static void verticalInto(
            long[] source,
            int width,
            int height,
            int radius,
            long[] result) {
        for (int x = 0; x < width; x++) {
            long window = 0L;
            int initialBottom = Math.min(height - 1, radius);
            for (int y = 0; y <= initialBottom; y++) {
                window = Math.addExact(window, source[y * width + x]);
            }

            for (int y = 0; y < height; y++) {
                result[y * width + x] = window;
                int leaving = y - radius;
                if (leaving >= 0) {
                    window = Math.subtractExact(window, source[leaving * width + x]);
                }
                int entering = y + radius + 1;
                if (entering < height) {
                    window = Math.addExact(window, source[entering * width + x]);
                }
            }
        }
    }

    private static void requireValid(
            long[] source,
            int width,
            int height,
            int radius,
            long[] horizontalScratch,
            long[] result) {
        if (source == null || horizontalScratch == null || result == null) {
            throw new IllegalArgumentException("dense box-sum buffers must not be null");
        }
        if (width <= 0 || height <= 0 || radius < 0) {
            throw new IllegalArgumentException("dense box-sum dimensions/radius are invalid");
        }
        int area = Math.multiplyExact(width, height);
        if (source.length != area || horizontalScratch.length != area || result.length != area) {
            throw new IllegalArgumentException("dense box-sum buffers must match horizontal area");
        }
        if (source == horizontalScratch
                || source == result
                || horizontalScratch == result) {
            throw new IllegalArgumentException("dense box-sum buffers must not alias");
        }
    }
}
