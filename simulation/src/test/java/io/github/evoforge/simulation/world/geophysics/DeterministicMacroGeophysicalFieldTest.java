package io.github.evoforge.simulation.world.geophysics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

final class DeterministicMacroGeophysicalFieldTest {
    private static final long SEED = 0x0123456789ABCDEFL;
    private static final long REVISION = 7L;
    private static final MacroGeophysicsDefinition DEFINITION = MacroGeophysicsPreset.BALANCED.definition();

    @Test
    void fixedCoordinatesHaveStableRegressionValues() {
        DeterministicMacroGeophysicalField field = field();

        assertEquals(0.45772718566638565d, field.elevationAt(0L, 0L));
        assertEquals(0.253988623047904d, field.elevationAt(123_456L, 789_012L));
        assertEquals(-0.5713694549443915d, field.elevationAt(-987_654_321L, 123_456_789L));
        assertEquals(0.32868554974810266d, field.elevationAt(1L << 40, -(1L << 39)));
    }

    @Test
    void queryOrderDoesNotChangeWorldTruth() {
        DeterministicMacroGeophysicalField field = field();
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
        double baseline = field().elevationAt(x, y);

        assertNotEquals(
                baseline,
                new DeterministicMacroGeophysicalField(SEED + 1L, REVISION, DEFINITION).elevationAt(x, y));
        assertNotEquals(
                baseline,
                new DeterministicMacroGeophysicalField(SEED, REVISION + 1L, DEFINITION).elevationAt(x, y));
    }

    @Test
    void oceanIsDerivedOnlyFromMacroElevationAndSeaDatum() {
        DeterministicMacroGeophysicalField field = field();

        for (long y = -3_000_000L; y <= 3_000_000L; y += 173_021L) {
            for (long x = -3_000_000L; x <= 3_000_000L; x += 191_237L) {
                double elevation = field.elevationAt(x, y);
                assertTrue(elevation >= -1.0d && elevation <= 1.0d);
                assertEquals(elevation < MacroGeophysicalField.SEA_DATUM, field.isOceanAt(x, y));
            }
        }
    }

    @Test
    void increasingOceanPrevalenceMovesTheSharedFieldTowardOcean() {
        MacroGeophysicsDefinition dry = MacroGeophysicsDefinition.of(0.20d, 0.60d, 0.55d, 0.35d, 0.50d);
        MacroGeophysicsDefinition wet = MacroGeophysicsDefinition.of(0.80d, 0.60d, 0.55d, 0.35d, 0.50d);
        MacroGeophysicalField dryField = MacroGeophysics.create(SEED, REVISION, dry);
        MacroGeophysicalField wetField = MacroGeophysics.create(SEED, REVISION, wet);

        for (long y = 0L; y <= 8_000_000L; y += 1_000_000L) {
            for (long x = 0L; x <= 8_000_000L; x += 1_000_000L) {
                assertTrue(wetField.elevationAt(x, y) < dryField.elevationAt(x, y));
            }
        }
    }

    @Test
    void archipelagoPresetProducesMoreCoastTransitionsThanSupercontinentPreset() {
        MacroGeophysicalField supercontinent = MacroGeophysics.create(
                0x45A10F0E2026L,
                1L,
                MacroGeophysicsPreset.SUPERCONTINENT.definition());
        MacroGeophysicalField archipelago = MacroGeophysics.create(
                0x45A10F0E2026L,
                1L,
                MacroGeophysicsPreset.ARCHIPELAGO.definition());

        int supercontinentTransitions = coastTransitions(supercontinent, 64, 16_000_000L);
        int archipelagoTransitions = coastTransitions(archipelago, 64, 16_000_000L);

        assertTrue(
                archipelagoTransitions > supercontinentTransitions,
                "archipelago intent should produce a more fragmented macro coastline");
    }

    private static DeterministicMacroGeophysicalField field() {
        return new DeterministicMacroGeophysicalField(SEED, REVISION, DEFINITION);
    }

    private static int coastTransitions(MacroGeophysicalField field, int sideSamples, long logicalSide) {
        boolean[][] land = new boolean[sideSamples][sideSamples];
        for (int y = 0; y < sideSamples; y++) {
            long worldY = Math.round(y * (logicalSide - 1d) / (sideSamples - 1d));
            for (int x = 0; x < sideSamples; x++) {
                long worldX = Math.round(x * (logicalSide - 1d) / (sideSamples - 1d));
                land[y][x] = !field.isOceanAt(worldX, worldY);
            }
        }

        int transitions = 0;
        for (int y = 0; y < sideSamples; y++) {
            for (int x = 0; x < sideSamples - 1; x++) {
                if (land[y][x] != land[y][x + 1]) transitions++;
            }
        }
        for (int y = 0; y < sideSamples - 1; y++) {
            for (int x = 0; x < sideSamples; x++) {
                if (land[y][x] != land[y + 1][x]) transitions++;
            }
        }
        return transitions;
    }
}
