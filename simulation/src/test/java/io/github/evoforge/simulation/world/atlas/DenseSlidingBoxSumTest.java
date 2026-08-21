package io.github.evoforge.simulation.world.atlas;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Random;
import org.junit.jupiter.api.Test;

final class DenseSlidingBoxSumTest {

    @Test
    void matchesReferenceRectangleSumAcrossRepresentativeDimensionsAndRadii() {
        int[][] dimensions = {
            {1, 1},
            {2, 3},
            {7, 5},
            {17, 13},
            {64, 33}
        };
        int[] radii = {0, 1, 2, 5, 19, 96};
        Random random = new Random(91_337L);

        for (int[] dimension : dimensions) {
            int width = dimension[0];
            int height = dimension[1];
            int area = Math.multiplyExact(width, height);
            long[] source = new long[area];
            for (int cell = 0; cell < area; cell++) {
                source[cell] = random.nextInt(1_000_001);
            }

            for (int radius : radii) {
                long[] scratch = new long[area];
                long[] actual = new long[area];
                DenseSlidingBoxSum.sumInto(source, width, height, radius, scratch, actual);
                assertArrayEquals(
                        reference(source, width, height, radius),
                        actual,
                        "sliding box sum mismatch for " + width + "x" + height + " radius=" + radius);
            }
        }
    }

    @Test
    void rejectsAliasingBecauseCallerOwnedScratchHasExplicitLifetime() {
        long[] source = {1L, 2L, 3L, 4L};
        long[] output = new long[4];
        assertThrows(
                IllegalArgumentException.class,
                () -> DenseSlidingBoxSum.sumInto(source, 2, 2, 1, source, output));
        assertThrows(
                IllegalArgumentException.class,
                () -> DenseSlidingBoxSum.sumInto(source, 2, 2, 1, output, output));
    }

    private static long[] reference(long[] source, int width, int height, int radius) {
        long[] result = new long[source.length];
        for (int y = 0; y < height; y++) {
            int minY = Math.max(0, y - radius);
            int maxY = Math.min(height - 1, y + radius);
            for (int x = 0; x < width; x++) {
                int minX = Math.max(0, x - radius);
                int maxX = Math.min(width - 1, x + radius);
                long sum = 0L;
                for (int sourceY = minY; sourceY <= maxY; sourceY++) {
                    int row = sourceY * width;
                    for (int sourceX = minX; sourceX <= maxX; sourceX++) {
                        sum = Math.addExact(sum, source[row + sourceX]);
                    }
                }
                result[y * width + x] = sum;
            }
        }
        return result;
    }
}
