package io.github.evoforge.simulation.world.atlas;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

final class PositiveDoubleRadixSelectorTest {

    @Test
    void matchesArraysSortForEveryPositiveOrderStatistic() {
        double[] source = {
            Double.NaN,
            Double.NEGATIVE_INFINITY,
            -17.25d,
            -0.0d,
            0.0d,
            Double.MIN_VALUE,
            Double.MIN_NORMAL,
            0.000_001d,
            0.125d,
            0.125d,
            0.5d,
            1.0d,
            1.0d,
            Math.nextUp(1.0d),
            17.25d,
            Double.MAX_VALUE,
            Double.POSITIVE_INFINITY
        };
        double[] positive = Arrays.stream(source)
                .filter(value -> value > 0d)
                .toArray();
        Arrays.sort(positive);

        for (int rank = 0; rank < positive.length; rank++) {
            double selected = PositiveDoubleRadixSelector.select(source, positive.length, rank);
            assertEquals(
                    Double.doubleToRawLongBits(positive[rank]),
                    Double.doubleToRawLongBits(selected),
                    "radix selector changed sorted positive rank " + rank);
        }
    }

    @Test
    void matchesSortAcrossDeterministicDensePopulationAndDuplicateCutoffs() {
        double[] source = new double[20_000];
        long state = 0x9e3779b97f4a7c15L;
        for (int index = 0; index < source.length; index++) {
            state ^= state >>> 12;
            state ^= state << 25;
            state ^= state >>> 27;
            long mixed = state * 0x2545f4914f6cdd1dL;
            if ((index & 15) == 0) {
                source[index] = 0.25d;
            } else if ((index & 7) == 0) {
                source[index] = -0.5d;
            } else {
                source[index] = (mixed >>> 11) * 0x1.0p-53;
            }
        }

        double[] positive = Arrays.stream(source)
                .filter(value -> value > 0d)
                .toArray();
        Arrays.sort(positive);
        int[] ranks = {
            0,
            1,
            positive.length / 10,
            positive.length / 2,
            positive.length * 9 / 10,
            positive.length - 2,
            positive.length - 1
        };

        for (int rank : ranks) {
            double selected = PositiveDoubleRadixSelector.select(source, positive.length, rank);
            assertEquals(
                    Double.doubleToRawLongBits(positive[rank]),
                    Double.doubleToRawLongBits(selected),
                    "radix selector changed dense positive rank " + rank);
        }
    }
}
