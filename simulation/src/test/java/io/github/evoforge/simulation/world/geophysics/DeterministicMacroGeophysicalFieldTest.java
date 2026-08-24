package io.github.evoforge.simulation.world.geophysics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

final class DeterministicMacroGeophysicalFieldTest {
    private static final long SEED = 0x0123456789ABCDEFL;
    private static final long REVISION = 7L;

    @Test
    void fixedCoordinatesHaveStableRegressionValues() {
        DeterministicMacroGeophysicalField field = new DeterministicMacroGeophysicalField(SEED, REVISION);

        assertEquals(0.461231142224015d, field.elevationAt(0L, 0L));
        assertEquals(0.08748701188659602d, field.elevationAt(123_456L, 789_012L));
        assertEquals(-0.33215471345310726d, field.elevationAt(-987_654_321L, 123_456_789L));
        assertEquals(-0.4863857640259379d, field.elevationAt(1L << 40, -(1L << 39)));
    }

    @Test
    void queryOrderDoesNotChangeWorldTruth() {
        DeterministicMacroGeophysicalField field = new DeterministicMacroGeophysicalField(SEED, REVISION);
        List<long[]> coordinates = List.of(
                new long[] {10L, 20L},
                new long[] {900_000L, 1_200_000L},
                new long[] {-700_000L, 333_333L},
                new long[] {1L << 35, -(1L << 34)});

        double[] forward = coordinates.stream()
                .mapToDouble(point -> field.elevationAt(point[0], point[1]))
                .toArray();

        for (int index = coordinates.size() - 1; index >= 0; index--) {
            long[] point = coordinates.get(index);
            field.elevationAt(point[0], point[1]);
        }

        double[] afterUnrelatedOrder = coordinates.stream()
                .mapToDouble(point -> field.elevationAt(point[0], point[1]))
                .toArray();

        for (int index = 0; index < forward.length; index++) {
            assertEquals(forward[index], afterUnrelatedOrder[index]);
        }
    }

    @Test
    void seedAndRevisionParticipateInAddressedTruth() {
        long x = 3_456_789L;
        long y = 7_654_321L;
        double baseline = new DeterministicMacroGeophysicalField(SEED, REVISION).elevationAt(x, y);

        assertNotEquals(
                baseline,
                new DeterministicMacroGeophysicalField(SEED + 1L, REVISION).elevationAt(x, y));
        assertNotEquals(
                baseline,
                new DeterministicMacroGeophysicalField(SEED, REVISION + 1L).elevationAt(x, y));
    }

    @Test
    void oceanIsDerivedOnlyFromMacroElevationAndSeaDatum() {
        DeterministicMacroGeophysicalField field = new DeterministicMacroGeophysicalField(SEED, REVISION);

        for (long y = -3_000_000L; y <= 3_000_000L; y += 173_021L) {
            for (long x = -3_000_000L; x <= 3_000_000L; x += 191_237L) {
                double elevation = field.elevationAt(x, y);
                assertTrue(elevation >= -1.0d && elevation <= 1.0d);
                assertEquals(elevation < MacroGeophysicalField.SEA_DATUM, field.isOceanAt(x, y));
            }
        }
    }
}
