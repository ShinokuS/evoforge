package io.github.evoforge.simulation.world.geophysics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;

final class MacroGeophysicalStructureTest {
    private static final long SEED = 0x0123456789ABCDEFL;
    private static final long REVISION = 7L;
    private static final MacroGeophysicsDefinition DEFINITION = MacroGeophysicsPreset.BALANCED.definition();

    @Test
    void publicFactoryReturnsElevationAndStructuralCapabilitiesTogether() {
        MacroGeophysicalModel model = model();

        assertEquals(-0.12400000000000001d, model.elevationAt(0L, 0L));
        MacroGeophysicalStructure structure = model.structureAt(0L, 0L);
        assertTrue(structure.continentalSupport() >= -1.0d);
        assertTrue(structure.continentalSupport() <= 1.0d);
    }

    @Test
    void structuralContextIsDeterministicAndQueryOrderIndependent() {
        MacroGeophysicalModel model = model();
        List<long[]> coordinates = List.of(
                new long[] {10L, 20L},
                new long[] {900_000L, 1_200_000L},
                new long[] {-700_000L, 333_333L},
                new long[] {1L << 35, -(1L << 34)});

        List<MacroGeophysicalStructure> before = coordinates.stream()
                .map(point -> model.structureAt(point[0], point[1]))
                .toList();

        for (int index = coordinates.size() - 1; index >= 0; index--) {
            long[] point = coordinates.get(index);
            model.structureAt(point[0] + 73_000_000L, point[1] - 41_000_000L);
            model.elevationAt(point[0] - 9_000_000L, point[1] + 17_000_000L);
        }

        for (int index = 0; index < coordinates.size(); index++) {
            long[] point = coordinates.get(index);
            assertEquals(before.get(index), model.structureAt(point[0], point[1]));
        }
    }

    @Test
    void structuralSamplesRespectPublicRangesAndBoundarySemantics() {
        MacroGeophysicalModel model = model();
        EnumSet<MacroGeophysicalBoundaryRegime> regimes =
                EnumSet.noneOf(MacroGeophysicalBoundaryRegime.class);

        for (long y = -8_000_000L; y <= 8_000_000L; y += 250_000L) {
            for (long x = -8_000_000L; x <= 8_000_000L; x += 250_000L) {
                MacroGeophysicalStructure sample = model.structureAt(x, y);
                assertTrue(sample.continentalSupport() >= -1.0d && sample.continentalSupport() <= 1.0d);
                assertTrue(sample.marginInfluence() >= 0.0d && sample.marginInfluence() <= 1.0d);
                assertTrue(sample.boundaryInfluence() >= 0.0d && sample.boundaryInfluence() <= 1.0d);
                assertTrue(sample.boundaryStrength() >= 0.0d && sample.boundaryStrength() <= 1.0d);
                assertNotEquals(sample.primaryRegion(), sample.secondaryRegion());
                assertEquals(
                        1.0d,
                        Math.hypot(sample.boundaryNormalX(), sample.boundaryNormalY()),
                        1.0e-9d);
                assertEquals(
                        sample.boundaryInfluence() < 0.05d,
                        sample.boundaryRegime() == MacroGeophysicalBoundaryRegime.INTERIOR);
                regimes.add(sample.boundaryRegime());
            }
        }

        assertTrue(regimes.contains(MacroGeophysicalBoundaryRegime.INTERIOR));
        regimes.remove(MacroGeophysicalBoundaryRegime.INTERIOR);
        assertTrue(regimes.size() >= 2, "representative macro world should expose multiple active boundary regimes");
    }

    @Test
    void deepInteriorKeepsStableStructuralRegionAcrossNearbyCoordinates() {
        MacroGeophysicalModel model = model();
        long[] interior = findInterior(model);
        MacroGeophysicalStructure center = model.structureAt(interior[0], interior[1]);

        for (long dy : new long[] {-1_000L, 0L, 1_000L}) {
            for (long dx : new long[] {-1_000L, 0L, 1_000L}) {
                MacroGeophysicalStructure nearby = model.structureAt(interior[0] + dx, interior[1] + dy);
                assertEquals(center.primaryRegion(), nearby.primaryRegion());
            }
        }
    }

    @Test
    void seedAndRevisionParticipateInStructuralTruth() {
        long x = 3_456_789L;
        long y = 7_654_321L;
        MacroGeophysicalStructure baseline = model().structureAt(x, y);

        MacroGeophysicalStructure differentSeed =
                MacroGeophysics.create(SEED + 1L, REVISION, DEFINITION).structureAt(x, y);
        MacroGeophysicalStructure differentRevision =
                MacroGeophysics.create(SEED, REVISION + 1L, DEFINITION).structureAt(x, y);

        assertNotEquals(baseline.primaryRegion(), differentSeed.primaryRegion());
        assertNotEquals(baseline.primaryRegion(), differentRevision.primaryRegion());
    }

    @Test
    void structuralReadsCannotMutateAcceptedMacroElevation() {
        MacroGeophysicalModel model = model();
        long x = 4_321_987L;
        long y = -8_765_123L;
        double before = model.elevationAt(x, y);

        for (int index = 0; index < 100; index++) {
            model.structureAt(x + index * 91_337L, y - index * 47_111L);
        }

        assertEquals(before, model.elevationAt(x, y));
    }

    private static MacroGeophysicalModel model() {
        return MacroGeophysics.create(SEED, REVISION, DEFINITION);
    }

    private static long[] findInterior(MacroGeophysicalModel model) {
        for (long y = -6_000_000L; y <= 6_000_000L; y += 100_000L) {
            for (long x = -6_000_000L; x <= 6_000_000L; x += 100_000L) {
                MacroGeophysicalStructure sample = model.structureAt(x, y);
                if (sample.boundaryInfluence() < 0.01d) return new long[] {x, y};
            }
        }
        throw new AssertionError("representative scan did not find a structural-region interior");
    }
}
